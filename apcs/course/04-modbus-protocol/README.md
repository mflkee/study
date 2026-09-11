# Модуль 4: Modbus Protocol

## Цель

Полностью освоить протокол Modbus: архитектуру, форматы кадров (RTU/TCP), коды функций, исключения, адресацию. Это теоретическая база для реализации сервера.

## Теория

### 4.1 Архитектура Modbus

```
┌─────────────────┐         ┌─────────────────┐
│  Master/Client   │ ◄─────► │  Slave/Server    │
│  (инициатор)     │         │  (отвечает)      │
│                  │         │                  │
│  SCADA, ПЛК,    │         │  ESP32, датчики,  │
│  Zynq, ноутбук  │         │  модули ввода    │
└─────────────────┘         └─────────────────┘
```

- **Master (Client)** — инициирует запросы
- **Slave (Server)** — отвечает на запросы, не может начать первым
- Адреса устройств: 1–247 (0 = broadcast, 248–255 зарезервированы)

### 4.2 Типы регистров Modbus

| Тип | Размер | Чтение | Запись | Функции | Назначение |
|-----|--------|--------|--------|---------|-----------|
| Coils | 1 бит | ✓ | ✓ | 0x01, 0x05, 0x0F | Реле, флаги |
| Discrete Inputs | 1 бит | ✓ | ✗ | 0x02 | Датчики on/off |
| Input Registers | 16 бит | ✓ | ✗ | 0x04 | Показания датчиков |
| Holding Registers | 16 бит | ✓ | ✓ | 0x03, 0x06, 0x10 | Настройки, переменные |

### 4.3 Адресация

Протокол (0-based) → Человеческая нотация (Modicon):
- Coils: 0 → 10001
- Discrete Inputs: 0 → 20001
- Input Registers: 0 → 30001
- Holding Registers: 0 → 40001

### 4.4 Коды функций

| Код | HEX | Назначение |
|-----|-----|-----------|
| 1 | 0x01 | Read Coils |
| 2 | 0x02 | Read Discrete Inputs |
| 3 | 0x03 | Read Holding Registers |
| 4 | 0x04 | Read Input Registers |
| 5 | 0x05 | Write Single Coil |
| 6 | 0x06 | Write Single Register |
| 15 | 0x0F | Write Multiple Coils |
| 16 | 0x10 | Write Multiple Registers |
| 23 | 0x17 | Read/Write Multiple Registers |

### 4.5 Modbus RTU

**Структура кадра (ADU):**
```
[Device Address 1B] [Function Code 1B] [Data NB] [CRC-16 2B]
```

**Критичные детали:**
- CRC-16 Modbus: полином 0xA001, начальное значение 0xFFFF, reflection
- Пауза между кадрами: ≥ 3.5 символов
- Пауза внутри кадра: ≤ 1.5 символов
- Big-endian для полей

### 4.6 Modbus TCP

**Структура кадра (ADU):**
```
[MBAP Header 7B] [PDU]
```

**MBAP Header:**
```
Transaction ID  2B  — ID запроса (копируется в ответ)
Protocol ID     2B  — всегда 0x0000
Length          2B  — длина Unit ID + PDU
Unit ID         1B  — адрес устройства
```

**Отличия от RTU:**
- НЕТ CRC (обеспечивается TCP)
- НЕТ таймингов (кадры по полю Length)
- Порт по умолчанию: 502
- Big-endian для всех полей

### 4.7 Исключения

**Формат ответа-исключения:**
```
[Function Code | 0x80] [Exception Code]
```

| Код | HEX | Название |
|-----|-----|---------|
| 01 | 0x01 | Illegal Function |
| 02 | 0x02 | Illegal Data Address |
| 03 | 0x03 | Illegal Data Value |
| 04 | 0x04 | Failure in Associated Device |

### 4.8 Endianness

- Регистры: big-endian (标准)
- Float/int32: НЕ стандартизировано (нужно документировать!)
- Битовая упаковка Coils: LSB first, padding нулями

### 4.9 Лимиты

| Функция | Мин | Макс |
|---------|-----|------|
| Read Coils (0x01) | 1 | 2000 |
| Read Discrete Inputs (0x02) | 1 | 2000 |
| Read Holding Registers (0x03) | 1 | 125 |
| Read Input Registers (0x04) | 1 | 125 |
| Write Multiple Coils (0x0F) | 1 | 1968 |
| Write Multiple Registers (0x10) | 1 | 123 |

### 4.10 Common Pitfalls — частые ошибки

**1. Endianness float**
```python
# ❌ Неправильно: каждый регистр по отдельности
value = struct.unpack('>f', bytes([reg_hi, reg_lo]))[0]

# ✅ Правильно: 2 регистра в float32
raw = (reg_hi << 16) | reg_lo
value = struct.unpack('>f', struct.pack('>I', raw))[0]
```

**2. Inter-frame gap (3.5 символа)**
```python
# ❌ Нет паузы между кадрами
send_frame(frame1)
send_frame(frame2)  # Slave может воспринять как один кадр

# ✅ Пауза 3.5 символов
send_frame(frame1)
time.sleep(0.005)  # 5 мс при 9600 baud
send_frame(frame2)
```

**3. Unit ID vs Slave ID**
```python
# ❌ Путаница: TCP protocol_id != unit_id
client.unit = 1  # Это не protocol ID!

# ✅ В TCP: Unit ID = Slave ID
client.unit_id = 1  # Идентификатор устройства на шине
```

**4. Broadcast (address 0)**
```python
# ❌ Broadcast не требует ответа
client.write_register(0, 42, unit=0)  # Нет ответа от slave

# ✅ Broadcast — запись всем, ответа нет
client.write_register(0, 42, unit=0)  # OK, но не ждите ответа
```

**5. Конец ночи (garbage data)**
```python
# ❌ Чтение после ошибки
response = client.read_holding_registers(0, 10, unit=1)
if response.isError():
    # Данные могут быть мусором!
    value = response.registers[0]  # ОШИБКА

# ✅ Всегда проверяйте ошибки
response = client.read_holding_registers(0, 10, unit=1)
if not response.isError():
    value = response.registers[0]
```

## Примеры

См. папку `examples/`:
- `rtu_frame.py` — генерация RTU-кадра с CRC
- `tcp_frame.py` — генерация TCP-кадра с MBAP
- `client_demo.py` — пример клиента на Python (pymodbus)

## Упражнения

### Упр. 4.1: CRC-16
Реализуйте CRC-16 Modbus на Python/Rust. Проверьте на известных значениях:
- CRC("123456789") должен быть 0x4B37

### Упр. 4.2: PDU Parser
Напишите парсер PDU: принимает байты, возвращает структуру с кодом функции и данными.

### Упр. 4.3: Modbus Client
Используя pymodbus (Python), подключитесь к серверу (mabi-modbus или собственному) и:
- Прочитайте 10 Holding Registers
- Запишите значение в Register 0
- Вызовите исключение (обратитесь к несуществующему адресу)

### Упр. 4.4: Wireshark
Перехватите Modbus TCP трафик через Wireshark. Определите MBAP Header, Transaction ID, Function Code.

## Проверка

```bash
# Запуск симулятора
docker run --rm -p 502:502 oitc/modbus-server

# pymodbus клиент
python3 -c "from pymodbus.client import ModbusTcpClient; c=ModbusTcpClient('127.0.0.1'); c.connect(); print(c.read_holding_registers(0, 10)); c.close()"

# Установка pymodbus
pip install pymodbus
```

## Следующий модуль

Переходите к [Модулю 5: Modbus on Rust](../05-modbus-rust/).
