//! Пример 01: пул соединений — конфигурация, статистика, таймауты.
//!
//! Запуск: `cargo run --example 01-pool`
//!
//! Пул — это НЕ «одно соединение»; он держит до max_connections живых
//! соединений и выдаёт их запросам. Под нагрузкой лишние запросы ЖДУТ
//! свободного соединения до acquire_timeout (урок 01).

use anyhow::Result;
use pg_course_module_06::database_url;
use sqlx::postgres::PgPoolOptions;
use std::time::Duration;

#[tokio::main]
async fn main() -> Result<()> {
    // Полный конфиг пула (в модуле 04 он был минимальным).
    let pool = PgPoolOptions::new()
        .min_connections(1) // держать минимум 1 (идеальный прогрев)
        .max_connections(5) // потолок одновременных соединений
        .acquire_timeout(Duration::from_secs(5)) // сколько ждём свободное соединение
        .idle_timeout(Duration::from_secs(60)) // простой дольше — закрыть
        .connect(&database_url())
        .await?;

    println!(
        "пул до запросов: size={} idle={}",
        pool.size(),
        pool.num_idle()
    );

    // 20 конкурентных задач на пуле из 5 соединений: все дождутся очереди.
    let mut handles = Vec::new();
    for i in 0..20 {
        let pool = pool.clone();
        handles.push(tokio::spawn(async move {
            tokio::time::sleep(Duration::from_millis(10)).await;
            let n: i64 = sqlx::query_scalar("SELECT count(*) FROM devices")
                .fetch_one(&pool)
                .await
                .expect("запрос");
            (i, n)
        }));
    }
    let mut total = 0i64;
    for h in handles {
        let (i, n) = h.await?;
        total += n;
        eprintln!("  задача {i:>2}: устройств в базе = {n}");
    }
    println!("20 задач отработали через пул из 5 соединений, сумма count(*) = {total}");

    println!("пул после: size={} idle={}", pool.size(), pool.num_idle());

    // Таймаут: пул на 1 соединение, оба соединения держим — третье ждёт и роняет.
    let tiny = PgPoolOptions::new()
        .max_connections(1)
        .acquire_timeout(Duration::from_millis(150))
        .connect(&database_url())
        .await?;
    let _held1 = tiny.acquire().await?;
    match tiny.acquire().await {
        Ok(_) => println!("а мы второе соединение получили (неожиданно)"),
        Err(e) => println!("acquire_timeout сработал: {e}"),
    }
    println!("(уронили held1: `drop` вернул соединение пулу)");

    Ok(())
}
