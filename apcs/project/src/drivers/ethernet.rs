/// Ethernet драйвер для W5500

use log::debug;

pub struct EthernetDriver {
    ip: [u8; 4],
    mac: [u8; 6],
}

impl EthernetDriver {
    pub fn new(ip: [u8; 4], mac: [u8; 6]) -> Self {
        Self { ip, mac }
    }
    
    pub async fn init(&self) -> Result<(), String> {
        debug!("Initializing W5500 Ethernet: IP={:?}, MAC={:?}", self.ip, self.mac);
        // В реальности: инициализация W5500 через SPI
        Ok(())
    }
    
    pub fn get_ip(&self) -> [u8; 4] {
        self.ip
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_ethernet_creation() {
        let eth = EthernetDriver::new(
            [192, 168, 1, 100],
            [0x02, 0x01, 0x02, 0x03, 0x04, 0x05],
        );
        assert_eq!(eth.get_ip(), [192, 168, 1, 100]);
    }
}
