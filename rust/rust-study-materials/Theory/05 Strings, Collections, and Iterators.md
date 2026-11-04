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

Для частот, индексов, группировок. Среднее время доступа — O(1), а порядок
обхода **не определён**.

```rust
use std::collections::HashMap;

let mut freq = HashMap::new();
freq.insert("rust", 1);
```

Базовые операции: `insert` (забирает владение ключом и значением, возвращает
старое значение как `Option`), `get` (→ `Option<&V>`), `get_mut`, `remove`,
`contains_key`. Индексация `map[key]` паникует, если ключа нет — почти всегда
нужен `get`.

**Entry API** — «найти или создать за один проход», главный рабочий инструмент:

```rust
// счётчик
for word in "a b a c b a".split_whitespace() {
    *freq.entry(word).or_insert(0) += 1;
}

// группировка значений в список
groups.entry(dept).or_default().push(name);

// обновить, если ключ есть; вставить, если нет
scores.entry(team).and_modify(|s| *s += points).or_insert(points);
```

Искать по `&str` в `HashMap<String, _>` можно (работает `Borrow<str>`), но
вставлять нужно ровно тот тип ключа, что в объявлении.

Порядок обхода случаен. Если нужен стабильный порядок — берите `BTreeMap`
(ключи отсортированы, требование `Ord` вместо `Hash + Eq`) либо собирайте в `Vec`
и сортируйте.

Частые ошибки: полагаться на порядок обхода, писать `map[key]` вместо `get`,
забывать `*` в `*freq.entry(k).or_insert(0) += 1`.

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
- Полагаться на порядок обхода `HashMap` (он не определён).

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
