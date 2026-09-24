//! Лаба 01: «Считать из PG → посчитать → записать обратно» (решение).
//! Скопируйте содержимое в `examples/07-netto.rs` после своей попытки.

use anyhow::{Context, Result};
use chrono::{DateTime, Utc};
use pg_course_module_04::ex02_massa_netto::massa_netto;
use pg_course_module_04::new_pool;
use rust_decimal::Decimal;
use sqlx::FromRow;
use sqlx::PgPool;

/// Сырое измерение массомера: время и масса брутто (кг/ч).
#[derive(Debug, FromRow)]
struct RawReading {
    ts: DateTime<Utc>,
    gross: Decimal,
}

const WATER_PCT: &str = "0.50";
const SEDIMENT_PCT: &str = "0.05";
const DEVICE_TAG: &str = "M-01-001";

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // 1. Результат лабы — отдельная таблица (в проде — миграции, модуль 06).
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS derived_measurements (
             id        bigserial PRIMARY KEY,
             device_id int NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
             ts        timestamptz NOT NULL,
             gross     numeric(20,6) NOT NULL,
             water     numeric(8,4)  NOT NULL,
             sediment  numeric(8,4)  NOT NULL,
             netto     numeric(20,6) NOT NULL
         )",
    )
    .execute(&pool)
    .await?;
    sqlx::query("TRUNCATE derived_measurements RESTART IDENTITY CASCADE")
        .execute(&pool)
        .await?;

    println!("устройство: {DEVICE_TAG} (последние сутки по данным сида)");

    // 2. Чтение: масса брутто массомера за «последние сутки по данным сида».
    let readings = fetch_readings(&pool).await?;
    println!("считано измерений: {}", readings.len());
    for r in &readings {
        println!("  {}  {:<12} кг/ч", r.ts, r.gross);
    }

    // 3. Расчёт массы нетто для каждого чтения + 4. запись.
    let water = rust_decimal::Decimal::from_str_exact(WATER_PCT)?;
    let sediment = rust_decimal::Decimal::from_str_exact(SEDIMENT_PCT)?;

    let mut written = 0u64;
    for r in &readings {
        let netto = massa_netto(r.gross, water, sediment)
            .with_context(|| format!("расчёт нетто для {}", r.ts))?;
        written += write_derived(&pool, r.ts, r.gross, water, sediment, netto).await?;
    }

    println!("записано строк: {written}");
    Ok(())
}

/// Чтение сырых измерений массомера за последние сутки (по max(ts) сида).
async fn fetch_readings(pool: &PgPool) -> Result<Vec<RawReading>> {
    let readings: Vec<RawReading> = sqlx::query_as(
        "SELECT m.ts, m.value AS gross
           FROM measurements m
           JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1
            AND m.ts >= (SELECT max(ts) FROM measurements) - interval '24 hours'
          ORDER BY m.ts",
    )
    .bind(DEVICE_TAG)
    .fetch_all(pool)
    .await?;
    Ok(readings)
}

/// Запись одной строки результата; возвращает число записанных строк.
async fn write_derived(
    pool: &PgPool,
    ts: DateTime<Utc>,
    gross: Decimal,
    water: Decimal,
    sediment: Decimal,
    netto: Decimal,
) -> Result<u64> {
    let res = sqlx::query(
        "INSERT INTO derived_measurements (device_id, ts, gross, water, sediment, netto)
         VALUES ((SELECT id FROM devices WHERE tag = $1), $2, $3, $4, $5, $6)",
    )
    .bind(DEVICE_TAG)
    .bind(ts)
    .bind(gross)
    .bind(water)
    .bind(sediment)
    .bind(netto)
    .execute(pool)
    .await?;
    Ok(res.rows_affected())
}
