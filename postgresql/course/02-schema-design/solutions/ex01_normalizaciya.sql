-- Решение упражнения 01: нормализованная схема (3NF).
-- Справочники отдельно, факты только с FK и индексом (device_id, ts).

CREATE TABLE ex01_lines (
    id   serial PRIMARY KEY,
    name text NOT NULL UNIQUE
);

CREATE TABLE ex01_devices (
    id          serial PRIMARY KEY,
    tag         text NOT NULL UNIQUE,
    device_type text NOT NULL,
    model       text,
    line_id     int NOT NULL REFERENCES ex01_lines(id)
);

CREATE TABLE ex01_events (
    id        bigserial PRIMARY KEY,
    device_id int NOT NULL REFERENCES ex01_devices(id),
    ts        timestamptz NOT NULL,
    value     numeric(20,6) NOT NULL
);

CREATE INDEX ex01_events_device_ts_idx ON ex01_events (device_id, ts);