-- Ассерты упражнения 03: «последнее значение за сутки», не max.
DO $$
DECLARE v numeric;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.views
                   WHERE table_name = 'last_daily_measurement') THEN
        RAISE EXCEPTION 'представление last_daily_measurement не создано';
    END IF;

    IF (SELECT count(*) FROM last_daily_measurement) <> 10 THEN
        RAISE EXCEPTION 'ожидалось 10 строк (5 устройств x 2 дня)';
    END IF;

    -- device 1 за 2026-09-23: последний замер = 998.500000 (не max 1002.750000)
    SELECT last_value INTO v FROM last_daily_measurement
    WHERE device_id = 1 AND day = '2026-09-23';
    IF v IS DISTINCT FROM 998.500000 THEN
        RAISE EXCEPTION 'device 1 / 09-23: ожидалось последнее значение 998.5, а не max (или нет строки)';
    END IF;

    SELECT last_value INTO v FROM last_daily_measurement
    WHERE device_id = 4 AND day = '2026-09-23';
    IF v IS DISTINCT FROM 497.250000 THEN
        RAISE EXCEPTION 'device 4 / 09-23: ожидалось 497.25';
    END IF;
END $$;