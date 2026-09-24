-- 05-joins-group.sql — JOIN, GROUP BY, HAVING (каталог + измерения).
-- Требует применённых 00-schema-katalog.sql и 01-seed-katalog.sql.

-- 1. JOIN: устройства вместе с линиями
SELECT l.name AS line, d.tag, d.device_type
FROM equipment_lines l
JOIN devices d ON d.line_id = l.id
ORDER BY l.name, d.tag;

-- 2. LEFT JOIN: линии и число устройств (в т.ч. линии без устройств)
SELECT l.name, count(d.id) AS devices_count
FROM equipment_lines l
LEFT JOIN devices d ON d.line_id = l.id
GROUP BY l.name
ORDER BY l.name;

-- 3. GROUP BY ... HAVING: линии, где >= 2 устройств
SELECT d.line_id, count(*) AS cnt
FROM devices d
GROUP BY d.line_id
HAVING count(*) >= 2
ORDER BY d.line_id;

-- 4. Агрегаты измерений по устройствам
SELECT device_id,
       count(*)        AS n,
       min(value)      AS min_v,
       max(value)      AS max_v,
       round(avg(value), 3) AS avg_v
FROM measurements
GROUP BY device_id
ORDER BY device_id;