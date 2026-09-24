-- 0002: новый тип датчика «уровнемер» (level_meter).
-- Показывает эволюцию схемы: менять CHECK-ограничение + добавить строки.
-- Это образец того, что в упражнении 01 ученик сделает для 'flow_calc'.

ALTER TABLE devices DROP CONSTRAINT devices_device_type_check;
ALTER TABLE devices ADD CONSTRAINT devices_device_type_check
    CHECK (device_type IN ('mass_meter', 'density_meter', 'moisture_meter', 'level_meter'));

INSERT INTO equipment_lines (name, description) VALUES
    ('ЛИНИЯ-3', 'Линия измерения №3: уровнемер');

-- Привязка по имени линии (id берём из строки) — переносимо между стендами.
INSERT INTO devices (line_id, device_type, tag, model)
SELECT id, 'level_meter', 'L-03-001', 'MG-5100'
  FROM equipment_lines WHERE name = 'ЛИНИЯ-3';