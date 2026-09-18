# Шпаргалка по Rust (Модуль 1)

Это быстрый конспект. Подробные уроки — в [`lessons/`](lessons/00-setup-types.md),
запускаемые примеры — в `examples/`, упражнения — в `exercises/`.

## 1. Основные типы

```rust
let b: u8   = 255;        // беззнаковые: u8 u16 u32 u64 usize
let i: i32  = -1;         // знаковые:    i8 i16 i32 i64 isize
let f: f32  = 3.14;       // вещественные
let ok: bool = true;
let c: char = 'A';        // Unicode
let s: &str = "срез";     // заимствованная строка (borrow)
let o: String = String::from("owned");  // собственная (move)
```

## 2. Ownership (E0382, move)

- Один владелец, при выходе из области видимости — `drop`.
- `let b = a;` где `a: String` — move: `a` больше нет.
- Move-в-функцию и move-из-функции (вернул кортеж).
- `Clone` — явная глубокая копия; `#[derive(Clone)]`.

## 3. Borrowing (E0502)

- `&T` — читать можно сколько угодно параллельно.
- `&mut T` — писать можно один раз за раз.
- «много читателей ИЛИ один писатель».

## 4. Lifetimes

- Ссылка не живёт дольше своих данных.
- Elision (правило 2): один входной параметр → его lifetime у возврата.
- Два входных параметра → пишем `fn f<'a>(a: &'a T, b: &'a T) -> &'a T`.
- `&'static str` — строковой литерал (вся программа).

## 5. Structs, Enums, match

```rust
struct Sensor { name: String, enabled: bool }   // данные
enum Error { Timeout, BadCrc }                   // «один из»
match v {
    Error::Timeout => ...,
    Error::BadCrc  => ...,
}
if let Some(x) = opt { ... }    // один вариант
```

Типы-варианты могут нести данные: `enum E { Write(String) }`.

## 6. Traits

- Trait — описание поведения; `impl Trait for Type`.
- `#[derive(Debug, Clone, Copy, PartialEq)]` — автогенерация.
- Bounds: `T: Trait`, `where T: Trait`, `impl Trait`.
- `Display` для `format!`/`println!`; `std::error::Error` для `?`.

## 7. Коллекции

```rust
use std::collections::{BTreeMap, HashMap, VecDeque};

let v: Vec<u8> = vec![1, 2, 3];
let m: BTreeMap<u16, u16> = BTreeMap::new();
m.get(&addr).copied().unwrap_or(0);
let d: VecDeque<u8> = VecDeque::new();   // ring buffer
d.push_back(1); d.pop_front();
```

Итераторы: `iter().filter().map().sum()`, `collect()`, `enumerate()`.

## 8. Ошибки

```rust
fn f() -> Result<u16, ModbusError> {
    let v = read()?;          // пробросить ошибку
    Ok(v)
}
regs.get(&a).copied().ok_or(ModbusError::Timeout)?;
```

Свой тип ошибки: `enum + Display + std::error::Error`.

## 9. Потоки

```rust
let (tx, rx) = mpsc::channel::<u8>();
thread::spawn(move || { tx.send(1).unwrap(); });
let v: Vec<u8> = rx.iter().collect();

let shared = Arc::new(Mutex::new(0u32));  // деление между потоками
*shared.lock().unwrap() += 1;
```

- `Send` — можно отправить в другой поток.
- `Sync` — можно разделять через `&T`.

## 10. Где это в проекте (test-bench/tui)

| Тема        | Файл, строка                       | Заметка                        |
|-------------|------------------------------------|--------------------------------|
| enum ошибок | `frames.rs:18-33`                  | `ModbusError`, `Display`       |
| struct+impl | `master.rs:52-78`                  | `SerialMaster`, `open()`       |
| кольцо      | `master.rs:56` `VecDeque`          | `TRACE_CAP=100`                |
| BTreeMap    | `emulator.rs:69-72`                | карта регистров                |
| Arc/Mutex   | `worker.rs` (Общий `Emulator`)     | `Arc<Mutex<Emulator>>`         |
| мpeg-канал  | `worker.rs` шины событий           | `Event::Snapshot`, `PortFound` |
| generic     | `crc.rs:4` `crc16(&[u8])`          | работает с любых слайсов       |

## Быстрые команды

```bash
cargo new  app              # новый бинарник
cargo run                   # скомпилировать и запустить
cargo run --example 01_ownership
cargo test -p exercises     # unit-тесты упражнений
cargo clippy -p exercises   # статический анализ
```

Все уроки: [lessons/](lessons/00-setup-types.md).