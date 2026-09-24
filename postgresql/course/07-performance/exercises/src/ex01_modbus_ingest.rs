//! Упражнение 01: «Приём телеметрии Modbus» — три способа записи.
//!
//! **Кейс:** 2 (телеметрия). **Модуль:** 07. **Время:** 2.5 ч.
//!
//! ТЗ: шлюз прочитал массив показаний (пример 02/03) и должен записать их в
//! `telemetry_raw` как можно быстрее. Реализуй три метода записи (модуль 04,
//! урок 03):
//!
//! 1. `ingest_row` — построчно: цикл с `INSERT … ON CONFLICT (device_id, seq) DO NOTHING`;
//! 2. `ingest_batch` — одним запросом через `UNNEST($n::тип[], …)` (урок 03);
//! 3. `ingest_copy` — `COPY table FROM STDIN` (текстовый CSV) через
//!    `copy_in_raw` (пример 02).
//!
//! Плюс `copy_line` (CSV-строка) и `summarize` (отчёт замера).
//!
//! Проверка: `cargo test ex01_` — тесты сверят три метода по числу строк
//! (все записывают одно и то же) и формат CSV-строки.
//!
//! Решение — в `solutions/ex01_modbus_ingest.rs` (после своей попытки).

use crate::TelemetryPoint;
use anyhow::Result;
use sqlx::PgPool;

/// CSV-строка для COPY: `device_id,seq,ts,value` + перевод строки.
/// Точность numeric сохраняется за счёт Decimal ("1000.5", без потерь).
pub fn copy_line(_pt: &TelemetryPoint) -> String {
    todo!("format! с device_id, seq, ts, value (см. урок 03)")
}

/// Построчная вставка (цикл); возвращает число записанных строк.
pub async fn ingest_row(_pool: &PgPool, _pts: &[TelemetryPoint]) -> Result<u64> {
    todo!("цикл по точкам: INSERT … ON CONFLICT (device_id, seq) DO NOTHING")
}

/// Батч: один INSERT + UNNEST; возвращает число записанных строк.
pub async fn ingest_batch(_pool: &PgPool, _pts: &[TelemetryPoint]) -> Result<u64> {
    todo!("INSERT … SELECT * FROM UNNEST(…) ON CONFLICT DO NOTHING")
}

/// COPY FROM STDIN (CSV); возвращает число строк (rows_affected от finish).
pub async fn ingest_copy(_pool: &PgPool, _pts: &[TelemetryPoint]) -> Result<u64> {
    todo!("copy_in_raw + send(все строки) + finish → rows_affected")
}

/// Одна строка отчёта замера: `{метод}: {n} строк за {secs:.1} с ({tps:.0} строк/с)`.
pub fn summarize(_method: &str, _n: u64, _elapsed: std::time::Duration) -> String {
    todo!("формат отчёта, см. тест ex01_summarize")
}
