//! Ступень 3. Сумматор из гейтов — РАЗБОР.
//!
//!   Запуск: cargo run  --bin step3-answer
//!   Тесты:  cargo test --bin step3-answer

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

/// Полусумматор: сумма = XOR, перенос = AND.
fn half_adder(a: bool, b: bool) -> (bool, bool) {
    (xor(a, b), and(a, b))
}

/// Полный сумматор: два полусумматора + OR для переносов.
fn full_adder(a: bool, b: bool, cin: bool) -> (bool, bool) {
    let (s1, c1) = half_adder(a, b);
    let (s2, c2) = half_adder(s1, cin);
    (s2, or(c1, c2))
}

/// 8-битный ripple-carry сумматор: (сумма, переполнение).
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
