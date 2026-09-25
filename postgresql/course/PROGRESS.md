# Прогресс по курсу «Rust + PostgreSQL для СИКН»

Как отмечать: после прохождения пункта ставь `[x]`. Отмечай реально сделанное, а не «ознакомился» — курс строится на практике.

> Статус в сводной таблице — статус **контента**: ⬜ не создан · 🔶 создаётся · ✅ создан (проверен автором).
> Прохождение учеником отмечается в детальных чеклистах ниже.

## Сводка

| Модуль | Статус | Прочитан theory | Уроки | Примеры запущены | Упражнения | Лабы |
|---|---|---|---|---|---|---|
| 00-orientation | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 01-sql-foundations | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 02-schema-design | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 03-transactions-mvcc | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 04-rust-sql-first-steps | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 05-async-rust-tokio | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 06-sqlx-in-depth | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 07-performance | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 08-timeseries-partitioning | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 09-plpgsql-triggers-notify | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 10-app-patterns | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 11-web-api-axum | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 12-testing | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 13-ops-reliability | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 14-capstone-historian | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| 15-interview-prep | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

## Детально

### 00-orientation
- [ ] Прочитать `README.md` и `theory.md`
- [ ] Урок 01: настроить окружение (Rust, Docker, psql, sqlx-cli)
- [ ] Урок 02: поднять стенд, выполнить первый запрос к PostgreSQL
- [ ] Урок 03: карта компетенций и самооценка
- [ ] Упражнение «Диагностика окружения»
- [ ] Лаба «Стенд поднят» (критерии успеха)

### 01-sql-foundations
- [ ] Прочитать `theory.md`
- [ ] Уроки 01–05 (SELECT → DML → типы → JOIN → продвинутые)
- [ ] Запустить все примеры в PostgreSQL (стенд)
- [ ] Упражнения 1–3
- [ ] Лаба «Сломай и почини: точность и время»

_(остальные модули заполняются так же: теория → уроки → примеры → упражнения → лаба)_

### 02-schema-design
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] Примеры: CREATE TABLE + EXPLAIN на стенде
- [ ] Упражнения «Каталог оборудования»
- [ ] Лаба «Индекс под запросы телеметрии»

### 03-transactions-mvcc
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] Примеры: две сессии psql, уровни изоляции
- [ ] Упражнения «Очередь задач: SKIP LOCKED»
- [ ] Лаба «Долгая транзакция / deadlock»

### 04-rust-sql-first-steps
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] cargo-проект: `cargo run --example 02-sqlx-hello` показывает устройства справочника
- [ ] Упражнения «Каталог оборудования на Rust», «Масса нетто»: `cargo test` зелёный
- [ ] Лаба 01: `derived_measurements` заполнена, `netto` совпадает с ручным расчётом
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 05-async-rust-tokio
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] Примеры: `01-async-basics`, `02-tasks-kanaly`, `04-backpressure` запущены
- [ ] `03-otmena-graceful`: Ctrl-C → грациозный дренаж и итог
- [ ] Упражнение «Пайплайн опроса датчиков»: `cargo test ex01_` зелёный
- [ ] Лаба 01: воркер останавливается по SIGINT/SIGTERM без потерь
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 06-sqlx-in-depth
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] База `course_m06` создана, миграции 0001–0002 применены
- [ ] Примеры 01–05 запущены (пул, транзакции, миграции, ошибки, макросы)
- [ ] `cargo check --all-targets` работает БЕЗ `DATABASE_URL` (offline-кэш `.sqlx/`)
- [ ] Упражнение «Эволюция схемы»: миграция 0003 + `cargo test ex01_` зелёный
- [ ] Лаба 01: `Seq Scan` → `Index Scan`, объяснён N+1
- [ ] Лаба 02: `dead but not yet removable` с Rust-воркером, виновник найден
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 07-performance
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] `00-setup.sql` применён к `course_m06`, `pg_stat_statements` создан
- [ ] `cargo run --example 03-insert-methods -- --n 50000`: таблица row/batch/copy объяснена
- [ ] Эмулятор + шлюз (примеры 01–02) работают в двух терминалах
- [ ] Упражнения 01–02: `cargo test ex01_`/`ex02_` зелёные
- [ ] Лаба 01: узкое место подтверждено замером (TPS + EXPLAIN/pg_stat), изменения схемы откатаны
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 08-timeseries-partitioning
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] `00-setup-partitions.sql` применён (3 партиции, 1 М строк)
- [ ] Примеры 01–03 (partitions/retention/downsampling) прогнаны на course_m06
- [ ] Пример 04 (hypertable + cagg) прогнан на 15433 (timescale)
- [ ] Упражнение 01 «Сменный отчёт»: `check-sql.sh … ex01 …` → ok
- [ ] Упражнение 02 «Разверни TimescaleDB»: `check-sql.sh … ex02 … timescale course` → ok
- [ ] Лаба: «no partition of relation found for row» воспроизведён и починен
- [ ] `PROGRESS.md` обновлён

