# 04 Structs, Enums, and Pattern Matching

## Зачем это нужно

На этом этапе Rust перестаёт быть просто языком “про переменные и функции” и
становится языком моделирования предметной области.

## `struct`

`struct` нужен, когда у сущности есть несколько связанных полей:

```rust
struct Task {
    title: String,
    done: bool,
}
```

Поведение добавляется через `impl`:

```rust
impl Task {
    fn mark_done(&mut self) {
        self.done = true;
    }
}
```

## `enum`

`enum` нужен, когда у значения есть конечное число вариантов:

```rust
enum Priority {
    Low,
    Medium,
    High,
}
```

Это почти всегда лучше, чем хранить приоритет строкой `"high"` и надеяться, что
никто не ошибётся в буквах.

## `match`

Сила Rust в том, что `match` заставляет обрабатывать варианты явно:

```rust
match priority {
    Priority::Low => 1,
    Priority::Medium => 2,
    Priority::High => 3,
}
```

Такая явность уменьшает класс ошибок, когда один сценарий “забыли”.

## Почему это важнее классов

Во многих учебных курсах после базового синтаксиса идут классы и наследование. В
Rust вместо этого обычно используют:

- композицию;
- `enum` для вариативности;
- `trait` для общего интерфейса.

Это другой стиль проектирования, и к нему нужно привыкнуть отдельно.

## Частые ошибки

- Хранить состояния строками, а не `enum`.
- Делать слишком большие `struct` с разнородной ответственностью.
- Использовать `bool` там, где уже просится отдельный `enum`.
- Бояться `match` и заменять его запутанными `if`.

## Что сделать руками

- Koans: [03_structs_enums.rs](../Rust-Koans/tests/03_structs_enums.rs)
- Koans: [07_pattern_matching.rs](../Rust-Koans/tests/07_pattern_matching.rs)
- Practice: [4 Book Catalog](../Practice/4%20Book%20Catalog/README.md)
- Practice: [5 In-Memory TODO](../Practice/5%20In-Memory%20TODO/README.md)
- Домашнее: [2.2 Структуры, enum, методы и
  traits](../Homework/2.2%20Structs,%20Enums,%20Methods,%20and%20Traits/README.md)

## Вопросы для самопроверки

1. Когда `bool` уже недостаточно и нужен `enum`?
2. Почему `match` часто безопаснее длинной цепочки `if`?
3. Чем `struct` с `impl` в Rust концептуально отличается от классов в OOP-курсе?
