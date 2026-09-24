//! Решение упражнения 02: фикстуры и тест SQL-функции.
//! Скопируйте содержимое в `exercises/src/ex02_fixtures.rs` после попытки.

use anyhow::Result;
use rust_decimal::Decimal;
use sqlx::PgPool;
use std::str::FromStr;

/// Вставить фикстуры: product (sku, name, price) + order (qty).
pub async fn insert_fixtures(
    pool: &PgPool,
    sku: &str,
    name: &str,
    price: &str,
    qty: i32,
) -> Result<(i32, i32)> {
    let price = Decimal::from_str(price)?;
    let product_id: i32 = sqlx::query_scalar(
        "INSERT INTO products (sku, name, price) VALUES ($1, $2, $3) RETURNING id",
    )
    .bind(sku)
    .bind(name)
    .bind(price)
    .fetch_one(pool)
    .await?;
    let order_id: i32 =
        sqlx::query_scalar("INSERT INTO orders (product_id, qty) VALUES ($1, $2) RETURNING id")
            .bind(product_id)
            .bind(qty)
            .fetch_one(pool)
            .await?;
    Ok((product_id, order_id))
}

/// Вызвать SQL-функцию `order_total` (миграция 0002) для заказа.
pub async fn call_order_total(pool: &PgPool, order_id: i32) -> Result<Decimal> {
    let total: Decimal = sqlx::query_scalar("SELECT order_total($1)")
        .bind(order_id)
        .fetch_one(pool)
        .await?;
    Ok(total)
}
