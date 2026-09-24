# Урок 04: Миграции без даунтайма и обратная совместимость

**Модуль / кейс:** 10-app-patterns / кейс 10 (эволюция схемы)
**Время:** 2 ч

## Зачем это нужно

В проде нельзя «погасить сервер на минуту». Миграции выполняются на живую систему: знания, какие операции дешёвые, а какие роняют доступность, — обязательный навык инженера СИКН.

## Ключевые идеи (сжато)

- **Дёшево (метаданные)**: `ADD COLUMN … DEFAULT` (PG11+), `SET NOT NULL` (без NULL), `ADD CONSTRAINT` (с валидацией, см. ниже).
- **Дорого/опасно**: `CREATE INDEX` (блокирует запись) → лечится `CREATE INDEX CONCURRENTLY` (вне транзакции); `DROP COLUMN`/замена типа — переписывание.
- **Backfill пачками**: большой `UPDATE` заменяется на серию `UPDATE … WHERE id BETWEEN …` (не держим снимок часами — модуль 03).
- Обратная совместимость: старый код работает с новой схемой (пример: добавление колонки + `IF NOT EXISTS`/DEFAULT), выкатка — двухфазная (модуль 09/10).

## Разбор на примере

```bash
docker compose exec -T postgres psql -U course -d course_m06 -f - < course/10-app-patterns/examples/05-migrations-no-downtime.sql
```

Ключевые операции:

```sql
ALTER TABLE devices ADD COLUMN IF NOT EXISTS installed_since date DEFAULT CURRENT_DATE;
-- PG11+: только метаданные, таблица не переписывается — мгновенно даже на больших данных.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devices_line_type ON devices (line_id, device_type);
-- Долго, но НЕ блокирует запись; проверяй indisvalid (готовность).
```

Вывод (стенд): колонка добавлена, `SET NOT NULL` прошёл (NULL нет), index создан и в `pg_index` готов.

**Backfill-шаблон** (пачка 10 000):

```sql
UPDATE devices SET installed_since = '2026-01-01'
 WHERE id BETWEEN :from AND :to AND installed_since IS NULL;
```

## Как это устроено под капотом

- `ADD COLUMN … DEFAULT` в новых версиях не переписывает строки: значение виртуальное до первого UPDATE строки (метаданные).
- `CREATE INDEX` по умолчанию берёт `ShareLock` — блокирует запись на время построения; `CONCURRENTLY` строит в фоне (дороже, но без блокировки).
- `CONSTRAINT … NOT VALID` + `VALIDATE CONSTRAINT` — добавить ограничение без скана, потом проверить — дешёвый путь для больших таблиц.
- Ошибка миграции в проде → «POSIX-файлы»: не правим применённое, добавляем следующую миграцию (модуль 06).

## Типичные ошибки и грабли

1. **`CREATE INDEX` напрямую в проде** — минуты блокировки записи; только `CONCURRENTLY`.
2. **Backfill одним `UPDATE`** — долгая транзакция = bloat/снимок (модуль 03); пачками.
3. **Заменили тип колонки** — полное переписывание таблицы (минуты/часы); стратегия — новая колонка + двойная запись + switch.
4. **Правишь применённую миграцию** — расхождение checksum (модуль 06); добавь следующую.
5. **Контракт с кодом** — старый бинарь не знает новую колонку; `SELECT *` по полям, а не `*` вслепую; выкатка кода и миграции — безопасный порядок.

## Мини-задание

Опиши миграцию «добавить обязательную колонку `unit text NOT NULL` с дефолтом 'kg'» — без даунтайма: какие шаги и в каком порядке?

<details>
<summary>Ответ</summary>

1) `ADD COLUMN unit text NOT NULL DEFAULT 'kg'` (метаданные, мгновенно); 2) при необходимости backfill пачками `UPDATE … SET unit = 'kg' WHERE unit = 'kg'` (no-op, для снятия virtual-значений — опционально); 3) `ALTER COLUMN unit DROP DEFAULT` (когда код начнёт заполнять сам); 4) выкатка кода, использующего unit.
</details>

## Как это спросят на собеседовании

1. «Какие миграции безопасны на живой базе, а какие нет?» — метаданные/«±» vs полное переписывание/блокирующий индекс.
2. «Как добавить индекс без даунтайма?» — CREATE INDEX CONCURRENTLY (+VALIDATE).
3. «Как большой backfill не сломать прод?» — пачками, с паузами, по первичному ключу.

## Что читать дальше

- ALTER TABLE (виды блокировок): <https://www.postgresql.org/docs/18/sql-altertable.html>
- CREATE INDEX CONCURRENTLY: <https://www.postgresql.org/docs/18/sql-createindex.html>
- Стратегии (теория): <https://www.postgresql.org/docs/18/ddl-alter.html>