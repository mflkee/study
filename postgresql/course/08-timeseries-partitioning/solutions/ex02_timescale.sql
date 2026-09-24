-- Решение упражнения 02 (модуль 08): TimescaleDB hypertable + continuous aggregate.
-- Скопируйте в student.sql после своей попытки.
-- (создание cagg В транзакции автопроверки → WITH NO DATA + явный refresh)

CREATE TABLE probe_ts (
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL
);

SELECT create_hypertable('probe_ts', 'ts', chunk_time_interval => interval '7 days');

INSERT INTO probe_ts (device_id, ts, value)
SELECT (i % 3) + 1,
       '2026-09-01 00:00:00+00'::timestamptz + (i || ' minutes')::interval,
       i
  FROM generate_series(1, 1000) i;

CREATE MATERIALIZED VIEW probe_daily
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', ts) AS day,
       device_id,
       count(*)   AS n,
       sum(value) AS total
  FROM probe_ts
 GROUP BY day, device_id
WITH NO DATA;
-- (материализация — вне транзакций: в psql выполни
--  CALL refresh_continuous_aggregate('probe_daily', NULL, NULL);)