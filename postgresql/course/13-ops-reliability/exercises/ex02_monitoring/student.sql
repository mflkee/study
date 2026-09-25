-- Заготовка упражнения 02 (модуль 13): топ-5 медленных запросов.
-- Заполни запрос (по образцу examples/05-pg-stat-queries.sql) и положи
-- результат в temp-таблицу top_slow.

CREATE TEMP TABLE top_slow AS
SELECT 0 AS calls, 0.0 AS total_ms, 0.0 AS mean_ms, 'TODO' AS query;   -- замени