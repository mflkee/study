-- Ассерты упражнения 01: проверяют состояние справочника после решения.
DO $$
DECLARE
    lid int;
BEGIN
    -- Линия существует (переменная названа lid, чтобы не конфликтовать с колонкой line_id)
    SELECT id INTO lid FROM equipment_lines WHERE name = 'ЛИНИЯ-4';
    IF lid IS NULL THEN
        RAISE EXCEPTION 'линия ЛИНИЯ-4 не найдена';
    END IF;

    -- На линии ровно 2 устройства
    IF (SELECT count(*) FROM devices WHERE line_id = lid) <> 2 THEN
        RAISE EXCEPTION 'на ЛИНИЯ-4 должно быть 2 устройства, проверь свою вставку';
    END IF;

    -- Теги и типы
    IF NOT EXISTS (SELECT 1 FROM devices
                   WHERE line_id = lid AND tag = 'M-04-001' AND device_type = 'mass_meter') THEN
        RAISE EXCEPTION 'массомер M-04-001 не найден на ЛИНИЯ-4';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM devices
                   WHERE line_id = lid AND tag = 'W-04-001' AND device_type = 'moisture_meter') THEN
        RAISE EXCEPTION 'влагомер W-04-001 не найден на ЛИНИЯ-4';
    END IF;
END $$;