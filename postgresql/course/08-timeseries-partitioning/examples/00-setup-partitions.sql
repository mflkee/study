-- 00-setup-partitions.sql — нативное партиционирование по времени (модуль 08).
-- База: course_m06 (15432). Идемпотентно: DROP + пересоздание (учебный стенд).
-- Применение: infra/apply-db.sh course/08-timeseries-partitioning/examples/00-setup-partitions.sql
--            (в apply-db.sh база фиксирована course — для course_m06 используй:
--             docker compose exec -T postgres psql -U course -d course_m06 -f - < файл)

-- Мастер-таблица: измерения телеметрии, партиционируем по ts (месяц).
DROP TABLE IF EXISTS measurements_part CASCADE;

CREATE TABLE measurements_part (
    id        bigserial NOT NULL,
    device_id int NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL
) PARTITION BY RANGE (ts);

-- Партиции на месяц: 2026-08, 2026-09, 2026-10.
CREATE TABLE measurements_part_2026_08 PARTITION OF measurements_part
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE measurements_part_2026_09 PARTITION OF measurements_part
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE measurements_part_2026_10 PARTITION OF measurements_part
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- Сид: 1 000 000 измерений, 8 устройств, 60 дней (август–сентябрь).
INSERT INTO measurements_part (device_id, ts, value)
SELECT (i % 8) + 1,
       '2026-08-01 00:00:00+00'::timestamptz + (random() * interval '60 days'),
       round((random() * 1000)::numeric, 4)
  FROM generate_series(1, 1000000) i;

ANALYZE measurements_part;

-- Контроль: сколько строк в каждой партиции.
SELECT c.relname AS partition, pg_size_pretty(pg_total_relation_size(c.oid)) AS size
  FROM pg_inherits i
  JOIN pg_class c ON c.oid = i.inhrelid
  JOIN pg_class p ON p.oid = i.inhparent
 WHERE p.relname = 'measurements_part'
 ORDER BY c.relname;