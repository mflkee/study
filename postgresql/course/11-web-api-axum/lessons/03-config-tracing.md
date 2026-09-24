# Урок 03: Конфигурация, tracing и метрики

**Модуль / кейс:** 11-web-api-axum / кейс 9 (REST API), 13 (мониторинг — задел)
**Время:** 2 ч

## Зачем это нужно

Сервер живёт в проде: ими управляют через окружение (не правку кода), логирование даёт видимость запросов, метрики — алерты. Без этого «вчера ночью API молчал 40 минут» не диагностируется.

## Ключевые идеи (сжато)

- Конфигурация из env: `DATABASE_URL`, `PORT` + дефолты (без dotenv-магии).
- **tracing** + `tracing-subscriber` (EnvFilter): логи запросов через `TraceLayer` (tower-http).
- Метрики: простой счётчик (`AtomicU64`) в State + `GET /metrics` (формат Prometheus); промышленно — `prometheus`/`metrics` крейты (модуль 13).

## Разбор на примере

```bash
RUST_LOG=info,myapi=debug cargo run --example 02-measurements -- --port=3091
```

Код — `examples/02-measurements.rs`:

```rust
tracing_subscriber::fmt().with_env_filter(
    EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info,tower_http=debug")),
).init();
let port: u16 = env::var("PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(3091);
```

Живой лог запроса (стенд):

```
DEBUG request{method=GET uri=/devices/M-01-001/current ...}: tower_http::trace::on_request: started
DEBUG request{... status=200 latency=2 ms}: on_response: finished
```

Метрики (`/metrics`):

```
# TYPE requests_total counter
requests_total 3
```

Счётчик в `AppState`:

```rust
async fn metrics(State(state): State<AppState>) -> impl IntoResponse {
    format!("requests_total {}\n", state.requests.load(Ordering::Relaxed))
}
```

## Как это устроено под капотом

- `tracing` — структурированные «спаны» (поля множественные: method, uri, latency, status); `TraceLayer` создаёт спаны вокруг каждого запроса.
- `EnvFilter` (env `RUST_LOG`) управляет уровнем без пересборки.
- Счётчик в состоянии — «бедный вариант» метрик; полноценно — Prometheus-формат + `postgres_exporter` в модуле 13 (метрики PG-сервера изнутри).

## Типичные ошибки и грабли

1. **Нет логов** — не инициализирован subscriber или уровень `warn`; проверь `RUST_LOG`.
2. **Хардкод порта/URL** — env + дефолты; в compose — переменные окружения.
3. **Метрики вручную** — считай простые счётчики корректно (Atomic), а не `+= 1` в обычной переменной (Race).
4. **Логи без контекста** — включай `TraceLayer`, чтобы каждый span нёс method/uri/status/latency.
5. **RUST_LOG в проде** — выставлять осознанно (`warn,tower_http=trace` для аудита конкретного запроса).

## Мини-задание

Добавь счётчик «4xx» и «5xx» ответов (в `metrics` добавь строки `responses_4xx N`), попробуй `/devices/nonexistent/current` и проверь.

<details>
<summary>Ответ</summary>

Счётчики в State; в `ApiError::into_response` инкрементить по статусу (или через слой tower с постобработкой); в `metrics` выводить оба. После 404-запроса: `responses_4xx 1`.
</details>

## Как это спросят на собеседовании

1. «Как управлять конфигурацией проде?» — env + дефолты; секреты — вне git (модуль 13, CI).
2. «Чем tracing лучше println?» — структурированные спаны, уровни, производительность.
3. «Какие метрики важны для API?» — RPS, latency-percentiles, ошибки 4xx/5xx, очередь пула (модуль 13).

## Что читать дальше

- tracing: <https://docs.rs/tracing>, tracing-subscriber: <https://docs.rs/tracing-subscriber>
- Prometheus-формат: <https://prometheus.io/docs/instrumenting/exposition_formats/>
- tower-http (TraceLayer и другие слои): <https://docs.rs/tower-http>