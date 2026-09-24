-- Ассерты упражнения 02 (модуль 08): корректное развёртывание TimescaleDB.
-- (материализация cagg в транзакции автопроверки невозможна — WITH DATA/REFRESH
--  вне транзакций; цифры сравниваются в psql вне check-sql, см. task.md)
DO $$
DECLARE
    v_hyps  int;
    v_kind  text;
    v_rows  bigint;
BEGIN
    -- 1. probe_ts — настоящая гипертаблица.
    SELECT count(*) INTO v_hyps
      FROM timescaledb_information.hypertables
     WHERE hypertable_name = 'probe_ts';
    IF v_hyps = 0 THEN
        RAISE EXCEPTION 'probe_ts не создана как hypertable (create_hypertable?)';
    END IF;

    -- 2. probe_daily — continuous aggregate (не обычная матвью).
    SELECT materialization_hypertable_name INTO v_kind
      FROM timescaledb_information.continuous_aggregates
     WHERE view_name = 'probe_daily';
    IF v_kind IS NULL THEN
        RAISE EXCEPTION 'probe_daily не continuous aggregate (WITH (timescaledb.continuous))';
    END IF;

    -- 3. Данные на месте (1000 строк, 3 устройства).
    SELECT count(*) INTO v_rows FROM probe_ts;
    IF v_rows <> 1000 THEN
        RAISE EXCEPTION 'ожидалось 1000 показаний в probe_ts, получено %', v_rows;
    END IF;

    RAISE NOTICE 'ok: hypertable и continuous aggregate развёрнуты, данных %', v_rows;
END $$;