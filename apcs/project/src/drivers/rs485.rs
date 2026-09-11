/// RS-485 драйвер (управление направлением через GPIO)

use log::debug;

pub struct Rs485Driver {
    dir_pin: u8, // GPIO4
    tx_pin: u8,  // GPIO17
    rx_pin: u8,  // GPIO18
}

impl Rs485Driver {
    pub fn new(dir_pin: u8, tx_pin: u8, rx_pin: u8) -> Self {
        Self { dir_pin, tx_pin, rx_pin }
    }
    
    pub async fn init(&self) -> Result<(), String> {
        debug!("Initializing RS-485: DIR={}, TX={}, RX={}", 
               self.dir_pin, self.tx_pin, self.rx_pin);
        Ok(())
    }
    
    pub async fn set_transmit_mode(&self) -> Result<(), String> {
        debug!("RS-485: TX mode (DIR=HIGH)");
        // В реальности: GPIO set HIGH
        Ok(())
    }
    
    pub async fn set_receive_mode(&self) -> Result<(), String> {
        debug!("RS-485: RX mode (DIR=LOW)");
        // В реальности: GPIO set LOW
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_rs485_creation() {
        let rs485 = Rs485Driver::new(4, 17, 18);
        assert_eq!(rs485.dir_pin, 4);
    }
}
