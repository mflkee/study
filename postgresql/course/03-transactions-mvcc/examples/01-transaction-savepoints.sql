-- Пример: транзакция и savepoints (одна сессия).
-- Применение: infra/apply-db.sh course/03-transactions-mvcc/examples/01-transaction-savepoints.sql

BEGIN;

CREATE TEMP TABLE t_demo (id int, val text);

INSERT INTO t_demo VALUES (1, 'a');

SAVEPOINT sp1;
INSERT INTO t_demo VALUES (2, 'b');
ROLLBACK TO sp1;              -- откат только после savepoint; транзакция жива

INSERT INTO t_demo VALUES (3, 'c');

SELECT * FROM t_demo ORDER BY id;   -- ожидаем (1,a) и (3,c)

COMMIT;

-- Advisory locks: прикладной мьютекс
SELECT pg_try_advisory_lock(42) AS got_lock;
SELECT pg_advisory_unlock(42) AS released;