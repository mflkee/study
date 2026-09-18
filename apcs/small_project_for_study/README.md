# Мини-проект: кнопка + светодиод по Modbus RTU

Маленький полигон «от первого бита до ИВК»: ESP32-S3 управляет
**светодиодом** и читает **кнопку** по **Modbus RTU** (RS-485).
Реально собирается и реально работает на настольном столе —
без внешних АЦП и мультиплексоров, только провода.

```
 Светодиод (GPIO1)
  ▲
  │
┌─┴─────────────┐
│   mini-core   │  <-- ядро: dispatch() + CRC, проверяется cargo test на ПК
├───────────────┤
│   mini-fw     │  <-- прошивка: GPIO + UART RTU (esp-idf-hal)
└───────┬───────┘
        │ RS-485
  ┌─────┴─────┐
  │  ИВК      │  <-- Python-клиент mini_client.py (pyserial)
  └───────────┘
```

---

## Карта регистров (0-based)

| Тип        | Адрес | Описание              | FC read   | FC write         |
|------------|-------|-----------------------|-----------|------------------|
| COIL       | 0     | Светодиод (LED)       | FC01      | FC05 (эхо)       |
| COIL       | 1     | Кнопка (button)       | FC01      | read-only (0x02) |
| HOLDING    | 0     | Счётчик нажатий       | FC03      | read-only (0x04) |

- **SLAVE_ID** = 1
- **BAUD_RATE** = 9600, 8N1
- Unknown FC → exception 0x01; bad address → 0x02

---

## Структура

```
apcs/small_project_for_study/
├── Cargo.toml              # workspace: [core, fw]
├── core/                   # чистый Rust, работает на ПК
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs          # описание крейта
│       ├── crc.rs          # CRC-16 Modbus (0xA001) — append_crc / verify_crc + тест
│       ├── registers.rs    # карта регистров: dispatch() + build_response + exception_pdu + тесты
│       └── debounce.rs     # Debounce (SAMPLES=5) + тесты
├── fw/                     # прошивка ESP32-S3
│   ├── Cargo.toml
│   ├── build.rs            # простая build-скрипта
│   ├── rust-toolchain.toml # channel "esp"
│   ├── .cargo/config.toml  # target xtensa-esp32s3-espidf
│   └── src/
│       ├── main.rs         # init + опрос + Modbus RTU slave loop
│       ├── hw.rs           # Led / Button — обёртки над GPIO
│       └── rtu.rs          # Modbus RTU slave: handle_frame / slave_loop / write_all
├── scripts/
│   └── mini_client.py      # Python-клиент: read / led on|off / poll
└── course/
    └── 00-mini-project/    # урок (см. COURSE.md ниже)
```

---

## Быстрый старт

### 1) Ядро — тесты на ПК

```bash
cd apcs/small_project_for_study/core
cargo test
# 13 тестов — без железа
```

### 2) Прошивка — ESP32-S3

```bash
cd apcs/small_project_for_study/fw

# Сборка (разверните esp-idf, если нет):
cargo +esp build --release
```

> **Если submodule-clone падает по SSH:**
> ```bash
> GIT_CONFIG_COUNT=1 \
> GIT_CONFIG_KEY_0="url.https://github.com/.insteadOf" \
> GIT_CONFIG_VALUE_0="git@github.com:" \
> cargo +esp build --release
> ```

### 3) Прошивка через USB

```bash
espflash target/xtensa-esp32s3-espidf/release/mini-fw
# Не забудьте: TX GPIO43 / RX GPIO44 — RS-485 (MAX3485)
```

### 4) Python-клиент

```bash
pip install pyserial
cd apcs/small_project_for_study/scripts

# Один опрос:
python3 mini_client.py /dev/ttyUSB0 read

# Включить LED:
python3 mini_client.py /dev/ttyUSB0 led on

# Цикл (10 итераций по 1 с):
python3 mini_client.py /dev/ttyUSB0 poll --limit 10
```

---

## Распиновка (ESP32-S3 DevKitC)

| Функция | GPIO | Примечание                     |
|---------|------|--------------------------------|
| LED     | 1    | Активный высокий               |
| Button  | 0    | BOOT, Pull::Up, active-low     |
| UART TX | 43   | RS-485 (MAX3485 DI)            |
| UART RX | 44   | RS-485 (MAX3485 RO)            |

---

## Чекпоинты для занятия

- [ ] `cargo test` в core — 13 зелёных
- [ ] `cargo +esp check` в fw — без ошибок
- [ ] Стартовый «проблеск» LED (3 вспышки) после загрузки
- [ ] `mini_client.py read` — видим OFF / отпущена / 0
- [ ] `mini_client.py led on` — LED горит
- [ ] Нажимаем кнопку → `mini_client.py read` — нажатий=1
- [ ] `cargo +esp clippy` — чисто

---

## Ключевые файлы для разбора

| Что                              | Где                                |
|----------------------------------|------------------------------------|
| CRC-16 Modbus                    | `core/src/crc.rs`                  |
| Dispatch по FC + исключения      | `core/src/registers.rs`            |
| Дебаунс (простейший счётчик)     | `core/src/debounce.rs`             |
| RTU slave loop                   | `fw/src/rtu.rs`                    |
| GPIO-обёртки                     | `fw/src/hw.rs`                     |
| Мейнлуп:.poll + sync LED         | `fw/src/main.rs`                   |
