# Теория: ESP32-S3 Setup

## 1. ESP32-S3 архитектура

### Основные характеристики

| Параметр | Значение |
|----------|----------|
| Ядро | Dual-core Xtensa LX7, до 240 МГц |
| ОЗУ | 512 КБ SRAM + до 8 МБ PSRAM |
| Flash | До 16 МБ (внешняя) |
| Wi-Fi | 802.11 b/g/n 2.4 ГГц |
| Bluetooth | BLE 5.0 |
| GPIO | 45 программируемых пинов |
| UART | 3× |
| SPI | 2× |
| I2C | 2× |
| ADC | 2× (до 20 каналов) |
| USB | USB-OTG |

### Внутренняя структура

```
┌─────────────────────────────────────────────┐
│                ESP32-S3                      │
│                                              │
│  ┌──────────┐  ┌──────────┐                 │
│  │ Core 0   │  │ Core 1   │                 │
│  │ Xtensa   │  │ Xtensa   │                 │
│  │ LX7      │  │ LX7      │                 │
│  └──────────┘  └──────────┘                 │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │            Memory Map                 │   │
│  │  0x3FFB_0000: SRAM (512 KB)          │   │
│  │  0x4200_0000: Flash (mapped)         │   │
│  │  PSRAM: внешняя (до 8 МБ)            │   │
│  └──────────────────────────────────────┘   │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │          Peripherals                  │   │
│  │  GPIO, UART, SPI, I2C, ADC, PWM...   │   │
│  └──────────────────────────────────────┘   │
│                                              │
└─────────────────────────────────────────────┘
```

## 2. Выбор среды разработки

### Сравнение подходов

| Подход | Язык | Фреймворк | Размер кода | Async | Сложность |
|--------|------|-----------|-------------|-------|-----------|
| Arduino IDE | C++ | Arduino | ~500 КБ | Нет | Низкая |
| ESP-IDF | C | IDF | ~300 КБ | Есть | Средняя |
| ESP-IDF + Rust | Rust | esp-idf-hal | ~400 КБ | Есть | Средняя |
| Embassy + Rust | Rust | Embassy | ~50 КБ | Нативный | Высокая |

### Рекомендация

**Для старта:** ESP-IDF + esp-idf-hal (std, проверенный фреймворк)
**Для production:** Embassy (no_std, минимальный размер, async)

## 3. Установка Toolchain

### Rust

```bash
# Установка Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Добавление target
rustup target add thumbv7em-none-eabihf    # для ESP32 (если через Embassy)
rustup component add rust-src              # для сборки std
```

### ESP-IDF (для esp-idf-hal)

```bash
# Установка ESP-IDF
git clone --recursive https://github.com/espressif/esp-idf.git
cd esp-idf
./install.sh esp32s3
source export.sh

# Установка espup (рекомендуется)
cargo install espup
espup install
```

### VS Code

```bash
# Установка расширений
code --install-extension rust-lang.rust-analyzer
code --install-extension esp-idf.esp-idf-extension
```

## 4. Распиновка ESP32-S3 DevKitC

### Критичные пины

**GPIO 26-32:** ЗАНЯТЫ Flash/PSRAM. НЕЛЬЗЯ использовать!

**Strapping pins:** GPIO 0, 3, 45, 46 — определяют режим загрузки. Не трогать при старте!

### Распределение пинов для проекта

