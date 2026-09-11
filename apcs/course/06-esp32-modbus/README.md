# Модуль 6: ESP32 + Modbus

## Цель

Интегрировать Modbus на ESP32-S3: UART для RTU через MAX3485, TCP через Ethernet (W5500). Реализовать динамическую карту регистров.

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 6.1 UART для RS-485 | Конфигурация, тайминги, send/receive |
| 6.2 SPI W5500 | Инициализация, TCP socket, данные |
| 6.3 Конфигурация TOML | Загрузка, парсинг, DeviceConfig |
| 6.4 Register Map | Input/Holding/Coils, маппинг |
| 6.5 Исключения | Проверка границ, IllegalDataAddress |
| 6.6 Async паттерн | Poll scheduler, TCP server, tasks |

## Примеры

См. папку `examples/`:
- `uart_rs485.rs` — UART с управлением RS-485
- `w5500_init.rs` — инициализация W5500
- `register_map.rs` — динамическая карта регистров

## Упражнения

### Упр. 6.1: UART RS-485 Master
Реализуйте UART master, который:
- Конфигурирует UART: 9600 baud, 8E1
- Управляет GPIO4 для переключения направления
- Отправляет Modbus RTU запрос и читает ответ
- Проверяет CRC-16 ответа

### Упр. 6.2: W5500 TCP Server
Поднимите Modbus TCP сервер на ESP32 через W5500:
- Статический IP: 192.168.1.100
- Порт: 502
- 50 Holding Registers
- Тест: подключитесь с ноутбука через mbpoll

### Упр. 6.3: Register Map
Реализуйте RegisterMap с:
- 10 Input Registers (температуры)
- 4 Coils (статусы реле)
- 10 Holding Registers (команды)
- Доступ через Modbus TCP

### Упр. 6.4: Dynamic Configuration
Добавьте регистры конфигурации (0xF000+):
- Чтение: текущие настройки
- Запись: изменение интервала опроса
- Применение: запись в 0xFFFE перестраивает опрос

## Проверка

```bash
# Тест TCP сервера на ESP32
mbpoll -m tcp -a 1 -r 0 -c 10 192.168.1.100

# Проверка через UART
screen /dev/ttyUSB0 115200
```

## Следующий модуль

Переходите к [Модулю 7: Gateway Project](../07-gateway-project/).
