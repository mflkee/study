//! Ступень 1. Гейты и таблицы истинности — ЗАДАНИЕ.
//!
//! Замени каждый `todo!()` своей реализацией.
//!   Запуск: cargo run  --bin step1-task
//!   Тесты:  cargo test --bin step1-task
//!   Разбор: cargo run  --bin step1-answer
//!
//! Пока в телах стоят `todo!()`, компилятор считает параметры неиспользованными —
//! глушим этот шум, чтобы ты видел только свои ошибки.

#![allow(dead_code, unused_variables)]

// --- Гейты ---------------------------------------------------------------
// Каждый гейт — чистая функция bool -> bool.
// Подсказки: `&` = AND, `|` = OR, `^` = XOR, `!` = NOT.

fn and(a: bool, b: bool) -> bool {
    a & b
}

fn or(a: bool, b: bool) -> bool {
    a | b
}

fn xor(a: bool, b: bool) -> bool {
    a ^ b
}

fn nand(a: bool, b: bool) -> bool {
    !(a & b)
}

fn not(a: bool) -> bool {
    !a
}

// --- Композиция ----------------------------------------------------------

fn xor_from_gates(a: bool, b: bool) -> bool {
    (a | b) & !(a & b) // (a,b)=(1,0) => (1) & (0) => 1
}

fn and_from_nand(a: bool, b: bool) -> bool {
    !(!(a & b) & !(a & b))
}

// --- Таблица истинности --------------------------------------------------

/// Печатает таблицу истинности для бинарного гейта.
/// `gate` — указатель на функцию `fn(bool, bool) -> bool`.
fn truth_table(name: &str, gate: fn(bool, bool) -> bool) {
    println!("{name}:");
    println!("{:>5} {:>5} | {:>5}", "a", "b", "out");
    for a in [false, true] {
        for b in [false, true] {
            println!("{:>5} {:>5} | {:>5}", a, b, gate(a, b));
        }
    }
    println!()
}

/// Проверяет, что два гейта совпадают на всех 4 комбинациях входов.
fn same_for_all(g1: fn(bool, bool) -> bool, g2: fn(bool, bool) -> bool) -> bool {
    for a in [false, true] {
        for b in [false, true] {
            if g1(a, b) == g2(a, b) {
                return true;
            }
        }
    }
    false
}

fn main() {
    truth_table("AND", and);
    truth_table("OR", or);
    truth_table("XOR", xor);
    truth_table("NAND", nand);

    println!(
        "XOR из AND/OR/NOT == XOR : {}",
        same_for_all(xor, xor_from_gates)
    );
    println!(
        "AND из NAND       == AND : {}",
        same_for_all(and, and_from_nand)
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gates() {
        assert!(!and(false, false) && !and(true, false) && !and(false, true) && and(true, true));
        assert!(!or(false, false) && or(true, false) && or(false, true) && or(true, true));
        assert!(!xor(false, false) && xor(true, false) && xor(false, true) && !xor(true, true));
        assert!(nand(false, false) && nand(true, false) && nand(false, true) && !nand(true, true));
        assert!(not(false) && !not(true));
    }

    #[test]
    fn composition() {
        assert!(same_for_all(xor, xor_from_gates));
        assert!(same_for_all(and, and_from_nand));
    }
}
