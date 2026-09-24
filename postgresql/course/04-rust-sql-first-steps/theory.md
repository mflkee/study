# Теория модуля 04: первый Rust + PostgreSQL

Конспект-справочник. Развёрнутые примеры с фактическими выводами — в уроках (`lessons/`).

## Выбор стека (урок 01)

- **tokio-postgres** — async-драйвер «в лоб»: `(client, connection)` пара, `row.get(i)` по индексу, типы на слове, нет пула/миграций. Хорош как фундамент и для сравнения.
- **sqlx** (основной стек курса, решение D2) — async-драйвер + `PgPool`, `FromRow`, типизированные runtime-запросы `query_as::<_, T>`; макросы `query_as!` — отдельным уроком в модуле 06 (им нужен `DATABASE_URL`/`.sqlx` на этапе компиляции). Не ORM: SQL пишется явно (временные ряды, `COPY`, оконные функции).
- **diesel / sea-orm** — ORM; удобны на CRUD, мучительны на телеметрии и аудите.

## Подключение и запросы (урок 02)

- `PgPoolOptions::new().max_connections(n).connect(url)` — пул; URL по умолчанию: `postgres://course:course@localhost:15432/course?sslmode=disable` (стенд курса), переопределяется `DATABASE_URL`.
- `sqlx::query` — запрос без ожидаемого результата-строк; `query_as::<_, T>` — строки в `T` (кортежи или `FromRow`-структуры); `fetch_all` / `fetch_one` / `fetch_optional`.
- Параметры — `.bind()` по порядку `$1, $2, …`; за кулисами это prepared statements.
- `INSERT ... RETURNING` возвращает вставленную строку — не нужно второе чтение.

## Маппинг типов (урок 02)

| PostgreSQL | Rust | фича sqlx |
|---|---|---|
| `int2/int4/int8` | `i16/i32/i64` | — |
| `text`/`varchar` | `String` | — |
| `bool` | `bool` | — |
| `numeric(p,s)` | `rust_decimal::Decimal` | `rust_decimal` |
| `float8` | `f64` | — |
| `timestamptz` | `chrono::DateTime<Utc>` | `chrono` |
| `timestamp` | `chrono::NaiveDateTime` | `chrono` |
| `jsonb/json` | `serde_json::Value` | `json` |
| NULL | `Option<T>` | — |

Правила: метрология — только `Decimal`/`numeric`; время — только `timestamptz`/`DateTime<Utc>`; любая NULL-колонка — `Option<T>`.

## Точность: rust_decimal (урок 03, кейс 3)

- `f64` — двоичная мантисса: `0.1` не точное, ошибки копятся. Для метрологических величин нельзя.
- `Decimal` — 96-битная мантисса + scale (0..28), диапазон ~1e-28…1e28; `from_str("1000.1")` / `from_str_exact` (без округления).
- Арифметика `+ - * /` точная; **деление** с бесконечной дробью требует стратегии округления.
- Округление: `round_dp(n)` — половина от нуля; явно — `round_dp_with_strategy(n, RoundingStrategy::MidpointAwayFromZero)` (имя `RoundHalfUp` deprecated).
- Формула массы нетто: `m_н = m_бр × (1 − (W + X)/100)`, W/X — вода и примеси в %.

## Время: chrono (урок 03)

- `timestamptz` = момент в UTC → `DateTime<Utc>`; `timestamp` без TZ → `NaiveDateTime`.
- Сериализация — ISO 8601 (`2026-09-23T00:00:00Z`); зона сохраняется, отчёты не «плывут».

## Сериализация: serde (урок 03)

- `#[derive(Serialize)]` на структуре → `serde_json::to_string(_pretty)`.
- `Decimal` по умолчанию сериализуется **строкой** (`"995.000000"`) — точность не теряется; числом — через feature `serde-arbitrary-precision` осознанно.

## Батчевая запись (урок 03, задел модуля 07)

- Один запрос вместо N: `INSERT ... SELECT * FROM UNNEST($1::тип[], $2::тип[])` с `Vec<T>` в `bind`.
- Факт со стенда: 10 000 строк одним запросом ≈ 30 мс.

## Как всё связано

Каталог (кейс 1): `list_lines`/`list_devices`/`insert_device`/`last_measurement` — SQL-слой CRUD в `exercises/src/ex01_katalog.rs`, интерфейс — CLI `examples/06-katalog-cli.rs`.
Точность (кейс 3): `massa_netto` в `exercises/src/ex02_massa_netto.rs`.
Сквозной сценарий — лаба 01 (чтение → расчёт → запись в `derived_measurements`).