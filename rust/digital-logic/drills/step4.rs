//! Тренажёр ступени 4 (триггеры и регистры). 10 упражнений, 3 блока. ЗАДАНИЕ.
//!
//!   Прогресс: cargo test --bin step4-drills
//!   Карта:    cargo run  --bin step4-drills
//!   Теория:   step4-flipflop/LECTURE.md
//!
//! Здесь всё построено на структурах: состояние лежит в полях, методы меняют
//! его через `&mut self`. Структуры и `new()` уже даны — реализуй только
//! методы, помеченные `todo!()`.
//!
//! Два типа памяти:
//!   * защёлка (latch) — «прозрачная»: управляется уровнем сигнала;
//!   * триггер (flip-flop) — срабатывает по **фронту**: важно, что было
//!     с сигналом такта раньше. Поэтому структура хранит ещё и прошлый такт.
//!
//! Фронт (rising edge) — это переход такта из `false` в `true`.

#![allow(dead_code, unused_variables)]

// ===== Блок 1. Триггеры ==================================================

/// RS-триггер на двух NAND-гейтах. В отличие от варианта на NOR, здесь входы
/// **инверсные (активные низким уровнем)**: `s == false` — «поставить»,
/// `r == false` — «сбросить», оба `true` — держит, оба `false` — запрещено
/// (тогда **оба** выхода становятся `true`).
///
/// Модель схемы (обратная связь, как в лекции):
///   `q  = NAND(s, nq)`
///   `nq = NAND(r, q)`
/// Считай новые значения из старых и повторяй, пока не перестанут меняться.
struct SrNandLatch {
    q: bool,
    nq: bool,
}

impl SrNandLatch {
    fn new() -> Self {
        Self { q: false, nq: true }
    }

    fn set(&mut self, s: bool, r: bool) {
        todo!("RS-триггер на NAND")
    }
}

/// D-триггер: запоминает `d` **по фронту** такта `clk`.
struct DFlipFlop {
    q: bool,
    clk: bool, // прошлое состояние такта
}

impl DFlipFlop {
    fn new() -> Self {
        Self { q: false, clk: false }
    }

    /// Если `clk` только что перешёл `false → true` — запомнить `d`.
    /// Иначе ничего не менять. Прошлый такт хранится в `self.clk`.
    fn clock(&mut self, d: bool, clk: bool) {
        todo!("D-триггер по фронту")
    }
}

/// T-триггер: по фронту такта переключает выход, если `t` истинно;
/// если `t` ложно — держит.
struct TFlipFlop {
    q: bool,
    clk: bool,
}

impl TFlipFlop {
    fn new() -> Self {
        Self { q: false, clk: false }
    }

    fn clock(&mut self, t: bool, clk: bool) {
        todo!("T-триггер по фронту")
    }
}

// ===== Блок 2. Регистры ==================================================

/// 8-битный счётчик: по фронту такта увеличивается на 1, если `enable`.
/// При переходе через 255 обнуляется (переноса наружу у него нет).
struct Counter8 {
    value: u8,
    clk: bool,
}

impl Counter8 {
    fn new() -> Self {
        Self { value: 0, clk: false }
    }

    fn clock(&mut self, enable: bool, clk: bool) {
        todo!("счётчик по фронту")
    }
}

/// Сдвиговый регистр вправо: биты уезжают в сторону младших позиций, а новый
/// бит `din` входит в **старший** разряд (бит 7).
/// Один вызов `clock` — один такт, отдельный сигнал такта не нужен.
struct ShiftRight {
    bits: u8,
}

impl ShiftRight {
    fn new() -> Self {
        Self { bits: 0 }
    }

    fn clock(&mut self, din: bool) {
        todo!("сдвиг вправо с вдвиганием в старший разряд")
    }
}

/// Регистр с кольцевым сдвигом: по фронту такта биты едут влево,
/// а вытесненный старший бит возвращается в младший разряд (не теряется).
struct RotateLeft {
    bits: u8,
    clk: bool,
}

impl RotateLeft {
    fn new() -> Self {
        Self {
            bits: 0b1000_0001,
            clk: false,
        }
    }

