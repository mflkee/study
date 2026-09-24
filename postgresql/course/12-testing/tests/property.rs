//! Property-тесты массы нетто (кейс 3): proptest перебирает случайные входы
//! и проверяет ИНВАРИАНТЫ, а не конкретные числа.
//!
//! Запуск: `cargo test --test property`
//!
//! ВАЖНО: в proptest можно либо `assert!` (паники ловятся и репортятся), либо
//! `prop_assert!` с возвратом `Result<(), TestCaseError>` — смешивать нельзя.
//! Здесь выбран стиль `assert!` → замыкание возвращает `()`.

use pg_course_module_12::ex01_massa::{diff_on, netto_f64};
use pg_course_module_12::massa_netto;
use proptest::prelude::*;
use rust_decimal::Decimal;
use std::str::FromStr;

/// Decimal с «десятками»: 1253 → "125.3" (подозрительные на float десятые).
fn dec_tenths(v: i64) -> Decimal {
    Decimal::from_str(&format!("{}.{}", v / 10, v % 10)).expect("decimal")
}

proptest! {
    // Инвариант 1: нетто неотрицательна и не превышает брутто (при воде/примесях >= 0).
    #[test]
    fn netto_bounded_and_non_negative(
        gross10 in 0..1_000_000i64,
        w10 in 0..1_000i64,
        s10 in 0..1_000i64,
    ) {
        let w = dec_tenths(w10);
        let s = dec_tenths(s10);
        let valid = w + s < Decimal::from(100u32);
        if valid {
            let g = dec_tenths(gross10);
            if let Ok(n) = massa_netto(g, w, s) {
                assert!(n >= Decimal::ZERO, "нетто неотрицательна: {n}");
                assert!(n <= g, "нетто <= брутто: {n} > {g}");
            }
        }
    }

    // Инвариант 2: f64-версия корректно валидирует неверные входы (без NaN/паники).
    #[test]
    fn f64_validates_bad_input(gross in -1000.0f64..1000.0f64, w in -5.0f64..105.0f64) {
        match netto_f64(gross, w, 1.0) {
            Ok(n) => assert!(n.is_finite() && n >= 0.0, "f64-нетто неконечна: {n}"),
            Err(_) => { /* валидное отклонение входа */ }
        }
    }

    // Инвариант 3: decimal-версия никогда не отдаёт не-конечные значения.
    #[test]
    fn decimal_always_finite(gross10 in 0..1_000_000i64, w10 in 0..1_000i64, s10 in 0..1_000i64) {
        let g = dec_tenths(gross10);
        let w = dec_tenths(w10);
        let s = dec_tenths(s10);
        if let Ok(n) = massa_netto(g, w, s) {
            let f: f64 = n.to_string().parse().unwrap_or(f64::NAN);
            assert!(f.is_finite(), "decimal → finite f64, получено {f}");
        }
    }
}

// --- детерминированная демонстрация «накопления ошибки f64» (не proptest) ---

#[test]
fn f64_accumulates_error_on_tenths() {
    // На десятых f64 теряет точность; ищем максимальное расхождение на сетке.
    let mut max_diff = 0.0f64;
    let mut worst = (String::new(), 0.0f64);
    for g in 1..100 {
        let base = format!("{g}.1"); // 1.1, 2.1 …
        let g_d = Decimal::from_str(&base).unwrap();
        for w in 0..50 {
            let w_d = Decimal::from_str(&format!("{}.{}", w, 7)).unwrap();
            let d = diff_on(g_d, w_d, Decimal::from_str("0.1").unwrap());
            if d > max_diff {
                max_diff = d;
                worst = (base.clone(), g as f64);
            }
        }
    }
    assert!(max_diff > 0.0, "f64 неточно на десятых: есть расхождение");
    eprintln!("максимальное расхождение f64 vs decimal: {max_diff} (при {worst:?})");
}
