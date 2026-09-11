# Теория: ESP32 + Modbus

## 1. Архитектура ESP32 Modbus Gateway

```
                    ┌──────────────────────┐
                    │       ESP32-S3       │
                    │                      │
┌──────────┐        │  ┌────────────────┐  │        ┌──────────┐
│ RS-485   │◄──────►│  │  UART1 (RTU)   │  │◄──────►│ Ethernet │
│ MAX3485  │  TTL   │  │  9600-115200   │  │  SPI   │ W5500    │
└──────────┘        │  └────────────────┘  │        └──────────┘
   15+15+2          │                      │          TCP:502
   устройств        │  ┌────────────────┐  │         Zynq SoC
                    │  │  GPIO4 (DIR)   │  │
                    │  │  RS-485 toggle │  │
                    │  └────────────────┘  │
                    └──────────────────────┘
```

## 2. UART для Modbus RTU

### Настройка UART на ESP32-S3

```rust
use esp_hal::uart::{Config, DataBits, Parity, StopBits, Uart};
use esp_hal::gpio::{Level, Output};

// Конфигурация UART1
let uart_config = Config {
    baudrate: 9600,         // для RS-485
    data_bits: DataBits::DataBits8,
    parity: Parity::ParityNone,
    stop_bits: StopBits::StopBits1,
    ..Default::default()
};

let mut uart = Uart::new(peripherals.UART1, uart_config);

// GPIO4 — управление направлением RS-485
let mut dir_pin = Output::new(peripherals.GPIO4, Level::Low);
```

### Send/Receive Cycle

```rust
fn send_rtu_request(
    uart: &mut Uart,
    dir: &mut Output,
    device_addr: u8,
    fc: u8,
    data: &[u8],
) -> Result<Vec<u8>, ModbusError> {
    // 1. Формируем PDU
    let mut pdu = Vec::with_capacity(data.len() + 2);
    pdu.push(fc);
    pdu.extend_from_slice(data);
    
    // 2. Вычисляем CRC
    let mut frame = Vec::with_capacity(pdu.len() + 2);
    frame.push(device_addr);
    frame.extend_from_slice(&pdu);
    let crc = crc16_modbus(&frame);
    frame.push(crc as u8);
    frame.push((crc >> 8) as u8);
    
    // 3. Переключаем в режим передачи (TX)
    dir.set_high();
    
    // 4. Отправляем
    uart.write(&frame).map_err(|e| ModbusError::Transport(e.to_string()))?;
    uart.flush().map_err(|e| ModbusError::Transport(e.to_string()))?;
    
    // 5. Ждём передачу (depends on baudrate)
    esp_hal::delay::block_for(Duration::from_millis(10));
    
    // 6. Переключаем в режим приёма (RX)
    dir.set_low();
    
    // 7. Читаем ответ
    let mut response = [0u8; 256];
    let mut total_read = 0;
    
    // Таймаут ответа: ~100 мс
    let timeout = Duration::from_millis(100);
    let start = embassy_time::Instant::now();
    
    while start.elapsed() < timeout {
        if let Ok(n) = uart.read(&mut response[total_read..]) {
            total_read += n;
            if total_read >= 3 { // Минимальный ответ: addr + fc + crc
                break;
            }
        }
    }
    
    if total_read < 3 {
        return Err(ModbusError::Timeout);
    }
    
    Ok(response[..total_read].to_vec())
}
```

### Timing для RS-485

| Скорость | Время передачи 1 байта | Таймаут ответа |
|----------|------------------------|----------------|
| 9600 бод | 1.04 мс | 100 мс |
| 19200 бод | 0.52 мс | 50 мс |
| 38400 бод | 0.26 мс | 30 мс |
| 57600 бод | 0.17 мс | 20 мс |
| 115200 бод | 0.087 мс | 10 мс |

**Важно:** Таймаут ответа = время передачи最大的 ответа × 3.5 (inter-frame gap)

## 3. SPI для W5500

### Инициализация W5500

