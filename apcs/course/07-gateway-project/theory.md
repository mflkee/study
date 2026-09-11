# Теория: Gateway Project

## 1. Архитектура шлюза

### Концепция

Modbus Gateway — это устройство, преобразующее протоколы Modbus:
- **RTU** (последовательная линия RS-485) ↔ **TCP** (Ethernet/Wi-Fi)

### Схема потоков данных

```
┌─────────────────────────────────────────────────────────────┐
│                    Modbus Gateway                            │
│                                                              │
│  ┌────────────┐      ┌────────────┐      ┌────────────┐    │
│  │ RTU Master │─────►│  Register  │◄─────│ TCP Slave  │    │
│  │ (polling)  │      │    Map     │      │ (server)   │    │
│  └────────────┘      └────────────┘      └────────────┘    │
│       │                    │                    │            │
│       │                    │                    │            │
│  ┌────▼────┐          ┌────▼────┐          ┌────▼────┐    │
│  │ RS-485  │          │  RAM    │          │ Ethernet │    │
│  │ BUS     │          │ (data)  │          │ W5500    │    │
│  └─────────┘          └─────────┘          └─────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. RTU Master (опрос устройств)

### Паттерн опроса

```rust
pub struct PollScheduler {
    devices: Vec<Device>,
    interval_ms: u64,
}

impl PollScheduler {
    pub async fn run(&self, map: Arc<RwLock<RegisterMap>>) {
        loop {
            for device in &self.devices {
                match self.poll_device(device).await {
                    Ok(data) => {
                        let mut m = map.write().await;
                        self.update_registers(&mut m, device, &data);
                    }
                    Err(e) => {
                        log::warn!("Device {} poll error: {:?}", device.unit_id, e);
                        self.handle_error(device, &e);
                    }
                }
            }
            Timer::after_millis(self.interval_ms).await;
        }
    }
    
    async fn poll_device(&self, device: &Device) -> Result<Vec<u16>, ModbusError> {
        match device.type_field.as_str() {
            "temperature" => self.read_temperature(device).await,
            "pressure" => self.read_pressure(device).await,
            "pump" => self.read_pump_status(device).await,
            _ => Err(ModbusError::IllegalFunction),
        }
    }
    
    fn read_temperature(&self, device: &Device) -> Pin<Box<dyn Future<Output = Result<Vec<u16>, ModbusError>>>> {
        let unit_id = device.unit_id;
        let addr = device.address;
        
        Box::pin(async move {
            // FC04: Read Input Registers
            let pdu = [
                0x04,                          // Function code
                (addr >> 8) as u8,            // Start address high
                addr as u8,                    // Start address low
                0x00, 0x02,                    // Quantity (2 registers = 1 float)
            ];
            
            let response = send_rtu(unit_id, &pdu).await?;
            
            if response.len() >= 5 && response[0] == 0x04 {
                let byte_count = response[1] as usize;
                let data = &response[2..2 + byte_count];
                
                // Конвертируем 2 регистра в float
                let raw = ((data[0] as u32) << 24) | ((data[1] as u32) << 16) |
                          ((data[2] as u32) << 8) | (data[3] as u32);
                let value = f32::from_bits(raw);
                
                Ok(vec![(value * 10.0) as u16, 0]) // temperature * 10, alert
            } else {
                Err(ModbusError::Protocol(response[0]))
            }
        })
    }
}
```

### Обработка ошибок

```rust
impl PollScheduler {
    fn handle_error(&self, device: &Device, error: &ModbusError) {
        match error {
            ModbusError::Timeout => {
                log::warn!("Device {} timeout", device.unit_id);
                // Можно увеличить интервал опроса
            }
            ModbusError::CrcError => {
                log::error!("Device {} CRC error", device.unit_id);
                // Проверить кабели
            }
            ModbusError::IllegalDataAddress => {
                log::error!("Device {} register mapping error", device.unit_id);
            }
            _ => {
                log::error!("Device {} unknown error: {:?}", device.unit_id, error);
            }
        }
    }
}
```

## 3. TCP Slave (сервер для клиента)

### Архитектура сервера

```rust
pub struct ModbusTcpServer {
    listener: TcpListener,
    map: Arc<RwLock<RegisterMap>>,
}

