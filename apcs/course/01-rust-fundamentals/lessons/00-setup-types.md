# 00: Установка, типы, main, Cargo

> **Цель урока:** работать с Rust-инструментарием, понимать базовые типы,
> писать `fn main()` и проверять код командой `cargo`.

## Установка

```bash
# Arch Linux
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup update
rustup component add clippy rust-analyzer
```

**Проверка:**
```bash
rustc --version             # rustc 1.XX.0
cargo --version             # cargo 1.XX.0
clippy-driver --version
```

## Cargo: от идеи до бинарника

```bash
cargo new myproject     # создаёт src/main.rs + Cargo.toml
cd myproject
cargo run               # компилирует И запускает target/debug/myproject
cargo build --release   # оптимизированная сборка (target/release/)
```

### Что внутри Cargo.toml

```toml
[package]
name = "myproject"
version = "0.1.0"
edition = "2021"        # 2018 | 2021 | 2024

[dependencies]          # внешние крейты (аналог include в C)
```

В модуле 1 **внешних зависимостей нет** — всё умещается в stdlib.

## Типы: что нужно знать в первую очередь

```rust
// Целочисленные: разрядность определяет допустимый диапазон.
let a: u8 = 255;            // 0..=255        (1 байт)
let b: u16 = 65535;         // 0..=65535      (2 байта)
let c: i32 = -1000;         // -2^31..2^31-1  (4 байта)
let size: usize = 42;       // "размерный тип" (на 32/64-бит — 4/8 байт)
                            // для индексов, длин массивов, кол-ва элементов

// Вещественные:
let f: f32 = 3.14;          // одинарная точность (как у ESP32)
let g: f64 = 3.14159265;    // двойная точность

// Булевы:
let ready: bool = true;

// Символы и строки:
let ch: char = 'A';         // Unicode, 4 байта
let s: &str = "hello";      // заимствованная строка (срез в памяти)
let owned: String = "hi".to_string(); // собственная строка (move)
```

### Работа с байтами и адресами (Modbus-мир)

```rust
// Младший/старший байт 16-битного регистра:
let reg: u16 = 0xABCD;
reg >> 8;            // 0xAB — старший байт
reg & 0xFF;          // 0xCD — младший байт

// В emulator.rs:100-101 значения датчиков кладутся в регистры именно так:
let value: f32 = 20.5;
let bits = value.to_bits();          // u32 из битов f32
let hi = (bits >> 16) as u16;        // старшее слово
let lo = (bits & 0xFFFF) as u16;     // младшее слово
```

## `fn main()` и обычные функции

```rust
fn main() {
    let x = 1;
    let y = x + 1;          // привязки по умолчанию НЕ мутабельные
    println!("{x} + 1 = {y}");
}
```

## `println!`, `assert!`, `format!` — макросы

```rust
println!("value = {x}");             // вставка переменных
println!("{:#?}", my_struct);        // красивый вывод (Debug)
format!("hex: {:02X}", byte);        // возвращает String

assert_eq!(1 + 1, 2);    // паникует, если false
assert!(list.is_empty());
assert_ne!(a, b);        // паникует, если a == b
```

## Пример: проблема из мира Modbus

```rust
// Подсчитать сумму байтов кадра. В crc.rs решается так:
fn sum_bytes(data: &[u8]) -> u32 {
    data.iter().map(|&b| b as u32).sum()
}
// data — срез (&[u8]); читаем, не забирая владение.
```

## Задание

1. Напишите `fn main()`, который выводит сумму чисел 1..=10.
2. Проверьте результат через `assert_eq!`.
3. Создайте `let name: String = "ESP32".to_string();` и выведите `name.len()`.
4. Поэкспериментируйте с целочисленным делением: `5 / 2` vs `5 % 2`.

## Проверка

```bash
cargo run --example 00_types       # готовый пример из Урока 00
cargo test -p exercises            # все упражнения модуля (позже)
```

## Следующий урок

[01-ownership.md](01-ownership.md) — Move vs Copy: почему строки не копируются.