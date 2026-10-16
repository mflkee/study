//! Ступень 2. Битовые операции на u8 — ЗАДАНИЕ.
//!
//!   Запуск: cargo run  --bin step2-task
//!   Тесты:  cargo test --bin step2-task
//!   Разбор: cargo run  --bin step2-answer
//!   Теория: step2-bits/LECTURE.md
//!
//! Пока в телах стоят `todo!()`, компилятор считает параметры неиспользованными —
//! глушим этот шум, чтобы ты видел только свои ошибки.

#![allow(dead_code, unused_variables)]

/// Прочитать бит номер `i` (0..8).
fn get_bit(x: u8, i: u32) -> bool {
    (x >> i) & 1 == 1
}

/// Поставить бит `i` в 1.
fn set_bit(x: u8, i: u32) -> u8 {
    x | (1u8 << i)
}

/// Сбросить бит `i` в 0.
fn clear_bit(x: u8, i: u32) -> u8 {
    x & !(1u8 << i)
}

/// Инвертировать бит `i`.
fn flip_bit(x: u8, i: u32) -> u8 {
    x ^ (1u8 << i)
}

/// Чётность: истина, если единиц в байте нечётное число.
// fn parity(x: u8) -> bool {
//     let mut acc = 0;
//     for i in 0..8 {
//         if get_bit(x, i) == true {
//             acc += 1
//         }
//         if acc % 2 == 0 {
//             return false;
//         }
//     }
//     true
// }
//
fn parity(x: u8) -> bool {
    let mut acc = false;
    for i in 0..8 {
        acc ^= get_bit(x, i);
    }
    acc
}

/// Один побитовый гейт обрабатывает все 8 бит сразу.
/// Верни тройку (a AND b, a OR b, a XOR b).
fn byte_gates(a: u8, b: u8) -> (u8, u8, u8) {
    (a & b, a | b, a ^ b)
}

fn main() {
    let x = 0b1010_0000u8;
    println!("x             = {x:#010b}");
    println!("get_bit(x, 5) = {}", get_bit(x, 5));
    println!("set_bit(x, 0) = {:#010b}", set_bit(x, 0));
    println!("clear_bit(x,7)= {:#010b}", clear_bit(x, 7));
    println!("flip_bit(x, 5)= {:#010b}", flip_bit(x, 5));
    println!("parity(x)     = {}", parity(x));

    let (a, b) = (0b1100_1010u8, 0b1010_0110u8);
    let (and, or, xor) = byte_gates(a, b);
    println!();
    println!("a    = {a:#010b}");
    println!("b    = {b:#010b}");
    println!("a&b  = {and:#010b}");
    println!("a|b  = {or:#010b}");
    println!("a^b  = {xor:#010b}");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bits() {
        let x = 0b1000_0001u8;
        assert!(get_bit(x, 0) && get_bit(x, 7) && !get_bit(x, 3));
        assert_eq!(set_bit(0, 3), 0b1000);
        assert_eq!(clear_bit(0b1111, 2), 0b1011);
        assert_eq!(flip_bit(0b1010, 0), 0b1011);
    }

    #[test]
    fn parity_matches_count_ones() {
        for x in 0..=u8::MAX {
            assert_eq!(parity(x), x.count_ones() % 2 == 1);
        }
    }
}
