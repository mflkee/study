-- Ассерты упражнения 01: наличие 3 таблиц, UNIQUE(tag), FK, композитного индекса.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ex01_lines') THEN
        RAISE EXCEPTION 'нет таблицы ex01_lines';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ex01_devices') THEN
        RAISE EXCEPTION 'нет таблицы ex01_devices';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ex01_events') THEN
        RAISE EXCEPTION 'нет таблицы ex01_events';
    END IF;

    -- UNIQUE на tag в ex01_devices (indexdef содержит CREATE UNIQUE ... (tag))
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'ex01_devices' AND indexdef LIKE 'CREATE UNIQUE%' AND indexdef LIKE '%(tag)%'
    ) THEN
        RAISE EXCEPTION 'ex01_devices.tag должен быть уникальным (UNIQUE)';
    END IF;

    -- FK ex01_events -> ex01_devices
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'ex01_events' AND c.contype = 'f'
          AND c.confrelid = 'ex01_devices'::regclass
    ) THEN
        RAISE EXCEPTION 'ex01_events должен иметь FK на ex01_devices';
    END IF;

    -- Композитный индекс (device_id, ts) на ex01_events
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'ex01_events' AND indexdef LIKE '%(device_id, ts)%'
    ) THEN
        RAISE EXCEPTION 'нужен композитный индекс (device_id, ts) на ex01_events';
    END IF;
END $$;