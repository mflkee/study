/// Register map для Modbus RTU slave на ESP32

use std::collections::HashMap;

pub struct RegisterMap {
    input_registers: HashMap<u16, u16>,
    holding_registers: HashMap<u16, u16>,
    coils: HashMap<u16, bool>,
}

impl RegisterMap {
    pub fn new() -> Self {
        Self {
            input_registers: HashMap::new(),
            holding_registers: HashMap::new(),
            coils: HashMap::new(),
        }
    }
    
    pub fn set_input_register(&mut self, addr: u16, value: u16) {
        self.input_registers.insert(addr, value);
    }
    
    pub fn read_input_registers(&self, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        for i in 0..count {
            match self.input_registers.get(&(start + i)) {
                Some(&val) => result.push(val),
                None => return Err(0x02),
            }
        }
        Ok(result)
    }
    
    pub fn set_holding_register(&mut self, addr: u16, value: u16) {
        self.holding_registers.insert(addr, value);
    }
    
    pub fn read_holding_registers(&self, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        for i in 0..count {
            match self.holding_registers.get(&(start + i)) {
                Some(&val) => result.push(val),
                None => return Err(0x02),
            }
        }
        Ok(result)
    }
    
    pub fn write_holding_register(&mut self, addr: u16, value: u16) -> Result<(), u8> {
        self.holding_registers.insert(addr, value);
        Ok(())
    }
    
    pub fn set_coil(&mut self, addr: u16, value: bool) {
        self.coils.insert(addr, value);
    }
    
    pub fn read_coils(&self, start: u16, count: u16) -> Result<Vec<bool>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        for i in 0..count {
            match self.coils.get(&(start + i)) {
                Some(&val) => result.push(val),
                None => return Err(0x02),
            }
        }
        Ok(result)
    }
    
    pub fn write_coil(&mut self, addr: u16, value: bool) -> Result<(), u8> {
        self.coils.insert(addr, value);
        Ok(())
    }
    
    pub fn update_sensor_data(&mut self, base_addr: u16, index: u16, value: f32) {
        let bytes = value.to_be_bytes();
        self.set_input_register(base_addr + index * 2, u16::from_be_bytes([bytes[0], bytes[1]]));
        self.set_input_register(base_addr + index * 2 + 1, u16::from_be_bytes([bytes[2], bytes[3]]));
    }
}