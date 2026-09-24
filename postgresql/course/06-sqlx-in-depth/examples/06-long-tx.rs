//! Пример 06: долгая транзакция из Rust — виновник bloat (для лабы 02).
//!
//! Запуск: `cargo run --example 06-long-tx [--seconds 30]`
//!
//! Воркер открывает транзакцию REPEATABLE READ и держит снимок `seconds`
//! секунд. Пока он «спит», параллельный psql удаляет строки и запускает
//! `VACUUM VERBOSE` — удалённые строки не убираются: их «видит» снимок
//! воркера (`dead but not yet removable`). Потом транзакция закрывается —
//! VACUUM дочищает.
//!
//! application_name = long_tx_worker — по нему воркер находится в
//! `pg_stat_activity` (модуль 03 и лаба 02).

use anyhow::Result;
use pg_course_module_06::database_url;
use sqlx::postgres::PgPoolOptions;
use std::time::Duration;

fn worker_url() -> String {
    format!("{}&application_name=long_tx_worker", database_url())
}

#[tokio::main]
async fn main() -> Result<()> {
    let seconds: u64 = match std::env::args().nth(1).as_deref() {
        Some("--seconds") => std::env::args()
            .nth(2)
            .and_then(|s| s.parse().ok())
            .unwrap_or(20),
        _ => 20,
    };

    let pool = PgPoolOptions::new()
        .max_connections(2)
        .connect(&worker_url())
        .await?;

    // Открываем транзакцию со «срезом на весь день».
    let mut tx = pool.begin().await?;
    sqlx::query("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
        .execute(&mut *tx)
        .await?;
    let n: i64 = sqlx::query_scalar("SELECT count(*) FROM measurements")
        .fetch_one(&mut *tx)
        .await?;
    println!(
        "[воркер] транзакция открыта: вижу {n} измерений (снимок repeatable read); держу {seconds} с…"
    );

    // На этот момент в psql: DELETE части строк + VACUUM VERBOSE —
    // старые версии не убираются («dead but not yet removable»).
    tokio::time::sleep(Duration::from_secs(seconds)).await;

    // Тот же снимок: повторное чтение вернёт ТЕ ЖЕ данные (стабильность RR).
    let n2: i64 = sqlx::query_scalar("SELECT count(*) FROM measurements")
        .fetch_one(&mut *tx)
        .await?;
    println!("[воркер] тот же снимок после паузы: {n2} измерений (не изменилось)");

    tx.rollback().await?;
    println!("[воркер] транзакция закрыта — снимок отпущен, VACUUM может работать");
    Ok(())
}
