-- Решение упражнения 02 (модуль 13): топ-5 медленных запросов (pg_stat_statements).
-- Скопируй в exercises/ex02_monitoring/student.sql после попытки.

CREATE TEMP TABLE top_slow AS
SELECT calls,
       round(total_exec_time::numeric, 1) AS total_ms,
       round(mean_exec_time::numeric, 2)  AS mean_ms,
       left(query, 60)                     AS query
  FROM pg_stat_statements
 ORDER BY total_exec_time DESC
 LIMIT 5;