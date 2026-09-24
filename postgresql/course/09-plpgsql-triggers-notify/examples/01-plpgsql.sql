-- 01-plpgsql.sql — функции PL/pgSQL: переменные, циклы, обработка ошибок.
-- Модуль: 09, урок 01. База: course_m06. Запуск в psql.

-- 1. Простая функция: вернуть информацию по устройству.
CREATE OR REPLACE FUNCTION device_info(p_tag text)
RETURNS TABLE (id int, tag text, device_type text, model text) AS $$
BEGIN
    RETURN QUERY SELECT d.id, d.tag, d.device_type, d.model
                   FROM devices d WHERE d.tag = p_tag;
END $$ LANGUAGE plpgsql;

SELECT * FROM device_info('M-01-001');

-- 2. Цикл + агрегация: суммарные «показания» по всем устройствам (демо-счёт).
CREATE OR REPLACE FUNCTION total_values() RETURNS numeric AS $$
DECLARE
    r RECORD;
    total numeric := 0;
BEGIN
    FOR r IN SELECT id FROM devices LOOP
        total := total + (
            SELECT coalesce(sum(m.value), 0) FROM measurements m WHERE m.device_id = r.id
        );
    END LOOP;
    RETURN total;
END $$ LANGUAGE plpgsql;

SELECT total_values();

-- 3. Обработка ошибок: ловим блока?! нет — демонстрируем EXCEPTION блок.
CREATE OR REPLACE FUNCTION insert_device_safe(
    p_line_id int, p_type text, p_tag text, p_model text
) RETURNS text AS $$
BEGIN
    INSERT INTO devices (line_id, device_type, tag, model)
    VALUES (p_line_id, p_type, p_tag, p_model);
    RETURN 'ok';
EXCEPTION WHEN unique_violation THEN
    RETURN 'уже существует';
WHEN check_violation THEN
    RETURN 'неверный тип/ограничение';
END $$ LANGUAGE plpgsql;

SELECT insert_device_safe(1, 'mass_meter', 'M-01-001', 'дубль');   -- уже существует
SELECT insert_device_safe(1, 'warp', 'X-01-001', 'модель');        -- неверный CHECK