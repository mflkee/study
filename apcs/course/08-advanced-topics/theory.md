# Теория: Advanced Topics

## 1. Безопасность

### Проблемы безопасности в embedded Modbus

| Проблема | Описание | Решение |
|----------|----------|---------|
| Нет аутентификации | Любой может подключиться | IP фильтры, токены |
| Нет шифрования | Данные открыты | TLS (если есть CPU) |
| Modbus по умолчанию | Нет защиты | Альтернативные протоколы |
| Physical access | USB/JTAG | Физическая защита |

### IP фильтрация

```rust
pub struct IpFilter {
    allowed: Vec<IpAddr>,
    blocked: Vec<IpAddr>,
}

impl IpFilter {
    pub fn is_allowed(&self, addr: IpAddr) -> bool {
        if self.blocked.contains(&addr) {
            return false;
        }
        self.allowed.is_empty() || self.allowed.contains(&addr)
    }
}

// Использование
let filter = IpFilter {
    allowed: vec!["192.168.1.100".parse().unwrap()],
    blocked: vec![],
};

if !filter.is_allowed(client_addr) {
    log::warn!("Blocked connection from {}", client_addr);
    socket.close().await;
    return;
}
```

### Аутентификация через токены

```rust
pub struct TokenAuth {
    valid_tokens: HashSet<String>,
}

impl TokenAuth {
    pub fn authenticate(&self, token: &str) -> bool {
        self.valid_tokens.contains(token)
    }
}

// В запросе: добавляем токен в данные
// Или используем отдельный порт для авторизации
```

### Шифрование (TLS)

```rust
use tokio_rustls::TlsAcceptor;
use rustls::{Certificate, PrivateKey, ServerConfig};

// Генерация самоподписанных сертификатов
fn create_tls_config() -> ServerConfig {
    let cert = Certificate(vec![/* self-signed cert */]);
    let key = PrivateKey(vec![/* private key */]);
    
    ServerConfig::builder()
        .with_safe_defaults()
        .with_no_client_auth()
        .with_single_cert(vec![cert], key)
        .unwrap()
}
```

## 2. Производительность

### Оптимизация памяти

```rust
// 1. Избегайте аллокаций в runtime
// Плохо:
fn process() {
    let mut data = Vec::new(); // аллокация!
    data.push(1);
}

// Хорошо:
fn process() {
    let mut data = [0u8; 100]; // статический буфер
    data[0] = 1;
}

// 2. Используйте фиксированные массивы вместо Vec
pub struct SensorData {
    temperature: [f32; 15],
    pressure: [f32; 15],
}

// 3. Bit-packing для boolean значений
pub struct Status {
    bits: u8, // 8 boolean значений в 1 байте
}

impl Status {
    pub fn get(&self, bit: u8) -> bool {
        self.bits & (1 << bit) != 0
    }
    
    pub fn set(&mut self, bit: u8, value: bool) {
        if value {
            self.bits |= 1 << bit;
        } else {
            self.bits &= !(1 << bit);
        }
    }
}
```

### Оптимизация времени выполнения

```rust
// 1. Pre-allocated buffers
pub struct ModbusServer {
    tx_buf: [u8; 256],  // буфер отправки
    rx_buf: [u8; 256],  // буфер приёма
}

impl ModbusServer {
    pub fn handle_request(&mut self, data: &[u8]) -> usize {
        // Работаем с pre-allocated буферами
        let response = &mut self.tx_buf;
        // ... обработка
        response.len()
    }
}

// 2. Zero-copy parsing
fn parse_request(data: &[u8]) -> Request {
    // Не копируем данные, работаем через срезы
    Request {
        function: data[0],
        address: u16::from_be_bytes([data[1], data[2]]),
        count: u16::from_be_bytes([data[3], data[4]]),
    }
}

// 3. Избегайте divisions в критичных участках
// Плохо:
let avg = sum / count;

// Хорошее (если count всегда степень двойки):
let avg = sum >> count.trailing_zeros();
```

### Benchmarking

```rust
use std::time::Instant;

fn benchmark_modbus_parse() {
    let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xC5, 0xCD];
    
    let start = Instant::now();
    for _ in 0..1_000_000 {
        let _ = parse_request(&data);
    }
    let duration = start.elapsed();
    
    println!("Parse: {:?} for 1M iterations", duration);
}
```

## 3. Тестирование

