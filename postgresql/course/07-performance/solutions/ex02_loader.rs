//! Решение упражнения 02 «Собственный генератор нагрузки».
//! Скопируйте содержимое в `exercises/src/ex02_loader.rs` после попытки.

/// Операций в секунду: `ops / secs` (0, если secs <= 0).
pub fn tps(ops: u64, secs: f64) -> f64 {
    if secs <= 0.0 {
        0.0
    } else {
        ops as f64 / secs
    }
}

/// Одна строка отчёта: `{label}: {ops} операций за {secs:.1} с = {tps:.0} TPS`.
pub fn report_line(label: &str, ops: u64, secs: f64) -> String {
    format!(
        "{label}: {ops} операций за {secs:.1} с = {:.0} TPS",
        tps(ops, secs)
    )
}
