//! Ступень 4. Триггеры и регистры — РАЗБОР.
//!
//!   Запуск: cargo run  --bin step4-answer
//!   Тесты:  cargo test --bin step4-answer

/// RS-триггер на двух NOR-гейтах. Хранит один бит.
struct SrLatch {
    q: bool,
    nq: bool,
}

impl SrLatch {
    fn new() -> Self {
        Self { q: false, nq: true }
    }

    /// S — set, R — reset. S = R = true запрещено (оба выхода → 0).
    ///
    /// Два NOR-гейта с обратной связью:
    ///   Q  = !(R | Q̄)
    ///   Q̄  = !(S | Q)
    /// В железе это «устаканивается» за пару проходов. Гоняем цикл, пока
    /// значения не перестанут меняться.
    fn set(&mut self, s: bool, r: bool) {
        let (mut q, mut nq) = (self.q, self.nq);
        loop {
            let next_q = !(r | nq);
            let next_nq = !(s | q);
            if next_q == q && next_nq == nq {
                break;
            }
            q = next_q;
            nq = next_nq;
        }
        self.q = q;
        self.nq = nq;
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

    fn clock(&mut self, d: bool, enable: bool) {
        if enable {
            self.q = d;
        } // иначе держим прежнее значение
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

    /// Сдвиг влево на 1, новый бит в младший разряд. Старший бит «выпадает».
    fn clock(&mut self, din: bool) {
        self.bits = (self.bits << 1) | din as u8;
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
        assert_eq!(sh.value(), 0b1011);
    }
}