### 09-plpgsql-triggers-notify
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] `00-setup.sql` применён (аудит-цепочка, триггеры, NOTIFY, RLS)
- [ ] `cargo run --example 05-audit-verify` — «целостность: OK»
- [ ] Сквозной сценарий: слушатель получил события, `device_events` заполнена
- [ ] Упражнения 01–02: `cargo test ex01_`/`ex02_` зелёные
- [ ] Лаба: воспроизвёл DISABLE TRIGGER-дыру и NOTIFY-потери, починил
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 10-app-patterns
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] `00-setup.sql` применён (outbox, task_queue, metering_points)
- [ ] Примеры 01–05 прогнаны (repository, outbox, worker, buffer, миграции)
- [ ] Упражнения 01–02: `cargo test ex01_`/`ex02_` зелёные
- [ ] Лаба 01: PG реально остановлен; без потерь и дублей
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 11-web-api-axum
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] `00-setup.sql` применён (свежие серии M-01-001/D-01-001)
- [ ] `01-hello` и `02-measurements` запущены; все эндпоинты curl'ом + `/metrics`
- [ ] Упражнения: `cargo test ex01_trends`/`ex02_page` зелёные
- [ ] Лаба: 401 без токена, кэш current работает (pg_stat_statements не растёт)
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 12-testing
- [ ] Прочитать `theory.md`, пройти уроки 01–03
- [ ] `cargo test --test property` зелёный (инварианты массы нетто, proptest)
- [ ] `cargo test --test pg_integration` зелёный (testcontainers: миграции + функция с фикстурами)
- [ ] Упражнения 01–02 решены (заготовки → зелёные тесты)
- [ ] Лаба: тест миграций расширен (история, таблицы, функция, индекс); сломанная миграция → тест падает
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний
- [ ] `PROGRESS.md` обновлён

### 13-ops-reliability
- [ ] Прочитать `theory.md`, пройти уроки 01–04
- [ ] `01-pgdump-restore.sh`: дамп + сверка (100=100); `02-pgbasebackup.sh`: снимок
- [ ] `03-pitr-restore.sh`: восстановление из снимка (PITR-to-time — UNVERIFIED, см. пометку)
- [ ] `04-setup-replica.sh`: standby → streaming → промоушен (in_recovery=false)
- [ ] Grafana 3000: дашборд «PG обзор (курс)», targets UP (profile obs)
- [ ] Упражнения: ex01 (скрипт+ротация+сверка), ex02 (`check-sql.sh` → ok)
- [ ] Лаба: WAL-рост, «too many clients», «dead but not yet removable» воспроизведены и починены
- [ ] `PROGRESS.md` обновлён

### 14-capstone-historian
- [ ] Прочитать README и уроки 01–04 (ADR, шлюз, хранилище, API+дашборд)
- [ ] `docker compose -f course/14-capstone-historian/docker-compose.yml up -d` — одна команда
- [ ] Сквозной поток: эмулятор → шлюз → БД (15442) → API (3095) → Grafana (3001)
- [ ] `cargo test --test capstone_integration` зелёный (партиции, идемпотентность, аудит, API)
- [ ] Остановка postgres_cap → шлюз пишет в буфер; подъём → доставка без дублей
- [ ] ADR-01..04 прочитаны и поняты (архитектура, хранение, драйвер, буфер)
- [ ] `PROGRESS.md` обновлён

### 15-interview-prep
- [ ] Прочитать уроки 01–04, ответить на все вопросы (эталоны в `<details>`)
- [ ] Упражнение «Собеседование вслух»: 10 вопросов, чеклист «чего не хватило»
- [ ] Лаба «Разбери аварию»: воспроизвёл инцидент, заключение из 5 пунктов
- [ ] `PROGRESS.md` обновлён