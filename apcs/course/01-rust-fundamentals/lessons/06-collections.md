# 06: Коллекции — Vec, HashMap/BTreeMap, VecDeque

> **Цель урока:** использовать основные коллекции, которые встречаются
> в TUI: `Vec` (списки датчиков), `BTreeMap` (карта регистров),
> `VecDeque` (кольцо транзакций). И самое главное — итераторы.

## Vec<T> — динамический массив

```rust
let mut sensors: Vec<String> = Vec::new();
sensors.push("temp".into());
sensors.push("pressure".into());

sensors.len();          // 2
sensors[0];             // "temp"
sensors.get(5);         // Option<&String>, None вместо паники

// Индексирование [] в rust: даёт панику при выходе за границы.
// get() — безопасная версия.
```

## BTreeMap<u16, u16> — карта регистров

Именно так TUI хранит регистры устройства (emulator.rs:69-72):

```rust
use std::collections::BTreeMap;

let mut regs: BTreeMap<u16, u16> = BTreeMap::new();
regs.insert(0x0000, 100);
regs.insert(0x0001, 200);

regs.get(&0).copied();          // Option<u16> → Some(100)
regs.get(&0xFF).copied();       // None
regs.get(&addr).copied().unwrap_or(0);  // 0, если адреса нет
```

Ключевое отличие от `HashMap` — **порядок**: `BTreeMap` хранит
отсортированными, что удобно для обхода диапазонов адресов.

## VecDeque<T> — «кольцо» для лога шины

master.rs:56 держит лог транзакций в `VecDeque<TraceEntry>` и
**ограничивает** его до 100 записей (константа `TRACE_CAP`):

```rust
use std::collections::VecDeque;
let mut log: VecDeque<u8> = VecDeque::new();
log.push_back(1); log.push_back(2); log.push_back(3);
while log.len() > 2 { log.pop_front(); }   // кольцо: выкидываем старые
assert_eq!(log, vec![2, 3]);
```

## Итераторы — сердце Rust-стиля

Читаем список, фильтруем, преобразуем, складываем:

```rust
let nums = [1, 2, 3, 4, 5];

let even_sq: u16 = nums.iter()
    .filter(|&&x| x % 2 == 0)   // 2, 4
    .map(|&x| x * x)             // 4, 16
    .sum();                       // 20

let v: Vec<u16> = nums.iter().map(|&x| x * 2).collect();
```

### По байтам (crc.rs)

```rust
fn sum_bytes(s: &[u8]) -> u32 {
    s.iter().map(|&b| b as u32).sum()
}
```

### enumerate + zip

```rust
for (i, b) in data.iter().enumerate() {
    println!("byte {i}: {b:02X}");
}

let pairs: Vec<(u16, u16)> =
    nums.iter().copied().zip(nums.iter().copied().skip(1)).collect();
```

## Style guide для TUI

Лучший инструмент — итераторы, а не циклы с индексами (`for i in 0..len`).
Код становится короче и безопаснее:

```rust
// ❌ медленный, панике опасный
for i in 0..sensors.len() {
    if sensors[i].enabled { ... }
}
// ✅ как в worker.rs / эмуляторе
for s in &sensors { if s.enabled { ... } }
```

## Задание (проект: карта регистров)

См. `exercises/src/ex06_collections.rs`.

1. И найдите адрес регистра с минимальным значением через итераторы.
2. Вычислите среднее через `map` + `sum`.
3. Соберите список адресов, где значение больше порога.

## Следующий урок

[07-errors.md](07-errors.md) — Result, Option, ? и свои ошибки.