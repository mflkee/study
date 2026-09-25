//! REST API Historian (модуль 11): текущие значения и тренды для дашборда.
//!
//! Запуск: `cargo run --bin api [--port 3095]`

use anyhow::Result;
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use capstone_historian::{current_value, new_pool, trends, CurrentDto, TrendDto};
use serde::{Deserialize, Serialize};
use sqlx::PgPool;
use tracing_subscriber::EnvFilter;

#[derive(Clone)]
struct AppState {
    pool: PgPool,
}

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
struct ApiError {
    status: StatusCode,
    message: String,
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (
            self.status,
            Json(serde_json::json!({ "error": self.message })),
        )
            .into_response()
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(e: sqlx::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            message: format!("БД: {e}"),
        }
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(e: anyhow::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            message: format!("{e:#}"),
        }
    }
}

type ApiResult<T> = std::result::Result<T, ApiError>;

#[derive(sqlx::FromRow, Serialize)]
struct DeviceDto {
    id: i32,
    device_type: String,
    tag: String,
    model: Option<String>,
}

async fn list_devices(State(s): State<AppState>) -> ApiResult<Json<Vec<DeviceDto>>> {
    let rows: Vec<DeviceDto> =
        sqlx::query_as("SELECT id, device_type, tag, model FROM devices ORDER BY tag")
            .fetch_all(&s.pool)
            .await?;
    Ok(Json(rows))
}

async fn current(
    State(s): State<AppState>,
    Path(tag): Path<String>,
) -> ApiResult<Json<CurrentDto>> {
    let v = current_value(&s.pool, &tag)
        .await?
        .ok_or_else(|| ApiError {
            status: StatusCode::NOT_FOUND,
            message: format!("нет данных: {tag}"),
        })?;
    Ok(Json(v))
}

#[derive(Deserialize)]
struct TrendsQuery {
    bucket: Option<String>,
    hours: Option<i64>,
}

async fn trends_ep(
    State(s): State<AppState>,
    Path(tag): Path<String>,
    Query(q): Query<TrendsQuery>,
) -> ApiResult<Json<Vec<TrendDto>>> {
    let bucket = q.bucket.unwrap_or_else(|| "1 hour".into());
    let hours = q.hours.unwrap_or(6);
    Ok(Json(trends(&s.pool, &tag, &bucket, hours).await?))
}

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok" }))
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .init();
    let port: u16 = std::env::args()
        .position(|a| a == "--port")
        .and_then(|i| std::env::args().nth(i + 1))
        .and_then(|s| s.parse().ok())
        .unwrap_or(3095);
    let pool = new_pool(5).await?;
    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/devices", get(list_devices))
        .route("/devices/{tag}/current", get(current))
        .route("/devices/{tag}/trends", get(trends_ep))
        .with_state(AppState { pool })
        .layer(tower_http::trace::TraceLayer::new_for_http());
    let listener = tokio::net::TcpListener::bind(("127.0.0.1", port)).await?;
    println!("Historian API на http://127.0.0.1:{port}");
    axum::serve(listener, app).await?;
    Ok(())
}
