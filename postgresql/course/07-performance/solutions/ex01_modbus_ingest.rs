//! Решение упражнения 01 «Приём телеметрии Modbus».
//! Скопируйте содержимое в `exercises/src/ex01_modbus_ingest.rs` после попытки.

use crate::TelemetryPoint;
use anyhow::Result;
use sqlx::PgPool;

/// CSV-строка для COPY: `device_id,seq,ts,value` + перевод строки.
pub fn copy_line(pt: &TelemetryPoint) -> String {
    format!("{},{},{},{}\n", pt.device_id, pt.seq, pt.ts, pt.value)
}

/// Построчная вставка (цикл); возвращает число записанных строк.
pub async fn ingest_row(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let mut written = 0u64;
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
        written += res.rows_affected();
    }
    Ok(written)
}

/// Батч: один INSERT + UNNEST; возвращает число записанных строк.
pub async fn ingest_batch(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let device_ids: Vec<i32> = pts.iter().map(|p| p.device_id).collect();
    let seqs: Vec<i32> = pts.iter().map(|p| p.seq as i32).collect();
    let ts: Vec<_> = pts.iter().map(|p| p.ts).collect();
    let values: Vec<_> = pts.iter().map(|p| p.value).collect();

    let res = sqlx::query(
        "INSERT INTO telemetry_raw (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::int[], $3::timestamptz[], $4::numeric[])
         ON CONFLICT (device_id, seq) DO NOTHING",
    )
    .bind(&device_ids)
    .bind(&seqs)
    .bind(&ts)
    .bind(&values)
    .execute(pool)
    .await?;
    Ok(res.rows_affected())
}

/// COPY FROM STDIN (CSV); возвращает число строк (rows_affected от finish).
pub async fn ingest_copy(pool: &PgPool, pts: &[TelemetryPoint]) -> Result<u64> {
    let mut buf = String::new();
    for pt in pts {
        buf.push_str(&copy_line(pt));
    }
    let mut conn = pool.acquire().await?;
    let mut copy = conn
        .copy_in_raw("COPY telemetry_raw (device_id, seq, ts, value) FROM STDIN WITH (FORMAT csv)")
        .await?;
    copy.send(buf.as_bytes()).await?;
    let n = copy.finish().await?;
    Ok(n)
}

/// Одна строка отчёта замера: `{метод}: {n} строк за {secs:.1} с ({tps:.0} строк/с)`.
pub fn summarize(method: &str, n: u64, elapsed: std::time::Duration) -> String {
    let secs = elapsed.as_secs_f64();
    format!(
        "{method}: {n} строк за {secs:.1} с ({:.0} строк/с)",
        n as f64 / secs.max(1e-6)
    )
}
