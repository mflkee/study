//! Решение упражнения 02 «Масса нетто» (rust_decimal).
//! Чтобы применить: скопируйте содержимое в `exercises/src/ex02_massa_netto.rs`.

use anyhow::{anyhow, Result};
use rust_decimal::{Decimal, RoundingStrategy};

/// Масса нетто: `gross × (1 − (water + sediment) / 100)`,
/// округление до 4 знаков половиной от нуля.
pub fn massa_netto(gross: Decimal, water: Decimal, sediment: Decimal) -> Result<Decimal> {
    let hundred = Decimal::from(100u32);

    if gross < Decimal::ZERO {
        return Err(anyhow!("масса брутто не может быть отрицательной: {gross}"));
    }
    if !(Decimal::ZERO..hundred).contains(&water) {
        return Err(anyhow!("содержание воды вне диапазона [0, 100): {water}"));
    }
    if !(Decimal::ZERO..hundred).contains(&sediment) {
        return Err(anyhow!(
            "содержание примесей вне диапазона [0, 100): {sediment}"
        ));
    }

    let impurities = water + sediment;
    if impurities >= hundred {
        return Err(anyhow!("вода + примеси дают >= 100%: {water} + {sediment}"));
    }

    // (1 − (W + X) / 100) — точная десятичная арифметика, не float.
    let factor = (hundred - impurities) / hundred;
    let netto = gross * factor;

    // Половина от нуля (бывшее имя — RoundHalfUp, deprecated с 1.43).
    Ok(netto.round_dp_with_strategy(4, RoundingStrategy::MidpointAwayFromZero))
}
