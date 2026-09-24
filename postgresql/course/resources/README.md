# Ресурсы курса

Официальная документация, книги и практики по темам курса. Проверено на момент создания курса (сентябрь 2026).

## PostgreSQL

- Официальная документация 18: <https://www.postgresql.org/docs/18/> — главный источник. Начинать с Tutorial → SQL Language → Performance Tips (EXPLAIN).
- Учебный справочник по SQL (интерактив): <https://pgexercises.com/> — дриллинг JOIN/GROUP BY/оконных функций.
- PostgreSQL 14 Internals (Егор Рогов, Postgres Professional, на русском): <https://postgrespro.ru/education/books/internals> — глубина по MVCC, WAL, VACUUM, бэкапам. Версии старые, но внутреннее устройство актуально.
- Документация по бэкапам и PITR: <https://www.postgresql.org/docs/18/backup.html>
- Репликация: <https://www.postgresql.org/docs/18/warm-standby.html> (streaming), <https://www.postgresql.org/docs/18/logical-replication.html>
- TimescaleDB (документация): <https://docs.timescale.com/>

## Rust и асинхронность

- The Rust Book: <https://doc.rust-lang.org/book/>
- Rust by Example: <https://doc.rust-lang.org/rust-by-example/>
- Tokio Tutorial: <https://tokio.rs/tokio/tutorial> — async/await, задачи, каналы, select!
- Документация tokio: <https://docs.rs/tokio>

## Крейты (официальная документация по версиям курса)

- sqlx: <https://docs.rs/sqlx> и README репозитория <https://github.com/launchbadge/sqlx> (примеры, миграции, offline-режим макросов). CLI: <https://github.com/launchbadge/sqlx/tree/main/sqlx-cli>
- axum: <https://docs.rs/axum>, репозиторий <https://github.com/tokio-rs/axum> (examples — источник готовых сниппетов)
- rust_decimal: <https://docs.rs/rust_decimal> (документация по точной десятичной арифметике)
- tokio-modbus: <https://docs.rs/tokio-modbus>, репозиторий <https://github.com/slowtec/tokio-modbus>
- serde: <https://docs.rs/serde>

## Мониторинг и эксплуатация

- Prometheus: <https://prometheus.io/docs/prometheus/latest/getting_started/>
- Grafana: <https://grafana.com/docs/grafana/latest/>
- postgres_exporter: <https://github.com/prometheus-community/postgres_exporter>
- pgbench (в составе PostgreSQL): <https://www.postgresql.org/docs/18/pgbench.html>
- pg_stat_statements: <https://www.postgresql.org/docs/18/pgstatstatements.html>

## Доклады и статьи

- «Что делать, если PostgreSQL тормозит» — свежих авторитетных русскоязычных подборок мало; опирайся на официальные инструкции выше и на Internals Рогова для понимания причин.
- Руководство по SQL из документации (для быстрого повторения): <https://www.postgresql.org/docs/18/sql.html>