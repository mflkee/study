-- 00-setup.sql — таблицы модуля 07 (в базе course_m06): приём телеметрии.
-- Идемпотентно; применяется: infra/apply-db.sh (с URL базы course_m06 —
-- см. README модуля: infra/apply-db.sh course/07-performance/examples/00-setup.sql
-- после переключения default-базы, либо docker compose exec psql -d course_m06 -f ...).

-- Таблица приёма показаний (шлюз). UNIQUE (device_id, seq) — идемпотентность
-- повторной доставки (кейс 8, модуль 10).
CREATE TABLE IF NOT EXISTS telemetry_raw (
    id        bigserial PRIMARY KEY,
    device_id int NOT NULL,
    seq       int NOT NULL,          -- счётчик показаний устройства
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL,
    UNIQUE (device_id, seq)
);

-- Демо-таблица замеров «последние значения по устройствам» (лаба EXPLAIN).
CREATE TABLE IF NOT EXISTS events (
    id        bigserial PRIMARY KEY,
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL
);

TRUNCATE telemetry_raw, events;

-- Сид: несколько тысяч событий БЕЗ индекса (лаба «забытый индекс» — модуль 06,
-- здесь — «влияние индекса на запись»).
INSERT INTO events (device_id, ts, value)
SELECT i % 4, ('2026-09-25 00:00:00+00')::timestamptz + (i || ' seconds')::interval, i
  FROM generate_series(1, 20000) i;