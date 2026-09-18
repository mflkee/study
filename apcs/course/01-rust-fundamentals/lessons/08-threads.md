# 08: Потоки, каналы, Arc/Mutex и Send/Sync

> **Цель урока:** понять базовую работу с потоками и разделяемым
> состоянием — как в worker.rs (фоновые потоки шлют события в UI).
> Здесь — то, что понадобится для embedding/async.

## thread::spawn — фоновый поток

```rust
use std::thread;

let handle = thread::spawn(|| {
    // код в фоне
    7
});
let result = handle.join().unwrap();   // ждём завершения
```

`spawn` принимает **closure** (замыкание). Чтобы захватить переменные
внутри, используем `move || { ... }`.

## mpsc канал — «шина» между потоками

multi-producer, single-consumer: много потоков могут отправлять,
читать — один.

```rust
use std::sync::mpsc;
let (tx, rx) = mpsc::channel::<u8>();

thread::spawn(move || {
    for i in 0..3 { tx.send(i).unwrap(); }
});

let collected: Vec<u8> = rx.iter().collect();  // [0, 1, 2]
```

В worker.rs канал — фактически главный механизм общения: фоновые
исполнители шлют `Event::PortFound`, `Event::Snapshot` и т.д., а
UI-поток слушает `rx`.

## Arc<T> + Mutex<T> — разделяемое состояние

```rust
use std::sync::{Arc, Mutex};

let counter = Arc::new(Mutex::new(0u32));
let mut handles = Vec::new();

for _ in 0..10 {
    let counter = Arc::clone(&counter);   // дешёвый клон
    handles.push(thread::spawn(move || {
        let mut n = counter.lock().unwrap();   // берём критическую секцию
        *n += 1;
    }));
}
for h in handles { h.join().unwrap(); }

assert_eq!(*counter.lock().unwrap(), 10);
```

- `Arc` (atomic reference count) — счётчик ссылок, доступный из потоков.
- `Mutex` — межпоточная mutual exclusion: только один пишет за раз.

В TUI — `Arc<Mutex<Emulator>>` в worker.rs — «один экземпляр эмулятора,
доступный всем потокам».

## Send и Sync

- `Send` — тип можно перемещать в другой поток.
- `Sync` — тип можно разделять между потоками через `&T`.

```rust
// Rc — НЕ Send (счётчик ссылок только в одном потоке)
// Arc<T> где T: Send+Sync — можно шарить между потоками

let data = Arc::new(Mutex::new(vec![1, 2, 3]));  // Send + Sync = ok
```

Правило: чаще всего ошибки `Send` возникают, когда внутри потока
находится `Rc` или обычная ссылка. Заменить их на `Arc`/содержимое.

## Знакомство с async (предварительно)

`async fn` — функция, которая возвращает `Future`. Для запуска нужен
runtime. В embedded-мире это embassy, в hostinte — tokio:

```rust
// embassy-стиль; мы подробнее встретимся в модуле 02.
// Внутри — примерно то же, что и thread, но переиспользуя один поток.
async fn poll(serial: &mut SerialMaster) {
    loop {
        let data = serial.read_holding_registers(0, 10).await;
        // ...
    }
}
```

Ключевое отличие: `async` не создаёт потоки по умолчанию; он позволяет
**много задач на одном потоке**, ожидая их через `.await`.

## Задание (проект: фон + канал)

См. `exercises/src/ex08_threads.rs`.

1. Запустите фон, который шлёт 1..=n-числа в канал; соберите их сумму
   через `rx.iter().sum()`.
2. Отправьте строку по каналу, получите её в главном потоке.
3. Объясните, зачем нужен `move` и как работает `Arc::clone`.

## Следующий урок

[09-practice.md](09-practice.md) — сборка всего: структура + enum + кольцо
транзакций + Result.