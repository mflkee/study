//! Интеграционные тесты капстоуна на НАСТОЯЩЕМ PostgreSQL (testcontainers, модуль 12).
//! Покрывают ключевые требования: историчность данных (партиции + идемпотентность),
//! аудит-цепочка (модуль 09), API-запросы (модуль 11).
//!
//! Запуск: `cargo test --test capstone_integration`

use capstone_historian::{
    audit_chain_from_db, batch_deliver, current_value, ensure_partitions, trends, verify_chain,
    MeteringPoint,
};
use chrono::Utc;
use rust_decimal::Decimal;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use testcontainers::{runners::AsyncRunner, ContainerAsync};
use testcontainers_modules::postgres::Postgres;

async fn fresh_pg() -> (ContainerAsync<Postgres>, PgPool) {
    let container = Postgres::default().start().await.expect("контейнер PG");
    let port = container.get_host_port_ipv4(5432).await.expect("порт");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPoolOptions::new()
        .max_connections(4)
        .connect(&url)
        .await
        .expect("пул");
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("миграции");
    (container, pool)
}

fn pt(device_id: i32, seq: i64, hours_ago: i64) -> MeteringPoint {
    MeteringPoint {
        device_id,
        seq,
        ts: Utc::now() - chrono::Duration::hours(hours_ago),
        value: Decimal::from(1000 + seq),
    }
}

#[tokio::test]
async fn capstone_partitions_and_seed() {
    let (_c, pool) = fresh_pg().await;
    // Партиции созданы миграцией (историчность: схема диапазонов готова).
    for part in ["measurements_hist_2026_09", "measurements_hist_2026_10"] {
        let has: bool = sqlx::query_scalar("SELECT to_regclass($1) IS NOT NULL")
            .bind(part)
            .fetch_one(&pool)
            .await
            .unwrap();
        assert!(has, "партиция {part} существует");
    }
    // ensure_partitions создаёт недостающие месяца без ошибок (авто-мост).
    ensure_partitions(&pool, Utc::now(), Utc::now() + chrono::Duration::days(60))
        .await
        .expect("автосоздание партиций");
    // Каталог посеян.
    let n: i64 = sqlx::query_scalar("SELECT count(*) FROM devices")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert!(n >= 2, "засеяны устройства (найдено {n})");
}

#[tokio::test]
async fn capstone_history_idempotent() {
    let (_c, pool) = fresh_pg().await;
    let pts = vec![
        pt(1, 1, 25), // 25 часов назад → сентябрьская партиция по датам
        pt(1, 2, 3),
        pt(2, 1, 1),
    ];
    let first = batch_deliver(&pool, &pts).await.expect("первая доставка");
    assert_eq!(first, 3, "первый приём — 3 новые строки");

    // Повторная доставка — 0 новых (историчность: UNIQUE device_id+seq).
    let again = batch_deliver(&pool, &pts).await.expect("повтор");
    assert_eq!(again, 0, "нет дублей (ON CONFLICT)");

    let total: i64 = sqlx::query_scalar("SELECT count(*) FROM measurements_hist")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(total, 3, "в историческом архиве ровно 3 строки");
}

#[tokio::test]
async fn capstone_audit_chain_and_deny() {
    let (_c, pool) = fresh_pg().await;
    // Изменение каталога → аудит-цепочка растёт и проверяется.
    let id: i32 = sqlx::query_scalar(
        "INSERT INTO devices (device_type, tag, model) VALUES ('mass_meter','M-TST','X') RETURNING id",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    let rows = audit_chain_from_db(&pool).await.expect("цепочка");
    assert!(
        rows.len() >= 2,
        "аудит записал (seed INSERT отсутствует — только новые): {}",
        rows.len()
    );
    verify_chain(&rows).expect("цепочка честна");

    // Защита журнала: UPDATE запрещён триггером.
    let upd = sqlx::query("UPDATE audit_log SET action = 'hack' WHERE id = 1")
        .execute(&pool)
        .await;
    assert!(upd.is_err(), "аудит защищён от подмены");

    // Вторая запись → цепочка не разрывается.
    let _id2: i32 = sqlx::query_scalar(
        "INSERT INTO devices (device_type, tag, model) VALUES ('density_meter','D-TST','Y') RETURNING id",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    let rows2 = audit_chain_from_db(&pool).await.unwrap();
    verify_chain(&rows2).expect("цепочка после второй записи");
    sqlx::query("DELETE FROM devices WHERE id = $1")
        .bind(id)
        .execute(&pool)
        .await
        .unwrap();
}

#[tokio::test]
async fn capstone_api_queries() {
    let (_c, pool) = fresh_pg().await;
    for i in 0..4 {
        batch_deliver(&pool, &[pt(1, 100 + i, i)]).await.unwrap();
    }
    let cur = current_value(&pool, "M-01-001")
        .await
        .unwrap()
        .expect("current есть");
    assert_eq!(cur.tag, "M-01-001");
    let tr = trends(&pool, "M-01-001", "1 hour", 24).await.unwrap();
    assert!(!tr.is_empty(), "тренд непустой");
}
