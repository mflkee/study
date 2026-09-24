# Урок 03: Макросы query!/query_as! и offline-кэш

**Модуль / кейс:** 06-sqlx-in-depth / кейс 1 (каталог)
**Время:** 2 ч

## Зачем это нужно

Runtime-запросы (модуль 04, урок 02) проверяют типы только в момент выполнения. Макросы `query_as!` проверяют SQL **на этапе компиляции**: неверное имя колонки или тип — сборка падает, а не продакшен. Но за это — цена: макросу нужны метаданные БД при компиляции; аброник не жди для динамического SQL.

## Ключевые идеи (сжато)

- `query!("SQL", $1…)` / `query_as!(Struct, "SQL", …)` — компилятор сверяет SQL и типы с БД (или с offline-кэшем `.sqlx/`).
- **Offline-кэш**: каталог `.sqlx/` с описанием запросов (генерируется `cargo sqlx prepare`, коммитится) — сборка работает без живого стенда.
- Nullability: если колонка «может быть NULL» по метаданным — макрос даст `Option<T>`; принудительно non-null — суффикс `"!"` в алиасе (`count(*) AS "n!"`).
- **Когда макросы, когда runtime** (D2): стабильные запросы ядра — макросы; динамический SQL (фильтры по набору параметров) — runtime. Основной стиль курса — runtime.

## Разбор на примере

```bash
cargo run --example 05-makrosy
```

Код — `examples/05-makrosy.rs`. Запрос с `query_as!` мапится в структуру:

```rust
#[derive(Debug, FromRow)]
struct Device { id: i32, line_id: i32, device_type: String, tag: String, model: Option<String> }

let devices: Vec<Device> = sqlx::query_as!(
    Device,
    "SELECT id, line_id, device_type, tag, model
       FROM devices WHERE line_id = $1 ORDER BY id",
    1
).fetch_all(&pool).await?;
```

Фактический вывод (фрагмент):

```
устройств на ЛИНИИ-1: 3
  id=1 line=1 M-01-001: mass_meter (CMF-300)
  id=2 line=1 D-01-001: density_meter (MVD-1)
  id=3 line=1 W-01-001: moisture_meter (MVM-2)
измерений в базе: 7
```

**Проверь сам, что ловит компиляция:** поменяй `line_id` на `line_id_wrong` в SELECT или тип поля в структуре — `cargo check` упадёт с ошибкой про несовпадение типов/колонок. Для runtime-запроса (урок 02) это промелькнуло бы только в рантайме.

**Про `"n!"`:** `count(*)` по метаданным — nullable-кандидат, макрос даёт `Option<i64>`. Суффикс `!` в алиасе `AS "n!"` говорит макросу «точно не NULL» → `i64`.

## Как это устроено под капотом

- Макрос обращается к серверу (или к файлам `.sqlx/query-*.json`) за **описанием результата**: типы колонок, nullability, типы параметров. По нему генерируется код декодирования — отсюда проверка на компиляции.
- `cargo sqlx prepare` прогоняет код, перехватывает макросы и пишет `.sqlx/`. Если `.sqlx/` закоммичен — сборка `cargo check` работает без `DATABASE_URL`.
- Ограничение: макрос не умеет **динамические** части SQL (условные WHERE, собираемые на лету) — для этого runtime-запросы.

## Типичные ошибки и грабли

1. **`query!` без `DATABASE_URL` и без `.sqlx/`** — сборка падает: «can't find DATABASE_URL / offline data». В этом модуле `.sqlx/` закоммичен — так и должно быть.
2. **NULL в `Option`** — колонка nullable → `Option<T>`; забыл → макрос сам скажет в ошибке компиляции (это плюс!).
3. **Пересоздание offline-кэша** — поменял SQL в макросе → `cargo sqlx prepare` заново (иначе сборка на чужой машине упадёт).
4. **Динамический SQL в макрос** — не выйдет; бери runtime.
5. **`count(*)` дал `Option`** — суффикс `"!"` в алиасе; но только когда точно знаешь, что NULL невозможен.

## Мини-задание

Перепиши запрос «последнее измерение массомера по тегу» (урок 02 мини-задания) на `query_as!` и подготовь offline-кэш (`cargo sqlx prepare`). Проверь, что `cargo check` работает с выключенным стендом.

<details>
<summary>Ответ</summary>

```rust
let row = sqlx::query!(
    "SELECT m.value FROM measurements m
      JOIN devices d ON d.id = m.device_id
     WHERE d.tag = $1 ORDER BY m.ts DESC LIMIT 1",
    tag
).fetch_optional(&pool).await?;
// row.value: Option<Decimal> — макрос сам определил нуллабельность? Всё, что
// из таблицы — честно из метаданных. Затем cargo sqlx prepare → .sqlx/.
```
</details>

## Как это спросят на собеседовании

1. «В чём разница runtime и compile-time запросов sqlx?» — проверка при исполнении vs при сборке; макросы требуют DATABASE_URL/offline-кэш.
2. «Что такое offline-кэш и зачем он?» — `.sqlx/` от `cargo sqlx prepare`, собирает без живого стенда.
3. «Когда макросы не подходят?» — динамический SQL; архитектура курса: runtime по умолчанию (D2).

## Что читать дальше

- Архитектура макросов и offline-режим: <https://github.com/launchbadge/sqlx/blob/main/FAQ.md> и «Macro Features» в README репозитория sqlx
- `sqlx-cli prepare`: <https://github.com/launchbadge/sqlx/tree/main/sqlx-cli>