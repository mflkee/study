-- 02-triggers.sql — триггеры: BEFORE-валидация + AFTER-аудит (кейс 4).
-- Модуль: 09, урок 02. База: course_m06.

-- 1. BEFORE: валидация «на входе» — нельзя удалять последний массомер линии
--    (пример правила).
CREATE OR REPLACE FUNCTION prevent_last_mass_meter() RETURNS trigger AS $$
DECLARE
    n int;
BEGIN
    IF TG_OP = 'DELETE' AND OLD.device_type = 'mass_meter' THEN
        SELECT count(*) INTO n FROM devices
         WHERE line_id = OLD.line_id AND device_type = 'mass_meter';
        IF n <= 1 THEN
            RAISE EXCEPTION 'нельзя удалить последний массомер линии %', OLD.line_id;
        END IF;
    END IF;
    RETURN OLD;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_last_mass ON devices;
CREATE TRIGGER trg_prevent_last_mass
    BEFORE DELETE ON devices
    FOR EACH ROW EXECUTE FUNCTION prevent_last_mass_meter();

-- Прямо на каталоге не удалим (там по 1 массомеру на линию) —
-- демонстрируем на временной таблице:
CREATE TEMP TABLE demo_devices (id int, line_id int, device_type text);
ALTER TABLE demo_devices ADD COLUMN tag text;
INSERT INTO demo_devices VALUES (1, 1, 'mass_meter', 'M-DEMO');
-- (триггеры для временной таблицы — в модуле: так же DROP TRIGGER,
--  но в TEMP-таблице триггеры не наследуются автоматом — упрощённый пример в уроке)

-- 2. AFTER-аудит уже живёт в 00-setup: trg_devices_audit.
--    Показать, что записали при UPDATE:
UPDATE devices SET model = 'CMF-300v2' WHERE tag = 'M-01-001';
SELECT action, data->>'tag' AS tag, data->>'model' AS model
  FROM audit_log WHERE entity = 'devices' ORDER BY id DESC LIMIT 1;