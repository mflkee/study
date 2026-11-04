# HashMap&lt;K, V&gt; — шпаргалка и грабли

Ассоциативный массив «ключ → значение» из стандартной библиотеки.
Это то, что ищут, когда нужны частоты, индексы, группировки, кэш, словарь.

- Rust Book: <https://doc.rust-lang.org/book/ch08-03-hash-maps.html>
- std: <https://doc.rust-lang.org/std/collections/struct.HashMap.html>
- Живые примеры: `02_exercises/03_hashmaps/`, `03_projects/company_directory_cli/`

Средняя сложность: вставка / поиск / удаление — **O(1)**.
Порядок обхода **не определён** (см. §6).

## 1. Создание

```rust
use std::collections::HashMap;

let mut m: HashMap<String, i32> = HashMap::new();

// если заранее знаешь примерный размер — меньше реаллокаций
let mut with_cap = HashMap::with_capacity(64);

// из массива пар
let scores = HashMap::from([("red", 10), ("blue", 5)]);

// из любого итератора пар
let map: HashMap<&str, i32> = vec![("a", 1), ("b", 2)].into_iter().collect();
```

## 2. Базовые операции

| Операция                | Возвращает            | Заметка                          |
|-------------------------|-----------------------|----------------------------------|
| `insert(k, v)`          | `Option<V>`           | старое значение, если ключ был   |
| `get(&k)`               | `Option<&V>`          | ссылка, не копия                 |
| `get_mut(&k)`           | `Option<&mut V>`      | менять на месте                  |
| `remove(&k)`            | `Option<V>`           |                                    |
| `contains_key(&k)`      | `bool`                |                                    |
| `len()` / `is_empty()`  | `usize` / `bool`      |                                    |
| `clear()`               | `()`                  |                                    |

```rust
let mut m = HashMap::new();
m.insert("rust".to_string(), 1);

let old = m.insert("rust".to_string(), 2);   // Some(1) — ключ был

match m.get("rust") {                        // &str ищем в HashMap<String, _>
    Some(v) => println!("{v}"),
    None => println!("нет ключа"),
}

if let Some(v) = m.get_mut("rust") {
    *v += 1;                                 // разыменование ссылки
}

m.remove("rust");
```

Прямой индекс `m["rust"]` тоже компилируется, но **паникует**, если ключа нет.
Почти всегда нужен `get`.

## 3. Entry API — сердце HashMap

`entry(k)` даёт `Entry` (либо `Occupied`, либо `Vacant`) и позволяет
«найти или создать» одним заходом, без двойного поиска и без повторного клона ключа.

| Метод                            | Что делает                                             |
|----------------------------------|--------------------------------------------------------|
| `.or_insert(v)`                  | вставит `v`, если ключа нет; вернёт `&mut V`           |
| `.or_insert_with(‖ …)`           | то же, но значение создаётся **лениво**                |
| `.or_default()`                  | то же с `V::default()`                                 |
| `.and_modify(f)`                 | применит `f` к существующему значению                  |
| `.insert_entry(v)`               | как `or_insert`, но вернёт `OccupiedEntry` (Rust 1.83+) |

Три классических идиомы:

```rust
// 1) счётчик
for word in text.split_whitespace() {
    *counts.entry(word).or_insert(0) += 1;   // * — потому что or_insert даёт &mut
}

// 2) группировка
groups.entry(department).or_default().push(employee);

// 3) upsert (обнови, если есть; вставь, если нет)
scores.entry(team)
      .and_modify(|s| *s += points)
      .or_insert(points);
```

Почему не `get` + `insert`: это два поиска по таблице, плюс ключ пришлось бы
клонировать второй раз. `entry` делает всё за один проход.

## 4. Владение

- `insert` **забирает** владение ключом и значением: `String`-ключ уедет в карту,
  снаружи он больше недоступен.
- `get`/`get_mut` возвращают **ссылку**, а не копию. Нужно значение — `.copied()`
  (для `Copy`) или `.cloned()` (для `Clone`).
