# Теория модуля 10: паттерны приложений

Конспект-справочник. Примеры с фактическими выводами — в уроках (`lessons/`).

## Repository и границы транзакций (урок 01)

- `struct Repository { pool }` — SQL в одном месте (найти/создать/обновить).
- Транзакция = бизнес-операция (`begin` → операции на `&mut *tx` → `commit`); ошибка в середине откатывает всё.

## Outbox и идемпотентность (урок 02)

- Событие пишется в outbox В ТОЙ ЖЕ транзакции, что и данные; релей доставляет `pending` (SKIP LOCKED) и помечает `delivered`.
- Идемпотентность приема: `UNIQUE` + `ON CONFLICT DO NOTHING` (буфер шлюза) — повторная доставка не плодит дубли.
- Наблюдение: pending копятся → релей упал (алерт, модуль 13).

## Очередь задач (урок 03, кейс 7)

- Таблица-очередь (status/attempts/max_attempts/next_run_at); захват пачкой `FOR UPDATE SKIP LOCKED` + `UPDATE processing RETURNING`.
- Успех → done; ошибка → attempts+1 + backoff (`next_run_at`); после max → dead.
- Воркер tokio: цикл захвата + грациозная остановка (модуль 05).

## Миграции без даунтайма (урок 04)

- Дёшево: `ADD COLUMN … DEFAULT` (метаданные), `SET NOT NULL` без NULL, `CONSTRAINT NOT VALID` + VALIDATE.
- Дорого: `CREATE INDEX` (блокирует запись) — `CONCURRENTLY` (вне транзакции); backfill пачками по PK.
- Обратная совместимость: двухфазная выкатка «схема → код», не править применённые миграции.

## Инструменты модуля

| Файл | Что |
|---|---|
| `00-setup.sql` | outbox, task_queue, metering_points |
| `01-repository.rs` | repository + границы транзакций (откат «всё-или-ничего») |
| `02-outbox.rs` | атомарная запись данных+события, релей |
| `03-worker.rs` | воркер SKIP LOCKED: done/reтраи/dead |
| `04-buffer.rs` | шлюз-буфер: локальный файл → PG (идемпотентно) |
| `05-migrations-no-downtime.sql` | ADD COLUMN/INDEX CONCURRENTLY/backfill |
| `exercises/ex01_queue` / `ex02_buffer` | воркер+ретраи / буфер+ON CONFLICT |
| `labs/01-otkaz-bd.md` | реальная остановка PG, без потерь и дублей |