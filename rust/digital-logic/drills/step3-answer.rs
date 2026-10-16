//! Тренажёр ступени 3 (сумматоры) — РАЗБОР. 14 упражнений, 3 блока.
//!
//!   Тесты:  cargo test --bin step3-drills-answer
//!   Демо:   cargo run  --bin step3-drills-answer
//!   Теория: step3-adder/LECTURE.md
//!
//! Те же упражнения, что в `drills/step3.rs`, но с готовыми решениями.
//! Ключевая идея: идём по разрядам от младшего к старшему и передаём перенос.
//!

#![allow(dead_code, unused_variables)]

// --- Помощники (из ступени 2, менять не нужно) ---------------------------

fn get_bit(x: u8, i: u32) -> bool {
    (x >> i) & 1 == 1
}

fn set_bit(x: u8, i: u32) -> u8 {
    x | (1u8 << i)
}

// ===== Блок 1. Слагаемые =================================================

/// Полусумматор: два бита. Верни `(сумма, перенос)`.
fn half_adder(a: bool, b: bool) -> (bool, bool) {
    (a ^ b, a & b)
}

/// Только бит суммы полусумматора (два входа).
fn half_sum(a: bool, b: bool) -> bool {
    a ^ b
}

/// Только перенос полусумматора: когда он вообще возникает?
fn half_carry(a: bool, b: bool) -> bool {
    a & b
}

/// Полный сумматор: два бита плюс входной перенос `cin`.
/// Верни `(сумма, перенос)`. Собери из двух полусумматоров.
fn full_adder(a: bool, b: bool, cin: bool) -> (bool, bool) {
    let (s1, c1) = half_adder(a, b);
    let (s2, c2) = half_adder(s1, cin);
    (s2, c1 | c2)
}

/// Только выходной перенос полного сумматора («не влезло ли»).
/// Это «хотя бы два из трёх входов истинны».
fn full_carry(a: bool, b: bool, cin: bool) -> bool {
    (a & b) | (a & cin) | (b & cin)
}

// ===== Блок 2. Многоразрядные ============================================
// Общий приём: идём по разрядам от младшего к старшему, держим `carry`,
// каждый бит суммы вставляем в результат.

/// Сложение **младших 4 битов** `a` и `b`. Верни `(сумма, перенос из бита 3)`.
/// Старшие биты входов игнорируй, результат тоже не выходит за 4 бита.
fn adder4(a: u8, b: u8) -> (u8, bool) {
    let mut sum = 0u8;
    let mut carry = false;
    for i in 0..4 {
        let (s, c) = full_adder(get_bit(a, i), get_bit(b, i), carry);
        if s {
            sum = set_bit(sum, i);
        }
        carry = c;
    }
    (sum, carry)
}

/// Полный 8-битный сумматор. Верни `(сумма, переполнение)`.
fn adder8(a: u8, b: u8) -> (u8, bool) {
    let mut sum = 0u8;
    let mut carry = false;
    for i in 0..8 {
        let (s, c) = full_adder(get_bit(a, i), get_bit(b, i), carry);
        if s {
            sum = set_bit(sum, i);
        }
        carry = c;
    }
    (sum, carry)
}

/// Сложение с входным переносом: `a + b + cin`. Верни `(сумма, переполнение)`.
/// Это `adder8`, у которого младший разряд начинается не с нуля, а с `cin`.
fn add_cin(a: u8, b: u8, cin: bool) -> (u8, bool) {
    let mut sum = 0u8;
    let mut carry = cin;
    for i in 0..8 {
        let (s, c) = full_adder(get_bit(a, i), get_bit(b, i), carry);
        if s {
            sum = set_bit(sum, i);
        }
        carry = c;
    }
    (sum, carry)
}

/// 16-битный сумматор: тот же цикл, только по 16 разрядам.
fn adder16(a: u16, b: u16) -> (u16, bool) {
    let mut sum = 0u16;
    let mut carry = false;
    for i in 0..16 {
        let a_bit = (a >> i) & 1 == 1;
        let b_bit = (b >> i) & 1 == 1;
        let (s, c) = full_adder(a_bit, b_bit, carry);
        if s {
            sum |= 1u16 << i;
        }
        carry = c;
    }
    (sum, carry)
}

/// Прибавить 1. Верни `(сумма, переполнение)`. Частный случай сумматора.
fn increment(x: u8) -> (u8, bool) {
    adder8(x, 1)
}

