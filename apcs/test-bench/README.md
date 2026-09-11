# Test Bench — Modbus RTU для ESP32

## Структура

```
test-bench/
├── WIRING.md              # Распиновка: как расключить провода
├── README.md              # Этот файл
├── Cargo.toml             # Rust эмулятор ESP32 (для ПК)
├── src/
│   ├── main.rs            # Эмулятор Modbus RTU Slave (ПК)
│   ├── rtu_slave.rs       # Реализация RTU Slave
│   ├── register_map.rs    # Карта регистров
│   └── crc.rs             # CRC-16 Modbus
├── esp32-fw/              # Прошивка для ESP32
│   ├── Cargo.toml
│   ├── README.md
│   └── src/
│       ├── main.rs        # RTU Slave на ESP32
│       ├── rtu_slave.rs
│       ├── register_map.rs
│       └── crc.rs
└── scripts/
    ├── modbus_client.py    # Тестовый клиент (как ПЛК/SCADA)
    ├── zynq_simulator.py   # Симулятор Zynq (опрос датчиков в реальном времени)
    └── simulator.py        # Симулятор полевых устройств
```

## Сценарии использования

| Сценарий | Что запускается | Где |
|----------|-----------------|-----|
| Без ESP32 | Rust эмулятор на ПК | test-bench/ |
| С реальным ESP32 | esp32-fw прошивка | test-bench/esp32-fw/ |
| Имитация датчиков | Python simulator.py | ПК или ESP32 |

## Схема подключения (коротко)

```
MAX3485 модуль:
  RO  → ESP32 GPIO18
  DI  ← ESP32 GPIO17
  DE  + RE → ESP32 GPIO4  (закоротить DE и RE!)
  VCC → ESP32 3.3V
  GND → ESP32 GND

MAX3485:
  A → ZK-U485 A
  B → ZK-U485 B
  GND → ZK-U485 GND

ZK-U485 → PC USB
ESP32 (порт UART) → PC USB
```

## Быстрый старт

### 1. Проверка связи (без ESP32)

```bash
# Терминал 1: запускаем эмулятор ESP32 на ПК
cargo run --release -- /dev/ttyUSB1 9600

# Терминал 2: тестируем как ПЛК
python3 scripts/modbus_client.py /dev/ttyUSB0 9600
```

### 2. С реальным ESP32

```bash
# Прошивка
cd esp32-fw
cargo run --release

# serial monitor для отладки (см. ниже)
```

### 3. Zynq симулятор (основной режим)

```bash
# ПК постоянно опрашивает ESP32 (как Zynq)
python3 scripts/zynq_simulator.py /dev/ttyUSB0 9600 500
```

## Порты

```bash
# Определить порты
ls /dev/ttyUSB* /dev/ttyACM*

# Разрешение на доступ к порту (для пользователя)
sudo usermod -a -G uucp $USER   # Arch
sudo usermod -a -G dialout $USER # Ubuntu/Debian
# Перезайти в систему!
```

## Установка зависимостей

```bash
# Python
pip install pyserial

# Rust
cargo build
```

## Проверка связи

Если клиент не получает ответа:

1. **Проверьте DE/RE** — они должны быть соединены вместе
2. **Проверьте порты** — ZK-U485 и ESP32 это РАЗНЫЕ /dev/ttyUSB
3. **Проверьте байты кадра** — в WIRING.md есть hex-примеры
4. **Проверьте питание** — MAX3485 на 3.3V (не 5V!)
5. **Проверьте A/B** — A к A, B к B (не перепутать!)