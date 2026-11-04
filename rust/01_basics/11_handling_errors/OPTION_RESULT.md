# Option, Result и оператор `?` — шпаргалка

В Rust нет `null` и исключений. Их роль играют два типа-обёртки:

- `Option<T>` — «значение есть или его нет» (`Some` / `None`);
- `Result<T, E>` — «получилось или ошибка» (`Ok` / `Err`).

Компилятор заставляет обработать оба случая — забыть про ошибку нельзя.

- Rust Book: <https://doc.rust-lang.org/book/ch06-02-match.html>,
  <https://doc.rust-lang.org/book/ch09-02-recoverable-errors-with-result.html>
- Живые примеры: `01_basics/11_handling_errors/`

## 1. Option&lt;T&gt; — отсутствие значения

```rust
fn find_even(nums: &[i32]) -> Option<i32> {
    nums.iter().copied().find(|n| n % 2 == 0)
}

match find_even(&[1, 3, 4]) {
    Some(n) => println!("нашёл {n}"),
    None => println!("нет чётных"),
}
```

Основные методы:

| Метод                       | Что делает                                    |
|-----------------------------|-----------------------------------------------|
| `.unwrap()`                 | достать значение, **паника** на `None`        |
| `.expect("msg")`            | как `unwrap`, но с сообщением                 |
| `.unwrap_or(x)`             | значение или запасное `x`                     |
| `.unwrap_or_else(‖ …)`      | как выше, но `x` вычисляется лениво           |
| `.unwrap_or_default()`      | запасное `T::default()`                       |
| `.map(f)`                   | преобразовать `Some`, если он есть            |
| `.and_then(f)`              | `f` возвращает `Option` — цепочка без вложений|
| `.filter(p)`                | оставить `Some`, только если `p` истинно      |
| `.ok_or(e)` / `.ok_or_else` | превратить в `Result`                         |

```rust
let n = find_even(&[1, 3, 4]).unwrap_or(0);          // 4
let doubled = find_even(&[2, 3]).map(|n| n * 2);      // Some(4)
```

## 2. Result&lt;T, E&gt; — ошибка как значение

```rust
use std::fs;

fn read_len(path: &str) -> Result<usize, std::io::Error> {
    let text = fs::read_to_string(path)?;   // ошибка -> ранний return Err
    Ok(text.len())
}

match read_len("notes.txt") {
    Ok(n) => println!("{n} байт"),
    Err(e) => eprintln!("ошибка: {e}"),
}
```

Методы: `.map`, `.map_err`, `.and_then`, `.unwrap_or`, `.ok()` (→ `Option`),
`.err()`, `.is_ok()` / `.is_err()`. У `unwrap`/`expect` у `Result` паника на `Err`.

## 3. Оператор `?` — главный инструмент

`?` разворачивает `Result`/`Option`: при ошибке **досрочно возвращает** `Err` из
функции, при успехе даёт значение.

```rust
fn config_value(path: &str, key: &str) -> Result<String, std::io::Error> {
    let text = std::fs::read_to_string(path)?;    // ? вместо match
    let line = text.lines().find(|l| l.starts_with(key));
    Ok(line.unwrap_or("").to_string())
}
```

Правила:

- работает только в функции, возвращающей `Result` (или `Option`, или `impl Try`);
- тип ошибки конвертируется через `From` — поэтому `?` умеет превращать
  `io::Error` в твой `MyError`, если реализован `From<io::Error> for MyError`;
- `?` применим и к `Option` — тогда `None` возвращается досрочно;
- `main` тоже может возвращать `Result` (см. §6).

## 4. panic! или Result?

| Ситуация                                   | Что использовать |
|--------------------------------------------|------------------|
| Баг, нарушенный инвариант, «этого не может быть» | `panic!` / `unwrap` |
| Ошибка, которую вызывающий может обработать (файла нет, парсинг, сеть) | `Result` |
| Библиотечный код                          | почти всегда `Result` |

Правило: `unwrap` в библиотеке/проде — красный флаг. В тестах и прототипах — ок.

## 5. Свой тип ошибки

```rust
use std::fmt;

#[derive(Debug)]
enum AppError {
    Io(std::io::Error),
    BadFormat(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::Io(e) => write!(f, "ошибка ввода-вывода: {e}"),
            AppError::BadFormat(s) => write!(f, "неверный формат: {s}"),
        }
    }
}

impl std::error::Error for AppError {}

// благодаря From оператор `?` сам превратит io::Error в AppError
impl From<std::io::Error> for AppError {
    fn from(e: std::io::Error) -> Self {
        AppError::Io(e)
    }
}
```

Быстрые альтернативы для приложений: `Box<dyn std::error::Error>`;
внешние крейты — `anyhow` (в приложениях) и `thiserror` (в библиотеках).

## 6. main, возвращающий Result

```rust
use std::error::Error;

fn main() -> Result<(), Box<dyn Error>> {
    let text = std::fs::read_to_string("notes.txt")?;
    println!("{} байт", text.len());
    Ok(())
}
```

При `Err` программа завершится с ненулевым кодом и напечатает ошибку — без `match`.

## 7. Частые ошибки

- `unwrap()` на данных из внешнего мира (файлы, ввод, сеть) — падение вместо обработки.
- `match` там, где хватило бы `?` или `.map`.
- Вложенные `Some(Some(x))` — лечится `.and_then`, а не `match` в `match`.
- Забыть, что `?` требует `Result`/`Option` в возврате функции.
- Свой `From` для домена и `Box<dyn Error>` вперемешку без причины.

## 8. Что потрогать руками

- `01_basics/11_handling_errors/ai_vers` — вариант с `expect`
- `01_basics/11_handling_errors/rb_vers` — ручной `match`
- `01_basics/11_handling_errors/error_reporting` — `?` и разбор `ErrorKind`

## 9. Вопросы для самопроверки

1. Чем `Option` отличается от «значения, равного null»?
2. Что делает `?` и почему он требует `Result` в сигнатуре функции?
3. Когда паниковать, а когда возвращать `Result`?
4. Зачем `impl From<io::Error>` рядом со своим типом ошибки?
5. Чем `.and_then` отличается от `.map` при цепочке `Option`?
