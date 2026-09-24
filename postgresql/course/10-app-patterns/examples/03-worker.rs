//! Пример 03: воркер очереди задач (SKIP LOCKED) — ретраи и dead-letter (кейс 7).
//!
//! Запуск: `cargo run --example 03-worker [--rounds 5]`
//!
//! Урок 03: цикл «забрать пачку (SKIP LOCKED) → обработать → done/fail+backoff».
//! Два воркера одновременно НЕ заберут одну задачу; после max_attempts задача
//! уходит в dead-letter ('dead'). Это «персистентная» версия очереди с каналом
//! из модуля 05 — та же идея, но переживает рестарты.

use anyhow::{anyhow, Result};
use chrono::Utc;
use pg_course_module_10::new_pool;
use sqlx::PgPool;
use std::time::Duration;

fn backoff(attempts: i32) -> Duration {
    Duration::from_secs(10 * (1u64 << (attempts.max(0) as u32).saturating_sub(1)))
}

/// Забрать пачку задач (см. решение упражнения 01 — здесь эталонная копия).
async fn claim_batch(pool: &PgPool, limit: i32) -> Result<Vec<(i64, String)>> {
    let rows = sqlx::query_as::<_, (i64, String)>(
        "WITH claimed AS (
             SELECT id FROM task_queue
              WHERE status = 'pending' AND next_run_at <= now()
              ORDER BY id FOR UPDATE SKIP LOCKED LIMIT $1
         )
         UPDATE task_queue q SET status = 'processing'
           FROM claimed WHERE q.id = claimed.id
          RETURNING q.id, q.kind",
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// «Обработка» задачи; вид с ошибкой — для демонстрации ретраев.
fn process(kind: &str, payload: &serde_json::Value) -> Result<()> {
    match kind {
        "recalc" => {
            println!("  recalc {} → ok", payload["i"]);
            Ok(())
        }
        "fragile" => Err(anyhow!("fragile {} не вышло", payload["i"])),
        other => Err(anyhow!("неизвестный kind {other:?}")),
    }
}

async fn complete(pool: &PgPool, id: i64) -> Result<()> {
    sqlx::query("UPDATE task_queue SET status = 'done' WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await?;
    Ok(())
}

async fn fail_with_retry(pool: &PgPool, id: i64, max_attempts: i32) -> Result<()> {
    let attempts: i32 = sqlx::query_scalar("SELECT attempts FROM task_queue WHERE id = $1")
        .bind(id)
        .fetch_one(pool)
        .await?;
    let new_attempts = attempts + 1;
    let status = if new_attempts >= max_attempts {
        "dead"
    } else {
        "pending"
    };
    sqlx::query(
        "UPDATE task_queue SET attempts = $2, status = $3,
                next_run_at = now() + make_interval(secs => $4)
          WHERE id = $1",
    )
    .bind(id)
    .bind(new_attempts)
    .bind(status)
    .bind(backoff(new_attempts).as_secs() as i64)
    .execute(pool)
    .await?;
    println!("  задача {id}: попытка {new_attempts} не вышла -> {status}");
    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    let rounds: i32 = std::env::args()
        .nth(1)
        .and_then(|s| s.parse().ok())
        .unwrap_or(5);
    let pool = new_pool(5).await?;

    // Сид: 3 «живые» + 2 «хрупкие» задачи.
    sqlx::query("TRUNCATE task_queue").execute(&pool).await?;
    for kind in ["recalc", "recalc", "recalc", "fragile", "fragile"] {
        sqlx::query("INSERT INTO task_queue (kind, payload, max_attempts) VALUES ($1, $2, 3)")
            .bind(kind)
            .bind(serde_json::json!({"i": Utc::now().timestamp_millis()}))
            .execute(&pool)
            .await?;
    }

    for round in 0..rounds {
        let claimed = claim_batch(&pool, 5).await?;
        if claimed.is_empty() {
            println!("раунд {round}: задач нет — останавливаемся");
            break;
        }
        for (id, kind) in claimed {
            let payload: serde_json::Value =
                sqlx::query_scalar("SELECT payload FROM task_queue WHERE id = $1")
                    .bind(id)
                    .fetch_one(&pool)
                    .await?;
            match process(&kind, &payload) {
                Ok(()) => {
                    complete(&pool, id).await?;
                    println!("  задача {id} ({kind}) → done");
                }
                Err(e) => {
                    println!("  задача {id} ({kind}) ошибка: {e}");
                    fail_with_retry(&pool, id, 3).await?;
                }
            }
        }
        tokio::time::sleep(Duration::from_millis(20)).await; // «рабочий» такт
    }

    let stats: Vec<(String, i64)> =
        sqlx::query_as("SELECT status, count(*) FROM task_queue GROUP BY status ORDER BY 1")
            .fetch_all(&pool)
            .await?;
    println!("\nитог по очередям: {stats:?}");
    Ok(())
}
