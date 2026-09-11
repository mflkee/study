use std::collections::HashMap;
use crate::config::settings::DeviceConfig;

/// Карта регистров Modbus
/// 
/// Хранит все данные, доступные по Modbus TCP:
/// - Input Registers: данные с датчиков (температуры, давления)
/// - Holding Registers: команды управления, конфигурация
/// - Coils: статусы устройств
#[derive(Debug, Clone)]
pub struct RegisterMap {
    /// Input Registers (только чтение): температуры, давления
    input_registers: Vec<u16>,
    
    /// Holding Registers (чтение/запись): команды, конфигурация
    holding_registers: Vec<u16>,
    
    /// Coils (биты): статусы устройств
    coils: Vec<bool>,
    
    /// Маппинг: Slave ID → начальный адрес в input_registers
    device_map: HashMap<u8, DeviceMapping>,
    
    /// Конфигурация
    config: DeviceConfig,
}

#[derive(Debug, Clone)]
pub struct DeviceMapping {
    pub slave_id: u8,
    pub name: String,
    pub device_type: String,
    pub input_start: u16,
    pub holding_start: Option<u16>,
    pub coil_address: Option<u16>,
}

impl RegisterMap {
    pub fn new(config: &DeviceConfig) -> Self {
        // Определяем размеры на основе конфигурации
        let max_devices = config.devices.len().max(32);
        
        // Input Registers: 2 на каждый датчик (для float - 2 регистра)
        let input_size = max_devices * 2;
        
        // Holding Registers: команды + конфигурация
        let holding_size = 0x100; // 256 регистров
        
        // Coils: по одному на каждое устройство
        let coil_size = max_devices;
        
        // Создаём маппинг устройств
        let mut device_map = HashMap::new();
        let mut input_offset = 0u16;
        
        for device in &config.devices {
            let mapping = DeviceMapping {
                slave_id: device.id,
                name: device.name.clone(),
                device_type: device.device_type.clone(),
                input_start: input_offset,
                holding_start: device.command_register,
                coil_address: device.status_register,
            };
            
            // Каждый датчик занимает 2 регистра (для float)
            input_offset += 2;
            
            device_map.insert(device.id, mapping);
        }
        
        Self {
            input_registers: vec![0; input_size],
            holding_registers: vec![0; holding_size],
            coils: vec![false; coil_size],
            device_map,
            config: config.clone(),
        }
    }
    
    /// Чтение Input Registers
    pub fn read_input_registers(&self, address: u16, count: u16) -> Result<Vec<u16>, u8> {
        let start = address as usize;
        let end = start + count as usize;
        
        if end > self.input_registers.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        if count == 0 || count > 125 {
            return Err(0x03); // Illegal Data Value
        }
        
        Ok(self.input_registers[start..end].to_vec())
    }
    
    /// Чтение Holding Registers
    pub fn read_holding_registers(&self, address: u16, count: u16) -> Result<Vec<u16>, u8> {
        let start = address as usize;
        let end = start + count as usize;
        
        if end > self.holding_registers.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        if count == 0 || count > 125 {
            return Err(0x03); // Illegal Data Value
        }
        
        Ok(self.holding_registers[start..end].to_vec())
    }
    
    /// Запись одного Holding Register
    pub fn write_holding_register(&mut self, address: u16, value: u16) -> Result<(), u8> {
        let addr = address as usize;
        
        if addr >= self.holding_registers.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        self.holding_registers[addr] = value;
        Ok(())
    }
    
    /// Запись нескольких Holding Registers
    pub fn write_holding_registers(&mut self, address: u16, values: &[u16]) -> Result<(), u8> {
        let start = address as usize;
        let end = start + values.len();
        
        if end > self.holding_registers.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        if values.len() > 123 {
            return Err(0x03); // Illegal Data Value
        }
        
        self.holding_registers[start..end].copy_from_slice(values);
        Ok(())
    }
    
