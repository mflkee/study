//! Пример 03: маппинг типов PostgreSQL → Rust в sqlx.
//!
//! Запуск: `cargo run --example 03-mapping-types`
//!
//! Таблица соответствий (важная для практики):
//! - `int4` (serial/int)   → `i32`
//! - `int8` (bigserial)    → `i64`
//! - `text`                → `String`
//! - `timestamptz`         → `chrono::DateTime<chrono::Utc>`   (фича sqlx "chrono")
//! - `numeric(20,6)`       → `rust_decimal::Decimal`           (фича sqlx "rust_decimal")
//! - `int` (quality)       → `i32`
//! - NULL-колонка          → `Option<T>`
//!
//! Структура с деривацией `sqlx::FromRow` мапится по именам колонок.

use anyhow::Result;
use chrono::{DateTime, Utc};
use pg_course_module_04::{new_pool, Measurement};
use rust_decimal::Decimal;
use sqlx::FromRow;
use std::str::FromStr;

/// Строка «измерение + тег устройства» — колонки с алиасами совпадают с полями.
#[derive(Debug, FromRow)]
struct RichRow {
    id: i64,
    tag: String,
    ts: DateTime<Utc>, // timestamptz
    value: Decimal,    // numeric(20,6)
    quality: i32,      // int
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // 1. Читаем: значения из PG в типизированные поля.
    let rows: Vec<RichRow> = sqlx::query_as(
        "SELECT m.id, d.tag, m.ts, m.value, m.quality
           FROM measurements m
           JOIN devices d ON d.id = m.device_id
          ORDER BY m.ts DESC
          LIMIT 5",
    )
    .fetch_all(&pool)
    .await?;

    for r in &rows {
        println!(
            "id={:<3} tag={:<8} ts={} value={} quality={}",
            r.id, r.tag, r.ts, r.value, r.quality
        );
    }

    // 2. Пишем: INSERT ... RETURNING, параметры через bind.
    //    Вставленную строку тут же удаляем, чтобы не засорять сид.
    let inserted: Measurement = sqlx::query_as(
        "INSERT INTO measurements (device_id, ts, value, quality)
         VALUES ($1, $2, $3, $4)
         RETURNING id, device_id, ts, value, quality",
    )
    .bind(1i32) // device_id — массомер ЛИНИИ-1
    .bind(Utc::now()) // timestamptz
    .bind(Decimal::from_str("42.500000")?) // numeric(20,6)
    .bind(0i32) // quality = good
    .fetch_one(&pool)
    .await?;

    println!("вставлено: {inserted:?}");

    sqlx::query("DELETE FROM measurements WHERE id = $1")
        .bind(inserted.id)
        .execute(&pool)
        .await?;
    println!("удалено (сид не засорён)");

    Ok(())
}
