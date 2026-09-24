//! Пример 01: минимальный axum — маршруты и состояние (урок 01).
//!
//! Запуск: `cargo run --example 01-hello`
//! Проверка: curl http://127.0.0.1:3090/ и /healthz
//!
//! Здесь нет БД: показываем сами основы axum (Router, get, State, Json).

use axum::extract::State;
use axum::routing::get;
use axum::{Json, Router};
use serde_json::json;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

#[derive(Clone)]
struct AppState {
    hits: Arc<AtomicU64>,
}

async fn root(State(state): State<AppState>) -> Json<serde_json::Value> {
    let n = state.hits.fetch_add(1, Ordering::Relaxed) + 1;
    Json(json!({ "message": "API СИКН", "hits": n }))
}

async fn healthz() -> Json<serde_json::Value> {
    Json(json!({ "status": "ok" }))
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let state = AppState {
        hits: Arc::new(AtomicU64::new(0)),
    };
    let app = Router::new()
        .route("/", get(root))
        .route("/healthz", get(healthz))
        .with_state(state);

    let listener = tokio::net::TcpListener::bind("127.0.0.1:3090").await?;
    println!("сервер на http://127.0.0.1:3090");
    axum::serve(listener, app).await?;
    Ok(())
}
