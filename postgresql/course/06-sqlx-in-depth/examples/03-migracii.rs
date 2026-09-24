//! Пример 03: миграции — `sqlx::migrate!()` применяет схемы одной строкой.
//!
//! Запуск: `cargo run --example 03-migracii`
//!
//! Миграции лежат в `migrations/` (0001_…, 0002_…, и твоя 0003 из упражнения).
//! Встроенный макрос `sqlx::migrate!("migrations")` загружает их в бинарник
//! и применяет только ещё не применённые (версии хранятся в `_sqlx_migrations`).
//! Альтернатива — CLI: `sqlx migrate run` (требует sqlx-cli, он в окружении курса).

use anyhow::Result;
use pg_course_module_06::new_pool;

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // Если таблица миграций ещё не создана — первый запрос упадёт, ловим.
    let before: Result<(i64,), sqlx::Error> =
        sqlx::query_scalar("SELECT count(*) FROM _sqlx_migrations")
            .fetch_one(&pool)
            .await;
    println!(
        "миграций в базе до запуска: {}",
        before.map(|(n,)| n).unwrap_or(0)
    );

    // Применяем недостающие миграции (идемпотентно: повторный запуск не ломает).
    sqlx::migrate!("./migrations").run(&pool).await?;

    let applied: Vec<(i64, String, bool)> = sqlx::query_as(
        "SELECT version, description, success FROM _sqlx_migrations ORDER BY version",
    )
    .fetch_all(&pool)
    .await?;

    println!("после запуска в базе миграций: {}", applied.len());
    for (version, description, success) in applied {
        println!("  v{version}: {description} (success={success})");
    }

    // Проверка, что 0002 реально добавила уровнемер.
    let level: i64 =
        sqlx::query_scalar("SELECT count(*) FROM devices WHERE device_type = 'level_meter'")
            .fetch_one(&pool)
            .await?;
    println!("устройств level_meter (из миграции 0002): {level}");

    Ok(())
}
