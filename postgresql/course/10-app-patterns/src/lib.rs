//! Общая библиотека модуля 10 «Паттерны приложений».

use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;

pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15432/course_m06?sslmode=disable";

pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

pub async fn new_pool(max: u32) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .max_connections(max)
        .acquire_timeout(Duration::from_secs(5))
        .connect(&database_url())
        .await?)
}

/// Точка метеринга, которую шлюз доставляет в PG (уникальна по device_id+seq).
#[derive(Debug, Clone)]
pub struct MeteringPoint {
    pub device_id: i32,
    pub seq: i64,
    pub ts: chrono::DateTime<chrono::Utc>,
    pub value: rust_decimal::Decimal,
}

// --- Упражнения ---
#[path = "../exercises/src/ex01_queue.rs"]
pub mod ex01_queue;

#[path = "../exercises/src/ex02_buffer.rs"]
pub mod ex02_buffer;

#[cfg(test)]
mod tests {
    use super::*;

    // --- упражнение 01: очередь задач и ретраи ---

    #[test]
    fn ex01_backoff_grows_exponentially() {
        use crate::ex01_queue::backoff;
        let b1 = backoff(1).as_secs();
        let b2 = backoff(2).as_secs();
        let b3 = backoff(3).as_secs();
        assert!(
            b1 > 0 && b2 == 2 * b1 && b3 == 4 * b1,
            "экспонента: {b1} {b2} {b3}"
        );
    }

    #[tokio::test]
    async fn ex01_worker_lifecycle() {
        // Интеграционный: требует стенда + 00-setup.sql.
        use crate::ex01_queue::{claim_batch, complete, fail_with_retry, TaskStatus};
        let pool = new_pool(5).await.expect("стенд");
        sqlx::query("TRUNCATE task_queue")
            .execute(&pool)
            .await
            .expect("clean");

        // Три задачи с разной судьбой.
        for i in 0..3 {
            sqlx::query("INSERT INTO task_queue (kind, payload) VALUES ($1, $2)")
                .bind("kind".to_string())
                .bind(serde_json::json!({"i": i}))
                .execute(&pool)
                .await
                .expect("seed");
        }

        // Задача 1 → done.
        let batch = claim_batch(&pool, 3).await.expect("claim");
        assert_eq!(batch.len(), 3, "забрали 3 задачи");
        assert!(batch.iter().all(|t| t.status == TaskStatus::Processing));
        complete(&pool, batch[0].id).await.expect("complete");

        // Задача 2 → fail, но ретраится (attempts < max).
        fail_with_retry(&pool, batch[1].id, 3).await.expect("fail");
        // Задача 3 → fail до max -> dead.
        for _ in 0..3 {
            fail_with_retry(&pool, batch[2].id, 3).await.expect("fail");
        }

        let statuses: Vec<String> = sqlx::query_scalar("SELECT status FROM task_queue ORDER BY id")
            .fetch_all(&pool)
            .await
            .expect("statuses");
        assert_eq!(statuses[0], "done", "задача 1 выполнена");
        assert_eq!(statuses[1], "pending", "задача 2 ждёт ретрая");
        assert_eq!(
            statuses[2], "dead",
            "задача 3 в dead-letter после max попыток"
        );
        sqlx::query("TRUNCATE task_queue")
            .execute(&pool)
            .await
            .expect("cleanup");
    }

    // --- упражнение 02: буферизация и идемпотентность ---

    #[test]
    fn ex02_line_roundtrip() {
        use crate::ex02_buffer::{parse_lines, to_line};
        let pt = MeteringPoint {
            device_id: 3,
            seq: 42,
            ts: chrono::Utc::now(),
            value: rust_decimal::Decimal::from_str_exact("1234.5678").unwrap(),
        };
        let txt = to_line(&pt);
        let parsed = parse_lines(&txt).expect("parse");
        assert_eq!(parsed.len(), 1);
        assert_eq!(parsed[0].0, 3);
        assert_eq!(parsed[0].1, 42);
        assert_eq!(parsed[0].3, pt.value, "точность decimal сохраняется");
    }

    #[tokio::test]
    async fn ex02_delivery_idempotent() {
        use crate::ex02_buffer::deliver;
        let pool = new_pool(5).await.expect("стенд");
        sqlx::query("TRUNCATE metering_points")
            .execute(&pool)
            .await
            .expect("clean");

        let pts = vec![
            MeteringPoint {
                device_id: 1,
                seq: 1,
                ts: chrono::Utc::now(),
                value: rust_decimal::Decimal::from(10),
            },
            MeteringPoint {
                device_id: 1,
                seq: 2,
                ts: chrono::Utc::now(),
                value: rust_decimal::Decimal::from(20),
            },
        ];
        let first = deliver(&pool, &pts).await.expect("первая доставка");
        let second = deliver(&pool, &pts).await.expect("повторная доставка");
        assert_eq!(first, 2, "первый раз — 2 новые строки");
        assert_eq!(second, 0, "повтор — 0 дублей (ON CONFLICT DO NOTHING)");
        let n: i64 = sqlx::query_scalar("SELECT count(*) FROM metering_points")
            .fetch_one(&pool)
            .await
            .expect("count");
        assert_eq!(n, 2);
        sqlx::query("TRUNCATE metering_points")
            .execute(&pool)
            .await
            .expect("cleanup");
    }
}
