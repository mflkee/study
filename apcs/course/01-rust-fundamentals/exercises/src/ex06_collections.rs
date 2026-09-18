//! Упражнение 06: Collections + Iterators.
//!
//! Задание: поработайте с BTreeMap (карта регистров Modbus) и напишите
//! pipeline на итераторах (как в эмуляторе и crc-коде).

use std::collections::BTreeMap;

/// # Задание 1
/// Реализуйте поиск самого старого адреса с минимальным значением
/// в карте регистров (адреса — u16, значения — u16).
pub fn min_value_address(regs: &BTreeMap<u16, u16>) -> Option<u16> {
    regs.iter()
        .min_by_key(|(_, &value)| value)
        .map(|(&addr, _)| addr)
}

/// # Задание 2
/// Посчитать среднее значение регистров (целое, с округлением вниз).
pub fn avg_value(regs: &BTreeMap<u16, u16>) -> u16 {
    if regs.is_empty() {
        return 0;
    }
    let sum: u64 = regs.values().map(|&v| v as u64).sum();
    (sum / regs.len() as u64) as u16
}

/// # Скарбистый пример
/// Скопировать только адреса, где значение больше порога, — Vec<u16>.
pub fn addresses_above(regs: &BTreeMap<u16, u16>, threshold: u16) -> Vec<u16> {
    regs.iter()
        .filter(|(_, &value)| value > threshold)
        .map(|(&addr, _)| addr)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> BTreeMap<u16, u16> {
        BTreeMap::from([
            (0u16, 100u16),
            (1u16, 3u16),
            (2u16, 40u16),
        ])
    }

    #[test]
    fn min_address_is_1() {
        assert_eq!(min_value_address(&sample()), Some(1));
    }

    #[test]
    fn avg_is_47() {
        assert_eq!(avg_value(&sample()), 47); // (100+3+40)/3 = 47
    }

    #[test]
    fn empty_map_is_safe() {
        let empty = BTreeMap::new();
        assert_eq!(min_value_address(&empty), None);
        assert_eq!(avg_value(&empty), 0);
        assert_eq!(addresses_above(&empty, 0), vec![]);
    }

    #[test]
    fn above_threshold() {
        assert_eq!(addresses_above(&sample(), 10), vec![0, 2]);
    }
}