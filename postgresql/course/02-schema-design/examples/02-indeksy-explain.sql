-- 02-indeksy-explain.sql — демо типов индексов на синтетической таблице.
-- Всё внутри одной транзакции (ROLLBACK в конце) — стенд не меняется.
-- Применение: infra/apply-db.sh course/02-schema-design/examples/02-indeksy-explain.sql

BEGIN;

-- Большая таблица: телеметрия 50 устройств за ~139 дней (шаг 1 минута).
CREATE TEMP TABLE sensor_large (
    id        serial,
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(20,6) NOT NULL
);

INSERT INTO sensor_large (device_id, ts, value)
SELECT (i % 50) + 1,
       '2026-01-01 00:00:00+00'::timestamptz + (i * interval '1 minute'),
       round((random() * 100)::numeric, 3)
FROM generate_series(1, 200000) AS i;

-- Статистика: autovacuum НЕ обрабатывает временные таблицы,
-- поэтому ANALYZE обязателен, иначе планировщик не видит таблицу.
ANALYZE sensor_large;

-- 1. Точечный запрос без индекса: Seq Scan
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM sensor_large
WHERE device_id = 7 AND ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';

-- 2. Композитный B-tree (device_id, ts): точечный запрос
CREATE INDEX ON sensor_large USING btree (device_id, ts);

EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM sensor_large
WHERE device_id = 7 AND ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';

-- 3. BRIN по ts: выборка по времени (все устройства за период)
CREATE INDEX sensor_large_ts_brin ON sensor_large USING brin (ts);

-- Честный план (планировщик может предпочесть Seq Scan на таблице такого размера)
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM sensor_large
WHERE ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';

-- А теперь принудительно покажем BRIN-план (enable_seqscan = off): какой он по форме
-- и сколько буферов читает (BRIN отсекает блоки, не попадающие в диапазон).
SET LOCAL enable_seqscan = off;
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM sensor_large
WHERE ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';
RESET enable_seqscan;

-- 4. Размеры индексов для сравнения (temp-таблица: индексы ищем через pg_class)
SELECT c.relname AS index_name,
       pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_class c
JOIN pg_index i ON i.indexrelid = c.oid
WHERE i.indrelid = 'sensor_large'::regclass
ORDER BY pg_relation_size(c.oid) DESC;

ROLLBACK;