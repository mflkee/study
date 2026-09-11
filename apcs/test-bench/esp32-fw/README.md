# ESP32 Firmware — Modbus RTU Slave

## Распиновка

```
ESP32 HW678     MAX3485
GPIO17 (TX) ────► DI          ← Передача
GPIO18 (RX) ◄──── RO          ← Приём
GPIO4        ──── DE + RE     ← Направление (закоротить!)
3.3V         ──── VCC
GND          ──── GND

MAX3485 A ──── ZK-U485 A
MAX3485 B ──── ZK-U485 B
MAX3485 GND ── ZK-U485 GND
```

## Что делает

Работает как **Modbus RTU Slave** (ID=1) по RS-485.
Содержит имитацию датчиков:
- 15 температурных датчиков (Input Registers 0-29)
- 15 датчиков давления (Input Registers 30-59)
- 2 насоса (Coils 0-1)
- 32 Holding Registers (конфигурация)

## Сборка и прошивка

```bash
# Установка espup (один раз)
cargo install espup
rustup toolchain install esp --channel stable
espup install

# Сборка
cargo build --release

# Прошивка на ESP32
cargo run --release
```

## Проверка

После прошивки откройте serial monitor:

```bash
screen /dev/ttyUSB1 115200
```

И на другом PC-порту запустите тестовый клиент:

```bash
python3 ../scripts/modbus_client.py /dev/ttyUSB0 9600
```

## Pinout

| Пин | Назначение |
|-----|-----------|
| GPIO17 | UART1 TX → MAX3485 DI |
| GPIO18 | UART1 RX ← MAX3485 RO |
| GPIO4 | Направление RS-485 (DE+RE) |

## Параметры

| Параметр | Значение |
|----------|----------|
| Slave ID | 1 |
| Baud rate | 9600 |
| Data bits | 8 |
| Parity | None |
| Stop bits | 1 |