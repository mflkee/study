// ESP32 Modbus TCP/RTU Gateway
//
// Дипломная работа: Масштабируемый Modbus TCP/RTU шлюз на ESP32-S3
// для интеграции полевых устройств с Zynq SoC

pub mod config;
pub mod modbus;
pub mod drivers;
pub mod sensors;

// Экспорт для удобства
pub use config::settings::DeviceConfig;
pub use modbus::register_map::RegisterMap;
pub use modbus::tcp_slave::TcpSlave;
pub use modbus::rtu_master::RtuMaster;
