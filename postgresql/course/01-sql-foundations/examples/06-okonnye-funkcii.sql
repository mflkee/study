-- 06-okonnye-funkcii.sql — оконные функции: сменные/суточные агрегаты (кейс 5).
-- Требует применённых 00-schema-katalog.sql и 01-seed-katalog.sql.

-- 1. ROW_NUMBER: последнее изменение каждого устройства
SELECT device_id, ts, value
FROM (
    SELECT device_id, ts, value,
           row_number() OVER (PARTITION BY device_id ORDER BY ts DESC) AS rn
    FROM measurements
) t
WHERE rn = 1
ORDER BY device_id;

-- 2. LAG: дельта относительно предыдущего замера того же устройства
SELECT device_id, ts, value,
       round(value - lag(value) OVER (PARTITION BY device_id ORDER BY ts), 3) AS delta
FROM measurements
ORDER BY device_id, ts;

-- 3. GROUP BY по дню: сколько замеров и сумма по каждому устройству за день
SELECT device_id, ts::date AS day,
       count(*) AS n,
       round(sum(value), 3) AS total
FROM measurements
GROUP BY device_id, ts::date
ORDER BY device_id, day;

-- 4. rank: устройства по среднему значению (оконная функция над агрегатом)
SELECT device_id,
       round(avg(value), 3) AS avg_v,
       rank() OVER (ORDER BY avg(value) DESC) AS rnk
FROM measurements
GROUP BY device_id
ORDER BY rnk;