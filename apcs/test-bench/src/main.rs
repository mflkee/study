// ESP32 Modbus RTU — PC Test Bench
//
// Этот код запускается на ПК и эмулирует ESP32 как Modbus RTU Slave.
// Используйте его для тестирования ДО прошивки реального ESP32.

use std::sync::Arc;
use tokio::sync::RwLock;
use log::info;

mod rtu_slave;
mod register_map;
mod crc;

use register_map::RegisterMap;
use rtu_slave::RtuSlave;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
    
    info!("=== ESP32 Modbus RTU Test Bench ===");
    info!("Эмулятор ESP32 как Modbus RTU Slave");
    
    // Создаём共享ный map регистров
    let register_map = Arc::new(RwLock::new(RegisterMap::new()));
    
    // Заполняем тестовыми данными
    {
        let mut map = register_map.write().await;
        
        // Температуры (15 датчиков, по 2 регистра на float)
        for i in 0..15 {
            let temp = 20.0 + (i as f32) * 0.5;
            let bytes = temp.to_be_bytes();
            let reg0 = u16::from_be_bytes([bytes[0], bytes[1]]);
            let reg1 = u16::from_be_bytes([bytes[2], bytes[3]]);
            map.set_input_register(i * 2, reg0);
            map.set_input_register(i * 2 + 1, reg1);
        }
        
        // Давления (15 датчиков)
        for i in 15..30 {
            let pressure = 100.0 + (i as f32 - 15.0) * 1.5;
            let bytes = pressure.to_be_bytes();
            let reg0 = u16::from_be_bytes([bytes[0], bytes[1]]);
            let reg1 = u16::from_be_bytes([bytes[2], bytes[3]]);
            map.set_input_register(i * 2, reg0);
            map.set_input_register(i * 2 + 1, reg1);
        }
        
        // Насосы (2 штуки, coil 0 и 1)
        map.set_coil(0, true);
        map.set_coil(1, false);
        
        // Holding registers
        for i in 0..10 {
            map.set_holding_register(i, (i as u16) * 100);
        }
        
        info!("Test data loaded: 15 temp + 15 pressure + 2 pumps");
    }
    
    // Определяем последовательный порт
    let port = std::env::args().nth(1).unwrap_or_else(|| {
        if cfg!(target_os = "linux") {
            "/dev/ttyUSB0".to_string()
        } else if cfg!(target_os = "macos") {
            "/dev/tty.usbserial-0001".to_string()
        } else {
            "COM3".to_string()
        }
    });
    
    let baud_rate: u32 = std::env::args().nth(2)
        .and_then(|s| s.parse().ok())
        .unwrap_or(9600);
    
    info!("Opening serial port: {} @ {} baud", port, baud_rate);
    
    let slave = RtuSlave::new(port, baud_rate, 1, register_map.clone());
    
    info!("Slave ready. Waiting for Modbus RTU requests...");
    info!("Подключите ZK-U485 к USB и запустите тестовый клиент.");
    
    slave.run().await?;
    
    Ok(())
}
