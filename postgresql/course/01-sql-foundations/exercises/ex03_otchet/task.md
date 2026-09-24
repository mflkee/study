# Упражнение 03: Сменный отчёт — последнее значение за сутки

**Кейс:** 5 (сменные и суточные отчёты)
**Модуль:** 01-sql-foundations
**Время:** 40 мин
**Сложность:** реши сам

## Условие

Оператор просит суточный отчёт: **последнее** значение каждого устройства за каждые сутки (а не максимум!). Создай представление:

```sql
CREATE VIEW last_daily_measurement AS ...
```

с колонками: `device_id`, `day` (дата), `last_value` (последний замер устройства за сутки).

Используй оконную функцию `row_number()` по окну `(PARTITION BY device_id, ts::date ORDER BY ts DESC)` — и подзапрос/CTE, чтобы оставить `rn = 1`. Не используй `max(value)` — это не «последнее значение».

Заготовка: `student.sql` — представление с `max(value)` (делает ошибку намеренно).

## Как проверить

```bash
infra/check-sql.sh course/01-sql-foundations/exercises/ex03_otchet/student.sql \
                   course/01-sql-foundations/exercises/ex03_otchet/asserts.sql
```

## Критерии ассертов

- Представление `last_daily_measurement` существует.
- В нём 10 строк (5 устройств × 2 дня сид-данных).
- `device_id = 1` за `2026-09-23` → `last_value = 998.500000` (НЕ максимум 1002.750000).
- `device_id = 4` за `2026-09-23` → `last_value = 497.250000`.

Решение — в `../../solutions/ex03_otchet.sql` (после своей попытки).