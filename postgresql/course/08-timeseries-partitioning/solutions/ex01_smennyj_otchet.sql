-- Решение упражнения 01 (модуль 08): сменный отчёт по каждой «линии».
-- Скопируйте в student.sql после своей попытки.

CREATE TEMP TABLE report_shifts AS
SELECT date_bin('12 hours', ts, '2026-08-01 00:00:00+00'::timestamptz) AS shift,
       device_id,
       count(*)                 AS n,
       round(sum(value)::numeric, 2) AS total
  FROM measurements_part
 GROUP BY shift, device_id
 ORDER BY shift, device_id;