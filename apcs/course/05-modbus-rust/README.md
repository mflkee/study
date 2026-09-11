# Модуль 5: Modbus on Rust

## Цель

Освоить основные Rust-крейты для Modbus: tokio-modbus, rmodbus, modbus-rs. Реализовать простой TCP-сервер и клиент.

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 5.1 Обзор крейтов | tokio-modbus, modbus-rs, rmodbus, modbus-bridge |
| 5.2 tokio-modbus | TCP client, TCP server, Service trait |
| 5.3 rmodbus | no_std, ModbusContext, обработка ошибок |
| 5.4 modbus-bridge | Готовый шлюз RTU ↔ TCP |
| 5.5 PDU на Rust | Парсинг, формирование, MBAP Header |
| 5.6 CRC-16 | Алгоритм, тесты, оптимизация |
| 5.7 Shared State | Arc, Mutex, RwLock в async |
| 5.8 Логирование | env_logger, log macros |

## Примеры

См. папку `examples/`:
- `tokio_tcp_server.rs` — минимальный TCP-сервер
- `tokio_tcp_client.rs` — клиент для тестирования
- `rmodbus_server.rs` — сервер на rmodbus (no_std совместимый)
- `modbus_bridge.rs` — шлюз RTU ↔ TCP

## Упражнения

### Упр. 5.1: Minimal TCP Server
Реализуйте Modbus TCP сервер на tokio-modbus:
- Слушает порт 502
- Поддерживает: 0x03 (Read Holding Registers), 0x06 (Write Single Register)
- 100 Holding Registers, инициализированы нулями
- Возвращает исключения при ошибках

### Упр. 5.2: TCP Client Test
Напишите клиента, который:
- Подключается к серверу
- Записывает 10 значений (0..9) в регистры 0..9
- Читает их обратно и проверяет

### Упр. 5.3: Exception Handling
Добавьте в сервер обработку:
- Запрос к несуществующему адресу → Illegal Data Address (0x02)
- Запрос 200 регистров за раз → Illegal Data Value (0x03)
- Неподдерживаемую функцию → Illegal Function (0x01)

### Упр. 5.4: Shared State
Реализуйте сервер с разделяемым состоянием (Arc<Mutex<...>>):
- Один регистр обновляется фоновой задачей каждую секунду
- Клиент может прочитать актуальное значение

## Проверка

```bash
# Запуск сервера
cargo run --example tokio_tcp_server

# В другом терминале — тест
cargo run --example tokio_tcp_client

# Или через mbpoll
mbpoll -m tcp -a 1 -r 0 -c 10 127.0.0.1
```

## Следующий модуль

Переходите к [Модулю 6: ESP32 + Modbus](../06-esp32-modbus/).
