# Урок 01: ACID и управление транзакциями

**Кейсы:** 7 (очередь задач — основа)
**Время:** 1.5 ч

## Зачем это нужно

Любая запись в БД — это транзакция, даже неявная. Ошибки в управлении транзакциями (держишь открытой слишком долго, коммитишь частично, путаешь savepoint с rollback) — это зависшие процессы, потерянные данные и ночные инциденты. Урок — базовые механики, на которых стоит весь следующий код на Rust (модуль 06).

## Ключевые идеи (сжато)

- ACID: атомарность, согласованность, изоляция, долговечность.
- `BEGIN`/`COMMIT`/`ROLLBACK`; каждый оператор вне явной транзакции — неявная транзакция из одного оператора.
- **Savepoint** — точка отката внутри транзакции: `ROLLBACK TO sp` откатывает только до неё, транзакция продолжается.
- Атомарность видна по «всё или ничего»: при ошибке в середине (если не перехватил savepoint) ROLLBACK стирает всё.

## Разбор на примере

Запуск: `./infra/apply-db.sh course/03-transactions-mvcc/examples/01-transaction-savepoints.sql` (одна сессия).

**1. Savepoint: откатили «плохую» вставку, не убив транзакцию:**

```sql
BEGIN;
CREATE TEMP TABLE t_demo (id int, val text);
INSERT INTO t_demo VALUES (1, 'a');
SAVEPOINT sp1;
INSERT INTO t_demo VALUES (2, 'b');
ROLLBACK TO sp1;               -- строка (2,'b') исчезает
INSERT INTO t_demo VALUES (3, 'c');
SELECT * FROM t_demo ORDER BY id;
COMMIT;
```
```
 id | val
----+-----
  1 | a
  3 | c
```
После `ROLLBACK TO sp1` строка «b» откатилась, но транзакция жива — вставили «c» и закоммитили.

**2. Advisory-блокировка** (прикладной mutex на уровне БД):

```sql
SELECT pg_try_advisory_lock(42) AS got_lock;   -- t — замок взят без ожидания
SELECT pg_advisory_unlock(42) AS released;     -- t — отпущен
```

## Как это устроено под капотом

- PostgreSQL пишет изменения в **WAL** (write-ahead log) и кучи; COMMIT фиксирует точку в WAL (отсюда durability).
- Savepoints реализованы через undo-записи внутри транзакции: `ROLLBACK TO sp` откатывает команды после sp и **снимает их блокировки** (важно для конкурентности).
- Неявные транзакции: `INSERT ...;` — BEGIN/COMMIT вокруг одного оператора.
- Advisory locks живут в памяти сервера и не привязаны к строкам; `pg_try_advisory_lock` — неблокирующая версия (`pg_advisory_lock` ждёт).

## Типичные ошибки и грабли

1. **Долго открытая транзакция** — держит снимок/блокировки, блокирует VACUUM (урок 03) и растёт bloat. Транзакция должна быть короткой и явной.
2. **`ROLLBACK` вместо `ROLLBACK TO sp`** — уронили всю транзакцию, когда хотели откатить один шаг.
3. **Ошибка в середине без savepoint** — PostgreSQL не откатывает «сам всё» молча: до COMMIT состояние может быть частичным (и это нормально для long-транзакций с пойманными ошибками).
4. **Забытый COMMIT в коде** — с точки зрения других транзакций данные «не существуют»; в Rust такой паттерн разберём в модуле 06.
5. **Advisory lock без отпускания** — держится до конца сессии; пары lock/unlock всегда держи под контролем.

## Мини-задание

Напиши транзакцию: вставь строку, поставь savepoint, вставь вторую, откатись к savepoint, закоммить — и проверь, что осталась только первая.

<details>
<summary>Ответ</summary>

```sql
BEGIN;
INSERT INTO m VALUES (1);
SAVEPOINT sp;
INSERT INTO m VALUES (2);
ROLLBACK TO sp;
COMMIT;   -- останется только (1)
```
</details>

## Как это спросят на собеседовании

1. «Что даёт ACID и как PostgreSQL обеспечивает атомарность?»
2. «Чем ROLLBACK TO savepoint отличается от ROLLBACK?»
3. «Почему долгая транзакция опасна для VACUUM?» (см. урок 03)

## Что читать дальше

- Транзакции (tutorial): <https://www.postgresql.org/docs/18/tutorial-transactions.html>
- SAVEPOINT: <https://www.postgresql.org/docs/18/sql-savepoint.html>
- Advisory locks: <https://www.postgresql.org/docs/18/explicit-locking.html>