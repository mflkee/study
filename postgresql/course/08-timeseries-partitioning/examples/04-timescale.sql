-- 04-timescale.sql — TimescaleDB: hypertable и continuous aggregates.
-- Модуль: 08-timeseries-partitioning, урок 03. База: course на 15433 (profile timescale).
-- Запуск: docker compose exec -T timescale psql -U course -d course -f <этот файл>

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Гипертаблица: прозрачное партиционирование (чанки) по ts.
DROP TABLE IF EXISTS measurements_ts CASCADE;
CREATE TABLE measurements_ts (
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL
);
SELECT create_hypertable('measurements_ts', 'ts', chunk_time_interval => interval '7 days');

-- Чанки можно посмотреть:
SELECT chunk_name, chunk_schema, range_start, range_end
  FROM timescaledb_information.chunks
 WHERE hypertable_name = 'measurements_ts';

-- Те же 1 000 000 измерений:
INSERT INTO measurements_ts (device_id, ts, value)
SELECT (i % 8) + 1,
       '2026-08-01 00:00:00+00'::timestamptz + (random() * interval '60 days'),
       round((random() * 1000)::numeric, 4)
  FROM generate_series(1, 1000000) i;

-- Continuous aggregate: ТС поддерживает матвью «сами» (без ручного REFRESH)
-- и умеет обновлять частично (инкрементально).
CREATE MATERIALIZED VIEW daq_daily_tsc
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', ts) AS day,
       device_id,
       count(*)   AS n,
       sum(value) AS total
  FROM measurements_ts
 GROUP BY day, device_id;

-- Материализуем за диапазон (в проде — политика add_continuous_aggregate_policy).
CALL refresh_continuous_aggregate('daq_daily_tsc', '2026-08-01', '2026-10-01');

-- Отчёт — тот же, что и нативный daq_daily (модуль: сравнение в уроке 03):
-- время запроса и «свежесть» (policy-обновление в фоне).
\timing on
SELECT day, device_id, n, round(total::numeric, 2) AS total
  FROM daq_daily_tsc
 WHERE day >= '2026-08-25' AND day < '2026-08-28'
 ORDER BY day, device_id;
\timing off

-- Размер гипертаблицы: основной rel не хранит данные — смотрим по чанкам.
SELECT pg_size_pretty(hypertable_size('measurements_ts')) AS hypertable_size;

-- Автообновление: политика (в 2.30 — новая сигнатура, именованные аргументы).
SELECT add_continuous_aggregate_policy(
    continuous_aggregate => 'daq_daily_tsc',
    start_offset => INTERVAL '1 month',
    end_offset   => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour'
);