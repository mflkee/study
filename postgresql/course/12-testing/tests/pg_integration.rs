//! Интеграционные тесты с НАСТОЯЩИМ PostgreSQL в контейнере (testcontainers).
//!
//! Запуск: `cargo test --test pg_integration` (нужен Docker; каждый тест — свой
//! чистый контейнер, поэтому изоляция полная).
//!
//! Это и есть «лаба: тест миграций» — применяем миграции на СВЕЖУЮ базу и
//! проверяем схему и функции, а не на «живом» стенде курса.

use pg_course_module_12::ex02_fixtures::{call_order_total, insert_fixtures};
use rust_decimal::Decimal;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::str::FromStr;
use testcontainers::{runners::AsyncRunner, ContainerAsync};
use testcontainers_modules::postgres::Postgres;

/// Поднять контейнер PostgreSQL и вернуть пул к нему (URL из хостового порта).
async fn fresh_pg() -> (ContainerAsync<Postgres>, PgPool) {
    let container = Postgres::default().start().await.expect("контейнер PG");
    let port = container.get_host_port_ipv4(5432).await.expect("порт");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPoolOptions::new()
        .max_connections(4)
        .connect(&url)
        .await
        .expect("пул к контейнеру");
    (container, pool)
}

#[tokio::test]
async fn migrations_apply_to_fresh_db() {
    let (_container, pool) = fresh_pg().await;

    // «Тест миграций»: на СВЕЖУЮ базу применяются обе миграции модуля 12.
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("миграции применяются");

    // 1. История миграций: ровно 2, обе success.
    let applied: Vec<(i64, bool)> =
        sqlx::query_as("SELECT version, success FROM _sqlx_migrations ORDER BY version")
            .fetch_all(&pool)
            .await
            .expect("история");
    assert_eq!(applied.len(), 2, "миграции 0001 и 0002 применены");
    assert!(applied.iter().all(|(_, ok)| *ok), "все success");

    // 2. Схема: таблицы products/orders существуют.
    for rel in ["products", "orders"] {
        let has: bool = sqlx::query_scalar("SELECT to_regclass($1) IS NOT NULL")
            .bind(rel)
            .fetch_one(&pool)
            .await
            .expect("regclass");
        assert!(has, "таблица {rel} создана миграцией");
    }

    // 3. Функция order_total существует (миграция 0002).
    let has_fn: bool = sqlx::query_scalar("SELECT to_regprocedure('order_total(int)') IS NOT NULL")
        .fetch_one(&pool)
        .await
        .expect("procedure");
    assert!(has_fn, "функция order_total(int) создана миграцией");
}

#[tokio::test]
async fn order_total_function_with_fixtures() {
    let (_container, pool) = fresh_pg().await;
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("миграции");

    // Фикстуры (упражнение 02): товар 12.5000 × 3 = 37.5000.
    let (product_id, order_id) = insert_fixtures(&pool, "SKU-1", "Массомер", "12.5000", 3)
        .await
        .expect("фикстуры (реши упражнение 02)");
    let total = call_order_total(&pool, order_id).await.expect("функция");
    assert_eq!(
        total,
        Decimal::from_str("37.5000").unwrap(),
        "12.5 × 3 = 37.5"
    );

    // Ошибка на несуществующем заказе — SQL-функция кидает исключение.
    let err = call_order_total(&pool, 999_999).await;
    assert!(err.is_err(), "нет заказа → ошибка функции");

    // Уборка (контейнер уничтожается после drop — фикстуры чистим для порядка).
    sqlx::query("DELETE FROM products WHERE id = $1")
        .bind(product_id)
        .execute(&pool)
        .await
        .expect("clean");
}
