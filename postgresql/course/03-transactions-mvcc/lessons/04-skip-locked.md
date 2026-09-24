# Урок 04: Очередь задач — SELECT FOR UPDATE SKIP LOCKED

**Кейсы:** 7 (очередь задач на Postgres)
**Время:** 1.5 ч

## Зачем это нужно

Очередь задач на Postgres — честный паттерн для «сделать много однотипной работы параллельно»: обработка телеграмм, выгрузки, рассылки. Наивная реализация («выбрал задачи, обработал») ломается при нескольких воркерах: двое берут одну и ту же задачу. `FOR UPDATE SKIP LOCKED` решает это на уровне БД без отдельного брокера.

## Ключевые идеи (сжато)

- `SELECT ... FOR UPDATE` блокирует выбранные строки до конца транзакции — второй воркер ждёт, но ждёт **то же самое**.
- `SKIP LOCKED` — пропустить строки, залоченные другими: воркеры берут **разные** задачи и **не ждут**.
- Контракт очереди: захват (`pending → processing`) в одной транзакции с блокировкой → обработка → финальный статус.
- Обычный `FOR UPDATE` (без SKIP) блокируется на занятых строках — измеримая разница видна в psql.

## Разбор на примере (ДВА терминала)

Подготовка (одна сессия):

```sql
CREATE TABLE tasks (id serial PRIMARY KEY, status text NOT NULL DEFAULT 'pending', payload text);
INSERT INTO tasks (payload) SELECT 'payload-' || i FROM generate_series(1,10) i;
```

Запуск: `examples/04-skip-locked-sessions.sql`.

**Терминал A — воркер 1 (захват 2 задач):**
```sql
BEGIN;
WITH claimed AS (
    SELECT id FROM tasks WHERE status = 'pending'
    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 2
)
UPDATE tasks SET status = 'processing'
WHERE id IN (SELECT id FROM claimed) RETURNING id;
\! sleep 4            -- держим блокировки, имитируя обработку
COMMIT;
```
A забрал задачи 1 и 2 и держит их (status → processing).

**Терминал B — воркер 2, пока A работает:**
```sql
-- 1) SKIP LOCKED: мгновенно, другие строки
SELECT id FROM tasks WHERE status = 'pending' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 2;
```
Реальный вывод на стенде автора (мгновенно, без ожидания):
```
 3
 4
```
```sql
-- 2) Обычный FOR UPDATE без SKIP: БЛОКИРУЕТСЯ до COMMIT A
BEGIN;
SELECT id FROM tasks WHERE status = 'pending' ORDER BY id FOR UPDATE LIMIT 2;
COMMIT;
```
Реальный замер на стенде автора: этот запрос **ждал ~2.5 секунды** (до завершения транзакции A) — и только потом вернул доступные строки. SKIP LOCKED не блокировался вообще.

Итог статусов после обоих воркеров: `pending = 8`, `processing = 2` (A перевёл свои 2 задачи в processing; B в примере только посмотрел, без UPDATE).

## Как это устроено под капотом

- `FOR UPDATE` берёт эксклюзивный замок строки. Обычный `SELECT` его не видит, но второй `FOR UPDATE` — видит и ждёт (очередь «одна и та же строка»).
- `SKIP LOCKED` в исполнении: по мере сканирования строки, которые нельзя залочить немедленно, просто пропускаются — планировщик не ждёт.
- Захват через CTE (`WITH claimed AS (...) UPDATE ... RETURNING`) — атомарно: блокировка и смена статуса в одной транзакции.
- Ограничение: `SKIP LOCKED` не подходит, когда критично «все задачи будут обработаны ровно по очереди» — он не гарантирует порядок (могут прийти не самые ранние).

## Типичные ошибки и грабли

1. **`FOR UPDATE` без SKIP для воркеров** — все воркеры ждут одну строку, «очередь» схлопывается в последовательную обработку.
2. **Захват и обработка в разных транзакциях** — между `SELECT ... FOR UPDATE` и UPDATE блокировка потеряна, другой воркер уже взял задачу. Захват обязан быть атомарным (CTE+UPDATE).
3. **Забытый статус** — обработчик упал после захвата → задача навсегда в `processing`; нужен таймаут/перезахват (модуль 10: ретраи, dead-letter).
4. **PK-порядок вместо приоритета** — если у задач есть приоритет, в `ORDER BY` должен быть он.
5. **SKIP LOCKED в read-only SELECT** — без FOR UPDATE он бессмыслен (пропускать нечего).

## Мини-задание

Перепиши захват так, чтобы воркер брал ОДНУ задачу с максимальным приоритетом (`priority int`, больше = важнее), не блокируясь на чужих.

<details>
<summary>Ответ</summary>

```sql
WITH claimed AS (
    SELECT id FROM tasks
    WHERE status = 'pending'
    ORDER BY priority DESC, id
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
UPDATE tasks SET status = 'processing'
WHERE id IN (SELECT id FROM claimed) RETURNING id;
```
</details>

## Как это спросят на собеседовании

1. «Как построить очередь задач на PostgreSQL без внешнего брокера?»
2. «Чем SELECT ... FOR UPDATE отличается от ... FOR UPDATE SKIP LOCKED?»
3. «Что произойдёт с задачей, если воркер упал после захвата?»

## Что читать дальше

- FOR UPDATE / SKIP LOCKED: <https://www.postgresql.org/docs/18/sql-select.html#SQL-FOR-UPDATE-SHARE>
- Очереди на SKIP LOCKED (статья КD2NQ/2ndQuadrant): <https://www.2ndquadrant.com/en/blog/what-is-select-skip-locked-for-in-postgresql-9-5/>
- Rust-воркеры на этой очереди — модуль 10