-- 03-notify.sql — LISTEN/NOTIFY: триггер сообщает об изменении (кейс 6).
-- Модуль: 09, урок 03. База: course_m06.
--
-- Триггер trg_devices_notify уже создан в 00-setup: он шлёт pg_notify('device_changed')
-- при INSERT/UPDATE/DELETE на devices. Здесь — «ручной» LISTEN через psql:
--   терминал 1: LISTEN device_changed; INSERT INTO devices ... -> придёт payload
--   (запуск: psql -c "LISTEN device_changed;" в отдельном терминале)

-- Что шлёт триггер (проверим, не подвешивая слушателя):
SELECT pg_notify('device_changed',
    jsonb_build_object('id', 1, 'tag', 'M-01-001', 'op', 'TEST', 'ts', now())::text);

-- Полезное: проверка, что канал существует и кто слушает.
SELECT pg_listening_channels();                                   -- в текущей сессии
SELECT * FROM pg_stat_activity WHERE query LIKE '%LISTEN%';      -- слушатели

-- Ловушка урока 03: NOTIFY внутри НЕзакоммиченной транзакции НЕ доставляется
-- (доставка на COMMIT), а при ROLLBACK — пропадает вовсе. Проверка:
-- BEGIN; INSERT INTO devices ... ACCESS EXCLUSIVE? нет — просто:
-- BEGIN;
--   SELECT pg_notify('device_changed', '..');  -- уйдёт ТОЛЬКО после COMMIT
-- ROLLBACK;                                     -- и тут — НЕ уйдёт вовсе
-- (раскомментируй и запусти с парой терминалов: в терминале 2 с LISTEN события нет)