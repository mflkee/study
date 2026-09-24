//! Пример 03: сравнение способов записи — построчно vs batch (UNNEST) vs COPY.
//!
//! Запуск: `cargo run --example 03-insert-methods [--n 50000]`
//!
//! Одни и те же точки пишутся тремя способами (модуль 04, урок 03):
//! - `row`: цикл INSERT (N round-trip'ов);
//! - `batch`: один INSERT … SELECT * FROM UNNEST(…);
//! - `copy`: COPY FROM STDIN, текстовый CSV.
//!
//! Результат — таблица времени и скорость; тот же код — в упражнении 01.

use anyhow::Result;
use chrono::Utc;
use pg_course_module_07::{generate_points, new_pool, TelemetryPoint};
use rust_decimal::Decimal;
use sqlx::PgPool;
use std::time::Instant;

fn arg_after(flag: &str) -> Option<String> {
    let args: Vec<String> = std::env::args().collect();
    args.iter()
        .position(|a| a == flag)
        .and_then(|i| args.get(i + 1))
        .cloned()
}

#[tokio::main]
async fn main() -> Result<()> {
    let n: usize = arg_after("--n")
        .and_then(|s| s.parse().ok())
        .unwrap_or(50_000);
    let pool = new_pool(8).await?;
    let pts = generate_points(7, n, Utc::now());

    println!("сравнение на {n} точках (device_id=7, seq 0..{n}):\n");

    // Три способа записи, каждый на «чистой» таблице (TRUNCATE перед замером).
    let mut results = Vec::new();

    sqlx::query("TRUNCATE telemetry_raw").execute(&pool).await?;
    let t0 = Instant::now();
    let written = ingest_row(&pool, &pts).await?;
    results.push(("row", written, t0.elapsed()));

    sqlx::query("TRUNCATE telemetry_raw").execute(&pool).await?;
    let t0 = Instant::now();
    let written = ingest_batch(&pool, &pts).await?;
    results.push(("batch", written, t0.elapsed()));

    sqlx::query("TRUNCATE telemetry_raw").execute(&pool).await?;
    let t0 = Instant::now();
    let written = ingest_copy(&pool, &pts).await?;
    results.push(("copy", written, t0.elapsed()));

    println!(
        "{:<6} {:>10} {:>12} {:>14}",
        "метод", "строк", "время", "строк/с"
    );
    for (name, written, elapsed) in &results {
        let secs = elapsed.as_secs_f64();
        println!(
            "{name:<6} {written:>10} {secs:>9.3} с {:>12.0}",
            *written as f64 / secs.max(1e-6)
        );
    }

    let fastest = results
        .iter()
        .min_by(|a, b| a.2.cmp(&b.2))
        .expect("три метода");
    let row = &results[0];
    println!(
        "\nбыстрее всех: {} — {:.1}x против построчной записи",
        fastest.0,
        row.2.as_secs_f64() / fastest.2.as_secs_f64().max(1e-9)
    );
    Ok(())
}

async fn ingest_row(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let mut n = 0u64;
    for pt in pts {
        let res = sqlx::query(
            "INSERT INTO telemetry_raw (device_id, seq, ts, value)
             VALUES ($1, $2, $3, $4)
             ON CONFLICT (device_id, seq) DO NOTHING",
        )
        .bind(pt.device_id)
        .bind(pt.seq as i32)
        .bind(pt.ts)
        .bind(pt.value)
        .execute(pool)
        .await?;
        n += res.rows_affected();
    }
    Ok(n)
}

async fn ingest_batch(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let did: Vec<i32> = pts.iter().map(|p| p.device_id).collect();
    let seqs: Vec<i32> = pts.iter().map(|p| p.seq as i32).collect();
    let ts: Vec<_> = pts.iter().map(|p| p.ts).collect();
    let vals: Vec<Decimal> = pts.iter().map(|p| p.value).collect();
    let res = sqlx::query(
        "INSERT INTO telemetry_raw (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::int[], $3::timestamptz[], $4::numeric[])
         ON CONFLICT (device_id, seq) DO NOTHING",
    )
    .bind(&did)
    .bind(&seqs)
    .bind(&ts)
    .bind(&vals)
    .execute(pool)
    .await?;
    Ok(res.rows_affected())
}

async fn ingest_copy(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let mut buf = String::new();
    for pt in pts {
        buf.push_str(&format!(
            "{},{},{},{}\n",
            pt.device_id, pt.seq, pt.ts, pt.value
        ));
    }
    let mut conn = pool.acquire().await?;
    let mut copy = conn
        .copy_in_raw("COPY telemetry_raw (device_id, seq, ts, value) FROM STDIN WITH (FORMAT csv)")
        .await?;
    copy.send(buf.as_bytes()).await?;
    Ok(copy.finish().await?)
}
