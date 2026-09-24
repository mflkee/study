//! Пример 05: проверка целостности аудит-цепочки из Rust (кейс 4).
//!
//! Запуск: `cargo run --example 05-audit-verify`
//!
//! Читает `audit_log` из course_m06 и пересчитывает цепочку хэшей
//! (тот же алгоритм `audit_chain_hash`, что в БД). Если данные подменил
//! кто-то с прямым доступом — проверка падает с указанием строки.
//! Это эталонная реализация (упражнение 01 — «напиши сам» по этому образцу).

use anyhow::{anyhow, Result};
use pg_course_module_09::new_pool;
use sha2::{Digest, Sha256};

/// Зерно цепочки — совпадает с `audit_seed()` в 00-setup.sql.
const AUDIT_SEED: &str = "seed_СИКН-2026-v1";

#[derive(Debug)]
struct Row {
    id: i64,
    entity: String,
    entity_id: i32,
    action: String,
    data_txt: String,
    ts_txt: String,
    prev_hash: String,
    row_hash: String,
}

fn row_hash(prev: &str, entity: &str, id: i32, action: &str, data: &str, ts: &str) -> String {
    hex::encode(Sha256::digest(
        format!("{prev}|{entity}|{id}|{action}|{data}|{ts}").as_bytes(),
    ))
}

fn verify(rows: &[Row]) -> std::result::Result<(), String> {
    for (i, r) in rows.iter().enumerate() {
        if i == 0 && r.prev_hash != AUDIT_SEED {
            return Err(format!("строка {}: зерно неверное", r.id));
        }
        if i > 0 && r.prev_hash != rows[i - 1].row_hash {
            return Err(format!("строка {}: разрыв цепочки", r.id));
        }
        let expect = row_hash(
            &r.prev_hash,
            &r.entity,
            r.entity_id,
            &r.action,
            &r.data_txt,
            &r.ts_txt,
        );
        if expect != r.row_hash {
            return Err(format!("строка {}: данные подменены (хэш не совпал)", r.id));
        }
    }
    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;
    let rows: Vec<Row> =
        sqlx::query_as::<_, (i64, String, i32, String, String, String, String, String)>(
            "SELECT id, entity, entity_id, action, data::text, ts::text, prev_hash, row_hash
           FROM audit_log ORDER BY id",
        )
        .fetch_all(&pool)
        .await?
        .into_iter()
        .map(
            |(id, entity, entity_id, action, data_txt, ts_txt, prev_hash, row_hash)| Row {
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
        .collect();

    println!("строк журнала: {}", rows.len());
    match verify(&rows) {
        Ok(()) => {
            println!(
                "целостность: OK — цепочка хэшей согласована ({} записей)",
                rows.len()
            );
            Ok(())
        }
        Err(e) => Err(anyhow!("ЦЕЛОСТНОСТЬ НАРУШЕНА: {e}")),
    }
}
