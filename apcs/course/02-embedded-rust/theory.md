# Теория: Embedded Rust

## 1. std vs no_std

### Что такое std

`std` — стандартная библиотека Rust, содержащая:
- Файловая система (`std::fs`)
- Сеть (`std::net`)
- Потоки (`std::thread`)
- Сборщик мусора (нет, но есть аллокации)
- ОС-зависимые функции

### Что такое no_std

`no_std` — среда без стандартной библиотеки. Доступны только:
- `core` — базовые типы, итераторы, Option, Result
- `alloc` (опционально) — Vec, String, Box (требует аллокатор)

```rust
// no_std крейт
#![no_std]

// Если нужен alloc
extern crate alloc;
use alloc::vec::Vec;
use alloc::string::String;

// Или чистый core
use core::fmt;
```

### Когда использовать no_std

| Критерий | std | no_std |
|----------|-----|--------|
| Размер бинарника | > 1 МБ | < 100 КБ |
| ОС | Linux, Windows, macOS | Нет ОС |
| Аллокации | Свободные | Ограниченные |
| Примеры | Серверы на ПК | Микроконтроллеры |

## 2. Target Architecture

### ARM Cortex-M

ESP32-S3 использует ядро Xtensa (не ARM), но в embedded Rust часто работают с ARM:

```
thumbv7em-none-eabihf
│       │    │   │ │
│       │    │   │ └─ hard float
│       │    │   └─── embedded ABI
│       │    └────── bare-metal (нет ОС)
│       └─────────── ARMv7-M (Cortex-M4/M7)
└─────────────────── thumb instruction set
```

### Установка target

```bash
rustup target add thumbv7em-none-eabihf
rustup target add xtensa-esp32s3-none-elf  # для ESP32
```

## 3. PAC, HAL, и Application Layer

### PAC (Peripheral Access Crate)

Низкоуровневый доступ к регистрам периферии:

```rust
// Пример: настройка GPIO через регистры
use esp32s3::Peripherals;

let peripherals = Peripherals::take().unwrap();
let gpio = peripherals.GPIO;

// Запись в регистр напрямую
gpio.gpio_out_reg.modify(|_, w| w.gpio_out().set_bit());
```

### HAL (Hardware Abstraction Layer)

Высокоуровневая обёртка над PAC:

```rust
use esp_hal::gpio::{Level, Output, OutputPin};

// Через HAL — проще и безопаснее
let mut led = Output::new(peripherals.GPIO2, Level::Low);
led.set_high();
```

### Уровни абстракции

```
┌─────────────────────────────┐
│        Application          │  ← Ваш код
├─────────────────────────────┤
│         HAL                 │  ← esp-hal, stm32-hal
├─────────────────────────────┤
│         PAC                 │  ← esp32s3, stm32f4
├─────────────────────────────┤
│       Hardware Registers    │  ← Аппаратура
└─────────────────────────────┘
```

## 4. Embassy Framework

### Почему Embassy

- **Async на embedded** — нет блокирующих вызовов
- **Executor** — планировщик задач
- **Interoperability** — стандартный async/await

### Структура Embassy проекта

```rust
#![no_std]
#![no_main]

use embassy_executor::Spawner;
use embassy_time::Timer;
use esp_hal::gpio::{Level, Output};

#[embassy_executor::main]
async fn main(_spawner: Spawner) {
    // Инициализация
    let peripherals = esp_hal::init(esp_hal::Config::default());
    let mut led = Output::new(peripherals.GPIO2, Level::Low);
    
    // Async цикл
    loop {
        led.toggle();
        Timer::after_millis(500).await;
    }
}
```

### Embassy Tasks

```rust
#[embassy_executor::task]
async fn blink_task(mut led: Output<'static>) {
    loop {
        led.toggle();
        Timer::after_millis(500).await;
    }
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let peripherals = esp_hal::init(esp_hal::Config::default());
    let led = Output::new(peripherals.GPIO2, Level::Low);
    
    spawner.spawn(blink_task(led)).unwrap();
}
```

## 5. GPIO (General Purpose Input/Output)

### Режимы работы

| Режим | Описание | Пример |
|-------|----------|--------|
| Input | Чтение уровня | Кнопка |
| Output | Управление уровнем | LED, реле |
| Alternate | Альтернативная функция | UART, SPI |
| Analog | АЦП/ЦАП | Датчик температуры |

### Пример: управление LED

```rust
use esp_hal::gpio::{Level, Output, Input, Pull};

// Выход: управление LED
let mut led = Output::new(peripherals.GPIO2, Level::Low);
led.set_high();  // включить
led.set_low();   // выключить
led.toggle();    // переключить

// Вход: чтение кнопки
let button = Input::new(peripherals.GPIO0, Pull::Up);
if button.is_high() {
    // Кнопка нажата
}
```

### Прерывания (Interrupts)

