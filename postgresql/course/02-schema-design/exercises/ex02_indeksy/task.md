# Упражнение 02: Индексы под реальные запросы каталога

**Кейсы:** 1 (каталог), 5 (отчёты)
**Модуль:** 02-schema-design
**Время:** 30 мин
**Сложность:** реши сам

## Условие

В схеме каталога (`equipment_lines`, `devices`, `measurements` — модуль 01) не хватает индексов под два постоянных запроса:

1. «Все ли плохие замеры за период?» — фильтр `WHERE quality = 1 AND ts BETWEEN ...`. Обычный B-tree по `ts` бесполезен (замеры quality=1 — редкие): нужен **partial-индекс** `ON measurements (ts) WHERE quality = 1`.
2. «Какие устройства на линии и какого типа?» — фильтр `WHERE line_id = ... AND device_type = ...`. Нужен **композитный B-tree** `ON devices (line_id, device_type)`.

Дай индексам осмысленные имена (например `idx_measurements_bad_quality`, `idx_devices_line_type`). Таблицы уже существуют — только индексы.

Заготовка: `student.sql` — создаёт обычный (не partial) индекс по `quality` (ошибка намеренно).

## Как проверить

```bash
infra/check-sql.sh course/02-schema-design/exercises/ex02_indeksy/student.sql \
                   course/02-schema-design/exercises/ex02_indeksy/asserts.sql
```

Проверка в транзакции (ROLLBACK) — индексы после проверки исчезают.

## Критерии ассертов

- На `measurements` есть индекс с `WHERE quality = 1` в определении.
- На `devices` есть композитный индекс, содержащий `(line_id, device_type)`.

Решение — в `../../solutions/ex02_indeksy.sql` (после своей попытки).