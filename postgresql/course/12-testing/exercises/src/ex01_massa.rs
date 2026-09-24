//! Упражнение 01: «Поточность vs f64» — заготовка для property-тестов (кейс 3).
//!
//! **Кейс:** 3 (точность расчётов). **Модуль:** 12. **Время:** 1.5 ч.
//!
//! ТЗ: property-тесты (proptest) проверяют инварианты массы нетто на МИЛЛИОНАХ
//! случайных входов. Для этого нужны две чисто функции: точная (`Decimal`) и
//! «наивная» (`f64`). Реализуй их по формуле и собери `netto_value` — точку с
//! разницей между подходами (для демонстрации накопления ошибки).
//!
//! Проверка: `cargo test --test property` + юнит-тесты в lib.
//!
//! Решение — в `solutions/ex01_massa.rs`.

use rust_decimal::Decimal;

/// Масса нетто на `Decimal` (адаптер; эталон — в `src/lib.rs::massa_netto`).
pub fn netto_decimal(
    _gross: Decimal,
    _water: Decimal,
    _sediment: Decimal,
) -> anyhow::Result<Decimal> {
    todo!("тот же расчёт, что в lib::massa_netto (или вызови его)")
}

/// Масса нетто на `f64` — демонстрация потери точности (не для проде!).
pub fn netto_f64(_gross: f64, _water: f64, _sediment: f64) -> anyhow::Result<f64> {
    todo!("gross * (1 − (water+sediment)/100) с валидацией")
}

/// Разница между подходами для конкретного входа (модуль |decimal − f64|).
pub fn diff_on(_gross_d: Decimal, _water_d: Decimal, _sed_d: Decimal) -> f64 {
    todo!("|netto_decimal(Decimal) − netto_f64(f64)| — как f64")
}
