# Теория модуля 11: REST API на axum + sqlx

Конспект-справочник. Примеры с фактическими ответами — в уроках (`lessons/`).

## Маршруты и состояние (урок 01)

- `Router::new().route("/path", get(h))`; параметры пути — `{tag}` (не `:tag`).
- Extractor'ы: `State<AppState>` (пул, счётчики), `Path<String>`, `Query<T>`, `Json<T>`.
- `#[derive(Clone)] AppState { pool, … }` + `.with_state(state)`; слои — `tower_http::trace::TraceLayer`.

## Хендлеры, DTO, пагинация, ошибки (урок 02)

- DTO: `Serialize` наружу, `FromRow` из БД; Decimal → JSON строкой (точность), время — ISO 8601.
- Пагинация: `limit`/`offset` с клампами (чистая `page_params`, упражнение 02), `LIMIT $n OFFSET $m`.
- Ошибки: `ApiError { status, message }` + `IntoResponse` → `{"error": …}`; единый формат.
- Динамический SQL — только bind-параметры; sqlx 0.9: динамическую строку из фиксированных фрагментов помечают `AssertSqlSafe` (против инъекций).

## Конфигурация, tracing, метрики (урок 03)

- env (`DATABASE_URL`, `PORT`) + дефолты.
- `tracing` + `EnvFilter` (`RUST_LOG`) + `TraceLayer` — структурированные логи запросов.
- Метрики: счётчики в State + `GET /metrics` (Prometheus-формат); ино-профиго — крейты metrics/prometheus (модуль 13).

## Кейс 9: эндпоинты дашборда

| Маршрут | Что | Готовность |
|---|---|---|
| `GET /healthz`, `/metrics` | здоровье, счётчики | всегда |
| `GET /devices` | список устройств | всегда |
| `GET /devices/{tag}/current` | последнее значение | всегда |
| `GET /devices/{tag}/values?limit&offset` | пагинация (упражнение 02) | всегда |
| `GET /devices/{tag}/trends?bucket&from&to` | тренды (упражнение 01) | после решения |
| Лаба 01 | токен + кэш current | доработка |