impl ModbusTcpServer {
    pub async fn run(&self) {
        loop {
            match self.listener.accept().await {
                Ok((socket, addr)) => {
                    log::info!("New TCP connection from {}", addr);
                    let map = self.map.clone();
                    tokio::spawn(async move {
                        Self::handle_client(socket, map).await;
                    });
                }
                Err(e) => {
                    log::error!("Accept error: {:?}", e);
                }
            }
        }
    }
    
    async fn handle_client(mut socket: TcpSocket, map: Arc<RwLock<RegisterMap>>) {
        let mut buf = [0u8; 1024];
        
        loop {
            match socket.read(&mut buf).await {
                Ok(0) => {
                    log::info!("Client disconnected");
                    break;
                }
                Ok(n) => {
                    // Парсим MBAP Header
                    if n < 7 {
                        log::warn!("Incomplete MBAP header");
                        continue;
                    }
                    
                    let mbap = MbapHeader::from_bytes(&buf[..7].try_into().unwrap());
                    let unit_id = mbap.unit_id;
                    let tx_id = mbap.transaction_id;
                    
                    // Парсим PDU
                    let pdu = &buf[7..n];
                    let fc = pdu[0];
                    
                    log::debug!("TCP request: tx=0x{:04X} unit={} fc=0x{:02X}", 
                                tx_id, unit_id, fc);
                    
                    // Обрабатываем запрос
                    let response = match fc {
                        0x03 => {
                            // Read Holding Registers
                            let addr = ((pdu[1] as u16) << 8) | (pdu[2] as u16);
                            let count = ((pdu[3] as u16) << 8) | (pdu[4] as u16);
                            
                            let m = map.read().await;
                            match m.read_holding_registers(addr, count) {
                                Ok(values) => {
                                    let mut resp = vec![0x03, (values.len() * 2) as u8];
                                    for v in values {
                                        resp.extend_from_slice(&v.to_be_bytes());
                                    }
                                    resp
                                }
                                Err(e) => {
                                    vec![0x83, e as u8]
                                }
                            }
                        }
                        0x04 => {
                            // Read Input Registers
                            let addr = ((pdu[1] as u16) << 8) | (pdu[2] as u16);
                            let count = ((pdu[3] as u16) << 8) | (pdu[4] as u16);
                            
                            let m = map.read().await;
                            match m.read_input_registers(addr, count) {
                                Ok(values) => {
                                    let mut resp = vec![0x04, (values.len() * 2) as u8];
                                    for v in values {
                                        resp.extend_from_slice(&v.to_be_bytes());
                                    }
                                    resp
                                }
                                Err(e) => {
                                    vec![0x84, e as u8]
                                }
                            }
                        }
                        _ => {
                            vec![fc | 0x80, 0x01] // Illegal Function
                        }
                    };
                    
                    // Формируем ответный MBAP
                    let resp_len = response.len() + 1; // Unit ID + PDU
                    let mut resp_buf = Vec::with_capacity(7 + response.len());
                    resp_buf.extend_from_slice(&tx_id.to_be_bytes());
                    resp_buf.extend_from_slice(&[0x00, 0x00]); // Protocol ID
                    resp_buf.extend_from_slice(&(resp_len as u16).to_be_bytes());
                    resp_buf.push(unit_id);
                    resp_buf.extend_from_slice(&response);
                    
                    if let Err(e) = socket.write_all(&resp_buf).await {
                        log::error!("Send error: {:?}", e);
                        break;
                    }
                }
                Err(e) => {
                    log::error!("Read error: {:?}", e);
                    break;
                }
            }
        }
    }
}
```

## 4. Карты регистров

### Схема памяти

```
Input Registers (FC04) — Read Only
┌────────────────┬────────────────┬─────────────────┐
│ Address        │ Type           │ Описание        │
├────────────────┼────────────────┼─────────────────┤
│ 0x0000-0x001D  │ float (2 reg)  │ Температура 1-15│
│ 0x0020-0x003D  │ float (2 reg)  │ Давление 1-15  │
│ 0x0040-0x005D  │ float (2 reg)  │ Насосы 1-2     │
│ 0x0060-0x007F  │ uint16         │ Статусы        │
│ 0x0080-0x00FF  │ reserved       │ Будущее        │
└────────────────┴────────────────┴─────────────────┘