    fn clock(&mut self, clk: bool) {
        todo!("кольцевой сдвиг влево по фронту")
    }
}

/// Параллельный регистр: по фронту такта, если `load` истинно, целиком
/// принимает `data`.
struct LoadReg {
    value: u8,
    clk: bool,
}

impl LoadReg {
    fn new() -> Self {
        Self { value: 0, clk: false }
    }

    fn load(&mut self, data: u8, load: bool, clk: bool) {
        todo!("параллельная загрузка по фронту")
    }
}

// ===== Блок 3. Применение ================================================

/// Детектор фронта как чистая функция: было `prev`, стало `now`.
fn rising_edge(prev: bool, now: bool) -> bool {
    todo!("детектор фронта")
}

/// Принять последовательность битов в байт: первый бит — самый старший.
/// Если битов больше 8 — учитывай только первые 8.
fn serial_to_u8(bits: &[bool]) -> u8 {
    todo!("приём битов в байт")
}

/// Посчитать импульсы (фронты) в сигнале. Считать, что до начала записи
/// сигнал был `false`. Один импульс = одна последовательность `false → true`.
fn count_pulses(signal: &[bool]) -> u8 {
    todo!("подсчёт импульсов")
}

fn main() {
    println!("Тренажёр ступени 4. Проверка: cargo test --bin step4-drills\n");
    println!("Блок 1: SrNandLatch::set, DFlipFlop::clock, TFlipFlop::clock");
    println!("Блок 2: Counter8::clock, ShiftRight::clock, RotateLeft::clock, LoadReg::load");
    println!("Блок 3: rising_edge, serial_to_u8, count_pulses");
    println!("\nВсего 10 упражнений = 11 тестов.");
}

#[cfg(test)]
mod block1_triggers {
    use super::*;

    #[test]
    fn drill_sr_nand_latch_works() {
        let mut l = SrNandLatch::new();
        assert!(!l.q && l.nq); // исходное: Q=0, Q̄=1

        l.set(false, true); // s активен (0) → поставить
        assert!(l.q && !l.nq);

        l.set(true, true); // оба неактивны → держит
        assert!(l.q && !l.nq);

        l.set(true, false); // r активен (0) → сбросить
        assert!(!l.q && l.nq);

        l.set(true, true); // держит
        assert!(!l.q && l.nq);
    }

    #[test]
    fn drill_sr_nand_latch_forbidden() {
        let mut l = SrNandLatch::new();
        l.set(false, false); // оба активны → оба выхода 1
        assert!(l.q && l.nq);
    }

    #[test]
    fn drill_d_flip_flop() {
        let mut f = DFlipFlop::new();
        f.clock(true, false); // такт не менялся — не записывает
        assert!(!f.q);

        f.clock(true, true); // фронт → запомнили 1
        assert!(f.q);

        f.clock(false, true); // такт держится высоким — фронта нет
        assert!(f.q);

        f.clock(false, false); // спад — не записывает
        assert!(f.q);

        f.clock(false, true); // фронт → запомнили 0
        assert!(!f.q);

        f.clock(true, true); // фронта нет — держит 0
        assert!(!f.q);

        f.clock(true, false);
        f.clock(true, true); // фронт → снова 1
        assert!(f.q);
    }

    #[test]
    fn drill_t_flip_flop() {
        let mut t = TFlipFlop::new();
        t.clock(true, false); // фронта нет
        assert!(!t.q);

        t.clock(true, true); // фронт, t=1 → переключился
        assert!(t.q);

        t.clock(false, true); // фронта нет
        assert!(t.q);

        t.clock(false, false);
        t.clock(true, true); // фронт, t=1 → обратно
        assert!(!t.q);

        t.clock(true, true); // фронта нет
        assert!(!t.q);

        t.clock(true, false);
        t.clock(false, true); // фронт, но t=0 → держит
        assert!(!t.q);

        t.clock(false, false);
        t.clock(true, true); // фронт, t=1 → переключился
        assert!(t.q);
    }
}

#[cfg(test)]
mod block2_registers {
    use super::*;

