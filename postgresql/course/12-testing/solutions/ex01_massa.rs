//! Решение упражнения 01: «Поточность vs f64» — для property-тестов.
//! Скопируйте содержимое в `exercises/src/ex01_massa.rs` после попытки.

use crate::massa_netto as lib_netto;
use anyhow::Result;
use rust_decimal::Decimal;

/// Масса нетто на `Decimal` (обращение к эталонной функции библиотеки).
pub fn netto_decimal(gross: Decimal, water: Decimal, sediment: Decimal) -> Result<Decimal> {
    lib_netto(gross, water, sediment)
}

/// Масса нетто на `f64` — демонстрация потери точности (не для проде!).
pub fn netto_f64(gross: f64, water: f64, sediment: f64) -> Result<f64> {
    if gross < 0.0 || !(0.0..100.0).contains(&water) || !(0.0..100.0).contains(&sediment) {
        anyhow::bail!("неверный вход");
    }
    if water + sediment >= 100.0 {
        anyhow::bail!("вода+примеси >= 100%");
    }
    Ok(gross * (1.0 - (water + sediment) / 100.0))
}

/// Разница между подходами для конкретного входа (модуль |decimal − f64|).
pub fn diff_on(gross_d: Decimal, water_d: Decimal, sed_d: Decimal) -> f64 {
    match (
        netto_decimal(gross_d, water_d, sed_d),
        netto_f64(
            gross_d.to_string().parse::<f64>().unwrap_or(0.0),
            water_d.to_string().parse::<f64>().unwrap_or(0.0),
            sed_d.to_string().parse::<f64>().unwrap_or(0.0),
        ),
    ) {
        (Ok(d), Ok(f)) => (d.to_string().parse::<f64>().unwrap_or(0.0) - f).abs(),
        _ => 0.0,
    }
}
