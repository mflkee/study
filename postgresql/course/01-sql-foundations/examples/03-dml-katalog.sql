-- 03-dml-katalog.sql — INSERT/UPDATE/DELETE и ограничения.
-- Требует применённых 00-schema-katalog.sql и 01-seed-katalog.sql.

-- 1. INSERT ... RETURNING (вернуть созданные строки)
INSERT INTO equipment_lines (name, description)
VALUES ('ЛИНИЯ-3', 'резервная линия')
RETURNING id, name;

-- 2. INSERT ... SELECT с защитой от дублей (не навредить при повторном прогоне)
INSERT INTO devices (line_id, device_type, tag, model)
SELECT 1, 'density_meter', 'D-01-002', 'MVD-2'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE tag = 'D-01-002');

-- 3. UPDATE ... RETURNING
UPDATE devices SET model = 'CMF-300/2' WHERE tag = 'M-01-001'
RETURNING tag, model;

-- 4. DELETE по условию (старые данные за пределами окна)
DELETE FROM measurements WHERE ts < '2026-09-01 00:00:00+00';

-- 5. Итог: сколько стало
SELECT (SELECT count(*) FROM equipment_lines) AS lines,
       (SELECT count(*) FROM devices)         AS devices,
       (SELECT count(*) FROM measurements)    AS measurements;