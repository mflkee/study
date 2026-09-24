-- 00-setup.sql — схема и тестовые данные модуля 04 (каталог оборудования).
-- Самодостаточная копия схемы модулей 01–02 (00-schema-katalog.sql +
-- 01-seed-katalog.sql): модуль 04 запускается даже на «чистом» стенде.
-- Идемпотентно: повторное применение безопасно (IF NOT EXISTS + TRUNCATE).
--
-- Применение: infra/apply-db.sh course/04-rust-sql-first-steps/examples/00-setup.sql

-- Линии измерения: «ЛИНИЯ-1», «ЛИНИЯ-2»...
CREATE TABLE IF NOT EXISTS equipment_lines (
    id          serial PRIMARY KEY,
    name        text NOT NULL UNIQUE,
    description text
);

-- Устройства на линии: массомер, плотномер, влагомер.
-- device_type — строкой с CHECK: enum не имеет CREATE TYPE IF NOT EXISTS.
CREATE TABLE IF NOT EXISTS devices (
    id          serial PRIMARY KEY,
    line_id     int  NOT NULL REFERENCES equipment_lines(id) ON DELETE CASCADE,
    device_type text NOT NULL CHECK (device_type IN ('mass_meter','density_meter','moisture_meter')),
    tag         text NOT NULL UNIQUE,
    model       text
);

-- Измерения (телеметрия). value — NUMERIC(20,6), не float8: метрологические
-- величины не должны терять точность (кейс 3, урок 03 этого модуля).
-- quality: 0 = good, 1 = bad, 2 = uncertain, 3 = manual.
CREATE TABLE IF NOT EXISTS measurements (
    id        bigserial PRIMARY KEY,
    device_id int  NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    ts        timestamptz NOT NULL,
    value     numeric(20,6) NOT NULL,
    quality   int NOT NULL DEFAULT 0 CHECK (quality BETWEEN 0 AND 3)
);

CREATE INDEX IF NOT EXISTS idx_measurements_device_ts
    ON measurements (device_id, ts);

TRUNCATE measurements, devices, equipment_lines RESTART IDENTITY CASCADE;

INSERT INTO equipment_lines (name, description) VALUES
    ('ЛИНИЯ-1', 'Линия измерения №1: массомер + плотномер'),
    ('ЛИНИЯ-2', 'Линия измерения №2: массомер');

INSERT INTO devices (line_id, device_type, tag, model) VALUES
    (1, 'mass_meter',     'M-01-001', 'CMF-300'),
    (1, 'density_meter',  'D-01-001', 'MVD-1'),
    (1, 'moisture_meter', 'W-01-001', 'MVM-2'),
    (2, 'mass_meter',     'M-02-001', 'CMF-200'),
    (2, 'density_meter',  'D-02-001', 'MVD-2');

-- Измерения: две линии, два дня, шаг 6 часов.
INSERT INTO measurements (device_id, ts, value) VALUES
    -- ЛИНИЯ-1, массомер: масса брутто, кг/ч
    (1, '2026-09-22 00:00:00+00', 1000.000000),
    (1, '2026-09-22 06:00:00+00', 1010.500000),
    (1, '2026-09-22 12:00:00+00', 1005.250000),
    (1, '2026-09-22 18:00:00+00', 990.125000),
    (1, '2026-09-23 00:00:00+00', 995.000000),
    (1, '2026-09-23 06:00:00+00', 1002.750000),
    (1, '2026-09-23 12:00:00+00', 998.500000),
    -- ЛИНИЯ-1, плотномер: плотность, кг/м3
    (2, '2026-09-22 00:00:00+00', 850.100000),
    (2, '2026-09-22 12:00:00+00', 850.300000),
    (2, '2026-09-23 00:00:00+00', 850.200000),
    (2, '2026-09-23 12:00:00+00', 850.150000),
    -- ЛИНИЯ-1, влагомер: содержание воды, %
    (3, '2026-09-22 00:00:00+00', 0.500000),
    (3, '2026-09-23 00:00:00+00', 0.480000),
    -- ЛИНИЯ-2, массомер
    (4, '2026-09-22 00:00:00+00', 500.000000),
    (4, '2026-09-22 06:00:00+00', 502.250000),
    (4, '2026-09-22 12:00:00+00', 498.750000),
    (4, '2026-09-22 18:00:00+00', 501.500000),
    (4, '2026-09-23 00:00:00+00', 499.750000),
    (4, '2026-09-23 06:00:00+00', 503.000000),
    (4, '2026-09-23 12:00:00+00', 497.250000),
    -- ЛИНИЯ-2, плотномер
    (5, '2026-09-22 00:00:00+00', 860.000000),
    (5, '2026-09-22 12:00:00+00', 859.800000),
    (5, '2026-09-23 00:00:00+00', 860.100000),
    (5, '2026-09-23 12:00:00+00', 859.950000);