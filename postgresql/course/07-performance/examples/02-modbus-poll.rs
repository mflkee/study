//! Пример 02: шлюз — опрос Modbus-устройства каждые 500 мс, запись в PG.
//!
//! Запуск (в двух терминалах):
//!   терминал 1: cargo run --example 01-modbus-emulator
//!   терминал 2: cargo run --example 02-modbus-poll -- --count 10
//!
//! Опции: --addr 127.0.0.1:15502 · --count N (лимит опросов) ·
//!         --interval-ms 500 · --mode row|batch|copy (способ записи, урок 03)
//!
//! Запись идемпотентна: UNIQUE(device_id, seq) + ON CONFLICT DO NOTHING
//! (кейс 8 — повторная доставка без дублей; модуль 10).

use anyhow::Result;
use chrono::{DateTime, Utc};
use pg_course_module_07::new_pool;
use rust_decimal::Decimal;
use sqlx::PgPool;
use std::net::SocketAddr;
use tokio::time::{interval, Duration, Instant};
use tokio_modbus::prelude::*;

fn arg_after(flag: &str) -> Option<String> {
    let args: Vec<String> = std::env::args().collect();
    args.iter()
        .position(|a| a == flag)
        .and_then(|i| args.get(i + 1))
        .cloned()
}

#[tokio::main]
async fn main() -> Result<()> {
    let addr: SocketAddr = arg_after("--addr")
        .unwrap_or_else(|| "127.0.0.1:15502".to_owned())
        .parse()?;
    let count: Option<u32> = arg_after("--count").and_then(|s| s.parse().ok());
    let interval_ms: u64 = arg_after("--interval-ms")
        .and_then(|s| s.parse().ok())
        .unwrap_or(500);
    let mode: String = arg_after("--mode").unwrap_or_else(|| "row".to_owned());

    let pool = new_pool(5).await?;
    let mut ctx = tokio_modbus::client::tcp::connect(addr).await?;
    println!(
        "шлюз: опрос {addr} раз в {interval_ms} мс, способ записи: {mode}, приём {count:?} показаний"
    );

    let mut seq: u32 = 0;
    let mut ticks = interval(Duration::from_millis(interval_ms));
    loop {
        ticks.tick().await;
        let t0 = Instant::now();
        // Чтение 4 holding-регистров (двойной Result: транспорт / Modbus-exception).
        let raw: Vec<u16> = ctx.read_holding_registers(0, 4).await??;
        let value = Decimal::from(raw[0]); // «масса»
        let ts = Utc::now();

        match mode.as_str() {
            "row" => ingest_row(&pool, 1, seq, ts, value).await?,
            "batch" => ingest_batch(&pool, 1, seq, ts, value).await?,
            "copy" => ingest_copy(&pool, 1, seq, ts, value).await?,
            other => anyhow::bail!("неизвестный режим {other:?} (row|batch|copy)"),
        }
        println!(
            "seq={seq:>3} mass={value} ts={ts} запись за {:?}",
            t0.elapsed()
        );
        seq += 1;
        if count.is_some_and(|c| seq >= c) {
            break;
        }
    }
    let n: i64 = sqlx::query_scalar("SELECT count(*) FROM telemetry_raw")
        .fetch_one(&pool)
        .await?;
    println!("шлюз завершился: в telemetry_raw {n} строк");
    Ok(())
}

/// Построчная вставка (модуль 04) — медленный эталон для сравнения.
async fn ingest_row(
    pool: &PgPool,
    device_id: i32,
    seq: u32,
    ts: DateTime<Utc>,
    value: Decimal,
) -> Result<()> {
    sqlx::query(
        "INSERT INTO telemetry_raw (device_id, seq, ts, value)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (device_id, seq) DO NOTHING",
    )
    .bind(device_id)
    .bind(seq as i32)
    .bind(ts)
    .bind(value)
    .execute(pool)
    .await?;
    Ok(())
}

/// Батч через UNNEST — для одной точки не нужен, показан для полноты API.
async fn ingest_batch(
    pool: &PgPool,
    device_id: i32,
    seq: u32,
    ts: DateTime<Utc>,
    value: Decimal,
) -> Result<()> {
    sqlx::query(
        "INSERT INTO telemetry_raw (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::int[], $3::timestamptz[], $4::numeric[])
         ON CONFLICT (device_id, seq) DO NOTHING",
    )
    .bind(vec![device_id])
    .bind(vec![seq as i32])
    .bind(vec![ts])
    .bind(vec![value])
    .execute(pool)
    .await?;
    Ok(())
}

/// COPY одной строки (в текстовом формате) — для шлюза тоже избыточно,
/// но показывает путь «строй строку протокола» (урок 03).
async fn ingest_copy(
    pool: &PgPool,
    device_id: i32,
    seq: u32,
    ts: DateTime<Utc>,
    value: Decimal,
) -> Result<()> {
    let mut conn = pool.acquire().await?;
    let mut copy = conn
        .copy_in_raw("COPY telemetry_raw (device_id, seq, ts, value) FROM STDIN WITH (FORMAT csv)")
        .await?;
    let line = format!("{device_id},{seq},{ts},{value}\n");
    copy.send(line.as_bytes()).await?;
    copy.finish().await?;
    Ok(())
}

// Задел урока 03: точный формат строки COPY.
/// Сериализация точки в строку CSV COPY (все 4 колонки).
pub fn copy_line(pt: &pg_course_module_07::TelemetryPoint) -> String {
    format!("{},{},{},{}\n", pt.device_id, pt.seq, pt.ts, pt.value)
}
