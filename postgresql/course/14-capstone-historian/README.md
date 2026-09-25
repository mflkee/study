# Капстоун 14: Historian — итоговый проект

Мини-система сбора и хранения данных СИКН: **эмулятор Modbus TCP → Rust-шлюз (опрос, буфер, батч) → PostgreSQL (партиции, аудит, триггеры) → REST API axum → дашборд Grafana**.

## Быстрый старт (одной командой)

```bash
# 1. Поднять БД капстоуна и Grafana (порт 15442 / 3001):
docker compose -f course/14-capstone-historian/docker-compose.yml up -d

# 2. Три бинаря капстоуна (из course/14-capstone-historian):
cargo run --bin emulator &
cargo run --bin gateway &
cargo run --bin api -- --port 3095

# 3. Проверка сквозного потока:
curl -s localhost:3095/devices
curl -s localhost:3095/devices/M-01-001/current
curl -s "localhost:3095/devices/M-01-001/trends?bucket=1%20minute"
# Grafana: http://localhost:3001 (admin/admin) → «Historian: поток от Modbus к дашборду»
```

## Архитектура

```
[Emulator (Modbus TCP)] --500мс--> [Gateway] --батч--> [PostgreSQL capstone:15442]
                                          |                     |
                                          +--буфер файл-->     v
[Grafana :3001] <--Postgres SQL-- [PostgreSQL]  [REST API axum :3095] <-- curl/клиенты
```

- **эмулятор** — tokio-modbus сервер, регистры «живут» (масса растёт, плотность колеблется);
- **gateway** — опрос 500 мс; батч `UNNEST` + `ON CONFLICT`; при отказе PG — локальный буфер
  и периодический flush (без дублей); Ctrl-C — грациозный флаш;
- **хранилище** — партиционированная `measurements_hist` (RANGE по месяцам, авто-мост
  `ensure_partitions`), каталог с аудит-цепочкой sha256 (триггеры), идемпотентность приёма;
- **api** — устройства, текущее значение, тренды (эпоховые бакеты — без date_bin гонок);
- **Grafana** — datasource PostgreSQL (pровижининг) + дашборд «Historian».

## Проверка и тесты

```bash
docker compose -f course/14-capstone-historian/docker-compose.yml config --quiet
cargo test --test capstone_integration   # настоящий PG в контейнере (модуль 12)
```

Тесты покрывают: партиции и автосоздание месяца; историчность (повторная доставка → 0 дублей);
аудит-цепочку (защита от подмены, разрыв ловится); API‑запросы current/trends.

## Структура

```
├── Cargo.toml            # бинари: emulator, gateway, api
├── docker-compose.yml    # postgres_cap:15442 + grafana_cap:3001 (одна команда)
├── grafana/provisioning  # datasource PostgreSQL + дашборд Historian
├── migrations/           # 0001_capstone.sql (схема + аудит + партиции)
├── src/lib.rs            # батч/буфер/аудит/API (переиспользуемое)
├── src/bin/emulator.rs, gateway.rs, api.rs
├── tests/capstone_integration.rs   # testcontainers
└── docs/ADR.md           # ADR-01..04 (архитектура, хранение, драйвер, буфер)
```

## Оценка

~20 ч. Сложность задач капстоуна — в связке компонентов, а не в новом API: всё
переиспользует проверенные модули 05–12 (каналы, батч, партиции, аудит, REST,
testcontainers).