// ===== Блок 3. Применение ================================================

/// Было ли переполнение при сложении двух байтов.
fn overflowed(a: u8, b: u8) -> bool {
    adder8(a, b).1
}

/// Сложить **три** байта. Их сумма может доходить до 765, поэтому результат —
/// это `u16`, в него влезает всё без переполнения.
fn add_three(a: u8, b: u8, c: u8) -> u16 {
    a as u16 + b as u16 + c as u16
}

/// Дополнительный код: «минус x» в 8 битах. Идея: `!x + 1`.
/// Грабля: `!0 + 1` — это `255 + 1`, и обычное `+` здесь паникует.
/// Нужен `wrapping_add`.
fn negate(x: u8) -> u8 {
    (!x).wrapping_add(1)
}

/// Вычитание: `a - b`. Верни `(результат, был_ли_заём)`,
/// где заём — это `true`, когда `a < b` (результат «ушёл в минус»).
/// Строй через `negate` и свой сумматор; готовый `overflowing_sub` — только
/// для сверки в тестах.
fn sub8(a: u8, b: u8) -> (u8, bool) {
    let (r, _) = adder8(a, negate(b));
    (r, a < b)
}

fn main() {
    println!("Разбор тренажёра ступени 3: adder8 против встроенного\n");
    println!(
        "{:>4} {:>4} | {:>4} {:>5} | builtin",
        "a", "b", "sum", "carry"
    );
    for (a, b) in [
        (0u8, 0u8),
        (7, 5),
        (200, 55),
        (255, 1),
        (128, 128),
        (42, 42),
    ] {
        let (sum, carry) = adder8(a, b);
        let (bsum, bcarry) = a.overflowing_add(b);
        let ok = if (sum, carry) == (bsum, bcarry) {
            "ok"
        } else {
            "MISMATCH"
        };
        println!("{a:>4} {b:>4} | {sum:>4} {carry:>5} | {bsum:>4} {bcarry:>5}  {ok}");
    }
    println!("\nadder16(40000, 40000)  = {:?}", adder16(40000, 40000));
    println!("sub8(3, 5)             = {:?}", sub8(3, 5));
    println!("negate(5)              = {}", negate(5));
    println!("add_three(255,255,255) = {}", add_three(255, 255, 255));
    println!("\nпроверка: cargo test --bin step3-drills-answer");
}

#[cfg(test)]
mod block1_adders {
    use super::*;

    #[test]
    fn drill_half_adder() {
        assert_eq!(half_adder(false, false), (false, false));
        assert_eq!(half_adder(true, false), (true, false));
        assert_eq!(half_adder(false, true), (true, false));
        assert_eq!(half_adder(true, true), (false, true));
    }

    #[test]
    fn drill_half_sum() {
        assert!(!half_sum(false, false));
        assert!(half_sum(true, false));
        assert!(half_sum(false, true));
        assert!(!half_sum(true, true));
    }

    #[test]
    fn drill_half_carry() {
        assert!(!half_carry(false, false));
        assert!(!half_carry(true, false));
        assert!(!half_carry(false, true));
        assert!(half_carry(true, true));
    }

    #[test]
    fn drill_full_adder() {
        // (a, b, cin) -> (сумма, перенос)
        assert_eq!(full_adder(false, false, false), (false, false));
        assert_eq!(full_adder(true, false, false), (true, false));
        assert_eq!(full_adder(false, true, false), (true, false));
        assert_eq!(full_adder(true, true, false), (false, true));
        assert_eq!(full_adder(false, false, true), (true, false));
        assert_eq!(full_adder(true, false, true), (false, true));
        assert_eq!(full_adder(false, true, true), (false, true));
        assert_eq!(full_adder(true, true, true), (true, true));
    }

    #[test]
    fn drill_full_carry() {
        assert!(!full_carry(false, false, false));
        assert!(!full_carry(true, false, false));
        assert!(!full_carry(false, true, false));
        assert!(!full_carry(false, false, true));
        assert!(full_carry(true, true, false));
        assert!(full_carry(true, false, true));
        assert!(full_carry(false, true, true));
        assert!(full_carry(true, true, true));
    }
}

#[cfg(test)]
mod block2_multibit {
    use super::*;

