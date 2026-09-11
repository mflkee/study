# Теория: Modbus on Rust

## 1. Обзор экосистемы Modbus в Rust

### Крейты для Modbus

| Крейт | no_std | std | async | TCP | RTU | Уровень |
|-------|--------|-----|-------|-----|-----|---------|
| tokio-modbus | ✗ | ✓ | ✓ | ✓ | ✓ | Высокий |
| modbus-rs | ✓ | ✓ | ✗ | ✓ | ✓ | Средний |
| rmodbus | ✓ | ✓ | ✗ | ✓ | ✓ | Низкий |
| modbus-bridge | ✓ | ✗ | ✓ | ✓ | ✓ | Готовый шлюз |
| async-modbus | ✓ | ✗ | ✓ | ✓ | ✓ | Средний |

### Как выбрать крейт

```
Вы пишете для ПК/сервера?
├── Да → tokio-modbus (async + std)
└── Нет (embedded)
    ├── Нужен готовый шлюз? → modbus-bridge
    ├── Нужен async? → async-modbus
    └── Нет → rmodbus или modbus-rs
```

## 2. tokio-modbus — подробно

### Архитектура

```
┌─────────────────────────────────┐
│         Your Application        │
├─────────────────────────────────┤
│        tokio-modbus             │
│  ┌────────────┬──────────────┐ │
│  │ TCP Client │ TCP Server   │ │
│  │ RTU Client │ RTU Server   │ │
│  └────────────┴──────────────┘ │
├─────────────────────────────────┤
│         tokio (runtime)         │
├─────────────────────────────────┤
│         TCP / Serial            │
└─────────────────────────────────┘
```

### TCP Client

```rust
use tokio_modbus::prelude::*;
use tokio_modbus::client::tcp::connect;

#[tokio::main]
async fn main() {
    // Подключение
    let ctx = connect("192.168.1.100:502".parse().unwrap())
        .await
        .unwrap();
    
    // Read Holding Registers
    let result = ctx.read_holding_registers(0, 10).await.unwrap();
    println!("Registers: {:?}", result);
    
    // Write Single Register
    ctx.write_single_register(0, 42).await.unwrap();
    
    // Write Multiple Registers
    ctx.write_multiple_registers(10, &[100, 200, 300]).await.unwrap();
    
    // Read Coils
    let coils = ctx.read_coils(0, 8).await.unwrap();
    println!("Coils: {:?}", coils);
}
```

### TCP Server

```rust
use tokio_modbus::prelude::*;
use tokio_modbus::server::{self, Service};
use std::pin::Pin;
use std::future::Future;

#[derive(Clone)]
struct ModbusServer {
    // shared state
}

impl Service for ModbusServer {
    type Request = Request;
    type Response = Response;
    type Error = std::io::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn call(&self, req: Self::Request) -> Self::Future {
        let result = match req {
            Request::ReadHoldingRegisters(addr, cnt) => {
                // Чтение из хранилища
                Ok(Response::ReadHoldingRegisters(vec![0; cnt as usize]))
            }
            Request::WriteSingleRegister(addr, val) => {
                // Запись в хранилище
                Ok(Response::WriteSingleRegister(addr, val))
            }
            _ => Err(std::io::Error::new(
                std::io::ErrorKind::Other,
                "Not implemented"
            )),
        };
        Box::pin(async { result })
    }
}

#[tokio::main]
async fn main() {
    let listener = tokio::net::TcpListener::bind("0.0.0.0:502").await.unwrap();
    server::tcp::run(listener, ModbusServer {}).await.unwrap();
}
```

## 3. rmodbus — для no_std

### Архитектура

```rust
use rmodbus::server::{ModbusServer, ModbusContext};
use rmodbus::ModbusProto;

// Контекст хранения данных
struct MyContext {
    holding_registers: [u16; 100],
    coils: [bool; 64],
}

impl ModbusContext for MyContext {
    fn read_holding_registers(&self, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let s = start as usize;
        let c = count as usize;
        
        if s + c > self.holding_registers.len() {
            return Err(0x02); // Illegal Data Address
        }
        
        Ok(self.holding_registers[s..s+c].to_vec())
    }
    
    fn write_single_register(&mut self, address: u16, value: u16) -> Result<(), u8> {
        if address as usize >= self.holding_registers.len() {
            return Err(0x02);
        }
        
        self.holding_registers[address as usize] = value;
        Ok(())
    }
    
    fn read_coils(&self, start: u16, count: u16) -> Result<Vec<bool>, u8> {
        let s = start as usize;
        let c = count as usize;
        
        if s + c > self.coils.len() {
            return Err(0x02);
        }
        
        Ok(self.coils[s..s+c].to_vec())
    }
}
```

## 4. modbus-bridge — готовый шлюз

### Использование

```rust
use modbus_bridge::{Bridge, Transport};

#[tokio::main]
async fn main() {
    let bridge = Bridge::new()
        .tcp("0.0.0.0:502")           // TCP slave
        .rs485("/dev/ttyUSB0", 9600);  // RTU master
    
    bridge.run().await.unwrap();
}
```

## 5. Реализация Modbus PDU на Rust

