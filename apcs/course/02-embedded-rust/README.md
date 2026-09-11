# Модуль 2: Embedded Rust

## Цель

Понять мир embedded на Rust: no_std, bare-metal, HAL (Hardware Abstraction Layer), Embassy framework, Async на микроконтроллерах.

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 2.1 std vs no_std | Когда использовать, core vs alloc |
| 2.2 Target Architecture | ARM Cortex-M, ESP32, thumbv7em |
| 2.3 PAC/HAL/App | Уровни абстракции |
| 2.4 Embassy | Async executor, tasks, interop |
| 2.5 GPIO | Input/Output, прерывания |
| 2.6 UART | Конфигурация, send/receive, RS-485 |
| 2.7 SPI | Режим, передача, W5500 |
| 2.8 Timers | SysTick, Watchdog, delays |
| 2.9 DMA | Direct Memory Access |
| 2.10 Паттерны | Concurrency без потоков, shared state |

### 2.1 Embedded Rust Overview

- std vs no_std: разница и компромиссы
- #[no_std] атрибут
- core vs alloc crates
- target architecture: thumbv7em-none-eabihf (ARM Cortex-M)

### 2.2 HAL (Hardware Abstraction Layer)

- Что такое HAL и зачем он нужен
- embedded-hal trait ecosystem
- Паттерн: PAC (Peripheral Access Crate) → HAL → приложение
- Примеры: esp-hal, stm32-hal

### 2.3 Embassy Framework

- Почему Embassy: async на embedded
- Executor и tasks
- peripherals и spawner
- Интеграция с WiFi, TCP/IP стеком

### 2.4 GPIO и Peripheral Control

- Настройка пинов (input, output, alternate function)
- Прерывания (interrupts) и обработчики
- DMA (Direct Memory Access) для высокоскоростных периферий

### 2.5 UART, SPI, I2C на Rust

- Конфигурация UART (baud rate, parity, stop bits)
- SPI: master/slave, modes, clock polarity
- I2C: addressing, read/write, repeated start
- Работа с буферами в no_std контексте

### 2.6 Timers и Delays

- SysTick timer
- Async delays в Embassy
- Watchdog timer

## Примеры кода

См. папку `examples/`:

- `blinky.rs` — мигание LED на ESP32-S3
- `uart_echo.rs` — эхо через UART
- `spi_master.rs` — SPI master
- `embassy_task.rs` — async задача в Embassy

## Упражнения

### Упр. 2.1: LED Blink

Настройте GPIO пин для управления LED. Реализуйте мигание с переменной задержкой.

### Упр. 2.2: UART Communication

Настройте UART на GPIO17 (TX) и GPIO18 (RX). Реализуйте:

- Приём строки и эхо обратно
- Парсинг команд (LED_ON, LED_OFF, STATUS)

### Упр. 2.3: SPI Device

Подключите SPI-устройство (можно W5500 или PCA9685).
Настройте SPI master и прочитите 4 байта из регистра.

### Упр. 2.4: Embassy Async

Создайте 2 async задачи в Embassy:

- Задача 1: мигает LED каждые 500мс
- Задача 2: считает секунды и выводит в UART

## Проверка

```bash
# Для ESP32-S3 (если настроен toolchain)
cargo build --target thumbv7em-none-eabihf --release

# Или проверка синтаксиса
cargo check
```

## Следующий модуль

Переходите к [Модулю 3: ESP32-S3 Setup](../03-esp32-setup/).
