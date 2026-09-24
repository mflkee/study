//! Решение упражнения 02: «Буферизация при потере связи».
//! Скопируйте содержимое в `exercises/src/ex02_buffer.rs` после попытки.

use crate::MeteringPoint;
use anyhow::{anyhow, Result};
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use sqlx::PgPool;

/// Точка, прочитанная из буфера (device_id, seq, ts, value).
pub type BufferPoint = (i32, i64, DateTime<Utc>, Decimal);

/// Строка буфера: `device_id\tseq\tts\tvalue\n` (ts — RFC3339).
pub fn to_line(pt: &MeteringPoint) -> String {
    format!(
        "{}\t{}\t{}\t{}\n",
        pt.device_id,
        pt.seq,
        pt.ts.to_rfc3339(),
        pt.value
    )
}

/// Разбор строк буфера в точки (пропускает пустые строки и комментарии `#`).
pub fn parse_lines(data: &str) -> Result<Vec<BufferPoint>> {
    let mut out = Vec::new();
    for (n, line) in data.lines().enumerate() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let mut it = line.split('\t');
        let device_id: i32 = it
            .next()
            .ok_or_else(|| anyhow!("строка {n}: нет device_id"))?
            .parse()?;
        let seq: i64 = it
            .next()
            .ok_or_else(|| anyhow!("строка {n}: нет seq"))?
            .parse()?;
        let ts: DateTime<Utc> = it
            .next()
            .ok_or_else(|| anyhow!("строка {n}: нет ts"))?
            .parse()?;
        let value: Decimal = it
            .next()
            .ok_or_else(|| anyhow!("строка {n}: нет value"))?
            .parse()?;
        out.push((device_id, seq, ts, value));
    }
    Ok(out)
}

/// Доставка пачки в PG (новые строки; дубли отбрасываются).
pub async fn deliver(pool: &PgPool, pts: &[MeteringPoint]) -> Result<u64> {
    let device_ids: Vec<i32> = pts.iter().map(|p| p.device_id).collect();
    let seqs: Vec<i64> = pts.iter().map(|p| p.seq).collect();
    let ts: Vec<_> = pts.iter().map(|p| p.ts).collect();
    let values: Vec<Decimal> = pts.iter().map(|p| p.value).collect();

    let res = sqlx::query(
        "INSERT INTO metering_points (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::bigint[], $3::timestamptz[], $4::numeric[])
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