### Парсинг запроса

```rust
pub enum FunctionCode {
    ReadCoils = 0x01,
    ReadDiscreteInputs = 0x02,
    ReadHoldingRegisters = 0x03,
    ReadInputRegisters = 0x04,
    WriteSingleCoil = 0x05,
    WriteSingleRegister = 0x06,
    WriteMultipleCoils = 0x0F,
    WriteMultipleRegisters = 0x10,
}

pub struct ModbusRequest {
    pub function: FunctionCode,
    pub data: Vec<u8>,
}

impl ModbusRequest {
    pub fn parse(bytes: &[u8]) -> Result<Self, u8> {
        if bytes.is_empty() {
            return Err(0x01); // Illegal Function
        }
        
        let function = match bytes[0] {
            0x01 => FunctionCode::ReadCoils,
            0x02 => FunctionCode::ReadDiscreteInputs,
            0x03 => FunctionCode::ReadHoldingRegisters,
            0x04 => FunctionCode::ReadInputRegisters,
            0x05 => FunctionCode::WriteSingleCoil,
            0x06 => FunctionCode::WriteSingleRegister,
            0x0F => FunctionCode::WriteMultipleCoils,
            0x10 => FunctionCode::WriteMultipleRegisters,
            _ => return Err(0x01),
        };
        
        Ok(ModbusRequest {
            function,
            data: bytes[1..].to_vec(),
        })
    }
}
```

### Формирование ответа

```rust
pub struct ModbusResponse {
    pub pdu: Vec<u8>,
}

impl ModbusResponse {
    pub fn read_holding_registers(values: &[u16]) -> Self {
        let byte_count = values.len() * 2;
        let mut pdu = Vec::with_capacity(2 + byte_count);
        
        pdu.push(0x03); // Function code
        pdu.push(byte_count as u8);
        
        for &v in values {
            pdu.extend_from_slice(&v.to_be_bytes());
        }
        
        ModbusResponse { pdu }
    }
    
    pub fn exception(function_code: u8, exception_code: u8) -> Self {
        ModbusResponse {
            pdu: vec![function_code | 0x80, exception_code],
        }
    }
}
```

### Формирование MBAP Header

```rust
pub struct MbapHeader {
    pub transaction_id: u16,
    pub protocol_id: u16,    // всегда 0
    pub length: u16,          // Unit ID + PDU
    pub unit_id: u8,
}

impl MbapHeader {
    pub fn new(transaction_id: u16, unit_id: u8, pdu_len: usize) -> Self {
        MbapHeader {
            transaction_id,
            protocol_id: 0,
            length: (1 + pdu_len) as u16, // Unit ID + PDU
            unit_id,
        }
    }
    
    pub fn to_bytes(&self) -> [u8; 7] {
        let mut bytes = [0u8; 7];
        bytes[0..2].copy_from_slice(&self.transaction_id.to_be_bytes());
        bytes[2..4].copy_from_slice(&self.protocol_id.to_be_bytes());
        bytes[4..6].copy_from_slice(&self.length.to_be_bytes());
        bytes[6] = self.unit_id;
        bytes
    }
    
    pub fn from_bytes(bytes: &[u8; 7]) -> Self {
        MbapHeader {
            transaction_id: u16::from_be_bytes([bytes[0], bytes[1]]),
            protocol_id: u16::from_be_bytes([bytes[2], bytes[3]]),
            length: u16::from_be_bytes([bytes[4], bytes[5]]),
            unit_id: bytes[6],
        }
    }
}
```

## 6. CRC-16 Modbus на Rust

```rust
pub fn crc16_modbus(data: &[u8]) -> u16 {
    let mut crc: u16 = 0xFFFF;
    
    for &byte in data {
        crc ^= byte as u16;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    
    crc
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_crc16() {
        // Тестовое значение из спецификации
        let data = b"123456789";
        assert_eq!(crc16_modbus(data), 0x4B37);
    }
}
```

## 7. Shared State в async

```rust
use std::sync::Arc;
use tokio::sync::RwLock;

struct ModbusData {
    registers: Vec<u16>,
}

#[tokio::main]
async fn main() {
    let data = Arc::new(RwLock::new(ModbusData {
        registers: vec![0; 100],
    }));
    
    // Читатель
    let data_read = data.clone();
    tokio::spawn(async move {
        let d = data_read.read().await;
        println!("Register 0: {}", d.registers[0]);
    });
    
    // Писатель
    let data_write = data.clone();
    tokio::spawn(async move {
        let mut d = data_write.write().await;
        d.registers[0] = 42;
    });
}
```

## 8. Логирование

```rust
use log::{info, warn, error, debug};

fn setup_logging() {
    env_logger::Builder::from_env(
        env_logger::Env::default().default_filter_or("info")
    ).init();
}

fn handle_request(tx_id: u16, fc: u8, addr: u16) {
    debug!("Request: tx=0x{:04X} fc=0x{:02X} addr=0x{:04X}", tx_id, fc, addr);
    
    if addr > 1000 {
        warn!("Address out of range: 0x{:04X}", addr);
        return;
    }
    
    info!("Processing request for address 0x{:04X}", addr);
}
```
