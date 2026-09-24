# Урок 01: Базовый REST на axum — маршруты, состояние, слои

**Модуль / кейс:** 11-web-api-axum / кейс 9 (REST API для дашборда)
**Время:** 2.5 ч

## Зачем это нужно

Данные СИКН отдаются наружу через API: текущие значения, тренды, значения по пагинации — под Grafana/внутренние дашборды. axum — современный async-фреймворк на tokio: маршруты, извлечение запросов, слои, целевой стек курса (Rust + PostgreSQL).

## Ключевые идеи (сжато)

- `Router::new().route("/path", get(handler))`; `{param}` в пути (axum 0.8: `{tag}`, не `:tag`).
- **Извлечение** (Extractor): `State<AppState>` (общие данные — пул), `Path<String>`, `Query<T>`, `Json<T>`.
- Состояние: `#[derive(Clone)] struct AppState { pool }` + `.with_state(state)`.
- Слои: `tower_http::trace::TraceLayer` — логирование запросов; `axum::serve(listener, app)`.

## Разбор на примере

```bash
cargo run --example 01-hello
curl http://127.0.0.1:3090/
```

Минимальный сервер (`examples/01-hello.rs`):

```rust
let app = Router::new()
    .route("/", get(root))
    .route("/healthz", get(healthz))
    .with_state(state);
axum::serve(listener, app).await?;
```

Хендлер со состоянием:

```rust
async fn root(State(state): State<AppState>) -> Json<serde_json::Value> {
    let n = state.hits.fetch_add(1, Ordering::Relaxed) + 1;
    Json(json!({ "message": "API СИКН", "hits": n }))
}
```

Ответ: `{"message":"API СИКН","hits":1}`.

## Как это устроено под капотом

- Хендлер — обычная `async fn` с параметрами-Extractor'ами; axum собирает их по порядку и возвращает типизированный ответ (`impl IntoResponse`).
- `State` — Arc-подобное состояние (Clone-дешёвое): передача пула и общих данных без глобалов.
- `Router` — сервис tower; слои оборачивают его (логирование, сжатие, CORS — модуль 13) и исполняются до/после хендлера.

## Типичные ошибки и грабли

1. **`:tag` вместо `{tag}`** — синтаксис axum 0.7/0.8: `{tag}`; нотация `:x` не распознаётся.
2. **Хендлер без `State` при `.with_state`** — несоответствие типов собирается только при сборке Router в рантайме (перед запуском).
3. **Забытый `async` перед хендлером** — компилятор потребует Service.
4. **Пул создаётся на каждый запрос** — пул создаётся ОДИН РАЗ и живёт в State (модуль 06).

## Мини-задание

Добавь маршрут `GET /hello/{name}`, отвечающий `{"hello": "<name>"}` с Path-параметром.

<details>
<summary>Ответ</summary>

```rust
async fn hello(Path(name): Path<String>) -> Json<serde_json::Value> {
    Json(json!({ "hello": name }))
}
// Router::new().route("/hello/{name}", get(hello))
```
</details>

## Как это спросят на собеседовании

1. «Что такое Extractor в axum?» — типизированное извлечение из запроса (State/Path/Query/Json).
2. «Как передать пул хендлерам?» — состояние приложения `AppState { pool }` + `State`-вызов.
3. «Чем отличается Router от хендлера?» — Router — сервис tower, хендлер — обработчик конкретного пути.

## Что читать дальше

- axum: <https://docs.rs/axum> и примеры: <https://github.com/tokio-rs/axum/tree/main/examples>
- tower (слои): <https://docs.rs/tower>