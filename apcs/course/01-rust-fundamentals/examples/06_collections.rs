//! Урок 06: Collections и Iterators.
//!
//! Запуск: `cargo run --example 06_collections`
//!
//! Реальный код: emulator.rs:69-72 — BTreeMap для карты регистров;
//! master.rs:56 — VecDeque для лога транзакций (ring buffer);
//! worker.rs — Vec из сообщений.

use std::collections::{BTreeMap, HashMap, VecDeque};

/// Vec<T> — динамический массив. В emulator: `Vec<VirtualSensor>`.
fn vec_demo() {
    let mut sensors: Vec<String> = Vec::with_capacity(4);
    sensors.push("temp".into());
    sensors.push("pressure".into());

    assert_eq!(sensors.len(), 2);
    assert_eq!(sensors[0], "temp");

    // safe access — Option
    assert_eq!(sensors.get(5), None);
}

/// BTreeMap<u16, u16> — карта регистров. Как input_regs в emulator.rs:69.
/// BTreeMap отсортирован => можно обходить по диапазонам.
fn map_demo() {
    let mut regs: BTreeMap<u16, u16> = BTreeMap::new();
    regs.insert(0, 100);
    regs.insert(1, 200);

    // Читаем с дефолтом — ВАЖНО для Modbus: не все адреса существуют.
    assert_eq!(regs.get(&0).copied(), Some(100));
    assert_eq!(regs.get(&99).copied(), None);
    assert_eq!(regs.get(&99).copied().unwrap_or(0), 0);
}

/// HashMap — хэш-таблица, неупорядоченная. Хорошо для словарей по имени.
fn hashmap_demo() {
    let mut by_name: HashMap<String, u8> = HashMap::new();
    by_name.insert("pump".into(), 1);
    by_name.insert("boiler".into(), 2);

    // entry API — идiома "вставить если нет".
    by_name.entry("pump".to_string()).or_insert(99);
    assert_eq!(by_name["pump"], 1);
}

/// VecDeque — двусторонняя очередь. Ring buffer для лога шины (master.rs:56).
fn deque_demo() {
    let mut trace: VecDeque<u8> = VecDeque::new();
    for i in 0..5 {
        trace.push_back(i);
    }
    assert_eq!(trace.pop_front(), Some(0)); // круг
    assert_eq!(trace.len(), 4);

    // Ограничим до 3 — как TRACE_CAP в master.rs:15.
    while trace.len() > 3 {
        trace.pop_front();
    }
    assert_eq!(trace.len(), 3);
}

/// Iterator: цепочки map/filter/collect. У sum_bytes аналог в crc.rs, а
/// тут разберем чистые итераторы.
fn iterator_demo() {
    let nums: Vec<u16> = vec![1, 2, 3, 4, 5];

    // filter + map + sum:
    let even_sum: u16 = nums.iter().filter(|&&x| x % 2 == 0).map(|&x| x * x).sum();
    assert_eq!(even_sum, 20); // (2*2)+(4*4)

    // collect: превращаем итератор в Vec
    let squares: Vec<u16> = nums.iter().map(|&x| x * x).collect();
    assert_eq!(squares, vec![1, 4, 9, 16, 25]);

    // enumerate: как в to_hex (crc.rs:38)
    for (i, _) in nums.iter().enumerate().take(3) {
        assert!(i <= 2);
    }

    // zip — пара элементов с двух индексов
    let pairs: Vec<(u16, u16)> = nums.iter().copied().zip(nums.iter().copied().skip(1)).collect();
    assert_eq!(pairs.len(), 4);
}

/// Клонирование итератора по байту (как sum в crc-расчётах).
fn iter_bytes(s: &str) -> u64 {
    s.bytes().map(|b| b as u64).sum()
}

fn main() {
    vec_demo();
    map_demo();
    hashmap_demo();
    deque_demo();
    iterator_demo();
    assert_eq!(iter_bytes("abc"), 97 + 98 + 99);

    println!("06_collections: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deque_caps() {
        let mut d: VecDeque<u8> = (0..10).collect();
        while d.len() > 3 {
            d.pop_front();
        }
        assert_eq!(d, vec![7, 8, 9]);
    }

    #[test]
    fn map_default_get() {
        let regs = BTreeMap::from([(0u16, 10u16)]);
        assert_eq!(regs.get(&5).copied().unwrap_or_default(), 0);
    }
}