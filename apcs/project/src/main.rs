// ESP32 Modbus TCP/RTU Gateway - Main Entry Point
//
// Дипломная работа: Масштабируемый Modbus TCP/RTU шлюз на ESP32-S3
// для интеграции полевых устройств с Zynq SoC

use std::sync::Arc;
use tokio::sync::RwLock;
use log::{info, error};

use esp32_modbus_gateway::config::settings::DeviceConfig;
use esp32_modbus_gateway::modbus::register_map::RegisterMap;
use esp32_modbus_gateway::modbus::tcp_slave::TcpSlave;
use esp32_modbus_gateway::modbus::rtu_master::RtuMaster;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Инициализация логирования
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
    
    info!("ESP32 Modbus Gateway starting...");
    
    // Загрузка конфигурации
    let config = match DeviceConfig::load("config/device_config.toml") {
        Ok(c) => {
            info!("Config loaded: {}", c.device.name);
            c
        }
        Err(e) => {
            error!("Failed to load config: {}, using defaults", e);
            DeviceConfig::default()
        }
    };
    
    // Создание разделяемой карты регистров
    let register_map = Arc::new(RwLock::new(RegisterMap::new(&config)));
    
    // Запуск RTU Master (опрос RS-485 устройств) - фоновая задача
    let rtu_register_map = register_map.clone();
    let rtu_config = config.clone();
    
    tokio::spawn(async move {
        let mut rtu_master = RtuMaster::new(rtu_config).await;
        rtu_master.run(rtu_register_map).await;
    });
    
    // Запуск TCP Slave (главный цикл)
    let tcp_slave = TcpSlave::new(
        config.device.ip.clone(),
        config.device.modbus_port,
        register_map.clone(),
    );
    
    info!("TCP Slave listening on {}:{}", config.device.ip, config.device.modbus_port);
    
    tcp_slave.run().await?;
    
    Ok(())
}
