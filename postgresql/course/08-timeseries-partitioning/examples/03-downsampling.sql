-- 03-downsampling.sql — агрегаты по интервалам: date_bin, matview, сменный отчёт.
-- Модуль: 08-timeseries-partitioning, урок 02. База: course_m06.

-- 1. Суточный отчёт «по каждому устройству»: date_bin режет ts на бакеты.
   -- (здесь «устройство» = device_id; связка с линиями — в упражнении 01)
SELECT d AS day,
       device_id,
       count(*)                 AS n,
       round(sum(value)::numeric, 2) AS total
  FROM measurements_part,
       LATERAL (SELECT date_bin('1 day', ts, '2026-08-01 00:00:00+00'::timestamptz) AS d) b
 GROUP BY d, device_id
 ORDER BY d, device_id
 LIMIT 12;

-- 2. Отчёт за СМЕНУ (12 часов): те же бакеты, интервал другой.
SELECT d AS shift, device_id, count(*) AS n, round(sum(value)::numeric, 2) AS total
  FROM measurements_part,
       LATERAL (SELECT date_bin('12 hours', ts, '2026-08-01 00:00:00+00'::timestamptz) AS d) b
 GROUP BY d, device_id
 ORDER BY d, device_id
 LIMIT 12;

-- 3. Материализованное представление: суточники считаем один раз, дальше читаем.
CREATE MATERIALIZED VIEW IF NOT EXISTS daq_daily AS
SELECT date_bin('1 day', ts, '2026-08-01 00:00:00+00'::timestamptz) AS day,
       device_id,
       count(*)    AS n,
       sum(value)  AS total
  FROM measurements_part
 GROUP BY day, device_id;

REFRESH MATERIALIZED VIEW daq_daily;

-- Размер матвью vs исходной таблицы (порядки величин).
SELECT 'matview' AS what, pg_size_pretty(pg_total_relation_size('daq_daily')::bigint) AS size
UNION ALL
SELECT 'table', pg_size_pretty(pg_total_relation_size('measurements_part')::bigint);

-- 4. Отчёт по материализованным суткам (быстро: без скан�а исходных строк).
SELECT day, device_id, n, round(total::numeric, 2) AS total
  FROM daq_daily
 WHERE day >= '2026-08-25' AND day < '2026-08-28'
 ORDER BY day, device_id;

-- Ограничение: матвью не следит за новыми данными — нужен REFRESH по расписанию
-- (в лабе 01 — ловушка «отчёт вчерашний»; TimescaleDB решает это
-- continuous-агрегатами — урок 03).