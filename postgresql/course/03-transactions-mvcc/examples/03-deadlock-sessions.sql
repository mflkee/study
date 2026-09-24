-- Пример: DEADLOCK в двух сессиях.
--
-- Подготовка (одна сессия):
--   CREATE TABLE dlock (id int PRIMARY KEY, val text);
--   INSERT INTO dlock VALUES (1,'x'),(2,'y');
--
-- ТЕРМИНАЛ 1 (берёт строку 1, затем хочет строку 2):
BEGIN;
UPDATE dlock SET val = 'A-1' WHERE id = 1;
\! sleep 3
UPDATE dlock SET val = 'A-2' WHERE id = 2;
COMMIT;

-- ТЕРМИНАЛ 2 (стартует через ~1 секунду; берёт строку 2, затем хочет строку 1):
BEGIN;
UPDATE dlock SET val = 'B-2' WHERE id = 2;
\! sleep 3
UPDATE dlock SET val = 'B-1' WHERE id = 1;   -- DEADLOCK: откатывается с ошибкой
COMMIT;

-- Ожидай ошибку в терминале 2:
--   ERROR:  deadlock detected
--   DETAIL: Process ... waits for ShareLock on transaction ...; blocked by process ...
--           Process ... waits for ShareLock on transaction ...; blocked by process ...
--   CONTEXT: while updating tuple (0,2) in relation "dlock"

-- Очистка: DROP TABLE dlock;