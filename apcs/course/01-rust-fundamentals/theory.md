# Теория: Rust Fundamentals для Embedded

## 1. Ownership — система владения

Ownership — ключевая концепция Rust, обеспечивающая безопасность памяти без сборщика мусора.

### Правила Ownership

1. **Каждое значение имеет ровно одного владельца** (owner)
2. **Когда owner выходит из области видимости, значение удаляется** (drop)
3. **Владение можно передать** (move) или **заимствовать** (borrow)

```rust
fn main() {
    let s1 = String::from("hello"); // s1 — владелец строки
    let s2 = s1;                    // ownership передан в s2 (move)
    // println!("{}", s1);          // ОШИБКА: s1 больше не владеет!
    println!("{}", s2);             // OK: s2 — владелец
}
// s2 выходит из области видимости → строка удалена (drop)
```

### Move vs Copy

**Move** — ownership переходит от одного владеца к другому:

```rust
let a = String::from("hello");
let b = a;  // a → b (move)
// a больше не доступен
```

**Copy** — значение копируется (для примитивов: i32, f64, bool, char):

```rust
let x = 42;
let y = x;  // x скопирован в y
println!("{} {}", x, y); // оба доступны
```

**Ключевой момент:** `String`, `Vec`, `HashMap` — НЕ копируются, только move.

### Базовые типы и Copy

```rust
// Copy типы (примитивы)
let a: i32 = 1;
let b = a; // копия, оба доступны

// Не-Copy типы
let s1 = String::from("hello");
let s2 = s1; // move, s1 недоступен

// Массивы/срезы
let arr = [1, 2, 3];
let arr2 = arr; // копия для фиксированных размеров

// Срезы — всегда borrowing
let s = String::from("hello");
let slice = &s[0..2]; // borrow, s всё ещё доступен
```

## 2. Borrowing — заимствование

Borrowing позволяет читать/писать данные, не забирая ownership.

### Immutable references (&T)

```rust
fn calculate_length(s: &String) -> usize {
    s.len()  // читаем, не забирая ownership
}

fn main() {
    let s = String::from("hello");
    let len = calculate_length(&s); // передаём ссылку
    println!("{} has length {}", s, len); // s всё ещё доступна!
}
```

**Правило:** можно иметь **много immutable ссылок** одновременно.

### Mutable references (&mut T)

```rust
fn add_world(s: &mut String) {
    s.push_str(", world!");
}

fn main() {
    let mut s = String::from("hello");
    add_world(&mut s);
    println!("{}", s); // "hello, world!"
}
```

**Правило:** можно иметь **только одну mutable ссылку** в данный момент.

### Правило "один писатель ИЛИ много читателей"

```rust
let mut s = String::from("hello");

// ОК: много читателей
let r1 = &s;
let r2 = &s;
println!("{} {}", r1, r2);

// ОК: один писатель (когда читатели закончили)
let r3 = &mut s;
r3.push_str("!");
```

**Нельзя одновременно:**

```rust
let mut s = String::from("hello");
let r1 = &s;      // immutable
let r2 = &mut s;  // ОШИБКА: mutable + immutable одновременно!
```

## 3. Lifetime — время жизни

Lifetime — это область видимости ссылки. Rust проверяет, что ссылки всегда указывают на валидные данные.

### Явные аннотации ('a)

```rust
// 'a — lifetime: возвращаемая ссылка живёт столько же, сколько входная
fn longer<'a>(s1: &'a str, s2: &'a str) -> &'a str {
    if s1.len() > s2.len() { s1 } else { s2 }
}

fn main() {
    let s1 = String::from("long string");
    let result;
    {
        let s2 = String::from("xyz");
        result = longer(s1.as_str(), s2.as_str());
        println!("Longest: {}", result); // OK: s2 ещё жива
    }
    // println!("Longest: {}", result); // ОШИБКА: s2 уже удалена
}
```

### Lifetime elision rules

Rust автоматически выводит lifetime в простых случаях:

```rust
// Эквивалентно: fn first_word<'a>(s: &'a str) -> &'a str
fn first_word(s: &str) -> &str {
    let bytes = s.as_bytes();
    for (i, &item) in bytes.iter().enumerate() {
        if item == b' ' {
            return &s[0..i];
        }
    }
    s
}
```

**Правила elision:**

1. Каждый входной lifetime становится отдельным параметром
2. Если ровно один входной lifetime → он присваивается всем выходным
3. Если есть `&self` или `&mut self` → lifetime `self` присваивается всем выходным

### 'static lifetime

```rust
// 'static — ссылка живётตลอด время работы программы
let s: &'static str = "hello world";
```

**Когда использовать:**

