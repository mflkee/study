//! Пример 04: `pg_stat_statements` — топ медленных запросов.
//!
//! Запуск: `cargo run --example 04-pgstat` (после прогона других примеров — им
//! есть что записать в статистику).
//!
//! Требуется: расширение включено в сервере (docker-compose: `shared_preload_
//! libraries=pg_stat_statements`, см. infra) и создано в базе: psql
//! `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;`.

use anyhow::Result;
use pg_course_module_07::new_pool;

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    let rows: Vec<(String, f64, f64, i64)> = sqlx::query_as(
        "SELECT left(query, 55) AS query,
                total_exec_time, mean_exec_time, calls
           FROM pg_stat_statements
          WHERE query ILIKE '%telemetry_raw%'
             OR query ILIKE '%events%'
             OR query ILIKE '%pgbench%'
          ORDER BY total_exec_time DESC
          LIMIT 12",
    )
    .fetch_all(&pool)
    .await?;

    println!("{:─<90}", "");
    println!(
        "{:<55} {:>12} {:>10} {:>7}",
        "запрос", "total_ms", "mean_ms", "calls"
    );
    println!("{:─<90}", "");
    if rows.is_empty() {
        println!("(статистика пуста: сначала прогони примеры 02/03/05)");
    }
    for (q, total, mean, calls) in rows {
        println!("{q:<55} {total:>12.1} {mean:>10.2} {calls:>7}");
    }
    Ok(())
}
