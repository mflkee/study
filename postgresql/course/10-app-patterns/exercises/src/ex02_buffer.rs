//! Упражнение 02: «Буферизация при потере связи» — доставка без дублей (кейс 8).
//!
//! **Кейс:** 8 (буферизация шлюза). **Модуль:** 10. **Время:** 2 ч.
//!
//! ТЗ: шлюз копит показания в локальном файле (буфер), когда PG недоступен;
//! при восстановлении — доставляет в `metering_points` (источник правды Буффер ->
//! таблица) с идемпотентностью через `UNIQUE (device_id, seq)` +
//! `ON CONFLICT DO NOTHING` (повторная доставка не создаёт дубли).
//!
//! Реализуй:
//! 1. `to_line` / `parse_lines` — сериализация точки в строку файла и обратно;
//! 2. `deliver` — батч-вставка с ON CONFLICT DO NOTHING (возвращает СКОЛЬКО
//!    НОВЫХ строк вставлено).
//!
//! Проверка: `cargo test ex02_` (round-trip строки + повторная доставка → 0 дублей).
//!
//! Решение — в `solutions/ex02_buffer.rs`.

use crate::MeteringPoint;
use anyhow::Result;
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use sqlx::PgPool;

/// Точка, прочитанная из буфера (device_id, seq, ts, value).
pub type BufferPoint = (i32, i64, DateTime<Utc>, Decimal);

/// Строка буфера: `device_id\tseq\tts\tvalue\n` (ts — RFC3339).
pub fn to_line(_pt: &MeteringPoint) -> String {
    todo!("TSV-строка")
}

/// Разбор строк буфера в точки (пропускает пустые строки и комментарии `#`).
pub fn parse_lines(_data: &str) -> Result<Vec<BufferPoint>> {
    todo!("split по строкам/табам, парсинг каждого поля")
}

/// Доставка пачки в PG: INSERT ... ON CONFLICT (device_id, seq) DO NOTHING
/// (батч ч/з UNNEST — модуль 07); возвращает число НОВЫХ строк.
pub async fn deliver(_pool: &PgPool, _pts: &[MeteringPoint]) -> Result<u64> {
    todo!("UNNEST-батч с ON CONFLICT DO NOTHING → rows_affected")
}
