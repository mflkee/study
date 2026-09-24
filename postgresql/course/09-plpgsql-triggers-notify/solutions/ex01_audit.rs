//! Решение упражнения 01: «Журнал аудита с защитой от подмены».
//! Скопируйте содержимое в `exercises/src/ex01_audit.rs` после попытки.

use anyhow::{anyhow, Result};
use sha2::{Digest, Sha256};

/// Зерно цепочки — обязано совпадать с `audit_seed()` в БД (00-setup.sql).
pub const AUDIT_SEED: &str = "seed_СИКН-2026-v1";

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

/// Хэш строки журнала: sha256(prev|entity|entity_id|action|data|ts) hex —
/// ровно как `audit_chain_hash` в БД.
pub fn row_hash(
    prev: &str,
    entity: &str,
    entity_id: i32,
    action: &str,
    data_txt: &str,
    ts_txt: &str,
) -> String {
    let joined = format!("{prev}|{entity}|{entity_id}|{action}|{data_txt}|{ts_txt}");
    hex::encode(Sha256::digest(joined.as_bytes()))
}

/// Проверить цепочку слева направо.
pub fn verify_chain(rows: &[AuditRow]) -> Result<()> {
    for (i, r) in rows.iter().enumerate() {
        if i == 0 {
            if r.prev_hash != AUDIT_SEED {
                return Err(anyhow!(
                    "строка {}: зерно цепочки не совпадает (подменено начало)",
                    r.id
                ));
            }
        } else if r.prev_hash != rows[i - 1].row_hash {
            return Err(anyhow!(
                "строка {}: prev_hash не сходится с предыдущей (разрыв/вырезана строка)",
                r.id
            ));
        }

        let expected = row_hash(
            &r.prev_hash,
            &r.entity,
            r.entity_id,
            &r.action,
            &r.data_txt,
            &r.ts_txt,
        );
        if expected != r.row_hash {
            return Err(anyhow!(
                "строка {}: row_hash не совпадает — данные могли быть подменены",
                r.id
            ));
        }
    }
    Ok(())
}

/// Прочитать журнал из БД (data/ts как ::text — чтобы хэши сходились).
pub async fn audit_chain_from_db(pool: &sqlx::PgPool) -> Result<Vec<AuditRow>> {
    let rows = sqlx::query_as::<_, (i64, String, i32, String, String, String, String, String)>(
        "SELECT id, entity, entity_id, action, data::text, ts::text, prev_hash, row_hash
           FROM audit_log ORDER BY id",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows
        .into_iter()
        .map(
            |(id, entity, entity_id, action, data_txt, ts_txt, prev_hash, row_hash)| AuditRow {
                id,
                entity,
                entity_id,
                action,
                data_txt,
                ts_txt,
                prev_hash,
                row_hash,
            },
        )
        .collect())
}