```rust
use esp_hal::gpio::{Input, Pull, Event};

let mut button = Input::new(peripherals.GPIO0, Pull::Up);

// Настройка прерывания
button.listen(Event::FallingEdge);

// Обработчик прерывания (в async)
loop {
    button.wait_for_event().await;
    // Кнопка нажата
}
```

## 6. UART (Universal Asynchronous Receiver-Transmitter)

### Конфигурация

```rust
use esp_hal::uart::{Config, DataBits, Parity, StopBits, Uart};

let config = Config {
    baudrate: 9600,
    data_bits: DataBits::DataBits8,
    parity: Parity::ParityNone,
    stop_bits: StopBits::StopBits1,
};

let mut uart = Uart::new(peripherals.UART1, config);
```

### Отправка и приём

```rust
// Отправка
uart.write(b"Hello").unwrap();
uart.flush().unwrap();

// Приём
let mut buf = [0u8; 64];
let len = uart.read(&mut buf).unwrap();
```

### RS-485 через UART

```rust
// Управление направлением (GPIO4)
let mut dir_pin = Output::new(peripherals.GPIO4, Level::Low);

// Передача
dir_pin.set_high();  // TX mode
uart.write(&data).unwrap();
uart.flush().unwrap();
dir_pin.set_low();   // RX mode

// Приём
let len = uart.read(&mut buf).unwrap();
```

## 7. SPI (Serial Peripheral Interface)

### Конфигурация

```rust
use esp_hal::spi::{Mode, Phase, Polarity, Spi};

let spi = Spi::new(
    peripherals.SPI2,
    sck,
    mosi,
    miso,
    cs,
    10.MHz(),  // тактовая частота
    Mode {
        polarity: Polarity::IdleLow,
        phase: Phase::CaptureOnFirstTransition,
    },
);
```

### Передача данных

```rust
// Full-duplex transfer
let mut tx_buf = [0xAA, 0xBB, 0xCC, 0xDD];
let mut rx_buf = [0u8; 4];

spi.transfer(&mut rx_buf, &tx_buf).unwrap();

// Простая отправка
spi.write(&[0x01, 0x02, 0x03]).unwrap();
```

### Пример: W5500 через SPI

```rust
// Чтение регистра W5500
fn read_w5500_register(spi: &mut Spi, addr: u16) -> u8 {
    let cmd = [
        (addr >> 8) as u8,  // старший байт адреса
        addr as u8,          // младший байт адреса
        0x00,                // команда чтения
    ];
    
    let mut response = [0u8; 1];
    spi.write(&cmd).unwrap();
    spi.read(&mut response).unwrap();
    response[0]
}
```

## 8. Timers и Delays

### SysTick Timer

```rust
use esp_hal::timer::Timer;
use embassy_time::Timer as EmbassyTimer;

// Блокирующий delay (не в async)
esp_hal::delay::block_for(100.millis());

// Async delay (в Embassy)
EmbassyTimer::after_millis(500).await;
```

### Watchdog Timer

```rust
use esp_hal::wdt::Watchdog;

let mut wdt = Watchdog::new(peripherals.WDT);
wdt.start(5_000_000).unwrap(); // 5 секунд

// В основном цикле
loop {
    wdt.feed().unwrap(); // сброс таймера
    // ... работа
}
```

## 9. DMA (Direct Memory Access)

DMA позволяет передавать данные без участия CPU:

```rust
use esp_hal::dma::{Dma, DmaChannel};

// Настройка DMA канала
let dma = Dma::new(peripherals.DMA);

// SPI с DMA
let spi = spi.with_dma(dma_channel);

// Асинхронная передача
spi.transfer_dma(&mut tx_buf, &mut rx_buf).await;
```

## 10. Паттерны embedded разработки

### Concurrency без потоков

```rust
// Один поток, несколько задач через async
#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let peripherals = esp_hal::init(esp_hal::Config::default());
    
    // Spawn задач
    spawner.spawn(sensor_task()).unwrap();
    spawner.spawn(comm_task()).unwrap();
    spawner.spawn(watchdog_task()).unwrap();
}

#[embassy_executor::task]
async fn sensor_task() {
    loop {
        // Чтение датчиков
        Timer::after_millis(100).await;
    }
}

#[embassy_executor::task]
async fn comm_task() {
    loop {
        // Обработка сети
        Timer::after_millis(50).await;
    }
}
```

### Shared State

```rust
use core::sync::atomic::{AtomicBool, AtomicU32, Ordering};

static DATA_READY: AtomicBool = AtomicBool::new(false);
static SENSOR_VALUE: AtomicU32 = AtomicU32::new(0);

// В одном таске
SENSOR_VALUE.store(42, Ordering::Relaxed);
DATA_READY.store(true, Ordering::Release);

// В другом таске
if DATA_READY.load(Ordering::Acquire) {
    let value = SENSOR_VALUE.load(Ordering::Relaxed);
    // обработать value
}
```