- Строковые литералы (всегда 'static)
- Lazy statics
- Глобальные данные

## 4. Traits — типажи

Traits определяют общее поведение для типов (аналог интерфейсов).

### Определение и реализация

```rust
// Определяем trait
trait Drawable {
    fn draw(&self);

    // Метод по умолчанию
    fn describe(&self) -> String {
        String::from("I am drawable")
    }
}

// Структуры
struct Circle { radius: f64 }
struct Square { side: f64 }

// Реализуем trait для каждой структуры
impl Drawable for Circle {
    fn draw(&self) {
        println!("Drawing circle with radius {}", self.radius);
    }
}

impl Drawable for Square {
    fn draw(&self) {
        println!("Drawing square with side {}", self.side);
    }

    // Переопределяем метод по умолчанию
    fn describe(&self) -> String {
        format!("Square with side {}", self.side)
    }
}
```

### Trait bounds (ограничения)

```rust
// Функция принимает любой тип, реализующий Drawable
fn draw_all(items: &[&dyn Drawable]) {
    for item in items {
        item.draw();
    }
}

// Через where clause
fn process<T: Drawable + Clone>(item: &T) -> T {
    item.draw();
    item.clone()
}

// Три способа ограничения
fn example1<T: Drawable>(item: &T) {}           // inline
fn example2<T>(item: &T) where T: Drawable {}   // where clause
fn example3(item: &impl Drawable) {}            // impl Trait (синтаксический сахар)
```

### Associated types vs generics

```rust
// С generics: можно иметь несколько реализаций
trait Convertible<T> {
    fn convert(&self) -> T;
}

impl Convertible<i32> for String {
    fn convert(&self) -> i32 { self.len() as i32 }
}

// С associated type: только одна реализация
trait Iterator {
    type Item;
    fn next(&mut self) -> Option<Self::Item>;
}
```

## 5. Enums и Pattern Matching

### Algebraic data types

```rust
// Enum с вариантами
enum Message {
    Quit,                          // единица (unit)
    Move { x: i32, y: i32 },      // именованные поля
    Write(String),                 // tuple
    ChangeColor(i32, i32, i32),    // tuple
}
```

### Pattern matching

```rust
fn process_message(msg: Message) {
    match msg {
        Message::Quit => {
            println!("Quit");
        }
        Message::Move { x, y } => {
            println!("Move to ({}, {})", x, y);
        }
        Message::Write(text) => {
            println!("Text: {}", text);
        }
        Message::ChangeColor(r, g, b) => {
            println!("Color: ({}, {}, {})", r, g, b);
        }
    }
}
```

### if let и while let

```rust
// if let: обработка одного варианта
let config_max = Some(3u8);
if let Some(max) = config_max {
    println!("Maximum: {}", max);
}

// while let: цикл пока есть совпадение
let mut stack = Vec::new();
stack.push(1);
stack.push(2);
stack.push(3);

while let Some(top) = stack.pop() {
    println!("{}", top);  // 3, 2, 1
}
```

## 6. Error Handling

### Result<T, E>

```rust
use std::fs::File;
use std::io::{self, Read};

fn read_file(path: &str) -> Result<String, io::Error> {
    let mut file = File::open(path)?;  // ? — оператор传播 ошибки
    let mut contents = String::new();
    file.read_to_string(&mut contents)?;
    Ok(contents)
}

// Использование
match read_file("config.txt") {
    Ok(contents) => println!("File: {}", contents),
    Err(e) => println!("Error: {}", e),
}
```

### Option<T>

```rust
fn find_even(numbers: &[i32]) -> Option<i32> {
    for &n in numbers {
        if n % 2 == 0 {
            return Some(n);
        }
    }
    None
}

// Методы Option
let numbers = vec![1, 3, 4, 5];
let even = find_even(&numbers)
    .map(|n| n * 2)           // трансформация
    .unwrap_or(0);            // значение по умолчанию
```

### Собственные типы ошибок

```rust
use std::fmt;

#[derive(Debug)]
enum ModbusError {
    TransportError(String),
    ProtocolError { code: u8 },
    DeviceNotFound(u8),
}

impl fmt::Display for ModbusError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::TransportError(msg) => write!(f, "Transport: {}", msg),
            Self::ProtocolError { code } => write!(f, "Protocol error: 0x{:02X}", code),
            Self::DeviceNotFound(id) => write!(f, "Device {} not found", id),
        }
    }
}

impl std::error::Error for ModbusError {}

// Конвертация из std::io::Error
impl From<std::io::Error> for ModbusError {
    fn from(e: std::io::Error) -> Self {
        ModbusError::TransportError(e.to_string())
    }
}
```

## 7. Async/Await

### Основы async

```rust
use tokio::time::{sleep, Duration};

// async fn возвращает impl Future
async fn fetch_data(url: &str) -> Result<String, reqwest::Error> {
    let response = reqwest::get(url).await?;
    let body = response.text().await?;
    Ok(body)
}

#[tokio::main]
async fn main() {
    // .await приостанавливает выполнение
    let data = fetch_data("http://example.com").await;
    println!("{:?}", data);
}
```

### Concurrent execution

```rust
#[tokio::main]
async fn main() {
    // Параллельное выполнение
    let (r1, r2) = tokio::join!(
        fetch_data("http://a.com"),
        fetch_data("http://b.com"),
    );

    // Spawn фоновой задачи
    let handle = tokio::spawn(async {
        sleep(Duration::from_secs(1)).await;
        "done"
    });

    let result = handle.await.unwrap();
}
```

### Select macro

```rust
use tokio::select;

tokio::select! {
    _ = fetch_data("http://a.com") => {
        println!("First completed");
    }
    _ = fetch_data("http://b.com") => {
        println!("Second completed");
    }
}
```

## 8. Collections

### Vec<T>

```rust
let mut v = Vec::new();
v.push(5);
v.push(6);
v.push(7);

// Итерация
for i in &v {
    println!("{}", i);
}

// Мутабельная итерация
for i in &mut v {
    *i += 10;
}
```

### HashMap

```rust
use std::collections::HashMap;

let mut map = HashMap::new();
map.insert("key1", 100);
map.insert("key2", 200);

// Получение значения
if let Some(value) = map.get("key1") {
    println!("key1: {}", value);
}
```

### Iterator adaptors

```rust
let v = vec![1, 2, 3, 4, 5];

// Функциональный стиль
let sum: i32 = v.iter()
    .filter(|&&x| x % 2 == 0)
    .map(|&x| x * x)
    .sum();

println!("Sum of squares of even numbers: {}", sum); // 20
```

## 9. Concurrency (предварительно)

### Arc и Mutex

```rust
use std::sync::{Arc, Mutex};
use std::thread;

let counter = Arc::new(Mutex::new(0));
let mut handles = vec![];

for _ in 0..10 {
    let counter = Arc::clone(&counter);
    let handle = thread::spawn(move || {
        let mut num = counter.lock().unwrap();
        *num += 1;
    });
    handles.push(handle);
}

for handle in handles {
    handle.join().unwrap();
}

println!("Result: {}", *counter.lock().unwrap()); // 10
```

### Send и Sync

- `Send` — тип можно передавать в другой поток
- `Sync` — тип можно разделять между потоками через `&T`

```rust
// Arc<T> where T: Send + Sync — безопасно разделять между потоками
let data = Arc::new(Mutex::new(vec![1, 2, 3]));
```

## 10. Common Pitfalls — частые ошибки

### Ownership

```rust
// ❌ Move after use
let s = String::from("hello");
let s2 = s;
println!("{}", s); // ERROR: s was moved

// ✅ Clone если нужен оригинальный
let s = String::from("hello");
let s2 = s.clone();
println!("{}", s); // OK
```

### Borrowing

```rust
// ❌ Две mutable ссылки
let mut s = String::from("hello");
let r1 = &mut s;
let r2 = &mut s; // ERROR

// ✅ Разделить на области видимости
let mut s = String::from("hello");
{
    let r1 = &mut s;
    r1.push_str(" world");
}
let r2 = &mut s; // OK
```

### Lifetime

```rust
// ❌ Lifetime не совпадает
fn bad(s1: &str, s2: &str) -> &str {
    if s1.len() > s2.len() { s1 } else { s2 }
}

// ✅ Явная аннотация
fn good<'a>(s1: &'a str, s2: &'a str) -> &'a str {
    if s1.len() > s2.len() { s1 } else { s2 }
}
```

### Result и Option

```rust
// ❌ Игнорирование ошибок
let file = File::open("data.txt"); // Result, игнорируем

// ✅ Обработка ошибок
match File::open("data.txt") {
    Ok(file) => { /* работа с файлом */ }
    Err(e) => println!("Ошибка: {}", e),
}

// ✅ Или через ?
fn read_config() -> Result<String, io::Error> {
    let mut s = String::new();
    File::open("config.txt")?.read_to_string(&mut s)?;
    Ok(s)
}
```

### async/await

```rust
// ❌ async без runtime
async fn fetch() { /* ... */ }
fetch(); // Ничего не произойдёт!

// ✅ Нужен runtime (tokio, embassy)
#[tokio::main]
async fn main() {
    fetch().await;
}
```

### Thread safety

```rust
// ❌ Non-Send тип в потоке
use std::thread;
let rc = Rc::new(42);
thread::spawn(move || { println!("{}", rc); }); // ERROR: Rc не Send

// ✅ Использовать Arc
use std::sync::Arc;
let arc = Arc::new(42);
let arc2 = arc.clone();
thread::spawn(move || { println!("{}", arc2); }); // OK
```

### Collections

```rust
// ❌ Borrow checker не даёт изменить
let mut v = vec![1, 2, 3];
let first = &v[0];
v.push(4); // ERROR: v используется через first

// ✅ Сначала прочитать, потом изменить
let mut v = vec![1, 2, 3];
let first = v[0]; // Копия
v.push(4); // OK
println!("{}", first);
