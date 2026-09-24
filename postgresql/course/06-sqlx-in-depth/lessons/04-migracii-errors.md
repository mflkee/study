# Урок 04: Миграции и обработка ошибок БД

**Модуль / кейс:** 06-sqlx-in-depth / кейс 10 (эволюция схемы), 1 (каталог)
**Время:** 2 ч

## Зачем это нужно

Схема меняется: тот же «новый тип датчика» (упражнение 01) — это миграция + код. Миграции — источник истины о схеме: применяются по одному разу, в порядке версий, автоматически. Рядом — ошибки БД: «сырые» `sqlx::Error` неудобны в бизнес-логике; их классифицируют (thiserror) и решают, что ретраить.

## Ключевые идеи (сжато)

- Миграции: файлы `migrations/NNNN_описание.sql`, применяются по возрастанию версии; история — таблица `_sqlx_migrations`. Применение: `sqlx::migrate!().run(&pool)` (встроено в бинарник) или CLI `sqlx migrate run`.
- Ошибки: `sqlx::Error::Database` несёт код; полезны `UniqueViolation`, `CheckViolation`, `ForeignKeyViolation`, `Serialization`.
- thiserror — доменные ошибки (`DbError`) вместо строк; `#[from]` — автоматические конверсии.
- **Ретрай-политика**: транзиентные (serialization, таймауты) — да, с экспоненциальной задержкой; уникальность/CHECK — нет (это баги, повтор не поможет).

## Разбор на примере

Код — `examples/03-migracii.rs`, `examples/04-errors-retry.rs`; `src/lib.rs` — `classify` + `retry`.

**1. Применение миграций** (`examples/03-migracii.rs`):

```rust
sqlx::migrate!("./migrations").run(&pool).await?;   // применит недостающие
```

Фактический вывод:

```
миграций в базе до запуска: 2
после запуска в базе миграций: 2
  v1: equipment baseline (success=true)
  v2: add level meter (success=true)
устройств level_meter (из миграции 0002): 1
```

Идемпотентность: повторный запуск ничего не применяет. Аналог через CLI: `DATABASE_URL=… cargo sqlx migrate run --source migrations`.

**2. Классификация ошибок** (`examples/04-errors-retry.rs`):

```rust
match classify(err) {
    DbError::UniqueViolation(msg) => …,
    DbError::CheckViolation(msg)  => …,
    DbError::Sqlx(e)              => …,
}
```

Фактический вывод:

```
UniqueViolation: duplicate key value violates unique constraint "devices_tag_key"
CheckViolation: new row for relation "devices" violates check constraint "devices_device_type_check"
retry: потребовалось 3 попыток, результат = 42
```

`classify` смотрит на `sqlx::error::ErrorKind` (код ошибки сервера), а не на текст.

**3. Реальная история**: упражнение 01 — «эволюция схемы». Миграция 0003 расширяет CHECK на `flow_calc`; `classify(CheckViolation)` ловит «не той версии схема», если забыли миграцию.

## Как это устроено под капотом

- `sqlx::migrate!` внедряет файлы миграций в бинарник при компиляции; `run` сверяет версии из `_sqlx_migrations` и применяет недостающие **в транзакции** (каждая — `BEGIN … COMMIT`, при ошибке не фиксируется).
- `_sqlx_migrations` хранит `version`, `description`, `success`, `checksum` — изменение применённого файла ловится как ошибка (так и должно быть).
- Код ошибки: PostgreSQL присылает `SQLSTATE`; sqlx мапит типовые в `ErrorKind` (23505 = unique, 23514 = check, 40P01 = deadlock, 40001 = serialization).

## Типичные ошибки и грабли

1. **Отредактировал применённую миграцию** — «migration checksum mismatch»: причина в том, что схема уже не совпадает с файлами. Правильно: НОВАЯ миграция, а не правка старой.
2. **Ретрай UniqueViolation** — разводит баги по журналу; классифицируй и не ретрай.
3. **`try_` код ошибки по тексту** — хрупко; используй `ErrorKind`.
4. **Миграции из «CREATE TABLE IF NOT EXISTS»** — маскируют расхождения окружений; миграции должны быть точными (схема = история).
5. **Миграция с нарушенным порядком** — версии файлов должны идти строго по возрастанию; `sqlx` ругается на пропуски/дубли.

## Мини-задание

Напиши миграцию 0003 (упражнение 01), примени, затем **попробуй откатить** «вручную» (`ROLLBACK` не сработает — миграции не откатываются просто так; только новая миграция). Ответь: почему откат миграций редко делают автоматически?

<details>
<summary>Ответ</summary>

Миграции часто необратимы (DROP без возврата) или разрушительны для данных; авто-даунгрейды опасны в проде. Практика: forward-only + новая миграция-«откат» (осторожно, В ПРОДЕ — с бэкапом, модуль 13).
</details>

## Как это спросят на собеседовании

1. «Как хранить эволюцию схемы?» — версионированные миграции; применяются в порядке версий, однажды.
2. «Что делать, если миграция упала в проде?» — починить миграцию (не трогая применённые), применить, проверить `success`.
3. «Какие ошибки БД ретраить и почему?» — serialization/timeout — да; unique/check — нет.

## Что читать дальше

- sqlx migrate: <https://github.com/launchbadge/sqlx/blob/main/README.md#migrate> и `sqlx-cli` (команда `migrate`)
- Ошибки PostgreSQL (SQLSTATE): <https://www.postgresql.org/docs/18/errcodes-appendix.html>
- thiserror: <https://docs.rs/thiserror>