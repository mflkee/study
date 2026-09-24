//! Общая библиотека модуля 11 «REST API на axum + sqlx».
//!
//! `build_app` собирает Router приложения-дашборда (кейс 9): устройства,
//! текущее значение, пагинация значений, тренды. Хендлер трендов и пагинация —
//! из упражнений (`exercises/src/*`), поэтому до их решения тесты/эндпоинты
//! падают — так и задумано.

use anyhow::Result;
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::PgPool;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15432/course_m06?sslmode=disable";

pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

pub async fn new_pool(max: u32) -> Result<PgPool> {
    Ok(sqlx::postgres::PgPoolOptions::new()
        .max_connections(max)
        .acquire_timeout(Duration::from_secs(5))
        .connect(&database_url())
        .await?)
}

// --- состояние приложения ---

#[derive(Clone)]
pub struct AppState {
    pub pool: PgPool,
    pub requests: std::sync::Arc<AtomicU64>,
}

// --- ошибки API ---

/// Ошибка API: статус + JSON-тело `{"error": …}`.
#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct ApiError {
    pub status: StatusCode,
    pub message: String,
}

impl ApiError {
    pub fn not_found(msg: impl Into<String>) -> Self {
        Self {
            status: StatusCode::NOT_FOUND,
            message: msg.into(),
        }
    }
    pub fn bad_request(msg: impl Into<String>) -> Self {
        Self {
            status: StatusCode::BAD_REQUEST,
            message: msg.into(),
        }
    }
    pub fn internal(msg: impl Into<String>) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            message: msg.into(),
        }
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

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let body = Json(serde_json::json!({ "error": self.message }));
        (self.status, body).into_response()
    }
}

pub type ApiResult<T> = std::result::Result<T, ApiError>;

// --- DTO ---

#[derive(sqlx::FromRow, Serialize)]
pub struct DeviceDto {
    pub tag: String,
    pub device_type: String,
    pub model: Option<String>,
}

#[derive(sqlx::FromRow, Serialize)]
pub struct CurrentValueDto {
    pub tag: String,
    pub value: Decimal,
    pub ts: DateTime<Utc>,
    pub quality: i32,
}

#[derive(sqlx::FromRow, Serialize)]
pub struct ValueDto {
    pub ts: DateTime<Utc>,
    pub value: Decimal,
    pub quality: i32,
}

#[derive(Serialize)]
pub struct ValuePage {
    pub tag: String,
    pub items: Vec<ValueDto>,
    pub total: i64,
    pub limit: u32,
    pub offset: u32,
}

// --- хендлеры (готовые) ---

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok" }))
}

async fn list_devices(State(state): State<AppState>) -> ApiResult<Json<Vec<DeviceDto>>> {
    state.requests.fetch_add(1, Ordering::Relaxed);
    let rows: Vec<DeviceDto> =
        sqlx::query_as("SELECT d.tag, d.device_type, d.model FROM devices d ORDER BY d.tag")
            .fetch_all(&state.pool)
            .await?;
    Ok(Json(rows))
}

async fn current_value(
    State(state): State<AppState>,
    Path(tag): Path<String>,
) -> ApiResult<Json<CurrentValueDto>> {
    state.requests.fetch_add(1, Ordering::Relaxed);
    let row = sqlx::query_as::<_, CurrentValueDto>(
        "SELECT d.tag, m.value, m.ts, m.quality
           FROM measurements m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1
          ORDER BY m.ts DESC LIMIT 1",
    )
    .bind(&tag)
    .fetch_optional(&state.pool)
    .await?
    .ok_or_else(|| ApiError::not_found(format!("нет данных для устройства {tag}")))?;
    Ok(Json(row))
}

#[derive(Deserialize, Default)]
pub struct PageQuery {
    pub limit: Option<String>,
    pub offset: Option<String>,
}

async fn values_page(
    State(state): State<AppState>,
    Path(tag): Path<String>,
    Query(q): Query<PageQuery>,
) -> ApiResult<Json<ValuePage>> {
    state.requests.fetch_add(1, Ordering::Relaxed);
    use crate::ex02_page::page_params;
    let (limit, offset) = page_params(q.limit, q.offset);

    sqlx::query_scalar::<_, i32>("SELECT 1 FROM devices WHERE tag = $1")
        .bind(&tag)
        .fetch_optional(&state.pool)
        .await?
        .ok_or_else(|| ApiError::not_found(format!("устройство {tag} не найдено")))?;

    let items: Vec<ValueDto> = sqlx::query_as(
        "SELECT m.ts, m.value, m.quality
           FROM measurements m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1
          ORDER BY m.ts DESC
          LIMIT $2 OFFSET $3",
    )
    .bind(&tag)
    .bind(limit as i64)
    .bind(offset as i64)
    .fetch_all(&state.pool)
    .await?;

    let total: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM measurements m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1",
    )
    .bind(&tag)
    .fetch_one(&state.pool)
    .await?;

    Ok(Json(ValuePage {
        tag,
        items,
        total,
        limit,
        offset,
    }))
}

