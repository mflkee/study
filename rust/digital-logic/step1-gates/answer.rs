//! Ступень 1. Гейты и таблицы истинности — РАЗБОР.
//!
//!   Запуск: cargo run  --bin step1-answer
//!   Тесты:  cargo test --bin step1-answer

// --- Гейты ---------------------------------------------------------------

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

/// XOR, собранный из AND/OR/NOT — проверка, что гейты композируются.
fn xor_from_gates(a: bool, b: bool) -> bool {
    (a | b) & not(a & b)
}

/// AND, собранный из двух NAND — доказательство NAND-полноты.
fn and_from_nand(a: bool, b: bool) -> bool {
    nand(nand(a, b), nand(a, b))
}

// --- Таблица истинности --------------------------------------------------

fn truth_table(name: &str, gate: fn(bool, bool) -> bool) {
    println!("{name}:");
    println!("  {:>5} {:>5} | {:>5}", "a", "b", "out");
    for a in [false, true] {
        for b in [false, true] {
            println!("  {:>5} {:>5} | {:>5}", a, b, gate(a, b));
        }
    }
    println!();
}

fn same_for_all(g1: fn(bool, bool) -> bool, g2: fn(bool, bool) -> bool) -> bool {
    for a in [false, true] {
        for b in [false, true] {
            if g1(a, b) != g2(a, b) {
                return false;
            }
        }
    }
    true
}

fn main() {
    truth_table("AND", and);
    truth_table("OR", or);
    truth_table("XOR", xor);
    truth_table("NAND", nand);

    println!("XOR из AND/OR/NOT == XOR : {}", same_for_all(xor, xor_from_gates));
    println!("AND из NAND       == AND : {}", same_for_all(and, and_from_nand));
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
