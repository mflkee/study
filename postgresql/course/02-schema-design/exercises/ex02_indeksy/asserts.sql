-- Ассерты упражнения 02: partial-индекс с quality=1 и композитный (line_id, device_type).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'measurements' AND indexdef LIKE '%quality = 1%'
    ) THEN
        RAISE EXCEPTION 'нет partial-индекса на measurements с WHERE quality = 1';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'devices' AND indexdef LIKE '%(line_id, device_type)%'
    ) THEN
        RAISE EXCEPTION 'нет композитного индекса (line_id, device_type) на devices';
    END IF;
END $$;