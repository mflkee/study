/// Датчик давления

#[derive(Debug, Clone)]
pub struct PressureSensor {
    pub slave_id: u8,
    pub name: String,
    pub value: f32,
}

impl PressureSensor {
    pub fn new(slave_id: u8, name: &str) -> Self {
        Self {
            slave_id,
            name: name.to_string(),
            value: 0.0,
        }
    }
    
    pub fn update_from_registers(&mut self, registers: &[u16]) -> Result<(), String> {
        if registers.len() < 2 {
            return Err("Insufficient registers for float value".to_string());
        }
        
        let bytes = [
            (registers[0] >> 8) as u8,
            registers[0] as u8,
            (registers[1] >> 8) as u8,
            registers[1] as u8,
        ];
        
        self.value = f32::from_be_bytes(bytes);
        Ok(())
    }
    
    pub fn to_registers(&self) -> Vec<u16> {
        let bytes = self.value.to_be_bytes();
        vec![
            ((bytes[0] as u16) << 8) | (bytes[1] as u16),
            ((bytes[2] as u16) << 8) | (bytes[3] as u16),
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_pressure_conversion() {
        let mut sensor = PressureSensor::new(16, "Test");
        
        // 101.325 кПа = 0x42CAA666
        let registers = vec![0x42CA, 0xA666];
        sensor.update_from_registers(&registers).unwrap();
        
        assert!((sensor.value - 101.325).abs() < 0.1);
    }
}
