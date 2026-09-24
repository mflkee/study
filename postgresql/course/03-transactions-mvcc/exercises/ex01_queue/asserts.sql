-- Ассерты упражнения 01: структура очереди + атомарный захват по приоритету.
DO $$
DECLARE
    processing_id bigint;
    max_pr        int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'job_queue') THEN
        RAISE EXCEPTION 'нет таблицы job_queue';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'job_queue' AND column_name = 'status') THEN
        RAISE EXCEPTION 'нет колонки status';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'job_queue' AND column_name = 'priority') THEN
        RAISE EXCEPTION 'нет колонки priority';
    END IF;

    IF (SELECT count(*) FROM job_queue WHERE status = 'processing') <> 1 THEN
        RAISE EXCEPTION 'ровно одна задача должна быть в processing';
    END IF;

    SELECT id INTO processing_id FROM job_queue WHERE status = 'processing';
    SELECT max(priority) INTO max_pr FROM job_queue;

    IF (SELECT priority FROM job_queue WHERE id = processing_id) <> max_pr THEN
        RAISE EXCEPTION 'взятая задача не максимального приоритета (должен быть порядок ORDER BY priority DESC)';
    END IF;

    IF (SELECT count(*) FROM job_queue WHERE status = 'pending') <> 5 THEN
        RAISE EXCEPTION 'остальные 5 задач должны быть pending';
    END IF;
END $$;