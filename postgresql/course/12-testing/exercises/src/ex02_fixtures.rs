//! Упражнение 02: фикстуры и тест SQL-функции (урок 02).
//!
//! **Модуль:** 12. **Время:** 1.5 ч.
//!
//! ТЗ: интеграционный тест `tests/pg_integration.rs` использует две функции:
//! `insert_fixtures` (пары product+order) и `call_order_total` (вызов SQL-функции
//! `order_total` из миграции 0002). Реализуй их; тест прогоняется против
//! НАСТОЯЩЕГО PostgreSQL в контейнере (testcontainers) с применёнными миграциями.
//!
//! Проверка: `cargo test --test pg_integration`.
//!
//! Решение — в `solutions/ex02_fixtures.rs`.

use anyhow::Result;
use rust_decimal::Decimal;
use sqlx::PgPool;

/// Вставить фикстуры: product (sku, name, price) + order (qty).
/// Возвращает (product_id, order_id).
pub async fn insert_fixtures(
    _pool: &PgPool,
    _sku: &str,
    _name: &str,
    _price: &str,
    _qty: i32,
) -> Result<(i32, i32)> {
    todo!("INSERT product RETURNING id; INSERT order (product_id, qty) RETURNING id")
}

/// Вызвать SQL-функцию `order_total` (миграция 0002) для заказа.
pub async fn call_order_total(_pool: &PgPool, _order_id: i32) -> Result<Decimal> {
    todo!("SELECT order_total($1) — как Decimal")
}
