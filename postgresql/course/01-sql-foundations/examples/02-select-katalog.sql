-- 02-select-katalog.sql — SELECT: проекции, WHERE, ORDER BY, LIMIT, NULL-семантика.
-- Требует применённых 00-schema-katalog.sql и 01-seed-katalog.sql.

-- 1. Проекция + сортировка
SELECT id, name FROM equipment_lines ORDER BY id;

-- 2. Фильтр по внешнему ключу + сортировка по тегу
SELECT tag, device_type, model
FROM devices
WHERE line_id = 1
ORDER BY tag;

-- 3. DISTINCT: какие типы устройств есть в справочнике
SELECT DISTINCT device_type FROM devices ORDER BY device_type;

-- 4. NULL-семантика: у ЛИНИИ-2 description = NULL
SELECT id, name, description FROM equipment_lines ORDER BY id;
SELECT id, name FROM equipment_lines WHERE description IS NULL;
SELECT count(*) AS total, count(description) AS with_description FROM equipment_lines;

-- 5. LIMIT / OFFSET: первые 3 измерения
SELECT id, device_id, ts, value FROM measurements ORDER BY id LIMIT 3;