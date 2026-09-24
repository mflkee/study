-- 02-retention.sql — ретеншн: партиции удаляются мгновенно, без VACUUM-хвоста.
-- Модуль: 08-timeseries-partitioning, урок 01. База: course_m06.
-- На ОТДЕЛЬНОЙ таблице hist (основной сид measurements_part не трогаем).

-- Готовим «историю» с месячными партициями.
DROP TABLE IF EXISTS hist CASCADE;
CREATE TABLE hist (
    id    bigint NOT NULL,
    ts    timestamptz NOT NULL,
    value numeric(12,4) NOT NULL
) PARTITION BY RANGE (ts);
CREATE TABLE hist_2026_08 PARTITION OF hist FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE hist_2026_09 PARTITION OF hist FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

INSERT INTO hist (id, ts, value)
SELECT i, '2026-08-01 00:00:00+00'::timestamptz + ((i % 30) || ' days')::interval, i
  FROM generate_series(1, 20000) i;

SELECT c.relname, count(h.*) AS rows
  FROM pg_inherits i
  JOIN pg_class c ON c.oid = i.inhrelid
  LEFT JOIN hist h ON true
 WHERE i.inhparent = 'hist'::regclass
 GROUP BY c.relname
 ORDER BY c.relname;

-- «Ретеншн 60 дней»: август + 5 дней сентября уже вне окна.
-- Безопасно удалять ТОЛЬКО полностью завершившиеся партиции.
DROP TABLE hist_2026_08;

SELECT count(*) AS rows_after_retention FROM hist;   -- должно стать меньше

-- Последствия: данные августа исчезли ОПТОМ (это и хорошо для ретеншна,
-- и ловушка: обратной дороги нет — лаба 01 «Ретеншн удалил нужное»).
-- Смотрим, какие партиции остались.
SELECT c.relname FROM pg_inherits i
  JOIN pg_class c ON c.oid = i.inhrelid
 WHERE i.inhparent = 'hist'::regclass ORDER BY c.relname;