### Unit тесты

```rust
#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_crc16() {
        let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
        assert_eq!(crc16_modbus(&data), 0xC5CD);
    }
    
    #[test]
    fn test_register_bounds() {
        let map = RegisterMap::new();
        assert!(map.read_holding_registers(0, 100).is_ok());
        assert!(map.read_holding_registers(0, 101).is_err());
    }
    
    #[test]
    fn test_concurrent_access() {
        use std::sync::{Arc, RwLock};
        use std::thread;
        
        let map = Arc::new(RwLock::new(RegisterMap::new()));
        let mut handles = vec![];
        
        for i in 0..10 {
            let map = map.clone();
            handles.push(thread::spawn(move || {
                let mut m = map.write().unwrap();
                m.set_holding_register(i, i * 10);
            }));
        }
        
        for h in handles {
            h.join().unwrap();
        }
        
        let m = map.read().unwrap();
        for i in 0..10 {
            assert_eq!(m.get_holding_register(i), Some(i * 10));
        }
    }
}
```

### Интеграционные тесты

```rust
#[tokio::test]
async fn test_tcp_server() {
    // Запускаем сервер в фоне
    let server = tokio::spawn(async {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        
        // Обработка одного соединения
        let (mut socket, _) = listener.accept().await.unwrap();
        let mut buf = [0u8; 1024];
        let n = socket.read(&mut buf).await.unwrap();
        
        // Отправляем ответ
        socket.write_all(&buf[..n]).await.unwrap();
        
        port
    });
    
    let port = server.await.unwrap();
    
    // Подключаемся как клиент
    let mut client = TcpStream::connect(format!("127.0.0.1:{}", port)).await.unwrap();
    
    // Отправляем запрос
    let request = [0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
    client.write_all(&request).await.unwrap();
    
    // Читаем ответ
    let mut response = [0u8; 256];
    let n = client.read(&mut response).await.unwrap();
    
    assert!(n > 0);
}
```

### Hardware-in-the-loop тесты

```python
# Тест с реальным устройством
import pymodbus
import time

def test_modbus_gateway():
    client = ModbusTcpClient('192.168.1.100', port=502)
    client.connect()
    
    # Тест 1: Чтение температуры
    result = client.read_input_registers(0, 2, unit=1)
    assert not result.isError()
    assert len(result.registers) == 2
    
    # Тест 2: Запись конфигурации
    result = client.write_register(0, 5000, unit=1)
    assert not result.isError()
    
    # Тест 3: Проверка записи
    result = client.read_holding_registers(0, 1, unit=1)
    assert result.registers[0] == 5000
    
    client.close()
```

## 4. Мониторинг и логирование

### Структурированное логирование

```rust
use tracing::{info, warn, error, debug, span, Level};

pub fn setup_logging() {
    tracing_subscriber::fmt()
        .with_max_level(Level::DEBUG)
        .with_target(false)
        .with_thread_ids(true)
        .init();
}

fn handle_request(tx_id: u16, fc: u8, addr: u16) {
    let span = span!(Level::DEBUG, "modbus_request", tx_id = tx_id, fc = fc, addr = addr);
    let _enter = span.enter();
    
    debug!("Processing request");
    
    if addr > 1000 {
        warn!(addr = addr, "Address out of range");
        return;
    }
    
    info!(addr = addr, "Request processed");
}
```

### Метрики

```rust
use std::sync::atomic::{AtomicU64, Ordering};

pub struct Metrics {
    pub requests_total: AtomicU64,
    pub errors_total: AtomicU64,
    pub bytes_sent: AtomicU64,
    pub bytes_received: AtomicU64,
}

impl Metrics {
    pub fn new() -> Self {
        Metrics {
            requests_total: AtomicU64::new(0),
            errors_total: AtomicU64::new(0),
            bytes_sent: AtomicU64::new(0),
            bytes_received: AtomicU64::new(0),
        }
    }
    
    pub fn record_request(&self) {
        self.requests_total.fetch_add(1, Ordering::Relaxed);
    }
    
    pub fn record_error(&self) {
        self.errors_total.fetch_add(1, Ordering::Relaxed);
    }
    
    pub fn record_bytes(&self, sent: u64, received: u64) {
        self.bytes_sent.fetch_add(sent, Ordering::Relaxed);
        self.bytes_received.fetch_add(received, Ordering::Relaxed);
    }
}
```

