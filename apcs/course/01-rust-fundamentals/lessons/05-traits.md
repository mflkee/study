# 05: Traits — единое поведение для разных типов

> **Цель урока:** понять trait как «интерфейс» Rust, уметь реализовать
> его для своих типов и использовать в generic-функциях.
>
> В TUI это фундаментально: `ModbusError` реализует `Display`,
> `SerialMaster` использует `Box<dyn serialport::SerialPort>`, а общие
> сущности (датчики, устройства) имеют общие методы.

## Что такое trait

Trait описывает **общее поведение** (методы), которое могут иметь
разные типы. Аналог интерфейса в других языках, но с рядом особенностей.

```rust
trait Readable {
    fn read(&mut self, buf: &mut [u8]) -> usize;
}
```

Можно реализовать для любого типа:

```rust
struct Uart { fake: Vec<u8>, pos: usize }

impl Readable for Uart {
    fn read(&mut self, buf: &mut [u8]) -> usize {
        let n = buf.len().min(self.fake.len() - self.pos);
        buf[..n].copy_from_slice(&self.fake[self.pos..self.pos + n]);
        self.pos += n;
        n
    }
}
```

## Методы с реализацией по умолчанию

```rust
trait Named {
    fn name(&self) -> String;
    fn describe(&self) -> String {          // можно переопределить
        format!("named '{}'", self.name())
    }
}
```

## Trait bounds: «только T, который умеет X»

```rust
fn describe_named<T: Named>(value: &T) -> String {
    format!("device: {}", value.name())
}
```

Три равнозначных синтаксиса:

```rust
fn f<T: Readable>(x: &T) {}               // inline bound
fn g<T>(x: &T) where T: Readable {}       // where clause
fn h(x: &impl Readable) {}                // impl Trait
```

## String: Display и From

Самый частый trait на практике — `Display` (для `format!`/`println!`)
и `From` (для конвертации ошибок).

### Display для кастомного типа

```rust
impl fmt::Display for ModbusError {       // как frames.rs:35-52
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ModbusError::Timeout => write!(f, "timeout (no response)"),
            ModbusError::BadCrc => write!(f, "bad CRC"),
            _ => write!(f, "other error"),
        }
    }
}
```

После этого `format!("{err}")` и `println!("{err}")` работают.

## Generic-функции поверх trait

В модуле 1 мы уже видели generic-функцию для чтения:

```rust
fn read_all<T: Readable>(r: &mut T, cap: usize) -> Vec<u8> {
    let mut buf = vec![0u8; cap];
    let n = r.read(&mut buf);
    buf[..n].to_vec()
}
```

## Реальный код в TUI

- `frames.rs:35-52` — `Display` для `ModbusError`.
- `frames.rs:55` — `impl std::error::Error for ModbusError`.
- `master.rs:53` — `Box<dyn serialport::SerialPort>` (trait object;
  «любой тип, реализующий SerialPort»).
- `emulator.rs:22-31` — метод `.label()` для `DataType`.

## Задание (проект: trait Readable)

См. `exercises/src/ex05_traits.rs`.

1. Определите `trait Named`.
2. Реализуйте его для `Slave` и `Sensor`.
3. Напишите generic-функцию `describe<T: Named>`.
4. Реализуйте `Display` для `BusStatus`.

## Следующий урок

[06-collections.md](06-collections.md) — где в TUI живут Vec, BTreeMap, VecDeque.