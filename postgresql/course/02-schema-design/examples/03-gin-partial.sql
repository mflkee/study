-- 03-gin-partial.sql — GIN по jsonb и partial-индекс (демо формы планов).
-- Транзакция + ROLLBACK. Применение: infra/apply-db.sh .../03-gin-partial.sql

BEGIN;

CREATE TEMP TABLE device_meta (
    tag   text PRIMARY KEY,
    attrs jsonb
);

INSERT INTO device_meta VALUES
    ('M-01-001', '{"protocol":"modbus","unit":"kg/h"}'),
    ('M-02-001', '{"protocol":"modbus","unit":"kg/h"}'),
    ('D-02-001', '{"protocol":"modbus","unit":"kg/m3"}'),
    ('W-01-001', '{"protocol":"modbus","unit":"%"}');

-- GIN-индекс по jsonb: поиск внутри документа
CREATE INDEX device_meta_attrs_gin ON device_meta USING gin (attrs);

-- Чтобы показать форму плана (таблица мала — планировщик предпочёл бы Seq Scan)
SET LOCAL enable_seqscan = off;
EXPLAIN (ANALYZE, COSTS OFF)
SELECT tag FROM device_meta WHERE attrs @> '{"protocol":"modbus"}';
RESET enable_seqscan;

-- Partial-индекс: «запросы по массомерам» (подмножество строк)
CREATE INDEX device_meta_partial ON device_meta(tag) WHERE attrs @> '{"unit":"kg/h"}';

SET LOCAL enable_seqscan = off;
EXPLAIN (ANALYZE, COSTS OFF)
SELECT tag FROM device_meta WHERE attrs @> '{"unit":"kg/h"}';
RESET enable_seqscan;

ROLLBACK;