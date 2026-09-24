-- 0001: базовый справочник «каталог оборудования» (стартовая схема модуля 06).
-- База модуля — course_m06 (отдельная от каталога модулей 01–04).
-- Миграции применяются sqlx::migrate! (пример 03) или `sqlx migrate run`.

CREATE TABLE equipment_lines (
    id          serial PRIMARY KEY,
    name        text NOT NULL UNIQUE,
    description text
);

CREATE TABLE devices (
    id          serial PRIMARY KEY,
    line_id     int  NOT NULL REFERENCES equipment_lines(id) ON DELETE CASCADE,
    device_type text NOT NULL CHECK (device_type IN ('mass_meter','density_meter','moisture_meter')),
    tag         text NOT NULL UNIQUE,
    model       text
);

CREATE TABLE measurements (
    id        bigserial PRIMARY KEY,
    device_id int  NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    ts        timestamptz NOT NULL,
    value     numeric(20,6) NOT NULL,
    quality   int NOT NULL DEFAULT 0 CHECK (quality BETWEEN 0 AND 3)
);

-- Тестовые данные: две линии, пять устройств, немного измерений.
INSERT INTO equipment_lines (name, description) VALUES
    ('ЛИНИЯ-1', 'Линия измерения №1: массомер + плотномер'),
    ('ЛИНИЯ-2', 'Линия измерения №2: массомер');

INSERT INTO devices (line_id, device_type, tag, model) VALUES
    (1, 'mass_meter',     'M-01-001', 'CMF-300'),
    (1, 'density_meter',  'D-01-001', 'MVD-1'),
    (1, 'moisture_meter', 'W-01-001', 'MVM-2'),
    (2, 'mass_meter',     'M-02-001', 'CMF-200'),
    (2, 'density_meter',  'D-02-001', 'MVD-2');

INSERT INTO measurements (device_id, ts, value) VALUES
    (1, '2026-09-22 00:00:00+00', 1000.000000),
    (1, '2026-09-22 06:00:00+00', 1010.500000),
    (1, '2026-09-22 12:00:00+00', 1005.250000),
    (1, '2026-09-22 18:00:00+00', 990.125000),
    (1, '2026-09-23 00:00:00+00', 995.000000),
    (1, '2026-09-23 06:00:00+00', 1002.750000),
    (1, '2026-09-23 12:00:00+00', 998.500000);