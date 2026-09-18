//! Урок 08: Threads, Channels, Arc/Mutex, async-основы.
//!
//! Запуск: `cargo run --example 08_threads`
//!
//! Реальный код: `worker.rs` — потоки, mpsc-канал, Arc<Mutex<Emulator>>;
//! `main.rs` — event-loop. Фоновый поток читает данные и шлёт их в UI.

use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

/// Канал mpsc (multi-producer, single-consumer). В worker.rs данные
/// отправляются из фоновых потоков в UI-поток по каналу.
fn channel_demo() {
    let (tx, rx) = mpsc::channel::<u8>();

    thread::spawn(move || {
        for i in 0..3u8 {
            tx.send(i).unwrap(); // tx переехал в поток
        }
    });

    // Приём из канала — итератор stops on disconnect.
    let collected: Vec<u8> = rx.iter().collect();
    assert_eq!(collected, vec![0, 1, 2]);
}

/// Arc<T> — счётчик ссылок, делится между потоками (атомарно).
/// Mutex<T> — взаимное исключение "один писатель".
/// Точьно так в worker.rs: `Arc<Mutex<Emulator>>`.
fn arc_mutex_demo() {
    let counter = Arc::new(Mutex::new(0u32));
    let mut handles = Vec::new();

    for _ in 0..10 {
        let counter = Arc::clone(&counter);
        handles.push(thread::spawn(move || {
            let mut n = counter.lock().unwrap();
            *n += 1; // критическая секция
        }));
    }
    for h in handles {
        h.join().unwrap(); // ждём все потоки
    }
    assert_eq!(*counter.lock().unwrap(), 10);
}

/// Функция, которую можно вызвать "в потоке и подождать".
fn block_in_thread() {
    let t = thread::spawn(|| {
        thread::sleep(Duration::from_millis(1));
        7
    });
    assert_eq!(t.join().unwrap(), 7);
}

/// Потоки в фоне + канал как "publication в UI". В TUI это main app.
/// Здесь: фон пишет, главный читает с таймаутом.
fn background_publisher() {
    let (tx, rx) = mpsc::channel::<String>();
    thread::spawn(move || {
        tx.send("snapshot".to_string()).unwrap();
    });
    // recv_timeout -> Err(Timeout) если долго ждать.
    match rx.recv_timeout(Duration::from_millis(100)) {
        Ok(msg) => assert_eq!(msg, "snapshot"),
        Err(_) => panic!("no message"),
    }
}

fn main() {
    channel_demo();
    arc_mutex_demo();
    block_in_thread();
    background_publisher();
    println!("08_threads: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn shared_counter_thread_safe() {
        arc_mutex_demo();
    }

    #[test]
    fn mpsc_delivers_in_order() {
        let (tx, rx) = mpsc::channel::<u8>();
        thread::spawn(move || {
            for v in [10u8, 20, 30] {
                tx.send(v).unwrap();
            }
        });
        let got: Vec<u8> = rx.iter().collect();
        assert_eq!(got, vec![10, 20, 30]);
    }
}