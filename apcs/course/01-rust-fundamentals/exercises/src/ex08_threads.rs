//! Упражнение 08: Threads + Channels.
//!
//! Задание: реализуйте фоновый поток, который генерирует снапшоты
//! и шлёт их по каналу — как Worker шлёт Event::Snapshot в UI.

use std::sync::mpsc;

/// # Задание 1
/// Запустить поток, который пошлёт в канал числа 1..=n и закроется.
/// Вернуть receiver.
pub fn spawn_counter(n: u8) -> mpsc::Receiver<u8> {
    let (tx, rx) = mpsc::channel();
    std::thread::spawn(move || {
        for i in 1..=n {
            tx.send(i).unwrap();
        }
    });
    rx
}

/// # Задание 2
/// Посчитать сумму чисел, пришедших из канала (итератор receiver).
pub fn sum_from_channel(rx: mpsc::Receiver<u8>) -> u64 {
    rx.iter().map(|v| v as u64).sum()
}

/// # Задание 3
/// Отправить одну строку и сразу вернуть её обратно (а-ля "echo").
pub fn echo_string(tx: mpsc::Sender<String>, value: String) {
    tx.send(value).unwrap();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn counter_sums_1_to_5() {
        let rx = spawn_counter(5);
        assert_eq!(sum_from_channel(rx), 15);
    }

    #[test]
    fn channel_receives_string() {
        let (tx, rx) = mpsc::channel();
        echo_string(tx, "snapshot".into());
        assert_eq!(rx.recv().unwrap(), "snapshot");
    }
}