-- Упражнение 01: заготовка — захват БЕЗ приоритета (ошибка намеренно).
-- Правильный захват: ORDER BY priority DESC, id + FOR UPDATE SKIP LOCKED.

CREATE TABLE job_queue (
    id       bigserial PRIMARY KEY,
    payload  text NOT NULL,
    status   text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','processing','done','failed')),
    priority int  NOT NULL DEFAULT 0
);

INSERT INTO job_queue (payload, priority) VALUES
    ('a', 1), ('b', 5), ('c', 3), ('d', 9), ('e', 7), ('f', 2);

-- TODO: неверный порядок — по id, а не по приоритету.
WITH claimed AS (
    SELECT id FROM job_queue
    WHERE status = 'pending'
    ORDER BY id
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
UPDATE job_queue SET status = 'processing'
WHERE id IN (SELECT id FROM claimed) RETURNING id;