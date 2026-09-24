-- Пример: очередь задач — SELECT FOR UPDATE SKIP LOCKED в двух сессиях.
--
-- Подготовка (одна сессия):
--   CREATE TABLE tasks (id serial PRIMARY KEY, status text NOT NULL DEFAULT 'pending', payload text);
--   INSERT INTO tasks (payload) SELECT 'payload-' || i FROM generate_series(1,10) i;
--
-- ТЕРМИНАЛ 1 (воркер 1: захватывает 2 задачи и держит их 4 секунды):
BEGIN;
WITH claimed AS (
    SELECT id FROM tasks WHERE status = 'pending'
    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 2
)
UPDATE tasks SET status = 'processing'
WHERE id IN (SELECT id FROM claimed) RETURNING id;
\! sleep 4
COMMIT;

-- ТЕРМИНАЛ 2 (воркер 2, пока терминал 1 работает):
--   SELECT id FROM tasks WHERE status='pending' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 2;
--   Ожидай: мгновенно, ДРУГИЕ id (например 3, 4) — SKIP LOCKED не ждёт.
--
--   BEGIN;
--   SELECT id FROM tasks WHERE status='pending' ORDER BY id FOR UPDATE LIMIT 2;
--   -- ОЖИДАНИЕ (~2-3 сек, до COMMIT терминала 1): это обычный FOR UPDATE.
--   COMMIT;
--
-- Итог статусов: tasks: pending=8, processing=2