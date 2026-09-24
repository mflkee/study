-- Решение упражнения 01: добавить тип «расчётчик» (flow_calc).
-- Скопируйте в `migrations/0003_add_flow_calc.sql` после своей попытки.

ALTER TABLE devices DROP CONSTRAINT devices_device_type_check;
ALTER TABLE devices ADD CONSTRAINT devices_device_type_check
    CHECK (device_type IN ('mass_meter', 'density_meter', 'moisture_meter', 'level_meter', 'flow_calc'));

INSERT INTO devices (line_id, device_type, tag, model)
SELECT id, 'flow_calc', 'FC-01-001', 'FCC-900'
  FROM equipment_lines WHERE name = 'ЛИНИЯ-1';