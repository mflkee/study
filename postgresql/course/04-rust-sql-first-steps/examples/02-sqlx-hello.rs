//! Пример 02: тот же запрос на `sqlx` (основной стек курса, D2).
//!
//! Запуск: `cargo run --example 02-sqlx-hello`
//!
//! Отличия от примера 01:
//! - Работаем с ПУЛОМ соединений (`PgPool`), а не одним клиентом.
//! - Тип результата задаётся в коде: `Vec<(String, Option<String>)>`,
//!   sqlx проверяет совместимость типов на этапе выполнения запроса.
//! - NULL-колонка — просто `Option<T>`.

use anyhow::Result;
use sqlx::postgres::PgPoolOptions;

#[tokio::main]
async fn main() -> Result<()> {
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&pg_course_module_04::database_url())
        .await?;

    // Тип строки выведен из аннотации переменной: (tag, model).
    let rows: Vec<(String, Option<String>)> =
        sqlx::query_as("SELECT tag, model FROM devices ORDER BY id")
            .fetch_all(&pool)
            .await?;

    for (tag, model) in rows {
        println!("{tag}: {}", model.as_deref().unwrap_or("-"));
    }

    Ok(())
}
