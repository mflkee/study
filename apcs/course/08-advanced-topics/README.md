# Модуль 8: Advanced Topics

## Цель

Углубиться в безопасность, производительность, масштабирование и интеграцию с промышленными системами (Zynq, SCADA).

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 8.1 Безопасность | IP фильтры, токены, TLS |
| 8.2 Производительность | Zero-copy, pre-allocated buffers, DMA |
| 8.3 Тестирование | Unit, интеграционные, hardware-in-the-loop |
| 8.4 Мониторинг | Логирование, метрики, structured logging |
| 8.5 Power Management | Light/Deep sleep, watchdog |
| 8.6 OTA обновления | Remote firmware update |
| 8.7 Recovery | Panic handler, error recovery |
| 8.8 Документирование | doc comments, API documentation |

## Упражнения

### Упр. 8.1: IP Whitelist
Реализуйте фильтрацию TCP-соединений по IP.

### Упр. 8.2: Zero-Copy Parser
Перепишите парсер PDU без аллокаций (только срезы буфера).

### Упр. 8.3: Metrics Export
Экспортируйте метрики через HTTP endpoint:
- /metrics — Prometheus формат
- /status — JSON статус

### Упр. 8.4: Stress Test
Запустите стресс-тест с 50+ одновременными клиентами.

### Упр. 8.5: Documentation
Напишите полную документацию API Modbus:
- Описание всех регистров
- Форматы данных
- Примеры запросов/ответов

## Проверка

```bash
# Запуск всех тестов
cargo test

# Стресс-тест
./scripts/stress_test.sh 192.168.1.100

# Проверка производительности
cargo run --release -- --benchmark
```

## Завершение курса

Поздравляем! Вы освоили:
- Rust для embedded-разработки
- Протокол Modbus (RTU и TCP)
- Реализацию Modbus-сервера на ESP32-S3
- Интеграцию с промышленными системами

**Следующие шаги:**
1. Интеграция с реальным Zynq SoC
2. Тестирование на промышленном стенде
3. Оптимизация под конкретные требования
4. Документация для заказчика

**Полезные ссылки:**
- [The Embedded Rust Book](https://docs.rust-embedded.org/book/)
- [tokio-modbus](https://github.com/flosse/tokio-modbus)
- [ESP-IDF Rust](https://docs.espressif.com/projects/esp-idf/latest/en/)
- [Modbus.org](https://modbus.org/)
