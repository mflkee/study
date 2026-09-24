//! Общая библиотека модуля 09 «PL/pgSQL, триггеры, аудит, NOTIFY».

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

// --- Упражнения ---
#[path = "../exercises/src/ex01_audit.rs"]
pub mod ex01_audit;

#[path = "../exercises/src/ex02_events.rs"]
pub mod ex02_events;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ex01_audit::{row_hash, verify_chain, AuditRow};
    use crate::ex02_events::parse_payload;

    // --- упражнение 01: цепочка хэшей (чистая часть) ---

    #[test]
    fn ex01_row_hash_deterministic() {
        let h1 = row_hash(
            "seed",
            "devices",
            1,
            "INSERT",
            "{}",
            "2026-09-24 10:00:00+00",
        );
        let h2 = row_hash(
            "seed",
            "devices",
            1,
            "INSERT",
            "{}",
            "2026-09-24 10:00:00+00",
        );
        assert_eq!(h1, h2, "хэш детерминирован");
        assert_eq!(h1.len(), 64, "sha256 hex = 64 символа");
    }

    #[test]
    fn ex01_verify_chain_ok_and_broken() {
        // Честная цепочка из 3 строк (хэши считаем так же, как в решении).
        let seed = "seed_СИКН-2026-v1".to_string();
        let mut rows = Vec::new();
        let mut prev = seed.clone();
        for i in 1..=3i64 {
            let r = AuditRow {
                id: i,
                entity: "devices".into(),
                entity_id: 1,
                action: "INSERT".into(),
                data_txt: format!("{{\"tag\": \"d{i}\"}}"),
                ts_txt: format!("t{i}"),
                prev_hash: prev.clone(),
                row_hash: String::new(),
            };
            let rh = row_hash(
                &r.prev_hash,
                &r.entity,
                r.entity_id,
                &r.action,
                &r.data_txt,
                &r.ts_txt,
            );
            rows.push(AuditRow {
                row_hash: rh.clone(),
                ..r
            });
            prev = rh;
        }
        assert!(verify_chain(&rows).is_ok(), "честная цепочка проходит");

        // Взлом: изменили data во второй строке → цепочка рвётся.
        let mut tampered = rows.clone();
        tampered[1].data_txt = "{\"tag\": \"hacked\"}".into();
        assert!(
            verify_chain(&tampered).is_err(),
            "подменённая строка ловится"
        );

        // Взлом: вырвали строку из середины → prev_hash новой «первой» не равна зерну.
        let cut: Vec<AuditRow> = rows.into_iter().filter(|r| r.id != 2).collect();
        assert!(verify_chain(&cut).is_err(), "вырезанная строка ловится");
    }

    // --- упражнение 02: разбор payload (чистая часть) ---

    #[test]
    fn ex02_parse_payload_ok() {
        let ev = parse_payload(
            r#"{"id": 5, "tag": "M-01-001", "op": "INSERT", "ts": "2026-09-24T10:00:00Z"}"#,
        )
        .expect("валидный json");
        assert_eq!(ev.id, 5);
        assert_eq!(ev.tag, "M-01-001");
        assert_eq!(ev.op, "INSERT");
    }

    #[test]
    fn ex02_parse_payload_invalid() {
        assert!(parse_payload("not json").is_err());
        assert!(parse_payload(r#"{"id": "oops"}"#).is_err(), "id не число");
    }

    // --- интеграционные тесты (нужен стенд + 00-setup.sql) ---

    #[tokio::test]
    async fn ex01_audit_chain_integration() {
        use crate::ex01_audit::audit_chain_from_db;
        let pool = new_pool(5).await.expect("стенд");
        let rows = audit_chain_from_db(&pool).await.expect("чтение журнала");
        assert!(
            !rows.is_empty(),
            "в журнале есть записи (00-setup + операции)"
        );
        verify_chain(&rows).expect("данные в БД честны");
    }

    #[tokio::test]
    async fn ex02_listener_receives_notify() {
        use sqlx::postgres::PgListener;
        let pool = new_pool(5).await.expect("стенд");
        // Идемпотентность: убираем остатки прошлых прогонов, тег уникальный.
        sqlx::query("DELETE FROM devices WHERE tag LIKE 'FC-09-tst%'")
            .execute(&pool)
            .await
            .expect("pre-cleanup");
        let tag = format!(
            "FC-09-tst-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis()
        );
        let mut listener = PgListener::connect(&database_url())
            .await
            .expect("listener");
        listener.listen("device_changed").await.expect("listen");
        // Триггер devices_notify сработает на INSERT и пошлёт NOTIFY.
        let id: i32 = sqlx::query_scalar(
            "INSERT INTO devices (line_id, device_type, tag, model)
             VALUES (1, 'flow_calc', $1, 'test') RETURNING id",
        )
        .bind(&tag)
        .fetch_one(&pool)
        .await
        .expect("insert (триггеры отработают)");
        let notif = tokio::time::timeout(Duration::from_secs(5), listener.recv())
            .await
            .expect("нотификация в 5 с")
            .expect("recv");
        assert_eq!(notif.channel(), "device_changed");
        let ev = parse_payload(notif.payload()).expect("payload ок");
        assert_eq!(ev.tag, tag);
        assert_eq!(ev.id, id);
        // Уборка: удаление устройства (триггер уйдёт DELETE-нотификация, не мешает).
        sqlx::query("DELETE FROM devices WHERE id = $1")
            .bind(id)
            .execute(&pool)
            .await
            .expect("cleanup");
    }
}