```rust
use esp_hal::spi::{Mode, Phase, Polarity, Spi};
use esp_hal::gpio::{Level, Output, Input, Pull};

// SPI2 на ESP32-S3
let spi = Spi::new(
    peripherals.SPI2,
    peripherals.GPIO12,  // SCK
    peripherals.GPIO11,  // MOSI
    peripherals.GPIO13,  // MISO
    peripherals.GPIO10,  // CS
    10.MHz(),
    Mode {
        polarity: Polarity::IdleLow,
        phase: Phase::CaptureOnFirstTransition,
    },
);

let mut cs = Output::new(peripherals.GPIO10, Level::High);

// Регистры W5500
const W5500_COMMON_REGS: u16 = 0x0000;
const W5500_SHARED_REGS: u16 = 0x0001;

// Инициализация W5500
fn init_w5500(spi: &mut Spi, cs: &mut Output) {
    // 1. Сброс
    cs.set_low();
    spi.write(&[0x00, 0x00, 0x00]).unwrap(); // Mode register
    cs.set_high();
    
    // 2. Настройка MAC
    write_w5500_reg(spi, cs, 0x0009, &[0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF]);
    
    // 3. Настройка IP (DHCP или статический)
    write_w5500_reg(spi, cs, 0x000F, &[192, 168, 1, 100]); // IP
    write_w5500_reg(spi, cs, 0x0005, &[255, 255, 255, 0]);  // Mask
    write_w5500_reg(spi, cs, 0x0001, &[192, 168, 1, 1]);    // Gateway
}
```

### TCP Socket на W5500

```rust
// Открытие TCP сокета
fn open_tcp_socket(spi: &mut Spi, cs: &mut Output, socket: u8, port: u16) {
    // Socket Mode Register (TCP)
    write_w5500_reg(spi, cs, 0x0000 + socket * 0x100, &[0x01]);
    
    // Source Port
    write_w5500_reg(spi, cs, 0x0001 + socket * 0x100, 
        &[(port >> 8) as u8, port as u8]);
    
    // Open Command
    write_w5500_reg(spi, cs, 0x0001 + socket * 0x100, &[0x01]);
}

// Отправка данных
fn send_tcp_data(spi: &mut Spi, cs: &mut Output, socket: u8, data: &[u8]) {
    let tx_base = 0x4000 + socket * 0x100;
    
    // TX Free Size
    let free = read_w5500_reg(spi, cs, 0x0020 + socket * 0x100);
    
    if free >= data.len() as u8 {
        // TX Write Pointer
        let write_ptr = read_w5500_reg(spi, cs, 0x0024 + socket * 0x100);
        
        // Записываем данные
        write_w5500_mem(spi, cs, tx_base + write_ptr as u16, data);
        
        // Обновляем Write Pointer
        write_w5500_reg(spi, cs, 0x0024 + socket * 0x100, 
            &[(write_ptr + data.len() as u8) >> 8, 
              (write_ptr + data.len() as u8) & 0xFF]);
        
        // SEND Command
        write_w5500_reg(spi, cs, 0x0001 + socket * 0x100, &[0x20]);
    }
}
```

## 4. Конфигурация через TOML

### Структура конфигурации

```rust
use serde::Deserialize;

#[derive(Deserialize, Debug)]
pub struct Config {
    pub device: DeviceConfig,
    pub modbus: ModbusConfig,
    pub devices: Vec<Device>,
}

#[derive(Deserialize, Debug)]
pub struct DeviceConfig {
    pub id: String,
    pub name: String,
    pub log_level: String,
}

#[derive(Deserialize, Debug)]
pub struct ModbusConfig {
    pub tcp_port: u16,
    pub rtu: RtuConfig,
}

#[derive(Deserialize, Debug)]
pub struct RtuConfig {
    pub baud_rate: u32,
    pub data_bits: u8,
    pub stop_bits: u8,
    pub parity: String,
    pub timeout_ms: u32,
}

#[derive(Deserialize, Debug)]
pub struct Device {
    pub name: String,
    pub unit_id: u8,
    pub type_field: String,  // "temperature", "pressure", "pump"
    pub address: u16,
    pub scan_rate_ms: u32,
}

// Загрузка конфигурации
pub fn load_config(path: &str) -> Result<Config, toml::de::Error> {
    let content = std::fs::read_to_string(path).unwrap();
    toml::from_str(&content)
}
```

## 5. Маппинг регистров

### Карта регистров