## 5. Управление питанием

### Режимы сна

```rust
use esp_hal::rtc_cntl::{Rtc, SleepMode};

// Light Sleep — просыпается по таймеру или GPIO
fn enter_light_sleep(rtc: &mut Rtc, duration: Duration) {
    rtc.light_sleep(SleepMode::Timer(duration));
}

// Deep Sleep — минимальное потребление
fn enter_deep_sleep(rtc: &mut Rtc, duration: Duration) {
    rtc.deep_sleep(duration);
}
```

### Watchdog

```rust
use esp_hal::wdt::Watchdog;

pub struct WatchdogManager {
    wdt: Watchdog,
    timeout_ms: u32,
}

impl WatchdogManager {
    pub fn new(wdt: Watchdog, timeout_ms: u32) -> Self {
        WatchdogManager { wdt, timeout_ms }
    }
    
    pub fn start(&mut self) {
        self.wdt.start(self.timeout_ms).unwrap();
    }
    
    pub fn feed(&mut self) {
        self.wdt.feed().unwrap();
    }
}
```

## 6. OTA (Over-the-Air) обновления

```rust
use esp_http_client::{Client, Error};
use esp_ota::OtaUpdate;

pub async fn check_ota_update(server_url: &str) -> Result<(), Error> {
    let client = Client::new();
    
    // 1. Проверяем доступность обновления
    let response = client.get(server_url).send().await?;
    
    if response.status() == 200 {
        // 2. Скачиваем прошивку
        let firmware = response.bytes().await?;
        
        // 3. Проверяем checksum
        let expected_hash = get_expected_hash();
        if compute_hash(&firmware) != expected_hash {
            return Err(Error::ChecksumMismatch);
        }
        
        // 4. Применяем обновление
        let ota = OtaUpdate::new();
        ota.write(&firmware).await?;
        ota.finalize()?;
        
        // 5. Перезагрузка
        esp_hal::reset::reset();
    }
    
    Ok(())
}
```

## 7. Recovery и Error Handling

### Panic handler

```rust
#[panic_handler]
fn panic(info: &core::panic::PanicInfo) {
    // Логируем ошибку
    log::error!("PANIC: {}", info);
    
    // Сохраняем информацию (если есть FRAM/Flash)
    save_crash_info(info);
    
    // Перезагрузка
    esp_hal::reset::reset();
}
```

### Error recovery

```rust
pub struct RecoveryManager {
    error_count: u32,
    max_errors: u32,
    recovery_delay_ms: u32,
}

impl RecoveryManager {
    pub fn record_error(&mut self) {
        self.error_count += 1;
        
        if self.error_count >= self.max_errors {
            self.enter_recovery_mode();
        }
    }
    
    fn enter_recovery_mode(&self) {
        log::error!("Entering recovery mode after {} errors", self.error_count);
        
        // 1. Сбрасываем все соединения
        // 2. Включаем safe mode (минимальная функциональность)
        // 3. Ждём команды через отладочный порт
        loop {
            esp_hal::delay::block_for(Duration::from_secs(1));
        }
    }
    
    pub fn record_success(&mut self) {
        if self.error_count > 0 {
            self.error_count -= 1;
        }
    }
}
```

## 8. Документирование

### doc comments

```rust
/// Modbus Gateway для ESP32-S3
/// 
/// Преобразует Modbus RTU (RS-485) в Modbus TCP (Ethernet)
/// 
/// # Примеры
/// 
/// ```
/// let gateway = Gateway::new(config);
/// gateway.run().await;
/// ```
/// 
/// # Errors
/// 
/// Возвращает `GatewayError` в случае проблем с инициализацией
pub struct Gateway {
    config: Config,
}

impl Gateway {
    /// Создаёт новый экземпляр шлюза
    /// 
    /// # Arguments
    /// 
    /// * `config` - Конфигурация шлюза
    /// 
    /// # Examples
    /// 
    /// ```
    /// let config = Config::load("config.toml").unwrap();
    /// let gateway = Gateway::new(config);
    /// ```
    pub fn new(config: Config) -> Self {
        Gateway { config }
    }
    
    /// Запускает шлюз в бесконечном цикле
    /// 
    /// Эта функция блокирует поток до возникновения ошибки
    pub async fn run(&self) -> Result<(), GatewayError> {
        // ...
    }
}
```
