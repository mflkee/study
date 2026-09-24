//! Пример 05: батчевая запись — один запрос вместо N построчных.
//!
//! Запуск: `cargo run --example 05-batch-write`
//!
//! Идея: вставляем 10 000 строк ОДНИМ запросом через `UNNEST` —
//! массивы Postgres приходят из Rust как `Vec<T>` (параметры `$1`, `$2`).
//! Построчный INSERT в цикле = N сетевых round-trip'ов; здесь — один.
//! (Детальный замер и сравнение с COPY — модуль 07.)

use anyhow::Result;
use chrono::{DateTime, Utc};
use pg_course_module_04::new_pool;
use rust_decimal::Decimal;

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // Демо-таблица отдельно от каталога, чтобы сид не трогать.
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS batch_demo (
             ts    timestamptz NOT NULL,
             value numeric(20,6) NOT NULL
         )",
    )
    .execute(&pool)
    .await?;
    sqlx::query("TRUNCATE batch_demo").execute(&pool).await?;

    const N: usize = 10_000;
    let start = Utc::now();
    let ts: Vec<DateTime<Utc>> = (0..N)
        .map(|i| start + chrono::Duration::seconds(i as i64))
        .collect();
    let vals: Vec<Decimal> = (0..N)
        .map(|i| Decimal::from(i as i64 % 1000) + Decimal::new(i as i64 % 100, 2))
        .collect();

    let t0 = std::time::Instant::now();
    let res = sqlx::query(
        "INSERT INTO batch_demo (ts, value)
         SELECT * FROM UNNEST($1::timestamptz[], $2::numeric[])",
    )
    .bind(&ts)
    .bind(&vals)
    .execute(&pool)
    .await?;

    println!(
        "вставлено {} строк одним запросом за {:?}",
        res.rows_affected(),
        t0.elapsed()
    );

    // Проверка: количество строк на месте.
    let (cnt,): (i64,) = sqlx::query_as("SELECT count(*) FROM batch_demo")
        .fetch_one(&pool)
        .await?;
    assert_eq!(cnt, N as i64);
    println!("проверка: в таблице {cnt} строк");

    Ok(())
}