```rust
pub struct RegisterMap {
    // Input Registers (FC04) — только чтение
    input_registers: HashMap<u16, f32>,
    
    // Holding Registers (FC03) — чтение/запись
    holding_registers: HashMap<u16, f32>,
    
    // Coils (FC01) — булевы значения
    coils: HashMap<u16, bool>,
}

impl RegisterMap {
    pub fn new() -> Self {
        let mut map = RegisterMap {
            input_registers: HashMap::new(),
            holding_registers: HashMap::new(),
            coils: HashMap::new(),
        };
        
        // Инициализация регистров температуры (адрес 0-29)
        for i in 0..15 {
            map.input_registers.insert(i * 2, 0.0);     // Temperature
            map.input_registers.insert(i * 2 + 1, 0.0); // Alert level
        }
        
        // Инициализация регистров давления (адрес 30-59)
        for i in 15..30 {
            map.input_registers.insert(i * 2, 0.0);     // Pressure
            map.input_registers.insert(i * 2 + 1, 0.0); // Alert level
        }
        
        // Holding registers для конфигурации
        for i in 0..10 {
            map.holding_registers.insert(i, 0.0); // config values
        }
        
        // Coils для насосов
        map.coils.insert(0, false); // Pump 1
        map.coils.insert(1, false); // Pump 2
        
        map
    }
    
    pub fn get_input_register(&self, addr: u16) -> Option<f32> {
        self.input_registers.get(&addr).copied()
    }
    
    pub fn set_input_register(&mut self, addr: u16, value: f32) {
        self.input_registers.insert(addr, value);
    }
}
```

## 6. Обработка исключений

```rust
use crate::modbus::exceptions::ModbusError;

impl RegisterMap {
    pub fn read_holding_registers(&self, start: u16, count: u16) 
        -> Result<Vec<u16>, ModbusError> 
    {
        // Проверка границ
        if start + count > 100 {
            return Err(ModbusError::IllegalDataAddress);
        }
        
        // Проверка кратности
        if count == 0 {
            return Err(ModbusError::IllegalDataValue);
        }
        
        let mut result = Vec::with_capacity(count as usize);
        for i in start..start + count {
            if let Some(&val) = self.holding_registers.get(&i) {
                result.push(val as u16);
            } else {
                return Err(ModbusError::IllegalDataAddress);
            }
        }
        
        Ok(result)
    }
    
    pub fn write_single_register(&mut self, addr: u16, value: u16) 
        -> Result<(), ModbusError> 
    {
        if addr >= 100 {
            return Err(ModbusError::IllegalDataAddress);
        }
        
        self.holding_registers.insert(addr, value as f32);
        Ok(())
    }
}
```

## 7. Async паттерн

```rust
#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let peripherals = esp_hal::init(esp_hal::Config::default());
    
    // UART для RS-485
    let mut uart = Uart::new(peripherals.UART1, uart_config);
    let mut dir = Output::new(peripherals.GPIO4, Level::Low);
    
    // SPI для W5500
    let mut spi = Spi::new(peripherals.SPI2, ...);
    let mut cs = Output::new(peripherals.GPIO10, Level::High);
    
    // Общее состояние
    let register_map = Arc::new(RwLock::new(RegisterMap::new()));
    
    // Запуск задач
    spawner.spawn(poll_sensors(register_map.clone())).unwrap();
    spawner.spawn(modbus_tcp_server(register_map.clone())).unwrap();
    spawner.spawn(modbus_rtu_master(register_map.clone())).unwrap();
}

#[embassy_executor::task]
async fn modbus_rtu_master(map: Arc<RwLock<RegisterMap>>) {
    loop {
        for device in &config.devices {
            match read_device(device).await {
                Ok(value) => {
                    let mut m = map.write().await;
                    m.set_input_register(device.address, value);
                }
                Err(e) => {
                    log::warn!("Device {} error: {:?}", device.unit_id, e);
                }
            }
        }
        Timer::after_millis(config.modbus.rtu.scan_rate_ms).await;
    }
}

#[embassy_executor::task]
async fn modbus_tcp_server(map: Arc<RwLock<RegisterMap>>) {
    let listener = TcpListener::bind("0.0.0.0:502").await.unwrap();
    
    loop {
        let (socket, addr) = listener.accept().await.unwrap();
        log::info!("TCP connection from {}", addr);
        
        spawner.spawn(handle_tcp_client(socket, map.clone())).unwrap();
    }
}
```

## 8. Тестирование

### Unit тесты