    #[test]
    fn drill_adder4() {
        assert_eq!(adder4(0b0111, 0b0101), (12, false));
        assert_eq!(adder4(0b1111, 0b0001), (0b0000, true));
        assert_eq!(adder4(0b1000, 0b1000), (0b0000, true));
        assert_eq!(adder4(0xFF, 0xFF), (0b1110, true));
        assert_eq!(adder4(0, 0), (0, false));
        assert_eq!(adder4(0b1000, 0b0001), (0b1001, false));
    }

    #[test]
    fn drill_adder8() {
        assert_eq!(adder8(7, 5), (12, false));
        assert_eq!(adder8(255, 1), (0, true));
        assert_eq!(adder8(128, 128), (0, true));
        assert_eq!(adder8(200, 55), (255, false));
        for a in 0..=u8::MAX {
            for b in 0..=u8::MAX {
                assert_eq!(adder8(a, b), a.overflowing_add(b), "a={a} b={b}");
            }
        }
    }

    #[test]
    fn drill_add_cin() {
        assert_eq!(add_cin(0, 0, false), (0, false));
        assert_eq!(add_cin(0, 0, true), (1, false));
        assert_eq!(add_cin(1, 2, true), (4, false));
        assert_eq!(add_cin(255, 0, true), (0, true));
        assert_eq!(add_cin(128, 128, false), (0, true));
        assert_eq!(add_cin(254, 1, false), (255, false));
        for a in 0..=u8::MAX {
            for b in 0..=u8::MAX {
                for cin in [false, true] {
                    let total = a as u16 + b as u16 + cin as u16;
                    assert_eq!(add_cin(a, b, cin), (total as u8, total > 0xFF));
                }
            }
        }
    }

    #[test]
    fn drill_adder16() {
        assert_eq!(adder16(1000, 2000), (3000, false));
        assert_eq!(adder16(0, 0), (0, false));
        assert_eq!(adder16(u16::MAX, 1), (0, true));
        assert_eq!(adder16(40000, 40000), (14464, true));
        for a in (0..=u16::MAX).step_by(257) {
            for b in (0..=u16::MAX).step_by(251) {
                assert_eq!(adder16(a, b), a.overflowing_add(b), "a={a} b={b}");
            }
        }
    }

    #[test]
    fn drill_increment() {
        assert_eq!(increment(0), (1, false));
        assert_eq!(increment(1), (2, false));
        assert_eq!(increment(127), (128, false));
        assert_eq!(increment(254), (255, false));
        assert_eq!(increment(255), (0, true));
        for x in 0..=u8::MAX {
            assert_eq!(increment(x), x.overflowing_add(1));
        }
    }
}

#[cfg(test)]
mod block3_applied {
    use super::*;

    #[test]
    fn drill_overflowed() {
        assert!(!overflowed(0, 0));
        assert!(!overflowed(200, 55));
        assert!(overflowed(200, 56));
        assert!(overflowed(255, 1));
        assert!(!overflowed(128, 127));
        assert!(overflowed(128, 128));
        for a in 0..=u8::MAX {
            for b in 0..=u8::MAX {
                assert_eq!(overflowed(a, b), a.overflowing_add(b).1, "a={a} b={b}");
            }
        }
    }

    #[test]
    fn drill_add_three() {
        assert_eq!(add_three(0, 0, 0), 0);
        assert_eq!(add_three(1, 2, 3), 6);
        assert_eq!(add_three(255, 1, 0), 256);
        assert_eq!(add_three(255, 255, 255), 765);
        assert_eq!(add_three(100, 100, 100), 300);
    }

    #[test]
    fn drill_negate() {
        assert_eq!(negate(0), 0);
        assert_eq!(negate(1), 255);
        assert_eq!(negate(5), 251);
        assert_eq!(negate(128), 128);
        assert_eq!(negate(255), 1);
        for x in 0..=u8::MAX {
            assert_eq!(negate(x), 0u8.wrapping_sub(x), "x={x}");
        }
    }

    #[test]
    fn drill_sub8() {
        assert_eq!(sub8(5, 3), (2, false));
        assert_eq!(sub8(3, 5), (254, true));
        assert_eq!(sub8(0, 0), (0, false));
        assert_eq!(sub8(0, 1), (255, true));
        assert_eq!(sub8(255, 1), (254, false));
        for a in 0..=u8::MAX {
            for b in 0..=u8::MAX {
                assert_eq!(sub8(a, b), a.overflowing_sub(b), "a={a} b={b}");
            }
        }
    }
}
