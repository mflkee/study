//! Пример 04: шлюз-буфер при потере связи (кейс 8).
//!
//! Запуск:
//!   cargo run --example 04-buffer -- --generate 100   # пишет точки в буфер-файл
//!   cargo run --example 04-buffer -- --flush          # доставка буфера в PG
//!   cargo run --example 04-buffer -- --tail           # показать файл и таблицу
//!
//! Урок 03/лаба: источник правды шлюза — ЛОКАЛЬНЫЙ ФАЙЛ (буфер); PG — пункт
//! доставки. Если PG недоступен — доставка не теряет данные (файл цел);
//! после восстановления — повторный `--flush`, дубли исключены
//! (UNIQUE (device_id, seq) + ON CONFLICT DO NOTHING).
//! В лабе 01 postgres реально стопается (docker compose stop postgres).

use anyhow::{anyhow, Result};
use chrono::{DateTime, Utc};
use pg_course_module_10::{database_url, MeteringPoint};
use rust_decimal::Decimal;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::path::PathBuf;

const DEFAULT_BUFFER: &str = "buffer.tsv";

fn buffer_path() -> PathBuf {
    let args: Vec<String> = std::env::args().collect();
    args.iter()
        .position(|a| a == "--buffer")
        .and_then(|i| args.get(i + 1))
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(DEFAULT_BUFFER))
}

fn to_line(pt: &MeteringPoint) -> String {
    format!(
        "{}\t{}\t{}\t{}\n",
        pt.device_id,
        pt.seq,
        pt.ts.to_rfc3339(),
        pt.value
    )
}

type BufferPoint = (i32, i64, DateTime<Utc>, Decimal);

fn parse_lines(data: &str) -> Result<Vec<BufferPoint>> {
    let mut out = Vec::new();
    for (n, line) in data.lines().enumerate() {
        let l = line.trim();
        if l.is_empty() || l.starts_with('#') {
            continue;
        }
        let mut it = l.split('\t');
        let dev: i32 = it.next().ok_or_else(|| anyhow!("{n}: нет dev"))?.parse()?;
        let seq: i64 = it.next().ok_or_else(|| anyhow!("{n}: нет seq"))?.parse()?;
        let ts: DateTime<Utc> = it.next().ok_or_else(|| anyhow!("{n}: нет ts"))?.parse()?;
        let val: Decimal = it
            .next()
            .ok_or_else(|| anyhow!("{n}: нет value"))?
            .parse()?;
        out.push((dev, seq, ts, val));
    }
    Ok(out)
}

async fn deliver(pool: &PgPool, pts: &[BufferPoint]) -> Result<u64> {
    let did: Vec<i32> = pts.iter().map(|p| p.0).collect();
    let seqs: Vec<i64> = pts.iter().map(|p| p.1).collect();
    let ts: Vec<_> = pts.iter().map(|p| p.2).collect();
    let vals: Vec<Decimal> = pts.iter().map(|p| p.3).collect();
    let res = sqlx::query(
        "INSERT INTO metering_points (device_id, seq, ts, value)
         SELECT * FROM UNNEST($1::int[], $2::bigint[], $3::timestamptz[], $4::numeric[])
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

#[tokio::main]
async fn main() -> Result<()> {
    let cmd = std::env::args().nth(1).unwrap_or_else(|| "--usage".into());
    let path = buffer_path();

    match cmd.as_str() {
        "--generate" => {
            let n: usize = std::env::args()
                .nth(2)
                .and_then(|s| s.parse().ok())
                .unwrap_or(100);
            let mut out = std::fs::read_to_string(&path).unwrap_or_default();
            let lines = parse_lines(&out).unwrap_or_default();
            let base: i64 = lines.iter().map(|l| l.1).max().unwrap_or(0);
            for i in 1..=n as i64 {
                let pt = MeteringPoint {
                    device_id: 1,
                    seq: base + i,
                    ts: Utc::now(),
                    value: Decimal::from(1000 + (i % 100)),
                };
                out.push_str(&to_line(&pt));
            }
            std::fs::write(&path, out)?;
            println!("буфер {path:?}: сгенерировано {n} точек");
        }
        "--flush" => {
            let data = std::fs::read_to_string(&path).unwrap_or_default();
            let pts = parse_lines(&data)?;
            if pts.is_empty() {
                println!("буфер пуст — доставлять нечего");
                return Ok(());
            }
            let pool = PgPoolOptions::new()
                .max_connections(5)
                .connect(&database_url())
                .await?;
            match deliver(&pool, &pts).await {
                Ok(inserted) => {
                    // Все строки буфера доставлены (ON CONFLICT убрал дубли) — файл чист.
                    std::fs::write(&path, "")?;
                    println!(
                        "доставлено новых строк в PG: {inserted} из {} в буфере; буфер очищен",
                        pts.len()
                    );
                }
                Err(e) => {
                    // PG недоступен — НИЧЕГО не потеряно (данные в файле).
                    println!("доставка не удалась: {e}");
                    println!("данные целы в {path:?} — повторный flush после восстановления PG");
                }
            }
        }
        "--tail" => {
            let data = std::fs::read_to_string(&path).unwrap_or_default();
            let lines = data.lines().count();
            println!("буфер {path:?}: {lines} строк");
            for l in data.lines().take(5) {
                println!("  {l}");
            }
        }
        _ => {
            eprintln!(
                "использование: {} --generate N | --flush | --tail [путь к буферу]",
                std::env::args().next().unwrap_or_default()
            );
            std::process::exit(2);
        }
    }
    Ok(())
}
