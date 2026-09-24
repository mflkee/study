//! Пример 05: собственный генератор нагрузки (INSERT + UPDATE mix) и замер TPS.
//!
//! Запуск: `cargo run --example 05-loader [--seconds 5] [--pool 4] [--inserts 80]`
//!
//! Смесь «80% вставок в telemetry_raw + 20% обновлений events», пачками в
//! цикле. Сравнение с pgbench — в лабе 01 (pgbench живёт в контейнере:
//! `docker compose exec postgres pgbench -c 4 -j 2 -T 5 -U course course_m06`).
//!
//! Важно (урок 04): генератор НЕ должен замерять «сам себя» — задержки между
//! пачками управляются тикером, а не скоростью БД.

use anyhow::Result;
use chrono::Utc;
use pg_course_module_07::new_pool;
use rust_decimal::Decimal;
use std::time::{Duration, Instant};
use tokio::time::interval;

fn arg_after(flag: &str) -> Option<String> {
    let args: Vec<String> = std::env::args().collect();
    args.iter()
        .position(|a| a == flag)
        .and_then(|i| args.get(i + 1))
        .cloned()
}

#[tokio::main]
async fn main() -> Result<()> {
    let seconds: u64 = arg_after("--seconds")
        .and_then(|s| s.parse().ok())
        .unwrap_or(5);
    let pool_max: u32 = arg_after("--pool")
        .and_then(|s| s.parse().ok())
        .unwrap_or(4);
    let insert_share: u64 = arg_after("--inserts")
        .and_then(|s| s.parse().ok())
        .unwrap_or(80);
    let pool = new_pool(pool_max).await?;

    println!("нагрузка: {seconds} c, пул {pool_max}, вставок {insert_share}%");

    let mut inserts = 0u64;
    let mut updates = 0u64;
    let mut seq = 0u64;
    let t0 = Instant::now();
    let mut tick = interval(Duration::from_millis(100));

    while t0.elapsed().as_secs() < seconds {
        tick.tick().await;
        for _ in 0..20 {
            seq += 1;
            if seq % 100 < insert_share {
                sqlx::query(
                    "INSERT INTO telemetry_raw (device_id, seq, ts, value)
                     VALUES ($1, $2, $3, $4)
                     ON CONFLICT (device_id, seq) DO NOTHING",
                )
                .bind(1i32)
                .bind((seq % 2_000_000) as i32)
                .bind(Utc::now())
                .bind(Decimal::from((seq % 1000) as i64))
                .execute(&pool)
                .await?;
                inserts += 1;
            } else {
                sqlx::query("UPDATE events SET value = value + 0.0001 WHERE id = $1")
                    .bind((seq % 20_000) as i32 + 1)
                    .execute(&pool)
                    .await?;
                updates += 1;
            }
        }
    }

    let secs = t0.elapsed().as_secs_f64();
    let total = inserts + updates;
    println!(
        "итог: вставок {inserts}, обновлений {updates}, всего {total} операций за {secs:.1} с = {:.0} TPS",
        total as f64 / secs
    );
    Ok(())
}
