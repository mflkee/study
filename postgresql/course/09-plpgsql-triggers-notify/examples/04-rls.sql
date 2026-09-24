-- 04-rls.sql — роли и RLS: минимальные привилегии, политики строк (урок 04).
-- Модуль: 09. База: course_m06. Роли app_user/auditor созданы в 00-setup.

-- 0. Минимальные привилегии: обе роли только ЧИТАЮТ devices (запись — никогда):
GRANT SELECT ON devices TO app_user, auditor;

-- 1. На devices включена RLS (00-setup): auditor видит всё,
--    app_user — только «не-level_meter» строки. Проверяем под ролями:
SET ROLE auditor;
SELECT count(*) AS auditor_sees FROM devices;
RESET ROLE;

SET ROLE app_user;
SELECT count(*) AS app_sees FROM devices;
SELECT device_type, count(*) FROM devices GROUP BY device_type ORDER BY 1;
RESET ROLE;

-- 2. Запись app_user запрещена привилегиями (не триггером) — ожидаемая ошибка:
SET ROLE app_user;
INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'mass_meter', 'X-99-999', 'y');
RESET ROLE;

-- 3. Полезные вопросы для эксплуатации:
SELECT rolname FROM pg_roles WHERE rolname IN ('app_user', 'auditor', 'course');
SELECT polname, polrelid::regclass AS table,
       pg_get_expr(polqual, polrelid) AS using_expr
  FROM pg_policy;