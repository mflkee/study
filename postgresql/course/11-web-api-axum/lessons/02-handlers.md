# Урок 02: Хендлеры — DTO, пагинация, ошибки JSON

**Модуль / кейс:** 11-web-api-axum / кейс 9 (REST API)
**Время:** 2.5 ч

## Зачем это нужно

API — контракт с клиентом: стабильные DTO, безопасная пагинация, единый формат ошибок. Без этого дашборды «ломаются» при малейшем изменении, а клиенты не понимают, что пошло не так.

## Ключевые идеи (сжато)

- **DTO**: данные наружу — только нужные поля (`Serialize`), маппинг из строк БД (`FromRow`).
- Ответы: `Json<Vec<T>>`, `Json<Page{items,total,limit,offset}>`.
- **Пагинация**: параметры `limit`/`offset`, клампы (limit ≤ 200, offset ≥ 0), дефолты — чистая функция `page_params` (упражнение 02).
- **Ошибки**: единый тип `ApiError { status, message }` + `IntoResponse` → `{"error": …}`; не «сырые» серверные исключения.
- Динамический SQL — только с параметрами (`AssertSqlSafe` в sqlx 0.9 — урок 02).

## Разбор на примере

```bash
cargo run --example 02-measurements -- --port=3091
```

Код — `examples/02-measurements.rs` + `src/lib.rs` (`build_app`). Фактические ответы со стенда:

```
GET /devices
[{"tag":"D-01-001",...},{"tag":"M-01-001",...}, ...]

GET /devices/M-01-001/values?limit=2
{"tag":"M-01-001","items":[{"ts":"2026-09-24T20:33:30Z","value":"1000.000000","quality":0}, ...],
 "total":31,"limit":2,"offset":0}
```

Хендлер пагинации (фрагмент):

```rust
async fn values_page(State(state): State<AppState>, Path(tag): Path<String>,
                     Query(q): Query<PageQuery>) -> ApiResult<Json<ValuePage>> {
    let (limit, offset) = page_params(q.limit, q.offset);   // клампы
    let items = /* SELECT … LIMIT $2 OFFSET $3 */;
    let total  = /* count(*) */;
    Ok(Json(ValuePage { tag, items, total, limit, offset }))
}
```

Ошибки:

```rust
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.status, Json(json!({ "error": self.message }))).into_response()
    }
}
// нет данных → 404 {"error":"нет данных для устройства M-01-001"}
```

## Как это устроено под капотом

- `Serialize` — сериализация автоматически; `Decimal` уходит **строкой** (точность, модуль 04), `DateTime<Utc>` — ISO 8601 с `Z`.
- Пагинация на стороне БД (`LIMIT/OFFSET`) — страница не грузит всё (на больших сериях — cursor-пагинация как развитие, см. «со звёздочкой» лабы).
- `ApiError` как единый «мост» ошибок БД → HTTP: `#[from] sqlx::Error` → 500.

## Типичные ошибки и грабли

1. **Отдать всю таблицу** — забыли пагинацию; дашборд на 10⁶ строк встанет.
2. **Ошибки разными форматами** — единый `ApiError` везде.
3. **`limit` неограниченный** — «параметр доверия»; кламп обязателен (упражнение 02).
4. **Сектерная строка в SQL** — конкатенация пользовательских данных = инъекция; ТОЛЬКО `bind`. sqlx 0.9 требует явный `AssertSqlSafe` для динамики (мы строим её из фиксированных фрагментов — урок 02).
5. **JSON-схема «плывёт»** — v-версия в URL (`/v1/…`) или добавочные поля backward-compatible.

## Мини-задание

Добавь в `values_page` валидацию «tag непустой» и 404 с единым форматом ошибки, если измерений нет вовсе.

<details>
<summary>Ответ</summary>

Проверяй существование устройства заранее (как в `values_page`) и возвращай `ApiError::not_found(format!("устройство {tag} не найдено"))`; пустая страница — тоже допустимый 200 с `items: []`.
</details>

## Как это спросят на собеседовании

1. «Пагинация — какие бывают и чем limit/offset плох?» — offset скачет на штраф больших выборок; курсор устойчивее (это «со звёздочкой»).
2. «Единый формат ошибок зачем?» — контракт для клиента; `ApiError` + `IntoResponse`.
3. «Почему Decimal в JSON строкой?» — точность (модуль 04).

## Что читать дальше

- Отправка/получение JSON: <https://docs.rs/axum/latest/axum/struct.Json.html>
- Сериализация Decimal: <https://docs.rs/rust_decimal>
- Упражнения 01–02 модуля (тренды и пагинация)