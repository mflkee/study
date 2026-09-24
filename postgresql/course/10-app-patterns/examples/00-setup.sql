-- 00-setup.sql — модуль 10: outbox, очередь задач, приемник буферизованного шлюза.
-- База: course_m06. Идемпотентно (DROP + пересоздание — учебный стенд).

-- 1. Outbox: «исходящие события», записываются В ТОЙ ЖЕ транзакции, что и
--    бизнес-данные; релей доставляет их внешней системе (урок 02).
DROP TABLE IF EXISTS outbox;
CREATE TABLE outbox (
    id         bigserial PRIMARY KEY,
    aggregate  text NOT NULL,             -- например 'device.created'
    payload    jsonb NOT NULL,
    attempts   int NOT NULL DEFAULT 0,
    status     text NOT NULL DEFAULT 'pending'
               CHECK (status IN ('pending', 'delivered', 'dead')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbox_pending_idx ON outbox (id) WHERE status = 'pending';

-- 2. Очередь задач (кейс 7): воркеры берут пачками через SKIP LOCKED.
DROP TABLE IF EXISTS task_queue;
CREATE TABLE task_queue (
    id           bigserial PRIMARY KEY,
    kind         text NOT NULL,           -- 'recalc' | 'mail' | ...
    payload      jsonb NOT NULL,
    status       text NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending', 'processing', 'done', 'failed', 'dead')),
    attempts     int  NOT NULL DEFAULT 0,
    max_attempts int  NOT NULL DEFAULT 3,
    next_run_at  timestamptz NOT NULL DEFAULT now(),   -- ретрай-бэкoff
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX task_queue_pending_idx
    ON task_queue (id) WHERE status = 'pending';

-- 3. Приемник точек метеринга: UNIQUE (device_id, seq) = идемпотентность
--    повторной доставки (кейс 8, урок 02/03).
DROP TABLE IF EXISTS metering_points;
CREATE TABLE metering_points (
    device_id int NOT NULL,
    seq       bigint NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL,
    PRIMARY KEY (device_id, seq)
);

TRUNCATE outbox, task_queue, metering_points;