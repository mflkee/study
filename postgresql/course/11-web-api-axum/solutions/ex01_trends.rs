//! Решение упражнения 01: «Добавь эндпоинт трендов».
//! Скопируйте содержимое в `exercises/src/ex01_trends.rs` после попытки.

use crate::{ApiError, ApiResult, AppState};
use axum::extract::{Path, Query, State};
use axum::Json;
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize)]
pub struct TrendPoint {
    pub bucket: DateTime<Utc>,
    pub avg: Decimal,
    pub min: Decimal,
    pub max: Decimal,
    pub count: i64,
}

#[derive(Debug, Deserialize, Default)]
pub struct TrendsQuery {
    pub bucket: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
}

/// Фиксированная «точка нуля» сетки бакетов (как в модуле 08).
const ORIGIN: &str = "2026-01-01 00:00:00+00";

/// `GET /devices/{tag}/trends` — агрегаты по интервалам.
pub async fn trends_handler(
    State(state): State<AppState>,
    Path(tag): Path<String>,
    Query(q): Query<TrendsQuery>,
) -> ApiResult<Json<Vec<TrendPoint>>> {
    let bucket = q.bucket.unwrap_or_else(|| "1 day".to_owned());

    // Динамическое построение WHERE по from/to — это runtime-запрос (D2)
    // с параметрами (никаких строковых конкатенаций данных!).
    let mut sql = String::from(
        "SELECT date_bin($1::interval, m.ts, $2::timestamptz) AS bucket,
                round(avg(m.value)::numeric, 4) AS avg,
                min(m.value) AS min, max(m.value) AS max, count(*) AS count
           FROM measurements m JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $3",
    );
    if q.from.is_some() {
        sql.push_str(" AND m.ts >= $4");
    }
    if q.to.is_some() {
        sql.push_str(" AND m.ts < $5");
    }
    sql.push_str(" GROUP BY bucket ORDER BY bucket");

    // Динамическое построение WHERE по from/to — runtime-запрос (D2).
    // sqlx 0.9 требует явной пометки динамической строки как проверенной
    // (AssertSqlSafe): мы строим SQL ТОЛЬКО из фиксированных фрагментов,
    // пользовательские данные всегда в bind-параметрах, не конкатенации —
    // это и есть суть аудита (урок 02).
    let mut query = sqlx::query_as::<_, (DateTime<Utc>, Decimal, Decimal, Decimal, i64)>(
        sqlx::AssertSqlSafe(sql.as_str()),
    )
    .bind(&bucket)
    .bind(ORIGIN)
    .bind(&tag);
    if let Some(from) = q.from.as_deref() {
        query = query.bind(from);
    }
    if let Some(to) = q.to.as_deref() {
        query = query.bind(to);
    }

    let rows = query.fetch_all(&state.pool).await?;
    if rows.is_empty() {
        return Err(ApiError::not_found(format!(
            "нет измерений для устройства {tag}"
        )));
    }
    Ok(Json(
        rows.into_iter()
            .map(|(bucket, avg, min, max, count)| TrendPoint {
                bucket,
                avg,
                min,
                max,
                count,
            })
            .collect(),
    ))
}
