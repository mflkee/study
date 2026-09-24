# Урок 03: Блокировки, deadlock, VACUUM и bloat

**Кейсы:** 12 (инциденты)
**Время:** 2.5 ч

## Зачем это нужно

«Система встала», «все ждут какой-то одной транзакции», «таблица выросла в 10 раз от перезапусков» — это блокировки и bloat. Инциденты вокруг них — самые частые в эксплуатации PostgreSQL. Урок: как читать deadlock-сообщения и как долгая транзакция ломает VACUUM.

## Ключевые идеи (сжато)

- UPDATE одной строки = эксклюзивная блокировка строки до конца транзакции; второй писатель **ждёт**.
- **Deadlock**: взаимное ожидание; PostgreSQL через `deadlock_timeout` (1 с) находит цикл и **откатывает одну** транзакцию с понятным сообщением.
- **VACUUM** убирает старые версии строк; **снимок repeatable read мешает**: `N are dead but not yet removable`.
- Advisory locks — прикладные блокировки вне строк.

## Разбор на примере (ДВА терминала)

**Deadlock.** Таблица `dlock(id, val)` с двумя строками. Терминал A берёт строку 1, терминал B — строку 2, затем каждый хочет вторую:

Терминал A:
```sql
BEGIN;
UPDATE dlock SET val = 'A-1' WHERE id = 1;
\! sleep 3
UPDATE dlock SET val = 'A-2' WHERE id = 2;   -- ждёт B
COMMIT;
```
Терминал B (стартует через секунду):
```sql
BEGIN;
UPDATE dlock SET val = 'B-2' WHERE id = 2;
\! sleep 3
UPDATE dlock SET val = 'B-1' WHERE id = 1;   -- ждёт A -> DEADLOCK
COMMIT;
```

Реальный вывод на стенде автора — транзакция B была отменена с сообщением:

```
ERROR:  deadlock detected
DETAIL:  Process 10376 waits for ShareLock on transaction 836; blocked by process 10385.
Process 10385 waits for ShareLock on transaction 835; blocked by process 10376.
HINT:  See server log for query details.
CONTEXT:  while updating tuple (0,2) in relation "dlock"
```

Механика видна прямо в сообщении: **кто кого ждёт** (оба процесса в цикле). Транзакция A при этом успешно завершилась — сервер жертвует одной, чтобы разорвать цикл.

**Долгая транзакция блокирует VACUUM.** 60 000 строк; сессия A держит снимок repeatable read, сессия B удаляет 40 000:

Сессия A:
```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT count(*) FROM vac_demo;   -- держит снимок 5 секунд
\! sleep 5
ROLLBACK;
```
Сессия B (пока A жива):
```sql
DELETE FROM vac_demo WHERE id <= 40000;
VACUUM VERBOSE vac_demo;
```
Реальный вывод на стенде автора (пока снимок A жив):
```
tuples: 0 removed, 60000 remain, 40000 are dead but not yet removable
```
А через мгновение, когда A завершилась, повторный `VACUUM VERBOSE vac_demo;`:
```
tuples: 40000 removed, 6790 remain, 0 are dead but not yet removable
```

Первая строка — **симптом**: VACUUM не может убрать мёртвые версии, потому что их «видит» снимок A. Пока долгие транзакции висят, таблица растёт (bloat), диски полнятся, индексы раздуваются.

**Advisory locks** (`examples/01-transaction-savepoints.sql`): `pg_try_advisory_lock(42)` — взял без ожидания; `pg_advisory_unlock(42)` — отпустил. Прикладной мьютекс «один процесс за раз».

## Как это устроено под капотом

- `UPDATE` берёт эксклюзивный замок строки и удерживает его до конца транзакции (не до конца оператора!).
- Deadlock detect: граф ожиданий; цикл ловит фоновый процесс (deadlock_timeout), выбирает «жертву» и откатывает её.
- MVCC-версии: DELETE не стирает физически; удалённые версии помечаются и убираются VACUUM **только когда они невидимы всем снимкам**.
- Снимок repeatable read «держит» старые версии до конца транзакции — отсюда прямые потери для VACUUM.
- `pg_stat_activity.xact_start` — «с какого времени висит транзакция»; поиск долгих транзакций начинается с него.

## Типичные ошибки и грабли

1. **Блокировки из порядка обновления** — если все пишут строки в одном порядке (например, по id), deadlock практически исчезает. Хотим consistency — фиксируем порядок.
2. **«Потерянная» жертва deadlock** — код должен уметь повторить транзакцию после `deadlock detected` (retry; в Rust — модуль 06/10).
3. **Долгая транзакция «чтения»** — даже чистое чтение в repeatable read держит снимок и мешает VACUUM: проверяй `pg_stat_activity` на забытые `BEGIN`.
4. **Bloat «не ожидали»** — после массовых UPDATE/DELETE без VACUUM таблица не уменьшается сама; `VACUUM VERBOSE` показывает, сколько версий реально удалено.
5. **Advisory lock забыли отпустить** — живёт до конца сессии; пары lock/unlock строго парные.

## Мини-задание

Напиши, какой запрос найдёт «самую долгую» открытую транзакцию на сервере (подсказка: `pg_stat_activity`).

<details>
<summary>Ответ</summary>

```sql
SELECT pid, xact_start, state, left(query, 60) AS query
FROM pg_stat_activity
WHERE state <> 'idle'
ORDER BY xact_start
LIMIT 5;
```
По `xact_start` видно, какая транзакция держится дольше всех.
</details>

## Как это спросят на собеседовании

1. «Что такое deadlock и как PostgreSQL его обрабатывает?»
2. «Почему долгая транзакция опасна и как её найти?»
3. «Как избежать deadlock?» (порядок обновления, короткие транзакции)

## Что читать дальше

- Блокировки: <https://www.postgresql.org/docs/18/explicit-locking.html>
- VACUUM и обслуживание: <https://www.postgresql.org/docs/18/routine-vacuuming.html>
- Мониторинг pg_stat_activity: <https://www.postgresql.org/docs/18/monitoring-stats.html>