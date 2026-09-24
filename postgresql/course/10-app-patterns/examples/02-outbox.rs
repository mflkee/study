//! Пример 02: outbox pattern — событие пишем В ТОЙ ЖЕ транзакции, что и бизнес-данные.
//!
//! Запуск: `cargo run --example 02-outbox`
//!
//! Урок 02: атомарность «данные + исходящее событие» (иначе событие можно
//! потерять между COMMIT данных и записью события); релей — отдельный процесс,
//! доставляет недодоставленные (SKIP LOCKED), затем помечает delivered.
//! Идемпотентность доставки — в руках получателя (модуль 10).

use anyhow::Result;
use pg_course_module_10::new_pool;
use sqlx::PgPool;

/// Бизнес-операция: обновление устройства + событие в outbox — атомарно.
async fn touch_device(pool: &PgPool, tag: &str) -> Result<()> {
    let mut tx = pool.begin().await?;
    // «Изменение» (no-op, чтобы не портить данные, но триггер аудита сработает).
    sqlx::query("UPDATE devices SET model = model WHERE tag = $1")
        .bind(tag)
        .execute(&mut *tx)
        .await?;
    // Событие — в той же транзакции (внешняя система увидит только после COMMIT).
    sqlx::query("INSERT INTO outbox (aggregate, payload) VALUES ('device.updated', $1)")
        .bind(serde_json::json!({ "tag": tag }))
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    Ok(())
}

/// Релей: доставить пачку недодоставленных событий «внешней системе» (здесь — имитация).
async fn relay_once(pool: &PgPool) -> Result<u64> {
    let ids: Vec<i64> = sqlx::query_scalar(
        "SELECT id FROM outbox
          WHERE status = 'pending'
          ORDER BY id
          FOR UPDATE SKIP LOCKED
          LIMIT 5",
    )
    .fetch_all(pool)
    .await?;

    let mut delivered = 0u64;
    for id in ids {
        // Здесь была бы внешняя система (HTTP/внешний сервис). Имитация — 1:1.
        sqlx::query("UPDATE outbox SET status = 'delivered' WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;
        delivered += 1;
    }
    Ok(delivered)
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    touch_device(&pool, "M-01-001").await?;
    touch_device(&pool, "M-02-001").await?;
    println!("событий в outbox (pending): 2");

    let delivered = relay_once(&pool).await?;
    println!("релей доставил: {delivered}");

    let (pending, total): (i64, i64) = sqlx::query_as(
        "SELECT (SELECT count(*) FROM outbox WHERE status='pending'),
                (SELECT count(*) FROM outbox)",
    )
    .fetch_one(&pool)
    .await?;
    println!("осталось pending: {pending} из {total}");

    // Уборка демо.
    sqlx::query("DELETE FROM outbox").execute(&pool).await?;
    println!("демо-события удалены");
    Ok(())
}