```
ESP32-S3 DevKitC
┌─────────────────────────────────────────────────┐
│                                                 │
│  3V3  ───┐                                      │
│  GND  ───┤                                      │
│  GPIO0 ──┤ Strapping (не трогать)               │
│  GPIO1 ──┤ (ADC1, Touch)                        │
│  GPIO2 ──┤ (встроенный LED на некоторых платах)  │
│  GPIO3 ──┤ Strapping                            │
│  GPIO4 ──┼── RS-485 DIR (RE/DE)                 │
│  GPIO5 ──┤ (W5500 CS alternative)               │
│  ...                                            │
│  GPIO10 ─┼── SPI2 CS (W5500)                    │
│  GPIO11 ─┼── SPI2 MOSI (W5500)                  │
│  GPIO12 ─┼── SPI2 SCK (W5500)                   │
│  GPIO13 ─┼── SPI2 MISO (W5500)                  │
│  GPIO14 ─┼── W5500 IRQ                          │
│  ...                                            │
│  GPIO17 ─┼── UART1 TX (RS-485 MAX3485 DI)      │
│  GPIO18 ─┼── UART1 RX (RS-485 MAX3485 RO)      │
│  ...                                            │
│  GPIO26-32 ── Flash/PSRAM (НЕ ТРОГАТЬ!)         │
│  ...                                            │
│  GPIO43 ─┼── UART0 TX (USB отладка)             │
│  GPIO44 ─┼── UART0 RX (USB отладка)             │
│  GPIO45 ──┤ Strapping                            │
│  GPIO46 ──┤ Strapping                            │
│                                                 │
└─────────────────────────────────────────────────┘
```

### Таблица соответствия

| Пин | Функция в проекте | Подключение |
|-----|-------------------|-------------|
| GPIO4 | RS-485 DIR | MAX3485 RE+DE |
| GPIO10 | SPI2 CS | W5500 CS |
| GPIO11 | SPI2 MOSI | W5500 MOSI |
| GPIO12 | SPI2 SCK | W5500 SCK |
| GPIO13 | SPI2 MISO | W5500 MISO |
| GPIO14 | GPIO | W5500 INT |
| GPIO17 | UART1 TX | MAX3485 DI |
| GPIO18 | UART1 RX | MAX3485 RO |
| GPIO43 | UART0 TX | USB-UART bridge |
| GPIO44 | UART0 RX | USB-UART bridge |

## 5. Первая прошивка

### Hello World (ESP-IDF + Rust)

```bash
# Создание проекта
cargo generate esp-rs/esp-idf-template
cd my-project

# Сборка и прошивка
cargo build --release
cargo run --release
```

### Hello World (Embassy)

```bash
# Создание проекта
cargo generate esp-rs/esp-template
cd my-project

# Сборка
cargo build --release

# Прошивка
cargo run --release
```

### Проверка

```bash
# Монитор UART
screen /dev/ttyUSB0 115200
# или
minicom -D /dev/ttyUSB0 -b 115200
```

## 6. USB-UART Bridge

ESP32-S3 DevKitC имеет встроенный USB-UART мост (CP2102 или CH340).

### Определение порта

```bash
# После подключения USB
ls /dev/ttyUSB*
#или
ls /dev/ttyACM*

# Информация о устройстве
dmesg | tail
```

### Настройка прав

```bash
# Добавление в группу uucp (Arch Linux)
sudo usermod -a -G uucp $USER
# Перезайти в систему!

# Или временное решение
sudo chmod 666 /dev/ttyUSB0
```

## 7. Отладка

### Serial Monitor

```bash
# screen
screen /dev/ttyUSB0 115200

# minicom
minicom -D /dev/ttyUSB0 -b 115200

# idf_monitor (ESP-IDF)
idf.py monitor
```

### JTAG отладка

ESP32-S3 поддерживает JTAG через USB:

```bash
# OpenOCD
openocd -f interface/esp32s3.cfg -f target/esp32s3.cfg

# GDB
gdb target/remote:3333
```

## 8. Power Management

### Режимы питания

| Режим | Потребление | Описание |
|-------|-------------|----------|
| Active | ~80 мА | Полная работа |
| Modem Sleep | ~15 мА | Wi-Fi выключен |
| Light Sleep | ~800 мкА | CPU приостановлен |
| Deep Sleep | ~10 мкА | Только RTC |
| Hibernation | ~5 мкА | Минимум функций |

### Пример: Deep Sleep

```rust
use esp_hal::rtc_cntl::Rtc;

let rtc = Rtc::new(peripherals.RTC_CNTL);
rtc.sleep_deep(Duration::from_secs(30)); // проснуться через 30 сек
```
