//! Общая библиотека модуля 06 «sqlx: пул, транзакции, миграции».
//!
//! Здесь: подключение к базе модуля `course_m06`, типы ошибок (thiserror),
//! хелпер `retry` (экспоненциальная задержка) и включение упражнения.

use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;

/// База модуля — `course_m06` (отдельная от каталога модулей 01–04).
/// Создаётся один раз командой из README (идемпотентно).
pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15432/course_m06?sslmode=disable";

pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

/// Пул с конфигурацией «по-взрослому» (урок 01).
pub async fn new_pool(max: u32) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .min_connections(1)
        .max_connections(max)
        .acquire_timeout(Duration::from_secs(5))
        .idle_timeout(Duration::from_secs(60))
        .connect(&database_url())
        .await?)
}

// --- Ошибки БД (thiserror) ---

/// Ошибки домена «каталог»: понятные сообщения вместо «сырого» sqlx::Error.
#[derive(Debug, thiserror::Error)]
pub enum DbError {
    #[error("запись не найдена: {0}")]
    NotFound(String),
    #[error("нарушение уникальности: {0}")]
    UniqueViolation(String),
    #[error("нарушение ограничения CHECK: {0}")]
    CheckViolation(String),
    #[error("база данных: {0}")]
    Sqlx(#[from] sqlx::Error),
}

/// Различить типичные ошибки PostgreSQL по коду (детально — урок 04).
pub fn classify(e: sqlx::Error) -> DbError {
    if let sqlx::Error::Database(db_err) = &e {
        let kind = db_err.kind();
        let message = db_err.message().to_owned();
        return match kind {
            sqlx::error::ErrorKind::UniqueViolation => DbError::UniqueViolation(message),
            sqlx::error::ErrorKind::CheckViolation => DbError::CheckViolation(message),
            _ => DbError::Sqlx(e),
        };
    }
    DbError::Sqlx(e)
}

// --- Ретрай с экспоненциальной задержкой (урок 01) ---

/// Повторяет `f` до `attempts` раз с экспоненциальной задержкой (base, 2×base, …).
/// Полезно для транзиентных ошибок (перезапуск транзакции при serialization
/// failure, пики нагрузки). Блокирующие ошибки (нарушение CHECK и т.п.)
/// ретраить НЕ надо — решай по типу ошибки (урок 04).
pub async fn retry<F, Fut, T, E>(attempts: u32, base: Duration, mut f: F) -> Result<T, E>
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = Result<T, E>>,
{
    let mut last_err = None;
    for attempt in 0..attempts {
        match f().await {
            Ok(v) => return Ok(v),
            Err(e) => {
                last_err = Some(e);
                if attempt + 1 < attempts {
                    let delay = base * (1u32 << attempt); // base, 2base, 4base...
                    tokio::time::sleep(delay).await;
                }
            }
        }
    }
    Err(last_err.expect("attempts >= 1"))
}

// --- Упражнение: включено в библиотеку, чтобы его можно было тестировать ---
#[path = "../exercises/src/ex01_evolution.rs"]
pub mod ex01_evolution;

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[tokio::test]
    async fn retry_delivers_after_two_failures() {
        let calls = counter();
        let out = retry(3, Duration::from_millis(1), || {
            let calls = calls.clone();
            async move {
                calls.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                if calls.load(std::sync::atomic::Ordering::SeqCst) < 3 {
                    Err::<u32, &str>("ещё не готово")
                } else {
                    Ok(42)
                }
            }
        })
        .await;
        assert_eq!(out, Ok(42));
        assert_eq!(calls.load(std::sync::atomic::Ordering::SeqCst), 3);
    }

    #[tokio::test]
    async fn retry_gives_up_after_attempts() {
        let calls = counter();
        let out = retry(2, Duration::from_millis(1), || {
            let calls = calls.clone();
            async move {
                calls.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                Err::<u32, &str>("всегда падает")
            }
        })
        .await;
        assert!(out.is_err());
        assert_eq!(calls.load(std::sync::atomic::Ordering::SeqCst), 2);
    }

    #[test]
    fn default_url_is_module_db() {
        assert!(
            DEFAULT_DATABASE_URL.contains("course_m06"),
            "своя база модуля"
        );
        assert!(DEFAULT_DATABASE_URL.contains("15432"));
        assert_eq!(database_url(), DEFAULT_DATABASE_URL);
    }

    // --- упражнение 01: эволюция схемы ---
    // Чистая часть — без БД; интеграционная часть применяет миграции к course_m06.

    fn counter() -> std::sync::Arc<std::sync::atomic::AtomicU32> {
        std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0))
    }

    #[test]
    fn ex01_parse_flow_calc() {
        use ex01_evolution::DeviceKind;
        // После решения упражнения парсинг знает новый тип.
        assert_eq!(
            DeviceKind::parse("flow_calc").expect("ok").as_str(),
            "flow_calc"
        );
        assert_eq!(
            DeviceKind::parse("level_meter").expect("ok").as_str(),
            "level_meter"
        );
        assert!(DeviceKind::parse("unknown").is_err());
    }

    #[tokio::test]
    async fn ex01_migration_and_insert_flow_calc() {
        use ex01_evolution::add_flow_calc_device;
        let pool = new_pool(5).await.expect("стенд курса должен быть поднят");

        // Применяем миграции модуля (0001, 0002 и твою 0003, если написана).
        sqlx::migrate!("./migrations")
            .run(&pool)
            .await
            .expect("миграции применяются");

        // Зачистка от возможных остатков прошлых прогонов (тест идемпотентный).
        sqlx::query("DELETE FROM devices WHERE tag LIKE 'FC-99-%'")
            .execute(&pool)
            .await
            .expect("pre-cleanup");

        // 1) CHECK-ограничение обязано знать новый тип (это даёт миграция 0003).
        let knows_flow_calc: bool = sqlx::query_scalar(
            "SELECT pg_get_constraintdef(oid) LIKE '%flow_calc%'
               FROM pg_constraint
              WHERE conname = 'devices_device_type_check'",
        )
        .fetch_one(&pool)
        .await
        .expect("constraint существует");
        assert!(
            knows_flow_calc,
            "напиши миграцию migrations/0003_add_flow_calc.sql (расширь CHECK на 'flow_calc')"
        );

        // 2) вставка устройства нового типа проходит.
        add_flow_calc_device(&pool, "ЛИНИЯ-1", "FC-99-001", "FCC-900")
            .await
            .expect("вставка flow_calc после миграции");
        let n: i64 = sqlx::query_scalar("SELECT count(*) FROM devices WHERE tag = 'FC-99-001'")
            .fetch_one(&pool)
            .await
            .expect("счёт");
        assert_eq!(n, 1, "устройство нового типа на месте");

        // 3) за собой убираем (тест идемпотентный).
        sqlx::query("DELETE FROM devices WHERE tag = 'FC-99-001'")
            .execute(&pool)
            .await
            .expect("cleanup");
    }
}