    /// Чтение Coils
    pub fn read_coils(&self, address: u16, count: u16) -> Result<Vec<bool>, u8> {
        let start = address as usize;
        let end = start + count as usize;
        
        if end > self.coils.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        if count == 0 || count > 2000 {
            return Err(0x03); // Illegal Data Value
        }
        
        Ok(self.coils[start..end].to_vec())
    }
    
    /// Запись одного Coil
    pub fn write_coil(&mut self, address: u16, value: bool) -> Result<(), u8> {
        let addr = address as usize;
        
        if addr >= self.coils.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        self.coils[addr] = value;
        Ok(())
    }
    
    /// Обновление данных с датчика (вызывается RTU Master)
    pub fn update_sensor_data(&mut self, slave_id: u8, register: u16, value: u16) -> Result<(), u8> {
        if let Some(mapping) = self.device_map.get(&slave_id) {
            // Определяем тип устройства и обновляем соответствующий регистр
            match mapping.device_type.as_str() {
                "temperature" | "pressure" => {
                    // Два регистра на float значение
                    let addr = mapping.input_start + register;
                    if (addr as usize) < self.input_registers.len() {
                        self.input_registers[addr as usize] = value;
                        Ok(())
                    } else {
                        Err(0x02)
                    }
                }
                "pump" => {
                    // Статус в coils
                    if let Some(coil_addr) = mapping.coil_address {
                        let addr = coil_addr + register;
                        self.write_coil(addr, value != 0)
                    } else {
                        Err(0x02)
                    }
                }
                _ => Err(0x01), // Unsupported device type
            }
        } else {
            Err(0x02) // Unknown slave ID
        }
    }
    
    /// Получение конфигурации устройства по Slave ID
    pub fn get_device_config(&self, slave_id: u8) -> Option<&DeviceMapping> {
        self.device_map.get(&slave_id)
    }
    
    /// Проверка, является ли адрес командой пробоотборника
    pub fn is_pump_command(&self, address: u16) -> bool {
        for device in &self.config.devices {
            if device.device_type == "pump" {
                if let Some(cmd_reg) = device.command_register {
                    if address == cmd_reg {
                        return true;
                    }
                }
            }
        }
        false
    }
    
    /// Получение Slave ID для команды пробоотборника
    pub fn get_pump_slave_for_command(&self, address: u16) -> Option<u8> {
        for device in &self.config.devices {
            if device.device_type == "pump" {
                if let Some(cmd_reg) = device.command_register {
                    if address == cmd_reg {
                        return Some(device.id);
                    }
                }
            }
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_register_map_creation() {
        let config = DeviceConfig::default();
        let map = RegisterMap::new(&config);
        
        assert_eq!(map.input_registers.len(), 64); // 32 устройства × 2
        assert_eq!(map.holding_registers.len(), 256);
    }
    
    #[test]
    fn test_read_write_holding() {
        let config = DeviceConfig::default();
        let mut map = RegisterMap::new(&config);
        
        // Запись
        map.write_holding_register(0, 42).unwrap();
        
        // Чтение
        let values = map.read_holding_registers(0, 1).unwrap();
        assert_eq!(values[0], 42);
    }
    
    #[test]
    fn test_illegal_address() {
        let config = DeviceConfig::default();
        let map = RegisterMap::new(&config);
        
        let result = map.read_holding_registers(300, 10);
        assert_eq!(result, Err(0x02));
    }
    
    #[test]
    fn test_pump_command_detection() {
        let mut config = DeviceConfig::default();
        config.devices.push(crate::config::settings::DeviceEntry {
            id: 3,
            name: "Pump 1".to_string(),
            device_type: "pump".to_string(),
            registers: vec![],
            command_register: Some(0x30),
            status_register: Some(0x20),
        });
        
        let map = RegisterMap::new(&config);
        
        assert!(map.is_pump_command(0x30));
        assert!(!map.is_pump_command(0x00));
        assert_eq!(map.get_pump_slave_for_command(0x30), Some(3));
    }
}
