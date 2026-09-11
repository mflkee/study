use std::sync::Arc;
use tokio::sync::RwLock;
use log::{info, error, debug};

use crate::config::settings::DeviceConfig;
use super::register_map::RegisterMap;

/// Modbus RTU Master для опроса устройств по RS-485
pub struct RtuMaster {
    config: DeviceConfig,
}

impl RtuMaster {
    pub async fn new(config: DeviceConfig) -> Self {
        Self { config }
    }
    
    pub async fn run(&mut self, register_map: Arc<RwLock<RegisterMap>>) {
        info!("RTU Master started, polling every {}ms", self.config.rs485.poll_interval_ms);
        
        loop {
            for device in &self.config.devices {
                self.poll_device(device, &register_map).await;
            }
            
            tokio::time::sleep(
                tokio::time::Duration::from_millis(self.config.rs485.poll_interval_ms)
            ).await;
        }
    }
    
    async fn poll_device(
        &self,
        device: &crate::config::settings::DeviceEntry,
        register_map: &Arc<RwLock<RegisterMap>>,
    ) {
        debug!("Polling device {} (ID: {})", device.name, device.id);
        
        // Формируем RTU запрос
        let request = match device.device_type.as_str() {
            "temperature" | "pressure" => {
                self.build_read_holding_request(device.id, 0, 2)
            }
            "pump" => {
                self.build_read_holding_request(device.id, 0, 1)
            }
            _ => {
                error!("Unknown device type: {}", device.device_type);
                return;
            }
        };
        
        // Отправляем запрос (здесь заглушка - в реальности через UART)
        let response = self.send_rtu_request(&request).await;
        
        match response {
            Ok(data) => {
                // Парсим ответ
                if data.len() >= 5 {
                    let fc = data[1];
                    let byte_count = data[2] as usize;
                    
                    if fc == 0x03 && data.len() >= 3 + byte_count {
                        // Read Holding Registers response
                        let mut values = Vec::new();
                        for i in (3..3 + byte_count).step_by(2) {
                            if i + 1 < data.len() {
                                let value = u16::from_be_bytes([data[i], data[i + 1]]);
                                values.push(value);
                            }
                        }
                        
                        // Обновляем карту регистров
                        let mut map = register_map.write().await;
                        for (i, &value) in values.iter().enumerate() {
                            if let Some(reg) = device.registers.get(i) {
                                if let Err(e) = map.update_sensor_data(device.id, *reg, value) {
                                    error!("Failed to update register: {}", e);
                                }
                            }
                        }
                        
                        debug!("Device {} updated: {:?}", device.name, values);
                    }
                }
            }
            Err(e) => {
                error!("RTU request failed for device {}: {}", device.name, e);
            }
        }
    }
    
    fn build_read_holding_request(&self, slave_id: u8, start: u16, quantity: u16) -> Vec<u8> {
        let mut request = Vec::with_capacity(8);
        
        // Slave ID
        request.push(slave_id);
        // Function code: Read Holding Registers
        request.push(0x03);
        // Start address (big-endian)
        request.extend_from_slice(&start.to_be_bytes());
        // Quantity (big-endian)
        request.extend_from_slice(&quantity.to_be_bytes());
        
        // CRC-16
        let crc = self.calculate_crc16(&request);
        request.extend_from_slice(&crc.to_le_bytes());
        
        request
    }
    
    fn calculate_crc16(&self, data: &[u8]) -> u16 {
        let mut crc: u16 = 0xFFFF;
        
        for &byte in data {
            crc ^= byte as u16;
            for _ in 0..8 {
                if crc & 1 != 0 {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        
        crc
    }
    
    async fn send_rtu_request(&self, request: &[u8]) -> Result<Vec<u8>, String> {
        // Заглушка: в реальности здесь будет работа с UART
        // Для тестирования возвращаем имитированный ответ
        
        debug!("Sending RTU request: {:02X?}", request);
        
        // Имитация задержки
        tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;
        
        // Имитация ответа (3 регистра с значениями)
        let mut response = Vec::new();
        response.push(request[0]); // Slave ID
        response.push(0x03); // Function code
        response.push(6); // Byte count (3 регистра × 2 байта)
        response.extend_from_slice(&[0x41, 0xC8]); // 25.0 float
        response.extend_from_slice(&[0x42, 0x48]); // 50.0 float
        response.extend_from_slice(&[0x42, 0xC8]); // 100.0 float
        
        // CRC
        let crc = self.calculate_crc16(&response);
        response.extend_from_slice(&crc.to_le_bytes());
        
        Ok(response)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_crc16() {
        let config = DeviceConfig::default();
        let master = RtuMaster::new(config).await;
        
        // Тестовый пример
        let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
        let crc = master.calculate_crc16(&data);
        
        // Проверяем что CRC не нулевой
        assert_ne!(crc, 0);
    }
}
