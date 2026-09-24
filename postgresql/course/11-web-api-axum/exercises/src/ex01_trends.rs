//! Упражнение 01: «Добавь эндпоинт трендов» (кейс 9).
//!
//! **Кейс:** 9 (REST API для дашборда). **Модуль:** 11. **Время:** 2 ч.
//!
//! ТЗ: дашборду нужен тренд — агрегаты измерений по интервалам
//! (`date_bin`, модуль 08). Реализуй `trends_handler`:
//! `GET /devices/{tag}/trends?bucket=1h&from=…&to=…` → JSON-массив
//! `TrendPoint { bucket, avg, min, max, count }`.
//!
//! Подсказка: `date_bin($1::interval, m.ts, origin)` + `GROUP BY bucket`;
//! origin — любое фиксированное «начало сетки»; bucket default `'1 day'`;
//! from/to — опциональные фильтры по ts (в формате RFC3339).
//!
//! Проверка: `cargo test ex01_trends` — интеграционный тест прогоняет
//! запрос через Router (oneshot) и ждёт непустой массив.
//!
//! Решение — в `solutions/ex01_trends.rs`.

use crate::{ApiResult, AppState};
use axum::extract::{Path, Query, State};
use axum::Json;
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

/// Точка тренда: бакет + агрегаты по нему.
#[derive(Debug, Serialize)]
pub struct TrendPoint {
    pub bucket: DateTime<Utc>,
    pub avg: Decimal,
    pub min: Decimal,
    pub max: Decimal,
    pub count: i64,
}

/// Параметры запроса тренда (все опциональные).
#[derive(Debug, Deserialize, Default)]
pub struct TrendsQuery {
    pub bucket: Option<String>, // интервал: '1h' | '1 day' | '30 minutes' …
    pub from: Option<String>,   // RFC3339
    pub to: Option<String>,     // RFC3339
}

/// `GET /devices/{tag}/trends` — агрегаты по интервалам.
pub async fn trends_handler(
    _state: State<AppState>,
    _path: Path<String>,
    Query(_q): Query<TrendsQuery>,
) -> ApiResult<Json<Vec<TrendPoint>>> {
    todo!("date_bin-бакеты (модуль 08) + avg/min/max/count по тегу + from/to-фильтры; не забудь AssertSqlSafe для динамики (урок 02)")
}
