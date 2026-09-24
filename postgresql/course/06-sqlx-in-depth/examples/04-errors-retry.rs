//! Пример 04: ошибки БД — классификация (thiserror) и retry с задержкой.
//!
//! Запуск: `cargo run --example 04-errors-retry`
//!
//! Главное правило ретраев: крутить надо ТОЛЬКО транзиентные ошибки
//! (serialization failure, потеря соединения, таймауты). Уникальность/CHECK —
//! это баги данных: ретрай не поможет, только замаскирует.

use anyhow::{anyhow, Result};
use pg_course_module_06::{classify, new_pool, retry, DbError};
use std::time::Duration;

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // 1. UniqueViolation: тег devices UNIQUE — вставляем дубль.
    let err = sqlx::query(
        "INSERT INTO devices (line_id, device_type, tag, model)
         VALUES (1, 'mass_meter', 'M-01-001', 'дубль')",
    )
    .execute(&pool)
    .await
    .expect_err("дубль обязан упасть");
    match classify(err) {
        DbError::UniqueViolation(msg) => println!("UniqueViolation: {msg}"),
        other => println!("неожиданный тип: {other:?}"),
    }

    // 2. CheckViolation: неверный device_type.
    let err = sqlx::query(
        "INSERT INTO devices (line_id, device_type, tag, model)
         VALUES (1, 'warp_drive', 'X-01-001', 'модель')",
    )
    .execute(&pool)
    .await
    .expect_err("warp_drive не в CHECK");
    match classify(err) {
        DbError::CheckViolation(msg) => println!("CheckViolation: {msg}"),
        other => println!("неожиданный тип: {other:?}"),
    }

    // 3. Retry-хелпер: «транзиентная» ошибка первые 2 раза, потом успех.
    let calls = std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0));
    let value: u32 = retry(5, Duration::from_millis(20), || {
        let calls = calls.clone();
        async move {
            let n = calls.fetch_add(1, std::sync::atomic::Ordering::SeqCst) + 1;
            if n < 3 {
                Err(anyhow!("сегодня не вышло (транзиентно)"))
            } else {
                Ok(42)
            }
        }
    })
    .await?;
    println!(
        "retry: потребовалось {n_calls} попыток, результат = {value}",
        n_calls = calls.load(std::sync::atomic::Ordering::SeqCst)
    );

    // 4. Ретраить НАДО то, что реально транзиентно:
    //    serialization failure (модуль 03) — конфликт конкурентных
    //    транзакций, повтор через некоторое время — корректное лечение.
    //    Для упражнения — демо на счётчике, как в (3), но с задержкой,
    //    растущей экспоненциально (это делает сам хелпер retry).

    Ok(())
}
