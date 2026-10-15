//! Ступень 4. Триггеры и регистры — ЗАДАНИЕ.
//!
//!   Запуск: cargo run  --bin step4-task
//!   Тесты:  cargo test --bin step4-task
//!   Разбор: cargo run  --bin step4-answer
//!   Теория: step4-flipflop/LECTURE.md
//!
//! Пока в телах стоят `todo!()`, компилятор считает параметры неиспользованными —
//! глушим этот шум, чтобы ты видел только свои ошибки.

#![allow(dead_code, unused_variables)]

/// RS-триггер на двух NOR-гейтах. Хранит один бит.
struct SrLatch {
    q: bool,
    nq: bool,
}

impl SrLatch {
    fn new() -> Self {
        // Исходное состояние: Q = 0, Q̄ = 1.
        Self { q: false, nq: true }
    }

    /// S — set, R — reset. S = R = true запрещено.
    fn set(&mut self, s: bool, r: bool) {
        todo!("реализуй сам")
    }
}

/// D-защёлка: запоминает вход D, пока активен `enable`.
struct DLatch {
    q: bool,
}

impl DLatch {
    fn new() -> Self {
        Self { q: false }
    }

    /// Пока `enable` активен — запомни `d`, иначе держи прежнее значение.
    fn clock(&mut self, d: bool, enable: bool) {
        todo!("реализуй сам")
    }

    fn q(&self) -> bool {
        self.q
    }
}

/// 8-битный сдвиговый регистр.
struct ShiftReg {
    bits: u8,
}

impl ShiftReg {
    fn new() -> Self {
        Self { bits: 0 }
    }

    /// Сдвиг влево на 1, новый бит `din` вдвигается в младший разряд.
    fn clock(&mut self, din: bool) {
        todo!("реализуй сам")
    }

    fn value(&self) -> u8 {
        self.bits
    }
}

fn main() {
    println!("RS-триггер:");
    let mut sr = SrLatch::new();
    println!("  start            q = {}, nq = {}", sr.q, sr.nq);
    sr.set(true, false);
    println!("  set(S=1,R=0)     q = {}, nq = {}", sr.q, sr.nq);
    sr.set(false, false);
    println!("  hold(S=0,R=0)    q = {}, nq = {}", sr.q, sr.nq);
    sr.set(false, true);
    println!("  reset(S=0,R=1)   q = {}, nq = {}", sr.q, sr.nq);
    sr.set(false, false);
    println!("  hold(S=0,R=0)    q = {}, nq = {}", sr.q, sr.nq);

    println!("\nD-защёлка:");
    let mut d = DLatch::new();
    d.clock(true, false);
    println!("  d=1 enable=0     q = {} (не записалось)", d.q());
    d.clock(true, true);
    println!("  d=1 enable=1     q = {}", d.q());
    d.clock(false, false);
    println!("  d=0 enable=0     q = {} (держит 1)", d.q());

    println!("\nСдвиговый регистр (вдвигаем 1,0,1,1):");
    let mut sh = ShiftReg::new();
    for bit in [true, false, true, true] {
        sh.clock(bit);
        println!("  in={} -> {:#010b}", bit as u8, sh.value());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sr_holds_and_sets() {
        let mut sr = SrLatch::new();
        sr.set(true, false);
        assert!(sr.q && !sr.nq);
        sr.set(false, false);
        assert!(sr.q && !sr.nq); // держит
        sr.set(false, true);
        assert!(!sr.q && sr.nq);
        sr.set(false, false);
        assert!(!sr.q && sr.nq); // держит
    }

    #[test]
    fn sr_forbidden_state() {
        let mut sr = SrLatch::new();
        sr.set(true, true);
        assert!(!sr.q && !sr.nq); // S=R=1 -> оба выхода 0
    }

    #[test]
    fn d_latch_only_on_enable() {
        let mut d = DLatch::new();
        d.clock(true, false);
        assert!(!d.q());
        d.clock(true, true);
        assert!(d.q());
        d.clock(false, false);
        assert!(d.q()); // держит
    }

    #[test]
    fn shift_register() {
        let mut sh = ShiftReg::new();
        for bit in [true, false, true, true] {
            sh.clock(bit);
        }
        // вдвинули 1,0,1,1 -> младшие 4 бита 0b1011
        assert_eq!(sh.value(), 0b1011);
    }
}
