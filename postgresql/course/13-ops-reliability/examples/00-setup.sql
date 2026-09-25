-- 00-setup.sql — данные для демонстраций модуля 13 (база course_m06).
-- Простенькая таблица «тревог»: на ней показываем pg_dump/PITR/мониторинг.
-- Идемпотентно.

DROP TABLE IF EXISTS alarms;
CREATE TABLE alarms (
    id      bigserial PRIMARY KEY,
    source  text NOT NULL,
    ts      timestamptz NOT NULL DEFAULT now(),
    payload jsonb NOT NULL
);

-- Серия тревог за последние часы (детерминированно).
INSERT INTO alarms (source, ts, payload)
SELECT 'modbus',
       now() - ((100 - i) || ' minutes')::interval,
       jsonb_build_object('level', CASE WHEN i % 5 = 0 THEN 'crit' ELSE 'warn' END,
                          'seq', i)
  FROM generate_series(1, 100) i;

-- Сколько тревог «критично» (эталон для восстановлений).
SELECT count(*) FILTER (WHERE payload->>'level' = 'crit') AS crit_count FROM alarms;