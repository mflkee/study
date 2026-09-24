//! Пример 01: минимальный запрос на `tokio-postgres` (для сравнения).
//!
//! Запуск (после накатки схемы): `cargo run --example 01-tokio-postgres-hello`
//!
//! Что важно увидеть:
//! - `connect` возвращает ПАРУ: клиент и фоновую задачу протокола.
//!   Задачу надо обязательно `tokio::spawn` — без неё клиент «молчит».
//! - Чтение колонок — по индексу `row.get(0)`, типы проверяются только
//!   во время выполнения. Это главное отличие от sqlx (пример 02).

use anyhow::Result;
use tokio_postgres::{connect, NoTls};

#[tokio::main]
async fn main() -> Result<()> {
    let url = pg_course_module_04::database_url();

    // (client, connection): connection — фоновый цикл сообщений протокола.
    let (client, connection) = connect(&url, NoTls).await?;
    tokio::spawn(async move {
        if let Err(e) = connection.await {
            eprintln!("ошибка соединения: {e}");
        }
    });

    // Обычный параметризованный запрос: `&[]` — пустые параметры.
    let rows = client
        .query("SELECT tag, model FROM devices ORDER BY id", &[])
        .await?;

    for row in rows {
        // Тип указываешь сам при чтении: компилятор поверит на слово.
        let tag: &str = row.get(0);
        let model: Option<&str> = row.get(1);
        println!("{tag}: {}", model.unwrap_or("-"));
    }

    Ok(())
}
