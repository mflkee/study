# Ресурсы и литература

## Официальная документация Modbus

1. **Modbus Application Protocol Specification v1.1b3**
   - Ссылка: https://modbus.org/specs.php
   - Содержит: коды функций, PDU, исключения, адресацию
   - Обязательно к прочтению!

2. **Modbus Messaging on TCP/IP Implementation Guide v1.0b**
   - Ссылка: https://modbus.org/specs/Modbus_Messaging_Implementation_Guide_V1_0b.pdf
   - Содержит: MBAP Header, формат TCP, правила соединений

3. **Modbus over Serial Line Specification v1.02**
   - Ссылка: https://modbus.org/specs/Modbus_over_serial_line_V1_02.pdf
   - Содержит: RTU, CRC-16, тайминги, адресация

## Rust для Embedded

4. **The Embedded Rust Book** (бесплатная)
   - Ссылка: https://docs.rust-embedded.org/book/
   - Содержит: no_std, bare-metal, HAL, peripherals

5. **Rust Programming for Embedded Systems and IoT**
   - Автор: Lucian Marinescu
   - Содержит: ESP32, STM32, протоколы связи

6. **Rust by Example** (бесплатная)
   - Ссылка: https://doc.rust-lang.org/rust-by-example/
   - Основы Rust с примерами

## ESP32

7. **ESP-IDF Programming Guide**
   - Ссылка: https://docs.espressif.com/projects/esp-idf/
   - Официальная документация ESP-IDF

8. **ESP32-S3 Datasheet**
   - Путь: `../esp32-zynq-comm/esp32-s3datasheeten-1756902968_1.pdf`
   - Спецификация чипа ESP32-S3

9. **ESP32-S3 DevKitC Pinout**
   - Путь: `../esp32-zynq-comm/esp32-1756903042_1.pdf`
   - Распиновка платы

## Modbus на Rust (крейты)

10. **tokio-modbus**
    - GitHub: https://github.com/flosse/tokio-modbus
    - Async Modbus для tokio

11. **modbus-rs**
    - Crates.io: https://crates.io/crates/modbus-rs
    - Кроссплатформенный, поддержка no_std

12. **rmodbus**
    - Crates.io: https://crates.io/crates/rmodbus
    - Гибкий, без обвязки

13. **modbus-bridge**
    - GitHub: https://github.com/flosse/modbus-bridge
    - Готовый шлюз для ESP32 + Embassy

## Промышленные протоколы

14. **«Промышленные вычислительные сети»**
    - Авторы: И.А. Елизаров, В.Н. Назаров и др.
    - Содержит: Modbus (ASCII, RTU, TCP), примеры для контроллеров

15. **«Промышленные сети»**
    - Содержит: RS-232/RS-485, Modbus, примеры на C++ и C#

## Инструменты

16. **libmodbus** (C библиотека)
    - Ссылка: https://libmodbus.org/
    - Эталонная реализация, примеры серверов/клиентов

17. **pymodbus** (Python)
    - Ссылка: https://pymodbus.readthedocs.io/
    - Библиотека + симулятор сервера

18. **Wireshark**
    - Ссылка: https://www.wireshark.org/
    - Анализ Modbus TCP трафика

19. **mbpoll**
    - Установка: `yay -S mbpoll` (Arch)
    - CLI утилита для тестирования Modbus

## Видеокурсы

20. **Rust for Embedded Systems** (YouTube)
    - Серия от Rapid Embedded
    - Практические примеры на STM32 и ESP32

21. **Modbus Masterclass** (YouTube)
    - Серия от RealPars
    - Теория и практика Modbus

## Форумы и сообщества

22. **Rust Embedded Working Group**
    - Ссылка: https://rust-embedded.org/
    - Официальное сообщество

23. **ESP32 Rust**
    - GitHub: https://github.com/esp-rs
    - Экосистема Rust для ESP32

24. **Modbus.org Community**
    - Ссылка: https://modbus.org/
    - Официальное сообщество Modbus
