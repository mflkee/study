//! Пример 02: REST API дашборда на axum + sqlx (кейс 9).
//!
//! Запуск:
//!   cargo run --example 02-measurements [--port 3091]
//! Проверка (curl):
//!   curl -s http://127.0.0.1:3091/devices
//!   curl -s http://127.0.0.1:3091/devices/M-01-001/current
//!   curl -s http://127.0.0.1:3091/devices/M-01-001/values?limit=3
//!   curl -s http://127.0.0.1:3091/devices/M-01-001/trends?bucket=6 hours
//!   curl -s http://127.0.0.1:3091/metrics
//!
//! Уроки 01–03: конфигурация (env), tracing-слои, x; эндпоинт /trends — из
//! упражнения 01 (до решения вернёт 500 с «not yet implemented» — так и надо).

use pg_course_module_11::{build_app, new_pool};
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| EnvFilter::new("info,tower_http=debug")),
        )
        .init();

    let port: u16 = std::env::args()
        .nth(1)
        .and_then(|s| s.strip_prefix("--port=").map(str::to_owned))
        .and_then(|s| s.parse().ok())
        .unwrap_or_else(|| {
            std::env::var("PORT")
                .ok()
                .and_then(|p| p.parse().ok())
                .unwrap_or(3091)
        });

    let pool = new_pool(5).await?;
    let app = build_app(pool)?;

    let listener = tokio::net::TcpListener::bind(("127.0.0.1", port)).await?;
    tracing::info!("API на http://127.0.0.1:{port}");
    axum::serve(listener, app).await?;
    Ok(())
}