- Искать по `&str` в `HashMap<String, _>` можно: работает `Borrow<str>`.
  Но вставлять нужно ровно тот тип ключа, что в объявлении.

```rust
let mut m: HashMap<String, i32> = HashMap::new();
let key = String::from("rust");
m.insert(key, 1);
// println!("{key}");   // ОШИБКА: key перемещён в карту
```

## 5. Итерация

```rust
for (k, v) in &m   { }   // (&K, &V)
for k in m.keys()  { }
for v in m.values(){ }
for v in m.values_mut() { *v += 1; }
for (k, v) in m    { }   // into_iter(), забирает владение
```

Порядок обхода **не определён** и может отличаться между запусками и версиями —
внутри используется случайное начальное состояние (`RandomState`). Никогда не
полагайся на «порядок как вставил». Нужен стабильный порядок — собери в `Vec` и
отсортируй либо возьми `BTreeMap` (§7).

## 6. HashMap или BTreeMap

|                            | `HashMap`          | `BTreeMap`        |
|----------------------------|--------------------|-------------------|
| требование к ключу         | `Hash + Eq`        | `Ord`             |
| среднее время              | O(1)               | O(log n)          |
| порядок ключей             | не определён       | отсортирован      |
| диапазонные запросы        | нет                | есть (`range`)    |
| когда брать                | по умолчанию       | нужен порядок/диапазон |

## 7. Хешер под капотом

- По умолчанию `SipHash` + `RandomState`: устойчив к HashDoS-атакам, но не самый
  быстрый.
- Производительность критична — подключают внешние хешеры (`fxhash`, `ahash`)
  через `HashMap::with_hasher(...)` или `DefaultHasher`.
- **Свой тип ключом**: реализуй `Hash`, `Eq`, `PartialEq`
  (`#[derive(Hash, PartialEq, Eq)]`).

## 8. Частые ошибки

- Полагаться на порядок обхода — он случайный.
- `m[key]` вместо `get` — паника на отсутствующем ключе.
- Забыть `*` в `*count += 1` (у `&mut V`).
- Клонировать `String`-ключ там, где хватило бы `get(&str)` или `entry`.
- Ключ без `Hash + Eq` (например, `f64` — у него нет `Eq`, ключом быть не может;
  берут `OrderedFloat` или строку).
- Путать `HashMap` (неупорядоченный) и `BTreeMap`, когда тесту нужен порядок.

## 9. Рецепты на каждый день

```rust
// частотность слов
let mut freq: HashMap<&str, usize> = HashMap::new();
for w in "a b a c b a".split_whitespace() {
    *freq.entry(w).or_insert(0) += 1;
}

// top-N по значению
let mut pairs: Vec<_> = freq.iter().collect();
pairs.sort_by(|a, b| b.1.cmp(a.1));
for (w, c) in pairs.into_iter().take(3) {
    println!("{w}: {c}");
}

// слить вторую карту в первую (сложением значений)
for (k, v) in other {
    *first.entry(k).or_insert(0) += v;
}

// инвертировать карту (значение → ключ)
let inv: HashMap<_, _> = m.into_iter().map(|(k, v)| (v, k)).collect();
```

## 10. Что потрогать руками

- `02_exercises/03_hashmaps/01_word_frequencies` — счётчик + `max_by_key`
- `02_exercises/03_hashmaps/02_stats_mean_median_mode` — mean/median/mode
- `03_projects/company_directory_cli` — карта отделов на практике
- `rust-study-materials/Practice/6 Frequency Dictionary`, `9 Inventory Tracking`,
  `13 Log Analysis`

## 11. Вопросы для самопроверки

1. Почему в `*counts.entry(w).or_insert(0) += 1` нужна звёздочка?
2. Чем `or_insert` хуже `or_insert_with` и когда это реально важно?
3. Почему порядок обхода `HashMap` нельзя считать стабильным и какие задачи это ломает?
4. Когда `BTreeMap` выигрывает у `HashMap`?
5. Как искать по `&str` в `HashMap<String, T>` и почему это работает?
