-- Заготовка упражнения 02 (модуль 08): TimescaleDB hypertable + continuous aggregate.
-- Заполни: недостающие строки и замени TODO.

CREATE TABLE probe_ts (
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL
);

-- TODO: SELECT create_hypertable('probe_ts', 'ts', chunk_time_interval => interval '7 days');

-- TODO: вставка 1000 показаний (device_id (i % 3) + 1, ts '2026-09-01' + i минут, value = i)

-- TODO: CREATE MATERIALIZED VIEW probe_daily ... time_bucket('1 day', ts) ...
--       (в транзакции автопроверки нужен WITH NO DATA; материализация —
--        вне транзакций: CALL refresh_continuous_aggregate('probe_daily', NULL, NULL))