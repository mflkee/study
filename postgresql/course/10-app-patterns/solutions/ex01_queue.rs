//! Решение упражнения 01: «Очередь задач» — воркер, ретраи, dead-letter.
//! Скопируйте содержимое в `exercises/src/ex01_queue.rs` после попытки.

use anyhow::Result;
use serde_json::Value;
use sqlx::PgPool;
use std::time::Duration;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum TaskStatus {
    Pending,
    Processing,
    Done,
    Failed,
    Dead,
}

impl TaskStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            TaskStatus::Pending => "pending",
            TaskStatus::Processing => "processing",
            TaskStatus::Done => "done",
            TaskStatus::Failed => "failed",
            TaskStatus::Dead => "dead",
        }
    }
}

#[derive(Debug)]
pub struct Task {
    pub id: i64,
    pub kind: String,
    pub payload: Value,
    pub status: TaskStatus,
    pub attempts: i32,
}

const BACKOFF_BASE_SECS: u64 = 10;

/// Экспоненциальный бэкoff: base * 2^(attempts-1).
pub fn backoff(attempts: i32) -> Duration {
    let exp = attempts.max(0) as u32;
    Duration::from_secs(BACKOFF_BASE_SECS * (1u64 << exp.saturating_sub(1)))
}

/// Забрать пачку задач в обработку (SKIP LOCKED — без двойного захвата).
pub async fn claim_batch(pool: &PgPool, limit: i32) -> Result<Vec<Task>> {
    let rows = sqlx::query_as::<_, (i64, String, Value, String, i32)>(
        "WITH claimed AS (
             SELECT id FROM task_queue
              WHERE status = 'pending' AND next_run_at <= now()
              ORDER BY id
              FOR UPDATE SKIP LOCKED
              LIMIT $1
         )
         UPDATE task_queue q
            SET status = 'processing'
           FROM claimed
          WHERE q.id = claimed.id
          RETURNING q.id, q.kind, q.payload, q.status, q.attempts",
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|(id, kind, payload, status, attempts)| Task {
            id,
            kind,
            payload,
            status: match status.as_str() {
                "processing" => TaskStatus::Processing,
                "pending" => TaskStatus::Pending,
                "done" => TaskStatus::Done,
                "failed" => TaskStatus::Failed,
                _ => TaskStatus::Dead,
            },
            attempts,
        })
        .collect())
}

/// Отметить задачу выполненной.
pub async fn complete(pool: &PgPool, id: i64) -> Result<()> {
    sqlx::query("UPDATE task_queue SET status = 'done' WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await?;
    Ok(())
}

/// Обработать ошибку: attempts+1, при достижении max_attempts → 'dead',
/// иначе 'pending' c next_run_at = now() + backoff.
pub async fn fail_with_retry(pool: &PgPool, id: i64, max_attempts: i32) -> Result<TaskStatus> {
    // Читаем текущее число попыток (в проде — атомарный UPDATE с CTE;
    // здесь двухшагово для наглядности — тесты однопоточны).
    let attempts: i32 = sqlx::query_scalar("SELECT attempts FROM task_queue WHERE id = $1")
        .bind(id)
        .fetch_one(pool)
        .await?;

    let new_attempts = attempts + 1;
    let status = if new_attempts >= max_attempts {
        TaskStatus::Dead
    } else {
        TaskStatus::Pending
    };
    let backoff_secs = backoff(new_attempts).as_secs() as i64;

    // next_run_at — момент, когда задача снова «созреет» (бэкoff).
    sqlx::query(
        "UPDATE task_queue
            SET attempts = $2,
                status   = $3,
                next_run_at = now() + make_interval(secs => $4)
          WHERE id = $1",
    )
    .bind(id)
    .bind(new_attempts)
    .bind(status.as_str())
    .bind(backoff_secs)
    .execute(pool)
    .await?;

    Ok(status)
}
