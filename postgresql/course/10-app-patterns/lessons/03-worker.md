# Урок 03: Очередь задач на PostgreSQL — воркеры, ретраи, dead-letter

**Модуль / кейс:** 10-app-patterns / кейс 7 (очередь задач)
**Время:** 3 ч

## Зачем это нужно

Очередь на PostgreSQL (а не на отдельном брокере) — частый выбор в СИКН: та же БД уже есть, задачи переживают рестарты, атомарность и аудит бесплатно. Воркеры на tokio берут задачи `FOR UPDATE SKIP LOCKED` (кейс 7, модуль 03) — без двойной выдачи, с ретраями и dead-letter.

## Ключевые идеи (сжато)

- Таблица-очередь: `kind, payload, status (pending/processing/done/failed/dead), attempts, max_attempts, next_run_at`.
- `claim_batch`: CTE `SELECT … WHERE status='pending' AND next_run_at <= now() FOR UPDATE SKIP LOCKED LIMIT $1` → `UPDATE status='processing' RETURNING …`.
- Успех → `done`; ошибка → `attempts+1`, `next_run_at = now() + backoff`; после `max_attempts` → `dead` (dead-letter).
- Воркер (tokio): цикл «забрать пачку → обработать → отметить» + грациозная остановка (модуль 05).

## Разбор на примере

```bash
cargo run --example 03-worker -- 5
```

Код — `examples/03-worker.rs`. Захват пачки (эталон CTE):

```sql
WITH claimed AS (
    SELECT id FROM task_queue
     WHERE status = 'pending' AND next_run_at <= now()
     ORDER BY id
     FOR UPDATE SKIP LOCKED
     LIMIT $1
)
UPDATE task_queue q SET status = 'processing'
  FROM claimed WHERE q.id = claimed.id
  RETURNING q.id, q.kind;
```

Ретрай с бэкoff и dead-letter:

```sql
UPDATE task_queue
   SET attempts = $2, status = $3,            -- $3 = 'dead' | 'pending'
       next_run_at = now() + make_interval(secs => $4)
 WHERE id = $1;
```

Фактический вывод (стенд, 5 задач: 3 recalc + 2 fragile):

```
  задача 2 (recalc) → done
  задача 3 (recalc) → done
  задача 4 (fragile) ошибка: … не вышло
  задача 4: попытка 1 не вышла -> pending
  …
итог по очередям: [("done", 3), ("pending", 2)]
```

## Как это устроено под капотом

- `SKIP LOCKED` — ключ: два воркера не заберут одну строку (модуль 03); строки, залоченные другим воркером, просто пропускаются.
- Ретраи живут в БД (`next_run_at` — «созревание»); воркер не спит до бэкoff — он просто не видит задачи до времени.
- Dead-letter — статус 'dead' (можно отдельной таблицей `task_dead`); разбор причин — операционная задача.
- Пачки: LIMIT на количество задач за захват — контроль нагрузки и латентности.

## Типичные ошибки и грабли

1. **Захват БЕЗ SKIP LOCKED** — двойная выдача: два воркера тянут одну задачу.
2. **Ретрай без backoff** — «горячая петля» неудачных задач; экспонента обязательна.
3. **Задачи «зависли» в processing** — воркер упал после захвата; обработка: lease/timestamp + конкурентный «возврат» старых processing в pending (задание «со звёздочкой»).
4. **Без dead-letter** — битые задачи крутятся вечно, засоряя очередь.
5. **`next_run_at <= now()` забыли в WHERE индекса/запроса** — берутся задачи, ещё не «созревшие».

## Мини-задание

Добавь в воркер: при ошибке с `kind = 'fragile'` задача должна уходить в dead сразу (без ретраев) — и проверь на 2 задачах: одна done, одна dead.

<details>
<summary>Ответ</summary>

Перед вызовом fail_with_retry: `if kind == "fragile" { mark_dead(id) } else { fail_with_retry(id, max) }`. `mark_dead` — `UPDATE task_queue SET status='dead' WHERE id=$1`.
</details>

## Как это спросят на собеседовании

1. «Как сделать очередь без брокера?» — таблица + SKIP LOCKED-захват пачками.
2. «Как избежать двойной обработки?» — захват и отметка в одной транзакции (CTE).
3. «Как не крутить вечно битые?» — dead-letter + бэкoff + мониторинг.

## Что читать дальше

- SKIP LOCKED (модуль 03): блокировки строк.
- Pgjobq/pg-boss как промышленные обёртки над этой идеей: <https://github.com/timgit/pg-boss>
- Лаба модуля 10 (отказ БД) и упражнение 01