//! Ступень 2. Битовые операции на u8 — РАЗБОР.
//!
//!   Запуск: cargo run  --bin step2-answer
//!   Тесты:  cargo test --bin step2-answer

fn get_bit(x: u8, i: u32) -> bool {
    (x >> i) & 1 == 1
}

fn set_bit(x: u8, i: u32) -> u8 {
    x | (1u8 << i)
}

fn clear_bit(x: u8, i: u32) -> u8 {
    x & !(1u8 << i)
}

fn flip_bit(x: u8, i: u32) -> u8 {
    x ^ (1u8 << i)
}

/// Чётность: XOR всех битов. Истина, если единиц нечётное число.
fn parity(x: u8) -> bool {
    let mut acc = 0u8;
    for i in 0..8 {
        acc ^= (x >> i) & 1;
    }
    acc == 1
}

/// Тот же смысл, но в функциональном стиле — на будущее:
/// свёртка (fold) по битам через `get_bit`.
#[allow(dead_code)]
fn parity_fold(x: u8) -> bool {
    (0..8).fold(false, |acc, i| acc ^ get_bit(x, i))
}

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
    println!(
        "parity(x)     = {} (count_ones = {})",
        parity(x),
        x.count_ones()
    );

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
            assert_eq!(parity_fold(x), x.count_ones() % 2 == 1);
        }
    }
}
