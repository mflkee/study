-- 01-partition-queries.sql — партиционирование: pruning, B-tree vs BRIN.
-- Модуль: 08-timeseries-partitioning, урок 01.
-- База: course_m06 (15432). Запуск:
--   docker compose exec -T postgres psql -U course -d course_m06 -f <этот файл>

-- 1. Partition pruning: фильтр по ts читает ТОЛЬКО нужную партицию.
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*), sum(value) FROM measurements_part
 WHERE ts >= '2026-09-10' AND ts < '2026-09-11';

-- В плане должен быть один узел с measurements_part_2026_09 (не обе партиции!)
-- и БЕЗ фильтра ts внутри партиции: PostgreSQL ограничивает диапазон партицией.

-- 2. Без фильтра по ts — затронуты обе партиции (Seq Scan дважды).
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM measurements_part WHERE device_id = 3;

-- 3. B-tree (device_id, ts) в каждой партиции:
CREATE INDEX IF NOT EXISTS measurements_part_device_ts
    ON measurements_part (device_id, ts);

EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM measurements_part
 WHERE device_id = 3 AND ts >= '2026-09-01' AND ts < '2026-09-02';

-- 4. BRIN на ts: компактный индекс «диапазоны страниц» (по физическому порядку).
CREATE INDEX IF NOT EXISTS measurements_part_ts_brin
    ON measurements_part USING brin (ts);

-- Размеры индексов: B-tree против BRIN (обрати внимание на порядки!).
SELECT c.relname,
       pg_size_pretty(pg_relation_size(i.indexrelid)) AS index_size
  FROM pg_index i
  JOIN pg_class c ON c.oid = i.indexrelid
 WHERE i.indrelid IN
       (SELECT i2.inhrelid FROM pg_inherits i2
         JOIN pg_class p ON p.oid = i2.inhparent
        WHERE p.relname = 'measurements_part')
   AND c.relname LIKE 'measurements_part_2026_09%'
 ORDER BY c.relname;

EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM measurements_part
 WHERE ts >= '2026-09-20' AND ts < '2026-09-22';