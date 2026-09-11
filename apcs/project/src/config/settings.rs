use serde::Deserialize;
use std::fs;

#[derive(Debug, Clone, Deserialize)]
pub struct DeviceConfig {
    pub device: Device,
    pub rs485: Rs485Config,
    pub devices: Vec<DeviceEntry>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Device {
    pub name: String,
    pub ip: String,
    pub modbus_port: u16,
    pub slave_id: u8,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rs485Config {
    pub baud_rate: u32,
    pub data_bits: u8,
    pub parity: String,
    pub stop_bits: u8,
    pub poll_interval_ms: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DeviceEntry {
    pub id: u8,
    pub name: String,
    pub device_type: String,
    pub registers: Vec<u16>,
    pub command_register: Option<u16>,
    pub status_register: Option<u16>,
}

impl DeviceConfig {
    pub fn load(path: &str) -> anyhow::Result<Self> {
        let content = fs::read_to_string(path)?;
        let config: Self = toml::from_str(&content)?;
        Ok(config)
    }
}

// Пример конфигурации по умолчанию
impl Default for DeviceConfig {
    fn default() -> Self {
        Self {
            device: Device {
                name: "esp32-gateway".to_string(),
                ip: "192.168.1.100".to_string(),
                modbus_port: 502,
                slave_id: 1,
            },
            rs485: Rs485Config {
                baud_rate: 9600,
                data_bits: 8,
                parity: "none".to_string(),
                stop_bits: 1,
                poll_interval_ms: 500,
            },
            devices: vec![
                DeviceEntry {
                    id: 1,
                    name: "Temperature Sensor 1".to_string(),
                    device_type: "temperature".to_string(),
                    registers: vec![0x00],
                    command_register: None,
                    status_register: None,
                },
            ],
        }
    }
}
