-- Ассерты упражнения 02: тип netto = numeric, значение ровно 993.010000.
DO $$
BEGIN
    IF (SELECT data_type FROM information_schema.columns
        WHERE table_name = 'ex02_netto' AND column_name = 'netto') <> 'numeric' THEN
        RAISE EXCEPTION 'колонка netto должна быть numeric, а не float8';
    END IF;

    IF (SELECT count(*) FROM ex02_netto) <> 1 THEN
        RAISE EXCEPTION 'ожидалась ровно одна строка в ex02_netto';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ex02_netto WHERE netto::numeric = 993.010000) THEN
        RAISE EXCEPTION 'netto должно быть точно 993.010000; у тебя float-артефакт';
    END IF;
END $$;