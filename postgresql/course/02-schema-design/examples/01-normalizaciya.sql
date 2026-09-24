-- 01-normalizaciya.sql — демо: денормализованные события и их нормализация.
-- Всё внутри транзакции (ROLLBACK) — стенд не меняется.
-- Применение: infra/apply-db.sh course/02-schema-design/examples/01-normalizaciya.sql

BEGIN;

-- «Сырая» денормализованная таблица: справочные поля повторяются в каждой строке.
CREATE TEMP TABLE raw_events (
    line_name   text,
    device_tag  text,
    device_type text,
    model       text,
    ts          timestamptz,
    value       numeric(20,6)
);

INSERT INTO raw_events VALUES
    ('ЛИНИЯ-1', 'M-01-001', 'mass_meter',     'CMF-300', '2026-09-22 00:00:00+00', 1000.0),
    ('ЛИНИЯ-1', 'M-01-001', 'mass_meter',     'CMF-300', '2026-09-22 06:00:00+00', 1010.5),
    ('ЛИНИЯ-2', 'D-02-001', 'density_meter',  'MVD-2',   '2026-09-22 00:00:00+00', 860.0),
    ('ЛИНИЯ-2', 'D-02-001', 'density_meter',  'MVD-2',   '2026-09-22 12:00:00+00', 859.8),
    ('ЛИНИЯ-2', 'D-02-001', 'density_meter',  'MVD-1',   '2026-09-22 18:00:00+00', 860.1); -- опечатка модели

-- Симптом: справочные данные продублированы, модель «поплыла» по строкам
SELECT DISTINCT device_tag, model FROM raw_events ORDER BY device_tag;

-- Нормализация: справочники строим из DISTINCT-данных.
-- Дубли в «сырых» данных — типичная проблема: просто SELECT DISTINCT по модели
-- дал бы две строки на тег D-02-001 (опечатка MVD-1/MVD-2) и UNIQUE не создался бы.
-- Поэтому справочные значения дедуплицируем агрегатом (min) по бизнес-ключу tag.
CREATE TEMP TABLE n_lines AS
SELECT DISTINCT line_name AS name FROM raw_events;
ALTER TABLE n_lines ADD COLUMN id serial PRIMARY KEY;

CREATE TEMP TABLE n_devices AS
SELECT d.device_tag AS tag,
       d.device_type,
       min(d.model) AS model,
       l.id AS line_id
FROM raw_events d
JOIN n_lines l ON l.name = d.line_name
GROUP BY d.device_tag, d.device_type, l.id;
ALTER TABLE n_devices ADD COLUMN id serial PRIMARY KEY;
ALTER TABLE n_devices ADD CONSTRAINT n_devices_tag_uniq UNIQUE (tag);

CREATE TEMP TABLE n_events AS
SELECT e.ts, e.value, dev.id AS device_id
FROM raw_events e
JOIN n_devices dev ON dev.tag = e.device_tag;

-- Теперь правка модели — ОДНА строка (в сырой таблице — три)
UPDATE n_devices SET model = 'MVD-2' WHERE tag = 'D-02-001';

SELECT tag, model FROM n_devices ORDER BY tag;
SELECT count(*) AS events FROM n_events;

ROLLBACK;