# Embassy Skeleton для ESP32-S3

## Описание

Этот скелет проекта демонстрирует минимальный Embassy-проект для ESP32-S3 с async/await.

## Структура

```
embassy-skeleton/
├── Cargo.toml
├── .cargo/
│   └── config.toml        # Конфигурация для ESP32-S3
├── src/
│   ├── main.rs            # Точка входа
│   ├── sensors.rs         # Датчики
│   └── network.rs         # Сеть (TCP/UDP)
└── README.md
```

## Установка

```bash
# Установка target для ESP32-S3
rustup target add xtensa-esp32s3-none-elf

# Установка espup (если ещё нет)
cargo install espup
espup install

# Сборка
cargo build --release

# Прошивка
cargo run --release
```

## Что показывает

1. Async executor на embedded
2. GPIO через Embassy HAL
3. Таймеры и delays
4. Прерывания
5. Spawn задач

## Пример кода

```rust
#![no_std]
#![no_main]

use embassy_executor::Spawner;
use embassy_time::Timer;
use esp_hal::gpio::{Level, Output};
use esp_hal::timer::TimerGroup;

#[esp_hal_embassy::main]
async fn main(spawner: Spawner) {
    let peripherals = esp_hal::init(esp_hal::Config::default());
    
    let timg0 = TimerGroup::new(peripherals.TIMG0);
    esp_hal_embassy::init(timg0.timer0);
    
    let mut led = Output::new(peripherals.GPIO2, Level::Low);
    
    loop {
        led.toggle();
        Timer::after_millis(500).await;
    }
}
```
