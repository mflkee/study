# Упражнение 01: Очередь задач — атомарный захват

**Кейс:** 7 (очередь задач на Postgres)
**Модуль:** 03-transactions-mvcc
**Время:** 40 мин
**Сложность:** реши сам

## Условие

Создай таблицу очереди задач:

```sql
CREATE TABLE IF NOT EXISTS job_queue (
    id       bigserial PRIMARY KEY,
    payload  text NOT NULL,
    status   text NOT NULL DEFAULT 'pending'
             CHECK (status IN ('pending','processing','done','failed')),
    priority int  NOT NULL DEFAULT 0     -- больше = важнее
);
```

И реализуй **атомарный захват** одной задачи с максимальным приоритетом: `pending → processing`, с блокировкой `FOR UPDATE SKIP LOCKED`, в одной транзакции (CTE `WITH claimed AS (...) UPDATE ... RETURNING id`).

В файле: создай таблицу, вставь 6 задач с разными приоритетами, выполни захват одной задачи.

Сценарий проверки (ассерты): после захвата ровно **одна** строка в `processing`, и это строка с `priority = max(priority)`; остальные — `pending`. Захват выполняется в транзакции автопроверки (ROLLBACK) — таблица после проверки исчезает, можно создавать без `IF NOT EXISTS`.

Заготовка: `student.sql` — захватывает по `ORDER BY id` (неверно: игнорирует приоритет).

## Как проверить

```bash
infra/check-sql.sh course/03-transactions-mvcc/exercises/ex01_queue/student.sql \
                   course/03-transactions-mvcc/exercises/ex01_queue/asserts.sql
```

## Критерии ассертов

- Таблица `job_queue` существует с колонками `status` и `priority`.
- После захвата ровно 1 строка `processing`.
- Эта строка имеет максимальный приоритет.
- Остальные строки `pending`.

> Двухсессионная семантика SKIP LOCKED (воркеры не забирают чужие задачи) автопроверкой в одной транзакции не проверяется — она воспроизведена в уроке 04 (реальные выводы) и в лабе 01.

Решение — в `../../solutions/ex01_queue.sql` (после своей попытки).