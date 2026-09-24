# Урок 02: Подключение, первые запросы, маппинг типов

**Модуль / кейс:** 04-rust-sql-first-steps / кейсы 1 (каталог), 5 (отчёты)
**Время:** 2 ч

## Зачем это нужно

80% ошибок при работе с БД из Rust — это неверный маппинг типов: читаешь `timestamptz` как строку, теряешь точность `numeric` в `f64`, забываешь `Option` для NULL-колонок. Урок даёт таблицу соответствий PostgreSQL → Rust и рабочие паттерны чтения/записи (параметры, `INSERT ... RETURNING`).

## Ключевые идеи (сжато)

- Подключение — через **пул** `PgPool` (а не одно соединение): он переживает разрывы и распределяет запросы.
- Запросы: `query` (без результата/для count), `query_as` (строки → типы), `fetch_all` / `fetch_one` / `fetch_optional`.
- Параметры — через `.bind()`, позиции `$1, $2, …`; это **prepared statements** (безопасно от инъекций, кэшируется сервером).
- Таблица маппинга типов (ниже) — заучивается один раз и работает во всех модулях.

## Разбор на примере

Запусти `cargo run --example 03-mapping-types` и сверь вывод. Код — `examples/03-mapping-types.rs`.

**1. Чтение: строка → структура.** `sqlx::FromRow` мапит колонки по именам полей (алиасы в SQL):

```rust
#[derive(Debug, FromRow)]
struct RichRow {
    id: i64,          // bigserial → i64
    tag: String,      // text → String
    ts: DateTime<Utc>,// timestamptz → chrono (фича "chrono")
    value: Decimal,   // numeric(20,6) → rust_decimal (фича "rust_decimal")
    quality: i32,     // int → i32
}

let rows: Vec<RichRow> = sqlx::query_as(
    "SELECT m.id, d.tag, m.ts, m.value, m.quality
       FROM measurements m
       JOIN devices d ON d.id = m.device_id
      ORDER BY m.ts DESC
      LIMIT 5")
    .fetch_all(&pool).await?;
```

Фактический вывод (первые строки):

```
id=24  tag=D-02-001 ts=2026-09-23 12:00:00 UTC value=859.950000 quality=0
id=7   tag=M-01-001 ts=2026-09-23 12:00:00 UTC value=998.500000 quality=0
id=20  tag=M-02-001 ts=2026-09-23 12:00:00 UTC value=497.250000 quality=0
id=11  tag=D-01-001 ts=2026-09-23 12:00:00 UTC value=850.150000 quality=0
id=6   tag=M-01-001 ts=2026-09-23 06:00:00 UTC value=1002.750000 quality=0
```

**2. Запись: `INSERT ... RETURNING`** — вставка с возвратом всех полей строки (не нужен второй запрос на чтение):

```rust
let inserted: Measurement = sqlx::query_as(
    "INSERT INTO measurements (device_id, ts, value, quality)
     VALUES ($1, $2, $3, $4)
     RETURNING id, device_id, ts, value, quality")
    .bind(1_i32)                       // device_id
    .bind(Utc::now())                  // timestamptz
    .bind(Decimal::from_str("42.500000")?) // numeric(20,6)
    .bind(0_i32)                       // quality = good
    .fetch_one(&pool).await?;
```

Вывод: `вставлено: Measurement { id: 25, device_id: 1, ts: ..., value: 42.500000, quality: 0 }`. Пример тут же удаляет вставленную строку, чтобы не засорять сид.

### Таблица маппинга типов (PostgreSQL → Rust)

