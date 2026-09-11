# Модуль 7: Gateway Project

## Цель

Собрать полный Modbus TCP/RTU шлюз на ESP32-S3. Объединить RTU Master (RS-485) и TCP Slave (Ethernet) в один проект.

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 7.1 Архитектура шлюза | Блок-схема, потоки данных |
| 7.2 RTU Master | Poll scheduler, обработка ошибок |
| 7.3 TCP Server | MBAP Header, Service, клиенты |
| 7.4 Register Map | Input/Holding/Coils, карта памяти |
| 7.5 Конфигурация | TOML, загрузка, DeviceConfig |

```
┌─────────────────────────────────────────────────────────────┐
│                      ESP32-S3                                │
│                                                              │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────────┐  │
│  │ RTU Master  │───►│ Register Map │◄───│  TCP Slave     │  │
│  │ (UART1)     │    │ (Shared)     │    │  (W5500)       │  │
│  └──────┬──────┘    └──────────────┘    └───────┬────────┘  │
│         │                                       │            │
│    MAX3485                                 Ethernet          │
│    (GPIO4,17,18)                          (W5500)            │
└─────────┼───────────────────────────────────────┼────────────┘
          │                                       │
     RS-485 шина                          Ethernet коммутатор
          │                                       │
┌─────────┴───────────────────────────────────────┴────────────┐
│ Полевые устройства                          Zynq/ARM/ПЛК     │
│ (15 ДТ + 15 ДД + 2 пробоотборника)          (TCP Client)     │
└──────────────────────────────────────────────────────────────┘
```

## Структура проекта

```
project/
├── src/
│   ├── main.rs                    # Точка входа, инициализация
│   ├── modbus/
│   │   ├── mod.rs                 # Modbus модуль
│   │   ├── rtu_master.rs          # RTU Master для RS-485
│   │   ├── tcp_slave.rs           # TCP Slave (сервер)
│   │   ├── register_map.rs        # Карта регистров
│   │   ├── pdu.rs                 # Парсинг/формирование PDU
│   │   └── exceptions.rs          # Обработка исключений
│   ├── drivers/
│   │   ├── mod.rs
│   │   ├── uart.rs                # UART драйвер
│   │   ├── spi.rs                 # SPI драйвер
│   │   ├── rs485.rs               # RS-485 (GPIO4 control)
│   │   └── ethernet.rs            # W5500 Ethernet
│   ├── sensors/
│   │   ├── mod.rs
│   │   ├── temperature.rs         # Датчики температуры
│   │   └── pressure.rs            # Датчики давления
│   └── config/
│       ├── mod.rs
│       └── settings.rs            # Конфигурация устройства
├── config/
│   └── device_config.toml         # Конфигурация по умолчанию
├── tests/
│   ├── modbus_tcp_test.rs         # Интеграционные тесты TCP
│   └── register_map_test.rs       # Тесты карты регистров
├── scripts/
│   ├── test_client.py             # Тестовый клиент (pymodbus)
│   ├── simulator.py               # Симулятор устройств
│   └── flash.sh                   # Прошивка ESP32
└── Cargo.toml
```

## Потоки выполнения

### RTU Master (фоновая задача)
```
loop {
    for device in config.devices {
        // Формируем RTU запрос
        let request = build_rtu_request(device.id, device.registers);
        
        // Отправляем через UART (GPIO4 = HIGH)
        rs485_transmit(&request);
        
        // Ждём ответ (GPIO4 = LOW)
        let response = rs485_receive(timeout_ms);
        
        // Проверяем CRC
        if verify_crc(&response) {
            // Обновляем карту регистров
            register_map.update(device, &response);
        }
    }
    sleep(config.poll_interval_ms);
}
```

### TCP Slave (обработчик запросов)
```
async fn handle_tcp_request(request: TcpRequest) -> TcpResponse {
    match request.function_code {
        0x03 => {
            // Read Holding Registers
            let data = register_map.read_holding(request.address, request.count);
            Response::success(data)
        }
        0x06 => {
            // Write Single Register
            register_map.write_holding(request.address, request.value);
            
            // Если это команда пробоотборнику —转发 в RTU
            if is_pump_command(request.address) {
                rtu_forward_command(request.address, request.value);
            }
            Response::echo(request)
        }
        0x04 => {
            // Read Input Registers
            let data = register_map.read_input(request.address, request.count);
            Response::success(data)
        }
        _ => Response::exception(ILLEGAL_FUNCTION)
    }
}
```

## Упражнения

### Упр. 7.1: Project Setup
Создайте Cargo workspace для проекта:
```bash
cargo init --name esp32-modbus-gateway
```

### Упр. 7.2: Register Map Integration
Интегрируйте RegisterMap из модуля 6 с двумя источниками данных:
- RTU Master записывает данные с датчиков
- TCP Slave читает эти данные

### Упр. 7.3: RTU Master Full
Реализуйте RTU Master:
- Опрос 10 устройств (Slave ID 1-10)
- Чтение Holding Registers (0x03)
- Запись команд (0x06)
- Обновление RegisterMap каждые 500 мс

### Упр. 7.4: TCP Slave Full
Реализуйте TCP Slave:
- Поддержка: 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x0F, 0x10
- Исключения при ошибках
- Логирование всех запросов

### Упр. 7.5: Command Forwarding
Реализуйте转发 команд:
- Запись в Holding Register 100-103 →转发 в RTU (пробоотборники)
- Ожидание ответа от RTU
- Возврат подтверждения по TCP

### Упр. 7.6: Configuration
Добавьте регистры конфигурации:
- Интервал опроса (0xF000)
- Список устройств (0xF001-0xF020)
- Применение изменений без перезагрузки

### Упр. 7.7: Integration Test
Напишите интеграционный тест:
1. Запустите симулятор (Python) как "датчики"
2. Запустите ESP32 Gateway
3. Подключитесь с ноутбука как Zynq
4. Проверьте: чтение данных, запись команд

## Проверка

```bash
# Запуск тестов
cargo test

# Прошивка на ESP32
cargo run --release

# Тест клиента
python3 scripts/test_client.py --host 192.168.1.100 --port 502

# Проверка через mbpoll
mbpoll -m tcp -a 1 -r 0 -c 20 192.168.1.100
```

## Следующий модуль

Переходите к [Модулю 8: Advanced Topics](../08-advanced-topics/).
