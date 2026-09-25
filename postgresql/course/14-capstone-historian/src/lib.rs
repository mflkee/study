//! Библиотека капстоуна «Historian»: общие типы, буфер, батч, аудит, API.
//!
//! Идеи перенесены из модулей курса: батч+идемпотентность (07/10),
//! аудит-цепочка sha256 (09), REST (11). Всё чистое переиспользуется тестами.

use anyhow::{anyhow, Result};
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use sqlx::PgPool;

pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15442/capstone?sslmode=disable";

pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

pub async fn new_pool(max: u32) -> Result<PgPool> {
    Ok(sqlx::postgres::PgPoolOptions::new()
        .max_connections(max)
        .connect(&database_url())
        .await?)
}

// ============================================================
// Приём телеметрии (буфер + батч + идемпотентность)
// ============================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MeteringPoint {
    pub device_id: i32,
    pub seq: i64,
    pub ts: DateTime<Utc>,
    pub value: Decimal,
}

/// Строка буфера (TSV, RFC3339) — формат локального файла шлюза.
pub fn to_line(pt: &MeteringPoint) -> String {
    format!(
        "{}\t{}\t{}\t{}\n",
        pt.device_id,
        pt.seq,
        pt.ts.to_rfc3339(),
        pt.value
    )
}

pub fn parse_lines(data: &str) -> Result<Vec<MeteringPoint>> {
    let mut out = Vec::new();
    for (n, l) in data.lines().enumerate() {
        let l = l.trim();
        if l.is_empty() || l.starts_with('#') {
            continue;
        }
        let mut it = l.split('\t');
        let device_id: i32 = it
            .next()
            .ok_or_else(|| anyhow!("Строка {n}: нет device_id"))?
            .parse()?;
        let seq: i64 = it
            .next()
            .ok_or_else(|| anyhow!("Строка {n}: нет seq"))?
            .parse()?;
        let ts: DateTime<Utc> = it
            .next()
            .ok_or_else(|| anyhow!("Строка {n}: нет ts"))?
            .parse()?;
        let value: Decimal = it
            .next()
            .ok_or_else(|| anyhow!("Строка {n}: нет value"))?
            .parse()?;
        out.push(MeteringPoint {
            device_id,
            seq,
            ts,
            value,
        });
    }
    Ok(out)
}

/// Батч-доставка в партиционированную `measurements_hist` (ON CONFLICT → 0 дублей).
pub async fn batch_deliver(pool: &PgPool, pts: &[MeteringPoint]) -> Result<u64> {
    let did: Vec<i32> = pts.iter().map(|p| p.device_id).collect();
    let seqs: Vec<i64> = pts.iter().map(|p| p.seq).collect();
    let ts: Vec<_> = pts.iter().map(|p| p.ts).collect();
    let vals: Vec<Decimal> = pts.iter().map(|p| p.value).collect();
    let res = sqlx::query(
        "INSERT INTO measurements_hist (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::bigint[], $3::timestamptz[], $4::numeric[])
         ON CONFLICT (device_id, seq, ts) DO NOTHING",
    )
    .bind(&did)
    .bind(&seqs)
    .bind(&ts)
    .bind(&vals)
    .execute(pool)
    .await?;
    Ok(res.rows_affected())
}

/// Создать недостающие партиции на месяцы из диапазона (быстрый авто-мост).
/// DDL не принимает параметры — строки формируем ТОЛЬКО из внутренних значений
/// (имена/даты нашего формата), поэтому безопасны; помечены AssertSqlSafe (модуль 11).
pub async fn ensure_partitions(
    pool: &PgPool,
    from: DateTime<Utc>,
    to: DateTime<Utc>,
) -> Result<()> {
    let mut m = from;
    while m <= to {
        let name = format!("measurements_hist_{}", m.format("%Y_%m"));
        let from_s = m.format("%Y-%m-01 00:00:00+00").to_string();
        let to_s = (m + chrono::Months::new(1))
            .format("%Y-%m-01 00:00:00+00")
            .to_string();
        let sql = format!(
            "DO $$ BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = '{name}') THEN
                    EXECUTE 'CREATE TABLE {name} PARTITION OF measurements_hist
                             FOR VALUES FROM (''{from_s}'') TO (''{to_s}'')';
                END IF;
             END $$;"
        );
        sqlx::query(sqlx::AssertSqlSafe(sql.as_str()))
            .execute(pool)
            .await?;
        m = m + chrono::Months::new(1);
    }
    Ok(())
}

// ============================================================
// Аудит-цепочка (модуль 09)
// ============================================================

pub const AUDIT_SEED: &str = "seed_capstone_v1";

