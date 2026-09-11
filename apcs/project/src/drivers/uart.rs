/// UART драйвер для ESP32-S3

use log::debug;

pub struct UartDriver {
    port: String,
    baud_rate: u32,
}

impl UartDriver {
    pub fn new(port: &str, baud_rate: u32) -> Self {
        Self {
            port: port.to_string(),
            baud_rate,
        }
    }
    
    pub async fn init(&self) -> Result<(), String> {
        debug!("Initializing UART on {} at {} baud", self.port, self.baud_rate);
        // В реальности: настройка UART через HAL
        Ok(())
    }
    
    pub async fn write(&self, data: &[u8]) -> Result<(), String> {
        debug!("UART TX: {:02X?}", data);
        // В реальности: отправка данных через UART
        Ok(())
    }
    
    pub async fn read(&self, buf: &mut [u8]) -> Result<usize, String> {
        // В реальности: чтение данных из UART
        debug!("UART RX: {} bytes", buf.len());
        Ok(0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_uart_creation() {
        let uart = UartDriver::new("/dev/ttyUSB0", 9600);
        assert_eq!(uart.port, "/dev/ttyUSB0");
        assert_eq!(uart.baud_rate, 9600);
    }
}
