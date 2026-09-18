# Модуль 1: Rust Fundamentals

> **Цель:** освоить основы Rust, критичные для embedded-разработки:
> ownership, borrowing, lifetime, traits, generics, pattern matching,
> error handling, коллекции, потоки. Быстрый «вкат» — 10 уроков
> с примерами из реального кода (тестовый стенд Modbus RTU/TCP в
> `test-bench/tui/`).

## Карта модуля

Лучший путь — по порядку, каждый урок опирается на предыдущий:

| # | Урок | Тема | Пример (запускается) | Упражнение |
|---|------|------|----------------------|------------|
| [00](lessons/00-setup-types.md) | Установка, типы, Cargo | rustup, cargo, примитивы, `fn main`, макросы | `00_types` | — |
| [01](lessons/01-ownership.md) | Ownership | move, Copy, владение (E0382), RAII | `01_ownership` | `ex01_ownership` |
| [02](lessons/02-borrowing.md) | Borrowing | `&T`, `&mut T`, «читатели OR писатель» | `02_borrowing` | `ex02_borrowing` |
| [03](lessons/03-lifetimes.md) | Lifetimes | `'a`, elision, `'static` | `03_lifetimes` | `ex03_lifetimes` |
| [04](lessons/04-structs-enums.md) | Structs, Enums | ADT, `match`, `if let` | `04_structs_enums` | `ex04_structs_enums` |
| [05](lessons/05-traits.md) | Traits | trait, bounds, Display, generic-функции | `05_traits` | `ex05_traits` |
| [06](lessons/06-collections.md) | Коллекции | Vec, BTreeMap, VecDeque, итераторы | `06_collections` | `ex06_collections` |
| [07](lessons/07-errors.md) | Ошибки | Result, Option, `?`, свои ошибки | `07_errors` | `ex07_errors` |
| [08](lessons/08-threads.md) | Потоки | thread, mpsc, Arc/Mutex, Send/Sync | `08_threads` | `ex08_threads` |
| [09](lessons/09-practice.md) | Практика | BusLog: сводный мини-проект | `09_practice` | `ex09_practice` |

## Как работать

Каждый урок содержит:

- **Теорию** — минимум, но с кодом и с ссылками на `test-bench/tui/src/`.
- **Пример** (`examples/0X_*.rs`) — запускаемый и самопроверяющийся код.
- **Упражнение** (`exercises/src/ex0X_*.rs`) — заготовка с задачами
  и unit-тестами: `cargo test -p exercises` — зелёная проверка.

Схема «быстрый вкат»:

```bash
cd 01-rust-fundamentals
cargo run --example 00_types        # первый пример
cargo run --example 09_practice     # итоговая сумма
cargo test -p exercises             # все упражнения зелёные
cargo clippy -p exercises           # без предупреждений
```

## Как устроен cargo-проект модуля

```
01-rust-fundamentals/
├── Cargo.toml          # workspace: root + exercises
├── examples/           # один запускаемый пример на урок
│   ├── 00_types.rs ... 09_practice.rs
├── exercises/          # пакет упражнений (только тесты)
│   ├── Cargo.toml      # package "exercises"
│   └── src/ex0X_*.rs
├── lessons/            # сами уроки (00-09)
└── theory.md           # конспект по темам (быстрая шпаргалка)
```

## Проверка готовности

```bash
# 1. Все примеры работают
for e in 00_types 01_ownership 02_borrowing 03_lifetimes \
         04_structs_enums 05_traits 06_collections \
         07_errors 08_threads 09_practice; do
  cargo run -q --example "$e" >/dev/null || echo "FAIL $e"
done
echo "examples OK"

# 2. Все упражнения зелёные + без предупреждений
cargo test --workspace
cargo clippy -p exercises
```

Ожидаемый результат: **29 тестов проходит**, clippy молчит.

## Как упражнения «устроены»

Файл `exercises/src/ex0X_*.rs` — это **готовое решение** с явно
выделенным заданием (в комментариях) и проверяющими тестами. Схема
самопроверки:

1. Прочитайте задание.
2. Попробуйте написать функцию **сами** — закрыв глаза решение (или
   закомментировав тело функции).
3. Запустите `cargo test -p exercises` — тесты покажут, где промах.

Так упражнения превращаются из «прочитать готовое» в «воспроизвести —
и проверить».

## Следующий модуль

После завершения переходите к [Модулю 2: Embedded Rust](../02-embedded-rust/).