Holding Registers (FC03) — Read/Write
┌────────────────┬────────────────┬─────────────────┐
│ Address        │ Type           │ Описание        │
├────────────────┼────────────────┼─────────────────┤
│ 0x0000         │ uint16         │ Скорость опроса │
│ 0x0001         │ uint16         │ Таймаут RTU     │
│ 0x0002-0x000F  │ uint16         │ Конфигурация    │
│ 0x0010-0x001F  │ float          │ Пороги алармов  │
│ 0x0020-0x002F  │ reserved       │ Будущее        │
└────────────────┴────────────────┴─────────────────┘

Coils (FC01) — Read/Write (bool)
┌────────────────┬────────────────┬─────────────────┐
│ Address        │ Type           │ Описание        │
├────────────────┼────────────────┼─────────────────┤
│ 0x0000         │ bool           │ Насос 1         │
│ 0x0001         │ bool           │ Насос 2         │
│ 0x0002-0x000F  │ bool           │ Резерв          │
└────────────────┴────────────────┴─────────────────┘
```

### Пример: чтение температуры

```rust
// Температура хранится как float в 2 регистрах
// Address 0x0000 = старшие 16 бит
// Address 0x0001 = младшие 16 бит

fn read_temperature(map: &RegisterMap, sensor_id: u8) -> Option<f32> {
    let addr = (sensor_id as u16) * 2;
    
    let high = map.get_input_register(addr)? as u32;
    let low = map.get_input_register(addr + 1)? as u32;
    
    let raw = (high << 16) | low;
    Some(f32::from_bits(raw))
}
```

## 5. Конфигурация проекта

### Пример конфигурации

```toml
[device]
id = "gateway-001"
name = "ESP32-S3 Modbus Gateway"
log_level = "info"

[modbus]
tcp_port = 502

[modbus.rtu]
baud_rate = 9600
data_bits = 8
stop_bits = 1
parity = "none"
timeout_ms = 100

[[devices]]
name = "Temp Sensor 1"
unit_id = 1
type = "temperature"
address = 0
scan_rate_ms = 1000

[[devices]]
name = "Pressure Sensor 1"
unit_id = 2
type = "pressure"
address = 30
scan_rate_ms = 1000

[[devices]]
name = "Pump 1"
unit_id = 3
type = "pump"
address = 60
scan_rate_ms = 500
```

## 6. Загрузка конфигурации

```rust
use std::fs;
use toml::Value;

pub struct Config {
    pub device_id: String,
    pub tcp_port: u16,
    pub rtu: RtuConfig,
    pub devices: Vec<DeviceConfig>,
}

pub struct RtuConfig {
    pub baud_rate: u32,
    pub timeout_ms: u32,
}

pub struct DeviceConfig {
    pub unit_id: u8,
    pub name: String,
    pub type_field: String,
    pub address: u16,
}

impl Config {
    pub fn load(path: &str) -> Result<Self, ConfigError> {
        let content = fs::read_to_string(path)
            .map_err(|e| ConfigError::FileNotFound(e.to_string()))?;
        
        let value: Value = toml::from_str(&content)
            .map_err(|e| ConfigError::ParseError(e.to_string()))?;
        
        Ok(Config {
            device_id: value["device"]["id"].as_str()
                .unwrap_or("unknown").to_string(),
            tcp_port: value["modbus"]["tcp_port"].as_integer()
                .unwrap_or(502) as u16,
            rtu: RtuConfig {
                baud_rate: value["modbus"]["rtu"]["baud_rate"].as_integer()
                    .unwrap_or(9600) as u32,
                timeout_ms: value["modbus"]["rtu"]["timeout_ms"].as_integer()
                    .unwrap_or(100) as u32,
            },
            devices: Self::parse_devices(&value),
        })
    }
    
    fn parse_devices(value: &Value) -> Vec<DeviceConfig> {
        let mut devices = Vec::new();
        
        if let Some(device_list) = value["devices"].as_array() {
            for d in device_list {
                devices.push(DeviceConfig {
                    unit_id: d["unit_id"].as_integer().unwrap() as u8,
                    name: d["name"].as_str().unwrap().to_string(),
                    type_field: d["type"].as_str().unwrap().to_string(),
                    address: d["address"].as_integer().unwrap() as u16,
                });
            }
        }
        
        devices
    }
}
```
