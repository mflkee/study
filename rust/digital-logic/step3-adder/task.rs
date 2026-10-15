//! Ступень 3. Сумматор из гейтов — ЗАДАНИЕ.
//!
//!   Запуск: cargo run  --bin step3-task
//!   Тесты:  cargo test --bin step3-task
//!   Разбор: cargo run  --bin step3-answer
//!   Теория: step3-adder/LECTURE.md
//!
//! Пока в телах стоят `todo!()`, компилятор считает параметры неиспользованными —
//! глушим этот шум, чтобы ты видел только свои ошибки.

#![allow(dead_code, unused_variables)]

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

// --- Помощники для бит (как в ступени 2) ---------------------------------

fn get_bit(x: u8, i: u32) -> bool {
    (x >> i) & 1 == 1
}
fn set_bit(x: u8, i: u32) -> u8 {
    x | (1u8 << i)
}

// --- Сумматоры -----------------------------------------------------------

/// Полусумматор: складывает два бита. Верни (сумма, перенос).
fn half_adder(a: bool, b: bool) -> (bool, bool) {
    todo!("реализуй сам")
}

/// Полный сумматор: два бита плюс входной перенос. Верни (сумма, перенос).
fn full_adder(a: bool, b: bool, cin: bool) -> (bool, bool) {
    todo!("реализуй сам")
}

/// 8-битный ripple-carry сумматор. Верни (сумма, переполнение).
fn adder8(a: u8, b: u8) -> (u8, bool) {
    todo!("реализуй сам")
}

fn main() {
    let cases = [(0u8, 0u8), (1, 2), (200, 55), (255, 1), (128, 128), (42, 42)];
    println!("{:>4} {:>4} | {:>4} {:>5} | builtin", "a", "b", "sum", "carry");
    for (a, b) in cases {
        let (sum, carry) = adder8(a, b);
        let (bsum, bcarr) = a.overflowing_add(b);
        let ok = sum == bsum && carry == bcarr;
        println!(
            "{a:>4} {b:>4} | {sum:>4} {carry:>5} | {bsum:>4} {bcarr:>5}  {}",
            if ok { "ok" } else { "MISMATCH" }
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn half_adder_truth() {
        assert_eq!(half_adder(false, false), (false, false));
        assert_eq!(half_adder(true, false), (true, false));
        assert_eq!(half_adder(false, true), (true, false));
        assert_eq!(half_adder(true, true), (false, true));
    }

    #[test]
    fn matches_builtin() {
        for a in 0..=u8::MAX {
            for b in 0..=u8::MAX {
                let (s, c) = adder8(a, b);
                let (bs, bc) = a.overflowing_add(b);
                assert_eq!((s, c), (bs, bc), "a={a} b={b}");
            }
        }
    }
}