async fn metrics(State(state): State<AppState>) -> impl IntoResponse {
    let n = state.requests.load(Ordering::Relaxed);
    format!(
        "# HELP requests_total HTTP-запросы к API\n# TYPE requests_total counter\nrequests_total {n}\n"
    )
}

// --- упражнения ---
#[path = "../exercises/src/ex01_trends.rs"]
pub mod ex01_trends;

#[path = "../exercises/src/ex02_page.rs"]
pub mod ex02_page;

/// Готовое приложение API (кейс 9, уроки 01–03).
pub fn build_app(pool: PgPool) -> Result<Router> {
    let state = AppState {
        pool,
        requests: std::sync::Arc::new(AtomicU64::new(0)),
    };
    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/metrics", get(metrics))
        .route("/devices", get(list_devices))
        .route("/devices/{tag}/current", get(current_value))
        .route("/devices/{tag}/values", get(values_page))
        .route("/devices/{tag}/trends", get(ex01_trends::trends_handler))
        .with_state(state)
        .layer(tower_http::trace::TraceLayer::new_for_http());
    Ok(app)
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::{to_bytes, Body};
    use axum::http::Request;
    use serde_json::Value;
    use tower::ServiceExt;

    async fn api_get(app: Router, uri: &str) -> (StatusCode, Value) {
        let resp = app
            .oneshot(Request::builder().uri(uri).body(Body::empty()).unwrap())
            .await
            .expect("запрос");
        let status = resp.status();
        let body = to_bytes(resp.into_body(), usize::MAX).await.expect("тело");
        (status, serde_json::from_slice(&body).unwrap_or(Value::Null))
    }

    // --- упражнение 02: пагинация (чистые кейсы) ---

    #[test]
    fn ex02_page_defaults_and_clamps() {
        use crate::ex02_page::page_params;
        assert_eq!(page_params(None, None), (50, 0), "дефолты 50/0");
        assert_eq!(
            page_params(Some("10".into()), Some("20".into())),
            (10, 20),
            "обычный вызов"
        );
        assert_eq!(
            page_params(Some("99999".into()), None),
            (200, 0),
            "clamp limit"
        );
        assert_eq!(
            page_params(Some("abc".into()), Some("-5".into())),
            (50, 0),
            "битый ввод → дефолт"
        );
    }

    #[tokio::test]
    async fn ex02_api_values_pagination() {
        let pool = new_pool(5).await.expect("стенд (00-setup.sql применён)");
        let app = build_app(pool).expect("app");
        let (status, json) = api_get(app, "/devices/M-01-001/values?limit=2&offset=0").await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(
            json["items"].as_array().map(|a| a.len()).unwrap_or(0),
            2,
            "limit=2 вернул 2 значения"
        );
        assert!(json["total"].as_i64().unwrap_or(0) > 0, "total известен");
        assert_eq!(json["limit"], 2, "limit в ответе");
    }

    #[tokio::test]
    async fn ex01_api_trends_ok() {
        let pool = new_pool(5).await.expect("стенд (00-setup.sql применён)");
        let app = build_app(pool).expect("app");
        let (status, json) = api_get(app, "/devices/M-01-001/trends?bucket=1h").await;
        assert_eq!(status, StatusCode::OK, "тренд отвечает 200");
        let arr = json.as_array().expect("JSON-массив");
        assert!(!arr.is_empty(), "тренд непустой (в данных есть измерения)");
        assert!(arr[0].get("bucket").is_some() && arr[0].get("avg").is_some());
    }

    // --- базовый смоук API ---

    #[tokio::test]
    async fn api_devices_ok() {
        let pool = new_pool(5).await.expect("стенд");
        let app = build_app(pool).expect("app");
        let (status, json) = api_get(app, "/devices").await;
        assert_eq!(status, StatusCode::OK);
        assert!(!json.as_array().map(|a| a.is_empty()).unwrap_or(true));
    }
}
