//! Упражнение 01: «Очередь задач» — воркер, ретраи, dead-letter (кейс 7).
//!
//! **Кейс:** 7 (очередь задач на Postgres). **Модуль:** 10. **Время:** 2.5 ч.
//!
//! ТЗ (модуль 03 → теперь на Rust): таблица `task_queue`; воркеры берут задачи
//! пачками через `FOR UPDATE SKIP LOCKED`, выполняют и отмечают; при ошибке —
//! ретрай с экспоненциальным бэкoff, после `max_attempts` — в dead-letter.
//!
//! Реализуй:
//! 1. `backoff(attempts)` — чистая функция (экспоненциальная задержка);
//! 2. `claim_batch` — CTE: `WHERE status='pending' AND next_run_at <= now()
//!    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT $1`, затем `UPDATE … 'processing'
//!    RETURNING …` (два воркера не заберут одну задачу — SKIP LOCKED!);
//! 3. `complete` — статус 'done';
//! 4. `fail_with_retry` — attempts+1; если >= max_attempts → 'dead', иначе
//!    'pending' с `next_run_at = now() + backoff`.
//!
//! Проверка: `cargo test ex01_` (бэкoff-математика + интеграция: 3 задачи → done/pending/dead).
//!
//! Решение — в `solutions/ex01_queue.rs`.

use anyhow::Result;
use serde_json::Value;
use sqlx::PgPool;
use std::time::Duration;

/// Статус задачи (как в БД: 'pending' | 'processing' | 'done' | 'failed' | 'dead').
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

/// Задача из очереди.
#[derive(Debug)]
pub struct Task {
    pub id: i64,
    pub kind: String,
    pub payload: Value,
    pub status: TaskStatus,
    pub attempts: i32,
}

/// Экспоненциальный бэкoff: `base * 2^(attempts-1)` (base = 10 с).
pub fn backoff(_attempts: i32) -> Duration {
    todo!("10с * 2^(attempts-1)")
}

/// Забрать пачку задач в обработку (SKIP LOCKED — без двойного захвата).
pub async fn claim_batch(_pool: &PgPool, _limit: i32) -> Result<Vec<Task>> {
    todo!("CTE: FOR UPDATE SKIP LOCKED → UPDATE status='processing' RETURNING …")
}

/// Отметить задачу выполненной.
pub async fn complete(_pool: &PgPool, _id: i64) -> Result<()> {
    todo!("UPDATE task_queue SET status='done' WHERE id=$1")
}

/// Обработать ошибку: attempts+1, при достижении max_attempts → 'dead',
/// иначе 'pending' c next_run_at = now() + backoff. Возвращает новый статус.
pub async fn fail_with_retry(_pool: &PgPool, _id: i64, _max_attempts: i32) -> Result<TaskStatus> {
    todo!("читаем attempts → инкремент → статус/бэкoff → UPDATE")
}
