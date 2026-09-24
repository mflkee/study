-- Ассерты упражнения 01 (модуль 08): отчёт непустой и суммы сходятся с прямым расчётом.
DO $$
DECLARE
    v_rows   bigint;
    v_bad    bigint;
BEGIN
    -- 1. Отчёт непустой и в нём строки за сентябрь 2026.
    SELECT count(*) INTO v_rows FROM report_shifts
     WHERE shift >= '2026-09-01 00:00:00+00'
       AND shift <  '2026-10-01 00:00:00+00';
    IF v_rows = 0 THEN
        RAISE EXCEPTION 'отчёт пуст за сентябрь: проверь date_bin и период';
    END IF;

    -- 2. Бакеты ровно 12-часовые: разность соседних смен = 12 часов.
    SELECT count(*) INTO v_bad FROM (
        SELECT shift,
               lag(shift) OVER (ORDER BY shift) AS prev
          FROM (SELECT DISTINCT shift FROM report_shifts) s
    ) d
     WHERE prev IS NOT NULL
       AND (shift - prev) <> interval '12 hours';
    IF v_bad > 0 THEN
        RAISE EXCEPTION 'найден бакет не 12 часов (проверь date_bin и origin)';
    END IF;

    -- 3. Суммы сходятся с прямым пересчётом по тем же бакетам.
    SELECT count(*) INTO v_bad FROM (
        SELECT r.shift, r.device_id,
               round(r.total::numeric, 2)       AS report_total,
               round(SUM(m.value)::numeric, 2)   AS direct_total
          FROM report_shifts r
          JOIN measurements_part m
            ON m.device_id = r.device_id
           AND date_bin('12 hours', m.ts, '2026-08-01 00:00:00+00'::timestamptz) = r.shift
         GROUP BY r.shift, r.device_id, r.total
    ) cmp
     WHERE report_total <> direct_total;
    IF v_bad > 0 THEN
        RAISE EXCEPTION 'суммы отчёта не совпадают с прямым пересчётом';
    END IF;

    RAISE NOTICE 'ок: отчёт за сентябрь непустой (%), бакеты 12ч, суммы сходятся', v_rows;
END $$;