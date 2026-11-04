# Дженерики, трейты и времена жизни — шпаргалка

Три механизма, которые делают Rust одновременно гибким и безопасным:

- **дженерики** — один код для многих типов;
- **трейты** — общее поведение (аналог интерфейсов);
- **времена жизни (lifetimes)** — гарантия, что ссылка не переживёт данные.

- Rust Book: главы 10 (<https://doc.rust-lang.org/book/ch10-00-generics.html>)
- Живые примеры: `01_basics/12_generic_types/removing_dublication`

## 1. Зачем дженерики

Вместо копипасты «то же для `i32`, потом для `f64`, потом для `char`» пишем одну
функцию с параметром-типом `T`:

```rust
fn largest<T: PartialOrd>(list: &[T]) -> &T {
    let mut largest = &list[0];
    for item in list {
        if item > largest {
            largest = item;
        }
    }
    largest
}

largest(&[3, 1, 2]);       // T = i32
largest(&[1.5, 0.2, 3.9]); // T = f64
```

`T: PartialOrd` — **trait bound**: без него компилятор не знает, что `>` применим
к `T`. Именно отсюда — ошибки вида «`T` doesn't implement `PartialOrd`».

## 2. Дженерики в структурах и enum

```rust
struct Point<T> { x: T, y: T }

impl<T> Point<T> {
    fn x(&self) -> &T { &self.x }
}

impl Point<f64> {                 // метод только для Point<f64>
    fn length(&self) -> f64 {
        (self.x * self.x + self.y * self.y).sqrt()
    }
}
```

`Option<T>` и `Result<T, E>` из std — это тоже дженерики-`enum`.

**Мономорфизация**: компилятор генерирует отдельную копию кода под каждый
конкретный тип. На рантайме дженерики не медленнее ручного кода — «нулевая цена».

## 3. Trait bounds: где писать

```rust
// инлайн
fn print_all<T: std::fmt::Debug>(items: &[T]) {
    for x in items { println!("{x:?}"); }
}

// через where — читается лучше при многих bounds
fn combine<T, U>(a: T, b: U) -> String
where
    T: std::fmt::Display,
    U: Into<String>,
{
    format!("{a} {}", b.into())
}
```

## 4. Трейты — общее поведение

```rust
trait Summary {
    fn summarize(&self) -> String;              // обязательный метод

    fn announce(&self) -> String {              // метод ПО УМОЛЧАНИЮ
        format!("Новость: {}", self.summarize())
    }
}

struct Article { title: String }

impl Summary for Article {
    fn summarize(&self) -> String {
        self.title.clone()
    }
}
```

Трейт можно использовать как bound (`<T: Summary>`) или как тип-объект (см. §5).

### `impl Trait` — сокращение

```rust
fn notify(item: &impl Summary) { }        // = <T: Summary>(item: &T)
fn make() -> impl Summary { Article { title: "x".into() } }  // один конкретный тип
```

## 5. `<T: Trait>` vs `dyn Trait`

|                        | Дженерик / `impl Trait` | `dyn Trait`             |
|------------------------|-------------------------|-------------------------|
| диспетчеризация        | статическая (без vtable)| динамическая (vtable)   |
| скорость               | максимум                | чуть ниже                |
| разнородные в коллекции| нет                     | да (`Vec<Box<dyn Trait>>`)|
| размер                 | мономорфизация          | фиксированный указатель |

```rust
// разнородный список — там, где нужен именно dyn
let items: Vec<Box<dyn Summary>> = vec![
    Box::new(Article { title: "a".into() }),
    Box::new(Article { title: "b".into() }),
];
for it in &items { println!("{}", it.summarize()); }
```

По умолчанию — дженерики. `dyn` берут, когда реально нужна разнородность во
время выполнения.

## 6. Ассоциированные типы (кратко)

```rust
trait Container {
    type Item;                       // у каждой реализации ровно ОДИН Item
    fn get(&self, i: usize) -> Option<&Self::Item>;
}
```

Отличие от generic-параметра: ассоциированный тип задаёт реализация (например,
`Iterator::Item`), а generic-параметр — вызывающий код.

## 7. Времена жизни

Лайфтайм — это метка области, в которой ссылка гарантированно валидна. Нужны,
когда функция возвращает ссылку и компилятор должен понять, с чем её связать:

```rust
fn longest<'a>(a: &'a str, b: &'a str) -> &'a str {
    if a.len() > b.len() { a } else { b }
}
```

Читается как: «результат живёт не дольше, чем оба входа». Без этого компилятор не
пустит:

```rust
// fn bad() -> &str { let s = String::from("x"); &s }  // ошибка: вернули ссылку на локальное
```

Правила вывода (**elision**) — компилятор сам подставляет `'a` в частых случаях:

- одна входная ссылка → она же в выходе;
- метод с `&self` → выход связан с `&self`.

Поэтому в 90% кода лайфтаймы писать не нужно.

Структура, хранящая ссылку, требует явного лайфтайма:

```rust
struct Excerpt<'a> { part: &'a str }
```

`'static` — «живёт всю программу» (например, строковые литералы `"…"`).

## 8. Частые ошибки

- Забыть `#[derive(...)]` нужного трейта (`Debug`, `Clone`, `PartialEq`, `Hash`).
- Требовать лишние bounds «на всякий случай».
- Взять `dyn Trait` по значению (нельзя — тип `!Sized`) — нужен `Box`/ссылку.
- Путать generic-параметр и ассоциированный тип.
- Ошибки лайфтаймов почти всегда означают одно: возвращаем/храним ссылку на
  данные, которые живут меньше.

## 9. Что потрогать руками

- `01_basics/12_generic_types/removing_dublication` — обобщение `largest`

## 10. Вопросы для самопроверки

1. Почему без `T: PartialOrd` функция `largest` не компилируется?
2. Что такое мономорфизация и почему дженерики «бесплатны» на рантайме?
3. Когда выбрать `dyn Trait`, а когда `<T: Trait>`?
4. Зачем нужен `<'a>` в `fn longest<'a>(a: &'a str, b: &'a str) -> &'a str`?
5. Чем ассоциированный тип отличается от generic-параметра трейта?
