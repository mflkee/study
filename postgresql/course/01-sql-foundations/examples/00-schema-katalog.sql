-- 00-schema-katalog.sql — справочники и измерения «каталога оборудования».
-- Основа сквозных кейсов 1 (каталог), 3 (точность), 5 (отчёты).
-- Идемпотентно: при повторном применении ничего не ломает.
--
-- Применение: infra/apply-db.sh course/01-sql-foundations/examples/00-schema-katalog.sql

-- Линии (линии измерения: «ЛИНИЯ-1», «ЛИНИЯ-2»...)
CREATE TABLE IF NOT EXISTS equipment_lines (
    id          serial PRIMARY KEY,
    name        text NOT NULL UNIQUE,
    description text
);

-- Устройства на линии: массомер, плотномер, влагомер.
-- device_type — строкой с CHECK (не enum): enum не имеет CREATE TYPE IF NOT EXISTS,
-- а CHECK-ограничение переживает повторное применение скрипта.
CREATE TABLE IF NOT EXISTS devices (
    id          serial PRIMARY KEY,
    line_id     int  NOT NULL REFERENCES equipment_lines(id) ON DELETE CASCADE,
    device_type text NOT NULL CHECK (device_type IN ('mass_meter','density_meter','moisture_meter')),
    tag         text NOT NULL UNIQUE,   -- тег устройства, напр. 'M-01-001'
    model       text
);

-- Измерения (телеметрия). Числовые значения — NUMERIC(20,6), не float8:
-- метрологические величины не должны терять точность (кейс 3).
-- quality: 0 = good, 1 = bad, 2 = uncertain, 3 = manual.
CREATE TABLE IF NOT EXISTS measurements (
    id        bigserial PRIMARY KEY,
    device_id int  NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    ts        timestamptz NOT NULL,
    value     numeric(20,6) NOT NULL,
    quality   int NOT NULL DEFAULT 0 CHECK (quality BETWEEN 0 AND 3)
);

-- Типовой запрос телеметрии: «последние значения по каждому устройству» —
-- идёт по индексу (device_id, ts).
CREATE INDEX IF NOT EXISTS idx_measurements_device_ts
    ON measurements (device_id, ts);