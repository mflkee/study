/// Register map for Modbus RTU slave emulation

use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct RegisterMap {
    /// Input Registers (FC04) — read only
    input_registers: HashMap<u16, u16>,
    
    /// Holding Registers (FC03) — read/write
    holding_registers: HashMap<u16, u16>,
    
    /// Coils (FC01) — bit values
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
    
    // --- Input Registers ---
    
    pub fn get_input_register(&self, addr: u16) -> Option<u16> {
        self.input_registers.get(&addr).copied()
    }
    
    pub fn set_input_register(&mut self, addr: u16, value: u16) {
        self.input_registers.insert(addr, value);
    }
    
    pub fn read_input_registers(&self, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        
        for i in 0..count {
            let addr = start + i;
            match self.input_registers.get(&addr) {
                Some(&val) => result.push(val),
                None => return Err(0x02), // Illegal Data Address
            }
        }
        
        Ok(result)
    }
    
    // --- Holding Registers ---
    
    pub fn get_holding_register(&self, addr: u16) -> Option<u16> {
        self.holding_registers.get(&addr).copied()
    }
    
    pub fn set_holding_register(&mut self, addr: u16, value: u16) {
        self.holding_registers.insert(addr, value);
    }
    
    pub fn read_holding_registers(&self, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        
        for i in 0..count {
            let addr = start + i;
            match self.holding_registers.get(&addr) {
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
    
    // --- Coils ---
    
    pub fn get_coil(&self, addr: u16) -> Option<bool> {
        self.coils.get(&addr).copied()
    }
    
    pub fn set_coil(&mut self, addr: u16, value: bool) {
        self.coils.insert(addr, value);
    }
    
    pub fn read_coils(&self, start: u16, count: u16) -> Result<Vec<bool>, u8> {
        let mut result = Vec::with_capacity(count as usize);
        
        for i in 0..count {
            let addr = start + i;
            match self.coils.get(&addr) {
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
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_register_map() {
        let mut map = RegisterMap::new();
        
        // Input registers
        map.set_input_register(0, 0x41C8);
        map.set_input_register(1, 0x0000);
        
        let values = map.read_input_registers(0, 2).unwrap();
        assert_eq!(values, vec![0x41C8, 0x0000]);
    }
    
    #[test]
    fn test_holding_registers() {
        let mut map = RegisterMap::new();
        map.set_holding_register(0, 42);
        
        let values = map.read_holding_registers(0, 1).unwrap();
        assert_eq!(values[0], 42);
        
        map.write_holding_register(0, 100).unwrap();
        let values = map.read_holding_registers(0, 1).unwrap();
        assert_eq!(values[0], 100);
    }
    
    #[test]
    fn test_coils() {
        let mut map = RegisterMap::new();
        map.set_coil(0, true);
        map.set_coil(1, false);
        
        let coils = map.read_coils(0, 2).unwrap();
        assert_eq!(coils, vec![true, false]);
    }
    
    #[test]
    fn test_illegal_address() {
        let map = RegisterMap::new();
        assert_eq!(map.read_holding_registers(0, 1), Err(0x02));
    }
}
