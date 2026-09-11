# Модуль 1: Rust Fundamentals

## Цель

Освоить основы Rust, критичные для embedded-разработки: ownership, borrowing, lifetime, traits, generics, pattern matching, error handling.

## Теория

**Подробная теория:** [theory.md](theory.md)

| Раздел | Описание |
|--------|----------|
| 1.1 Ownership | Move semantics, Copy, правила владения |
| 1.2 Borrowing | &T vs &mut T, правило "читатели OR писатель" |
| 1.3 Lifetime | Аннотации 'a, elision rules, 'static |
| 1.4 Traits | Определение, реализация, generics, trait bounds |
| 1.5 Enums | Algebraic data types, pattern matching |
| 1.6 Error Handling | Result, Option, ?, собственные ошибки |
| 1.7 Collections | Vec, HashMap, iterators, zero-cost abstractions |
| 1.8 Async | async/await, Arc/Mutex, Send/Sync |

## Упражнения

См. папку `exercises/`:

### Упр. 1.1: Memory Safety
Напишите функцию, которая принимает строку и возвращает сумму всех ASCII-байтов.
Решите проблему ownership без clone().

### Упр. 1.2: Trait System
Создайте trait `Readable` для чтения данных из различных источников (UART, SPI, I2C).
Реализуйте его для 3 структур.

### Упр. 1.3: Error Handling
Реализуйте кастомную иерархию ошибок для Modbus-сервера:
- TransportError (сетевые ошибки)
- ProtocolError (ошибки протокола)
- DeviceError (ошибки устройства)

### Упр. 1.4: Async Basics
Напишите async-функцию, которая имитирует чтение из UART с таймаутом.
Используйте tokio::time::timeout.

## Проверка

```bash
cargo run --example ownership
cargo test --package exercises
```

## Следующий модуль

После завершения переходите к [Модулю 2: Embedded Rust](../02-embedded-rust/).
