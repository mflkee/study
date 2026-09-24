-- 04-tipy-numeric-timestamptz.sql — типы данных под метрологию.
-- NUMERIC vs float8, timestamptz vs timestamp, JSONB, enum, array.

-- 1. float8 vs numeric: 0.1 + 0.2
SELECT 0.1::float8 + 0.2::float8 AS float_sum,
       0.1::numeric + 0.2::numeric AS numeric_sum;

-- 2. timestamptz: один и тот же МОМЕНТ, разные записи
SET TIME ZONE 'UTC';
SELECT '2026-09-24 12:00:00+00'::timestamptz AS utc_noon,
       '2026-09-24 15:00:00+03'::timestamptz AS msk_noon;
SELECT ('2026-09-24 12:00:00+00'::timestamptz = '2026-09-24 15:00:00+03'::timestamptz) AS same_instant;

-- 3. timestamptz: отображение зависит от текущего часового пояса сессии
SET TIME ZONE 'Europe/Moscow';
SELECT '2026-09-24 12:00:00+00'::timestamptz AS shown_in_msk;
RESET TIME ZONE;

-- 4. Арифметика интервалов: timestamp (стена) vs timestamptz (момент)
SELECT '2026-09-24 12:00:00'::timestamp + interval '1 hour'    AS ts_plus,
       '2026-09-24 12:00:00+00'::timestamptz + interval '1 hour' AS tstz_plus;

-- 5. JSONB: порядок ключей не важен при сравнении
SELECT '{"tag":"M-01","v":1}'::jsonb = '{"v":1,"tag":"M-01"}'::jsonb AS jsonb_equal;

-- 6. array: основы
SELECT ARRAY['a','b','c'] AS arr,
       array_length(ARRAY[1,2,3], 1) AS len;

-- 7. enum: создание с защитой от повторного применения
DO $$ BEGIN
    CREATE TYPE device_kind AS ENUM ('mass_meter', 'density_meter', 'moisture_meter');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
SELECT 'mass_meter'::device_kind AS kind;