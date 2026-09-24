-- Решение упражнения 01: линия + два устройства.
-- id новой линии берётся из RETURNING через CTE — не хардкодим (после сида id=3!).
-- Применение проверки: infra/check-sql.sh <student.sql> asserts.sql

WITH new_line AS (
    INSERT INTO equipment_lines (name, description)
    VALUES ('ЛИНИЯ-4', 'линия измерения №4')
    RETURNING id
)
INSERT INTO devices (line_id, device_type, tag, model)
SELECT nl.id, v.device_type, v.tag, v.model
FROM new_line nl
CROSS JOIN (VALUES
    ('mass_meter',     'M-04-001', 'CMF-100'),
    ('moisture_meter', 'W-04-001', 'MVM-3')
) AS v(device_type, tag, model);