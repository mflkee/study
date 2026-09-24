# Теория модуля 06: sqlx — пул, транзакции, миграции

Конспект-справочник. Развёрнутые примеры с фактическими выводами — в уроках (`lessons/`).

## Пул соединений (урок 01)

- `PgPoolOptions`: `min_connections` (прогрев), `max_connections` (потолок), `acquire_timeout` (ждать свободное), `idle_timeout` (закрывать простаивающие). Соединения открываются лениво.
- Лимит пула не должен превышать `max_connections` сервера (по умолчанию 100); при переборе — `too many clients`.
- Статистика: `pool.size()`, `pool.num_idle()` (`num_waiting` в sqlx 0.9 нет).
- **Retry**: экспоненциальная задержка, только транзиентные ошибки (timeout, serialization, обрыв). Unique/CHECK — не ретраим.
- Тюнинг размера под нагрузкой — модуль 07; мониторинг — 13.

## Транзакции из Rust (урок 02)

- `pool.begin()` → `Transaction` (держит соединение!); `commit()` / `rollback()`; drop = rollback.
- Ошибка внутри транзакции → состояние `aborted`: следующие запросы падают до rollback.
- **Savepoint** — явным SQL: `SAVEPOINT sp` / `ROLLBACK TO sp` / `RELEASE sp` (вложенный API sqlx 0.9 скрыт).
- Границы: транзакция = бизнес-операция; долго держать — bloat (модуль 03, лаба 02 этого модуля).

## Макросы и offline-кэш (урок 03)

- `query_as!`/`query!` проверяют SQL и типы на компиляции (по метаданным БД или `.sqlx/`).
- Offline-кэш: `cargo sqlx prepare` → каталог `.sqlx/` (коммитится) → сборка без живого стенда.
- Nullability: nullable → `Option<T>`; принудительно non-null — `AS "n!"`.
- Динамический SQL — только runtime-запросы; основной стиль курса — runtime (D2).

## Миграции и ошибки (урок 04)

- Миграции: `migrations/NNNN_описание.sql`, применяются в порядке версий; история в `_sqlx_migrations` (version, success, checksum). Применение — `sqlx::migrate!("./migrations").run(&pool)` или CLI `sqlx migrate run --source migrations`.
- Изменение применённой миграции → ошибка checksum; правь только новыми миграциями.
- Ошибки: `Sqlx::Error::Database` → `ErrorKind` (UniqueViolation 23505, CheckViolation 23514, ForeignKeyViolation 23503, Serialization 40001, Deadlock 40P01). `classify` (в `src/lib.rs`) → `DbError` на thiserror.
- Ретрай-политика приложена к типу ошибки.

## Инструменты модуля

| Что | Файл |
|---|---|
| Пул, ошибки (DbError), retry | `src/lib.rs` |
| Пул: конфиг/статистика/таймаут | `examples/01-pool.rs` |
| Транзакции: commit/rollback/savepoint | `examples/02-tranzakcii.rs` |
| Миграции | `examples/03-migracii.rs` |
| Ошибки + retry | `examples/04-errors-retry.rs` |
| Макросы + offline-кэш | `examples/05-makrosy.rs` (+ `.sqlx/`) |
| Долгая транзакция (лаба 02) | `examples/06-long-tx.rs` |
| Упражнение «Эволюция схемы» | `exercises/src/ex01_evolution.rs` + миграция 0003 |
| Лабы | `labs/01-zabytyj-indeks.md`, `labs/02-dolgaya-tranzakciya.md` |