    #[test]
    fn drill_counter8() {
        let mut c = Counter8::new();
        c.clock(true, false); // фронта нет
        assert_eq!(c.value, 0);

        c.clock(true, true); // фронт, enable → 1
        assert_eq!(c.value, 1);

        c.clock(true, true); // такт не менялся
        assert_eq!(c.value, 1);

        c.clock(false, false);
        c.clock(true, true); // фронт → 2
        assert_eq!(c.value, 2);

        c.clock(false, false);
        c.clock(false, true); // фронт, но enable=0 → стоит
        assert_eq!(c.value, 2);

        c.value = 255;
        c.clock(false, false);
        c.clock(true, true); // фронт → обнулился
        assert_eq!(c.value, 0);

        // три импульса — три единицы
        let mut c2 = Counter8::new();
        for _ in 0..3 {
            c2.clock(true, false);
            c2.clock(true, true);
        }
        assert_eq!(c2.value, 3);
    }

    #[test]
    fn drill_shift_right() {
        let mut s = ShiftRight::new();
        s.clock(true);
        assert_eq!(s.bits, 0b1000_0000);
        s.clock(false);
        assert_eq!(s.bits, 0b0100_0000);
        s.clock(true);
        assert_eq!(s.bits, 0b1010_0000);
        s.clock(true);
        assert_eq!(s.bits, 0b1101_0000);
    }

    #[test]
    fn drill_rotate_left() {
        let mut r = RotateLeft::new(); // 0b1000_0001
        r.clock(false); // фронта нет
        assert_eq!(r.bits, 0b1000_0001);

        r.clock(true); // фронт → старший бит завернулся в младший
        assert_eq!(r.bits, 0b0000_0011);

        r.clock(true); // фронта нет
        assert_eq!(r.bits, 0b0000_0011);

        r.clock(false);
        r.clock(true); // фронт → ещё поворот
        assert_eq!(r.bits, 0b0000_0110);
    }

    #[test]
    fn drill_load_reg() {
        let mut r = LoadReg::new();
        r.load(0xAB, true, false); // фронта нет
        assert_eq!(r.value, 0);

        r.load(0xAB, true, true); // фронт → загрузили
        assert_eq!(r.value, 0xAB);

        r.load(0xCD, true, true); // такт не менялся
        assert_eq!(r.value, 0xAB);

        r.load(0xCD, false, false);
        r.load(0xCD, true, true); // фронт → новая загрузка
        assert_eq!(r.value, 0xCD);

        r.load(0xEF, false, true); // фронт, но load=0 → держит
        assert_eq!(r.value, 0xCD);
    }
}

#[cfg(test)]
mod block3_applied {
    use super::*;

    #[test]
    fn drill_rising_edge() {
        assert!(rising_edge(false, true));
        assert!(!rising_edge(true, true));
        assert!(!rising_edge(true, false));
        assert!(!rising_edge(false, false));
    }

    #[test]
    fn drill_serial_to_u8() {
        assert_eq!(serial_to_u8(&[]), 0);
        assert_eq!(serial_to_u8(&[true]), 1);
        assert_eq!(serial_to_u8(&[true, false, true, true]), 0b1011);
        assert_eq!(serial_to_u8(&[true, true, true, true, true, true, true, true]), 0xFF);
        assert_eq!(serial_to_u8(&[false, false, false, false, false, false, false, true]), 1);
        // лишние биты игнорируются
        assert_eq!(
            serial_to_u8(&[false, false, false, false, false, false, false, false, true, true]),
            0
        );
    }

    #[test]
    fn drill_count_pulses() {
        assert_eq!(count_pulses(&[]), 0);
        assert_eq!(count_pulses(&[false]), 0);
        assert_eq!(count_pulses(&[true]), 1);
        assert_eq!(count_pulses(&[true, true, true]), 1);
        assert_eq!(count_pulses(&[false, false]), 0);
        assert_eq!(count_pulses(&[false, true]), 1);
        assert_eq!(count_pulses(&[true, false]), 1);
        assert_eq!(count_pulses(&[true, false, true]), 2);
        assert_eq!(count_pulses(&[false, false, true, true, false, true]), 2);
    }
}