```rust
#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_crc16_modbus() {
        let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
        let crc = crc16_modbus(&data);
        assert_eq!(crc, 0xC5CD);
    }
    
    #[test]
    fn test_register_map() {
        let mut map = RegisterMap::new();
        map.set_input_register(0, 25.5);
        assert_eq!(map.get_input_register(0), Some(25.5));
    }
    
    #[test]
    fn test_exception_bounds() {
        let map = RegisterMap::new();
        let result = map.read_holding_registers(0, 150);
        assert!(result.is_err());
    }
}
```

### Интеграционный тест (Python)

```python
from pymodbus.client import ModbusTcpClient

def test_read_temperature():
    client = ModbusTcpClient('192.168.1.100', port=502)
    client.connect()
    
    result = client.read_input_registers(0, 2, unit=1)
    assert not result.isError()
    
    temp_raw = (result.registers[0] << 16) | result.registers[1]
    temp = struct.unpack('f', struct.pack('I', temp_raw))[0]
    
    assert -40.0 <= temp <= 85.0
    client.close()
```

## 9. Common Pitfalls — частые ошибки

### UART и RS-485

**1. Забыл переключить DIR pin**
```rust
// ❌ Отправка без переключения DIR
uart.write(&frame)?;

// ✅ Всегда переключать перед отправкой
dir_pin.set_high();  // TX mode
uart.write(&frame)?;
uart.flush()?;       // Ждём завершения передачи!
dir_pin.set_low();   // RX mode
```

**2. Нет flush() после отправки**
```rust
// ❌ Данные могут не дойти
uart.write(&frame);
dir_pin.set_low(); // Переключаем до завершения передачи!

// ✅ Ждём завершения
uart.write(&frame)?;
uart.flush()?;     // Безопасно переключаем
dir_pin.set_low();
```

**3. Неверный baud rate**
```
// ❌ Baud rate не совпадает с устройствами
// Устройство: 9600, ESP32: 115200

// ✅ Проверьте настройки всех устройств на шине
// Все устройства должны работать на одной скорости
```

### SPI и W5500

**1. CS не управляется**
```rust
// ❌ Забыл CS
spi.write(&[0x00, 0x01])?;

// ✅ Управление CS
cs.set_low();
spi.write(&[0x00, 0x01])?;
cs.set_high();
```

**2. Неверная частота SPI**
```rust
// ❌ Слишком быстро для W5500
let spi = Spi::new(..., 80.MHz(), ...);

// ✅ W5500 поддерживает до 80 МГц, но лучше 10-30 МГц для надёжности
let spi = Spi::new(..., 10.MHz(), ...);
```

### Register Map

**1. Два регистра для float**
```rust
// ❌ Пытаемся записать float в один регистр
map.set_holding(0, 25.5); // ОШИБКА: регистр 16-битный!

// ✅ Float занимает 2 регистра
let bytes = 25.5f32.to_be_bytes();
let reg0 = u16::from_be_bytes([bytes[0], bytes[1]]);
let reg1 = u16::from_be_bytes([bytes[2], bytes[3]]);
map.set_holding(0, reg0);
map.set_holding(1, reg1);
```

**2. Coils упакованы в байты**
```rust
// ❌ Чтение одного coil
let value = map.get_coil(5)?; // Булево значение

// ✅ Чтение нескольких coils (пакетная обработка)
let coils = map.read_coils(0, 8)?; // 8 coils в 1 байте
// Каждый бит = один coil
```

### Timing

**1. Inter-frame gap 3.5 символа**
```
// При 9600 baud: 3.5 * (1/9600) ≈ 365 мкс
// Минимальная пауза между кадрами

// ❌ Нет паузы между запросами
send_request(req1);
send_request(req2); // Slave не успевает обработать!

// ✅ Пауза 3.5 символа
send_request(req1);
delay_ms(5); // 5 мс > 365 мкс
send_request(req2);
```

**2. Таймаут ответа**
```
// ❌ Слишком короткий таймаут
let response = read_with_timeout(10); // 10 мс

// ✅ Таймаут = время передачи最大的 ответа × 3.5
// При 9600 baud и ответе 10 байт:
// 10 байт × 11 бит × (1/9600) ≈ 11.5 мс
// × 3.5 = ~40 мс
let response = read_with_timeout(40);
```
