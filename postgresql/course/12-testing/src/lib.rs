//! Общая библиотека модуля 12 «Тестирование».
//!
//! Чистые функции (тестируются юнит- и property-тестами) + подключение
//! упражнений. Реальный PostgreSQL в тестах — через testcontainers.

use rust_decimal::{Decimal, RoundingStrategy};

// ============================================================
// Кейс 3: масса нетто — decimal и f64 (накопление ошибки).
// Формула (модуль 04): m_н = m_бр × (1 − (W + X)/100).
// ============================================================

pub const DECIMAL_PRECISION: u32 = 4;

/// Масса нетто на `Decimal` — точная (модуль 04, решение упражнения).
pub fn massa_netto(gross: Decimal, water: Decimal, sediment: Decimal) -> anyhow::Result<Decimal> {
    let hundred = Decimal::from(100u32);
    if gross < Decimal::ZERO {
        anyhow::bail!("масса брутто отрицательна: {gross}");
    }
    if !(Decimal::ZERO..hundred).contains(&water) || !(Decimal::ZERO..hundred).contains(&sediment) {
        anyhow::bail!("вода/примеси вне [0,100)");
    }
    if water + sediment >= hundred {
        anyhow::bail!("вода+примеси >= 100%");
    }
    let factor = (hundred - (water + sediment)) / hundred;
    Ok((gross * factor)
        .round_dp_with_strategy(DECIMAL_PRECISION, RoundingStrategy::MidpointAwayFromZero))
}

/// Тот же расчёт на `f64` — для сравнения точности (это НЕ рекомендуемый путь).
pub fn massa_netto_f64(gross: f64, water: f64, sediment: f64) -> anyhow::Result<f64> {
    if gross < 0.0 || !(0.0..100.0).contains(&water) || !(0.0..100.0).contains(&sediment) {
        anyhow::bail!("неверный вход");
    }
    Ok(gross * (1.0 - (water + sediment) / 100.0))
}

// --- Упражнения ---
#[path = "../exercises/src/ex01_massa.rs"]
pub mod ex01_massa;

#[path = "../exercises/src/ex02_fixtures.rs"]
pub mod ex02_fixtures;

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::Decimal;
    use std::str::FromStr;

    fn de(s: &str) -> Decimal {
        Decimal::from_str(s).expect("ok")
    }

    #[test]
    fn massa_netto_known_value() {
        // 1000 × (1 − 0.0055) = 994.5
        assert_eq!(
            massa_netto(de("1000.00"), de("0.50"), de("0.05")).unwrap(),
            de("994.5000")
        );
    }

    #[test]
    fn ex01_netto_validation() {
        assert!(massa_netto(de("-1"), Decimal::ZERO, Decimal::ZERO).is_err());
        assert!(massa_netto(de("1"), de("99.5"), de("0.6")).is_err());
        assert!(massa_netto(de("1"), de("100"), Decimal::ZERO).is_err());
    }

    // --- интеграция: миграции/схема/функция проверяются в tests/ (testcontainers),
    //     а не в юнит-тестах ---
}
