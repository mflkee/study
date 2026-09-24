//! Пример 04: сериализация телеметрии в JSON (serde).
//!
//! Запуск: `cargo run --example 04-serde-telemetry`
//!
//! Сценарий СИКН: снять последние точки по всем устройствам и отдать
//! их в виде JSON (файл выгрузки, REST-ответ — модуль 11).
//! Точность: `Decimal` сериализуется так, чтобы значение не «поплыло»
//! (в отличие от f64, где 1000.1 превращается в длинную мантиссу).

use anyhow::Result;
use chrono::{DateTime, Utc};
use pg_course_module_04::new_pool;
use rust_decimal::Decimal;
use serde::Serialize;

/// Точка телеметрии: маппится из строки PG (FromRow) и сериализуется (Serialize).
#[derive(Debug, sqlx::FromRow, Serialize)]
struct TelemetryPoint {
    tag: String,
    ts: DateTime<Utc>,
    value: Decimal,
    quality: i32,
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    let points: Vec<TelemetryPoint> = sqlx::query_as(
        "SELECT d.tag, m.ts, m.value, m.quality
           FROM measurements m
           JOIN devices d ON d.id = m.device_id
          WHERE m.ts >= now() - interval '2 days'
          ORDER BY m.ts
          LIMIT 6",
    )
    .fetch_all(&pool)
    .await?;

    let json = serde_json::to_string_pretty(&points)?;
    println!("{json}");

    Ok(())
}