| PostgreSQL | Rust (sqlx) | Фича sqlx |
|---|---|---|
| `int2` (smallint) | `i16` | — |
| `int4` (int/serial) | `i32` | — |
| `int8` (bigint/bigserial) | `i64` | — |
| `text`, `varchar` | `String` | — |
| `bool` | `bool` | — |
| `numeric(p,s)` | `rust_decimal::Decimal` | `rust_decimal` |
| `float8` | `f64` | — |
| `timestamptz` | `chrono::DateTime<chrono::Utc>` | `chrono` |
| `timestamp` | `chrono::NaiveDateTime` | `chrono` |
| `date` | `chrono::NaiveDate` | `chrono` |
| `jsonb`, `json` | `serde_json::Value` | `json` |
| `uuid` | `uuid::Uuid` | `uuid` |
| NULL-колонка | `Option<T>` | — |

Практические правила СИКН:
- Метрологические величины («масса», «плотность») — только `numeric` → `Decimal` (кейс 3, урок 03).
- Момент времени — только `timestamptz` → `DateTime<Utc>` (модуль 01 про «время без TZ»).
- Любая колонка с `NULL` — `Option<T>`, иначе sqlx упадёт с ошибкой декодирования.

## Как это устроено под капотом

- **Пул** = набор живых соединений; `max_connections` — предел. `PgPoolOptions` открывает соединения лениво (при первом запросе) и поддерживает их (модуль 06 — конфигурация и таймауты).
- **`.bind()`** отправляет значение как параметр prepared statement: сервер парсит и планирует запрос один раз, потом подставляет значения. Это и защита от SQL-инъекций, и скорость.
- **`RETURNING`** использует особенность PostgreSQL: DML может вернуть изменённые строки — идеально для «вставил и получил id/все поля» без гонки.
- **sqlx проверяет совместимость типов** (Decode/Encode) в момент выполнения запроса: несовпадение (например, `Decimal` в колонку `text`) — ошибка с текстом типа, а не молчаливая порча данных.

## Типичные ошибки и грабли

1. **`numeric` в `f64`** — тихая потеря точности (урок 03, кейс 3). Для денег/метрологии — только `Decimal`.
2. **`timestamptz` как `NaiveDateTime`/строка** — теряется зона, отчёты «разъезжаются» по часовым поясам. Бери `DateTime<Utc>`.
3. **Форгот `Option` для NULL** — sqlx упадёт с «decode error» для первой же `model IS NULL`.
4. **Несовпадение имён полей и колонок** в `FromRow` — покрываем алиасами (`l.name AS line_name`) или переименованием полей.
5. **`bind` не по порядку `$1..$N`** — сервер честно скажет «bind message supplies 0 parameters», но после отладки кода, а не до.

## Мини-задание

Напиши функцию `latest_mass(tag: &str) -> i64`? Нет — проще: верни `Option<rust_decimal::Decimal>` для последнего значения массомера по тегу (без `quality`-фильтра). Используй `query_as::<_, (Decimal,)>` в паре с `ORDER BY ts DESC LIMIT 1`.

<details>
<summary>Ответ</summary>

```rust
let (value,): (Decimal,) = sqlx::query_as(
    "SELECT m.value FROM measurements m
      JOIN devices d ON d.id = m.device_id
     WHERE d.tag = $1
     ORDER BY m.ts DESC LIMIT 1")
    .bind(tag)
    .fetch_one(&pool).await?;
// для «нет данных» — fetch_optional + Option<(Decimal,)>
```
</details>

## Как это спросят на собеседовании

1. «Как в sqlx передать параметры и почему это безопасно?» — `.bind()` → prepared statement, `$N`, сервер планирует один раз.
2. «Почему `numeric` нельзя читать во `f64`?» — представление и накопление ошибки (урок 03).
3. «Что вернёт `fetch_optional` для пустого результата?» — `Ok(None)`.
4. «Зачем `RETURNING`?» — одна поездка в БД вместо INSERT + SELECT.

## Что читать дальше

- SQLx `PgPool` / `query_as`: <https://docs.rs/sqlx/latest/sqlx/>, глава про PostgreSQL в README репозитория (`postgres` → типы)
- Типы PostgreSQL: <https://www.postgresql.org/docs/18/datatype.html>