#[derive(Debug, Clone)]
pub struct AuditRow {
    pub id: i64,
    pub entity: String,
    pub entity_id: i32,
    pub action: String,
    pub data_txt: String,
    pub ts_txt: String,
    pub prev_hash: String,
    pub row_hash: String,
}

pub fn row_hash(prev: &str, entity: &str, id: i32, action: &str, data: &str, ts: &str) -> String {
    hex::encode(Sha256::digest(
        format!("{prev}|{entity}|{id}|{action}|{data}|{ts}").as_bytes(),
    ))
}

pub async fn audit_chain_from_db(pool: &PgPool) -> Result<Vec<AuditRow>> {
    let rows = sqlx::query_as::<_, (i64, String, i32, String, String, String, String, String)>(
        "SELECT id, entity, entity_id, action, data::text, ts::text, prev_hash, row_hash
           FROM audit_log ORDER BY id",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows
        .into_iter()
        .map(
            |(id, entity, entity_id, action, data_txt, ts_txt, prev_hash, row_hash)| AuditRow {
                id,
                entity,
                entity_id,
                action,
                data_txt,
                ts_txt,
                prev_hash,
                row_hash,
            },
        )
        .collect())
}

pub fn verify_chain(rows: &[AuditRow]) -> Result<()> {
    for (i, r) in rows.iter().enumerate() {
        if i == 0 && r.prev_hash != AUDIT_SEED {
            return Err(anyhow!("Строка {}: зерно не совпадает", r.id));
        }
        if i > 0 && r.prev_hash != rows[i - 1].row_hash {
            return Err(anyhow!("Строка {}: разрыв цепочки", r.id));
        }
        let expect = row_hash(
            &r.prev_hash,
            &r.entity,
            r.entity_id,
            &r.action,
            &r.data_txt,
            &r.ts_txt,
        );
        if expect != r.row_hash {
            return Err(anyhow!("Строка {}: данные подменены", r.id));
        }
    }
    Ok(())
}

// ============================================================
// REST API (модуль 11, урезанный для капстоуна)
// ============================================================

#[derive(sqlx::FromRow, Serialize)]
pub struct CurrentDto {
    pub tag: String,
    pub value: Decimal,
    pub ts: DateTime<Utc>,
}

#[derive(sqlx::FromRow, Serialize)]
pub struct TrendDto {
    pub bucket: DateTime<Utc>,
    pub avg: Decimal,
    pub count: i64,
}

pub async fn current_value(pool: &PgPool, tag: &str) -> Result<Option<CurrentDto>> {
    Ok(sqlx::query_as::<_, CurrentDto>(
        "SELECT d.tag, m.value, m.ts
           FROM measurements_hist m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1 ORDER BY m.ts DESC LIMIT 1",
    )
    .bind(tag)
    .fetch_optional(pool)
    .await?)
}

/// Секунды из строки интервала: "1 hour" → 3600, "30 minutes" → 1800, "1 day" → 86400.
pub fn bucket_seconds(bucket: &str) -> f64 {
    let mut it = bucket.split_whitespace();
    let n: f64 = it.next().and_then(|s| s.parse().ok()).unwrap_or(1.0);
    let unit = it.next().unwrap_or("hour");
    let mult = if unit.starts_with("second") || unit == "s" {
        1.0
    } else if unit.starts_with("minute") || unit == "m" {
        60.0
    } else if unit.starts_with("day") || unit == "d" {
        86_400.0
    } else {
        3_600.0 // hour(s)
    };
    n * mult
}

pub async fn trends(pool: &PgPool, tag: &str, bucket: &str, hours: i64) -> Result<Vec<TrendDto>> {
    // Бакет через эпоху с фиксированным «нулём» 2026-01-01 (как date_bin origin).
    let secs = bucket_seconds(bucket);
    let sql = "SELECT to_timestamp(
                    floor((EXTRACT(EPOCH FROM m.ts) - 1767225600) / $1) * $1 + 1767225600
                ) AS bucket,
                round(avg(m.value)::numeric, 4) AS avg, count(*) AS count
           FROM measurements_hist m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $2
            AND m.ts >= now() - make_interval(hours => $3::int)
          GROUP BY bucket ORDER BY bucket";
    let rows = sqlx::query_as::<_, TrendDto>(sql)
        .bind(secs)
        .bind(tag)
        .bind(hours as i32)
        .fetch_all(pool)
        .await;
    match rows {
        Ok(r) => Ok(r),
        Err(e) => {
            eprintln!("trends SQL: {sql}\nERR: {e}");
            Err(e.into())
        }
    }
}
