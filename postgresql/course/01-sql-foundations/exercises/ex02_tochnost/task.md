# Упражнение 02: Точность расчётов — масса нетто в NUMERIC

**Кейс:** 3 (точность расчётов)
**Модуль:** 01-sql-foundations
**Время:** 30 мин
**Сложность:** реши сам

## Условие

Расчёт массы нетто: `netto = brutto * (1 - moisture) * (1 - impurities)`, где
`brutto` — масса брутто (кг/ч), `moisture` — доля воды, `impurities` — доля примесей.

Создай таблицу `ex02_netto` и посчитай нетто для строки:
`brutto = 1000.0`, `moisture = 0.005`, `impurities = 0.002`.

Ключевое требование — **тип колонки netto должен быть NUMERIC** (не float8), а расчёт — с округлением до 3 знаков: `round(..., 3)`. Ожидаемый результат: **993.010**

Заготовка: `student.sql` — использует float8 (сделает ошибку точности специально). Перепиши на NUMERIC.

## Как проверить

```bash
infra/check-sql.sh course/01-sql-foundations/exercises/ex02_tochnost/student.sql \
                   course/01-sql-foundations/exercises/ex02_tochnost/asserts.sql
```

## Критерии ассертов

- Колонка `netto` имеет тип `numeric`.
- Значение `netto` равно точно `993.010000`.
- В таблице ровно одна строка.

Решение — в `../../solutions/ex02_tochnost.sql` (после своей попытки).