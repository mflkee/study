-- Ассерты упражнения 02 (модуль 13): top_slow непустая, с корректными полями.
DO $$
DECLARE
    v_rows int;
    v_bad  int;
BEGIN
    SELECT count(*) INTO v_rows FROM top_slow;
    IF v_rows = 0 THEN
        RAISE EXCEPTION 'top_slow пуст: заполни запрос по pg_stat_statements';
    END IF;

    SELECT count(*) INTO v_bad FROM top_slow
     WHERE calls IS NULL OR total_ms IS NULL OR query IS NULL OR query = 'TODO';
    IF v_bad > 0 THEN
        RAISE EXCEPTION 'в top_slow есть NULL/заглушки';
    END IF;

    RAISE NOTICE 'ок: top_slow содержит % строк (топ-5 по total_exec_time)', v_rows;
END $$;