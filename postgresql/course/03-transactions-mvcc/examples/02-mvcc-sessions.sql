-- Пример: MVCC и уровни изоляции в ДВУХ СЕССИЯХ (диапазоны).
--
-- Как запускать: открой ДВА терминала psql к стенду, например:
--   Терминал 1: docker compose exec postgres psql -U course -d course
--   Терминал 2: (второе окно) docker compose exec postgres psql -U course -d course
-- Ниже показано, что вводить в каждом. Перед стартом убедись, что
-- применён сид: ./infra/apply-db.sh course/01-sql-foundations/examples/01-seed-katalog.sql

------------------------------------------------------------------
-- ЧАСТЬ 1. Read committed: незакоммиченное не видно
------------------------------------------------------------------

-- ТЕРМИНАЛ 1 (пишет, НЕ коммитит):
BEGIN;
UPDATE measurements SET value = value + 1 WHERE id = 1;
\! sleep 4
COMMIT;

-- ТЕРМИНАЛ 2 (пока терминал 1 «спит»):
--   SELECT value FROM measurements WHERE id = 1;
--   Ожидай: 1000.000000  (изменение A невидимо)
-- Через пару секунд выполни ещё раз (после COMMIT терминала 1):
--   SELECT value FROM measurements WHERE id = 1;
--   Ожидай: 1001.000000

------------------------------------------------------------------
-- ЧАСТЬ 2. Repeatable read vs read committed
------------------------------------------------------------------

-- ТЕРМИНАЛ 1 (держит снимок):
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT count(*) FROM iso_demo;     -- ожидай 1
\! sleep 4
SELECT count(*) FROM iso_demo;     -- всё ещё 1, хотя ниже вставили строку
ROLLBACK;

-- ТЕРМИНАЛ 2 (пока терминал 1 спит) — вставляет и коммитит:
--   INSERT INTO iso_demo VALUES (2);   (таблица должна существовать, см. урок 02)

-- ТЕРМИНАЛ 3 / новый запрос в read committed:
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT count(*) FROM iso_demo;      -- 2 (новые коммиты видны)
ROLLBACK;