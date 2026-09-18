# 07: Ошибки — Result, Option, ? и свои типы ошибок

> **Цель урока:** перестать бояться `Result`: научиться обрабатывать
> ошибки и делать их читаемыми. В TUI — это `ModbusError` в frames.rs
> и Result-возвраты по всему проекту.

## Result<T, E> vs Option<T>

```rust
// Option: «значение есть или нет»
let value: Option<u16> = regs.get(&addr).copied();   // None если нет адреса

// Result: «значение или ошибка»
fn read(addr: u16) -> Result<u16, ModbusError> {
    Ok(100)
    // Err(ModbusError::IllegalAddress)
}
```

| Тип            | Значение                  |
|----------------|---------------------------|
| `Ok(value)`    | успех + результат         |
| `Err(error)`   | ошибка + описание         |
| `Some(value)`  | «да, есть»                |
| `None`         | «нет»                     |

## Оператор `?`

`?` в функции, возвращающей Result/Option, означает:
«если это ошибка — верни её как есть»:

```rust
use std::fs::File;
use std::io::{self, Read};

fn read_config(path: &str) -> Result<String, io::Error> {
    let mut f = File::open(path)?;    // на ошибке — return Err(...)
    let mut s = String::new();
    f.read_to_string(&mut s)?;
    Ok(s)
}
```

`?` уместен только там, где **есть** Result/Option в возврате.
В `main()` можно написать `fn main() -> Result<(), Box<dyn Error>>`.

## Обработка ошибок — три уровня

1. **Проигнорировать** — почти никогда: `let _ = ...;`
2. **Обработать явно**: `match` или `if let Ok(...) / if let Some(...)`.
3. **Пробросить**: `?` в функции с Result.

```rust
match read_two_registers(&regs, 2) {
    Ok(v) => println!("{v:?}"),
    Err(ModbusError::Timeout) => println!("no response"),
    Err(e) => println!("error: {e}"),
}
```

## Свои типы ошибок (как в TUI)

```rust
use std::fmt;

#[derive(Debug, Clone, PartialEq)]
enum ModbusError {
    Timeout,
    BadCrc,
    IllegalAddress,
}

impl fmt::Display for ModbusError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ModbusError::Timeout => write!(f, "timeout (no response)"),
            ModbusError::BadCrc => write!(f, "bad CRC"),
            ModbusError::IllegalAddress => write!(f, "illegal data address (0x02)"),
        }
    }
}

impl std::error::Error for ModbusError {}
```

Правила хорошего типа ошибок:

- `#[derive(Debug)]` — для отладки.
- Реализуйте `Display` — для пользователя.
- `impl std::error::Error` — чтобы `?` работал в generic-коде.

После этого:
```rust
let e = ModbusError::BadCrc;
println!("{e}");   // "bad CRC"
```

## Идиомы для кода

```rust
regs.get(&addr).copied().unwrap_or(0);
config.ok_or(ModbusError::Timeout)?;
map.remove(&k).unwrap_or_default();
```

## Задание (проект: Modbus-ошибки)

См. `exercises/src/ex07_errors.rs`.

1. Определите `enum SensorError` с вариантами (NotConfigured, OutOfRange).
2. Напишите `Display`, реализуйте `std::error::Error`.
3. Напишите функцию `read_scaled`, возвращающую Result, с обработкой
   обоих вариантов.

## Следующий урок

[08-threads.md](08-threads.md) — потоки, каналы, Arc/Mutex, Send/Sync.