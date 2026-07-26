# 05 Strings, Collections, and Iterators

## Главная идея

После базовых типов студент должен научиться нормально жить в мире `String`,
`Vec`, `HashMap` и iterator-цепочек. Это уже основная повседневная работа в
Rust.

## Строки

Самая важная пара:

- `String` - владеющая, изменяемая строка;
- `&str` - строковый срез.

В Rust строки UTF-8. Это значит, что:

- длина в байтах и длина в символах не одно и то же;
- индексировать строку как массив байтов нельзя “просто так”;
- безопаснее мыслить через `.chars()`, `.lines()`, `.split_whitespace()`.

## Коллекции

### `Vec<T>`

Главный динамический массив:

```rust
let mut items = Vec::new();
items.push(1);
```

### `HashMap<K, V>`

Для частот, индексов, группировок:

```rust
use std::collections::HashMap;

let mut freq = HashMap::new();
freq.insert("rust", 1);
```

## Итераторы

Rust-итераторы особенно сильны в трёх сценариях:

- преобразование (`map`);
- фильтрация (`filter`);
- агрегирование (`fold`, `sum`, `collect`).

Пример:

```rust
let passed: Vec<u8> = scores
    .iter()
    .copied()
    .filter(|score| *score >= 3)
    .collect();
```

Но у итераторов нет магического преимущества всегда и везде. Если простой `for`
читается лучше, используйте его.

## Частые ошибки

- Пытаться индексировать UTF-8 строку по “символьной” позиции.
- Создавать лишние `String`, когда нужен `&str`.
- Строить iterator-цепочку, которую потом сам автор не может объяснить.
- Забывать про `.copied()` или `.cloned()` при работе со ссылками из итераторов.

## Что сделать руками

- Koans:
  [04_collections_iterators.rs](../Rust-Koans/tests/04_collections_iterators.rs)
- Koans: [08_strings_utf8.rs](../Rust-Koans/tests/08_strings_utf8.rs)
- Practice: [6 Frequency Dictionary](../Practice/6%20Frequency%20Dictionary/README.md)
- Practice: [11 CSV-журнал
  успеваемости](../Practice/11%20CSV%20Gradebook/README.md)
- Practice: [13 Log Analysis](../Practice/13%20Log%20Analysis/README.md)
- Домашнее: [1.4 Итераторы и замыкания в
  Rust](../Homework/1.4%20Iterators%20and%20Closures%20in%20Rust/README.md)
- Домашнее: [3.2 Парсинг логов и
  отчёты](../Homework/3.2%20Log%20Parsing%20and%20Reports/README.md)

## Вопросы для самопроверки

1. Почему `.len()` у строки в Rust не равен “количеству букв”?
2. Когда iterator-цепочка улучшает код, а когда делает его хуже?
3. В каких задачах `HashMap` почти неизбежен?
