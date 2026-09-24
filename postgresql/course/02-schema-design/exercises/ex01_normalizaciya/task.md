# Упражнение 01: Нормализация схемы приёмки

**Кейс:** 1 (каталог оборудования)
**Модуль:** 02-schema-design
**Время:** 30 мин
**Сложность:** реши сам

## Условие

Приёмная кампания присылает «сырые» события в плоской таблице: в каждой строке повторяются имя линии, тег прибора, тип и модель. Твоя задача — спроектировать нормализованную схему (3NF) с тремя таблицами:

- `ex01_lines` — справочник линий: `id` (PK), `name` (уникальное);
- `ex01_devices` — справочник приборов: `id` (PK), `tag` (**уникальный**), `device_type`, `model`, `line_id` → FK на `ex01_lines`;
- `ex01_events` — факты: `id` (PK), `device_id` → FK на `ex01_devices`, `ts timestamptz`, `value numeric(20,6)`.

Дополнительно: на `ex01_events` создай **композитный индекс `(device_id, ts)`** — типовой запрос «значения устройства за период».

Заготовка: `student.sql` — пока одна плоская таблица. Впиши DDL трёх таблиц.

## Как проверить

```bash
infra/check-sql.sh course/02-schema-design/exercises/ex01_normalizaciya/student.sql \
                   course/02-schema-design/exercises/ex01_normalizaciya/asserts.sql
```

Выход 0 + `ok: проверка пройдена` — решение верное. Проверка в транзакции (ROLLBACK): схема не засоряется.

## Критерии ассертов

- Существуют `ex01_lines`, `ex01_devices`, `ex01_events`.
- `ex01_devices.tag` уникален (UNIQUE).
- `ex01_events` имеет FK на `ex01_devices`.
- На `ex01_events` есть композитный индекс с колонками `(device_id, ts)`.

Решение — в `../../solutions/ex01_normalizaciya.sql` (после своей попытки).