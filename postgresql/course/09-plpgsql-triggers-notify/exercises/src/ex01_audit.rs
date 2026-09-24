//! Упражнение 01: «Журнал аудита с защитой от подмены» (кейс 4).
//!
//! **Кейс:** 4 (аудит с защитой от подмены). **Модуль:** 09. **Время:** 2 ч.
//!
//! ТЗ: на стенде 00-setup.sql создаёт `audit_log` с цепочкой хэшей:
//! `row_hash = sha256(prev_hash | entity | entity_id | action | data | ts)`,
//! зерно цепочки — `seed_СИКН-2026-v1` (функция `audit_seed()` в БД).
//! UPDATE/DELETE журнала запрещены триггером — но данные может подменить
//! тот, у кого есть доступ к таблице напрямую. Твоя задача — проверка
//! целостности из Rust:
//!
//! 1. `row_hash` — посчитать хэш строки (тот же алгоритм, что в БД);
//! 2. `verify_chain` — проверить всю цепочку: зерно первой строки,
//!    связку prev_hash ↔ предыдущий row_hash, и собственные хэши.
//!
//! Проверка: `cargo test ex01_` — чистая часть (детерминизм, взлом строки,
//! вырезанная строка) + интеграционная (честны ли данные в course_m06).
//!
//! Решение — в `solutions/ex01_audit.rs`.

use anyhow::Result;

/// Зерно цепочки — ОБЯЗАНО совпадать с `audit_seed()` в БД (00-setup.sql).
pub const AUDIT_SEED: &str = "seed_СИКН-2026-v1";

/// Строка журнала (уже прочитанная из БД; text-представления data/ts сохранены,
/// чтобы повторно «склеить» строку хэширования БЕЗ переформатирования).
#[derive(Debug, Clone)]
pub struct AuditRow {
    pub id: i64,
    pub entity: String,
    pub entity_id: i32,
    pub action: String,
    pub data_txt: String,
    pub ts_txt: String,
    pub prev_hash: String,
    pub row_hash: String,
}

/// Хэш строки журнала: sha256(prev|entity|entity_id|action|data|ts) hex.
/// Напоминание (урок 03): алгоритм должен совпадать с SQL-функцией
/// `audit_chain_hash` — склейка через `|`.
pub fn row_hash(
    _prev: &str,
    _entity: &str,
    _entity_id: i32,
    _action: &str,
    _data_txt: &str,
    _ts_txt: &str,
) -> String {
    todo!("sha256 склейки через '|' → hex (sha2 + hex)")
}

/// Проверить цепочку слева направо.
/// Ошибка (Err с описанием) при: неверном зерне первой строки, разрыве
/// prev_hash ↔ row_hash, несовпадении собственного хэша (подмена данных).
pub fn verify_chain(_rows: &[AuditRow]) -> Result<()> {
    todo!("зерно → связка → собственные хэши; верни anyhow-ошибку при разрыве")
}

/// Прочитать журнал из БД (data/ts как ::text — чтобы хэши сходились).
pub async fn audit_chain_from_db(_pool: &sqlx::PgPool) -> Result<Vec<AuditRow>> {
    todo!("SELECT id, entity, entity_id, action, data::text, ts::text, prev_hash, row_hash FROM audit_log ORDER BY id")
}
