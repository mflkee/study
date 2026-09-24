# Урок 01: Интеграционные тесты с testcontainers

**Модуль / кейс:** 12-testing / кейс 4 (аудит), 10 (эволюция схемы)
**Время:** 2 ч

## Зачем это нужно

Юнит-тесты не ловят контракты БД: типы, миграции, функции, реальные ограничения. Для этого нужен **настоящий PostgreSQL**, но не общий стенд (мешают параллельные тесты и дев-данные). **testcontainers** поднимает изолированный контейнер на каждый тест — полная изоляция, как в проде CI.

## Ключевые идеи (сжато)

- `testcontainers` + `testcontainers-modules::postgres::Postgres` (фича `postgres`).
- `Postgres::default().start().await` → контейнер; порт — `get_host_port_ipv4(5432)`; URL собирается руками.
- Контейнер уничтожается при drop `ContainerAsync` — каждый тест получает чистую БД.
- Интеграционные тесты — в `tests/` (отдельные бинарники, не юнит `#[cfg(test)]`).

## Разбор на примере

```bash
cargo test --test pg_integration
```

Код — `tests/pg_integration.rs`. Хелпер:

```rust
async fn fresh_pg() -> (ContainerAsync<Postgres>, PgPool) {
    let container = Postgres::default().start().await.expect("контейнер PG");
    let port = container.get_host_port_ipv4(5432).await.expect("порт");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPoolOptions::new().max_connections(4).connect(&url).await.unwrap();
    (container, pool)
}
```

Тест миграций (лаба): свежая БД → `sqlx::migrate!("./migrations").run` → проверки схемы (таблицы, `to_regprocedure` для функции). Фактический итог на стенде автора: оба теста файла — `ok` (в т.ч. тест «миграции применены на СВЕЖУЮ базу»).

## Как это устроено под капотом

- testcontainers связывается с Docker (клиент), стартует контейнер, ищет свободный хостовый порт и маппит его (не фиксированный!). Порты маппятся на 127.0.0.1 — контейнеры не пересекаются.
- Каждый `start()` — отдельный контейнер (медленнее, но чисто); параллельные тесты не конфликтуют между собой и со стендом курса (порты случайные).
- Это же фундамент для CI (модуль 13): тесты гоняются на push без общего стенда.

## Типичные ошибки и грабли

1. **Фича модуля не включена**: `testcontainers-modules` собирает postgres-модуль за фичей `postgres` — без неё `use …postgres` не компилируется.
2. **Фиксированный порт в тесте** — next-контейнер может конфликтовать; бери `get_host_port_ipv4`.
3. **Забыл `drop` контейнера** — тест держит Docker-ресурсы; `ContainerAsync` чистит при выходе из области видимости.
4. **Не `#[tokio::test]`** — `start().await` требует async-runtime.
5. **Общий стенд вместо контейнера** — параллельные тесты конфликтуют; контейнер — изоляция.

## Мини-задание

Добавь третий тест в `pg_integration.rs`: создай через SQL таблицу `tmp_x`, вставь строку, проверь — на СВЕЖЕЙ базе (всё в одном контейнере, независимо от стенда курса).

<details>
<summary>Ответ</summary>

`let (_c, pool) = fresh_pg().await; sqlx::query("CREATE TABLE tmp_x (v int)").execute(&pool).await?; INSERT + SELECT` — и без миграций; контейнер изолирован.
</details>

## Как это спросят на собеседовании

1. «Как тестировать код против реальной БД без общего стенда?» — testcontainers: контейнер на тест.
2. «Чем это отличается от sqlx::test?» — testcontainers даёт полный контроль над контейнером; sqlx::test — управляемые БД с миграциями (урок 02).
3. «Почему порты не фиксированные?» — изоляция параллельных тестов.

## Что читать дальше

- testcontainers (Rust): <https://docs.rs/testcontainers>
- testcontainers-modules: <https://docs.rs/testcontainers-modules>
- Лаба «Тест миграций» и `tests/pg_integration.rs`