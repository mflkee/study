# Урок 04: JOIN, GROUP BY, HAVING

**Кейсы:** 1 (каталог оборудования), 5 (отчёты)
**Время:** 1.5 ч

## Зачем это нужно

Данные СИКН — это справочники + поток измерений: чтобы получить «какая линия и что мерила», нужно соединить три таблицы. GROUP BY превращает поток значений в отчёт («сколько замеров, минимум, максимум по устройству»). Это базовая аналитика, которую спрашивают и в работе, и на собеседованиях.

## Ключевые идеи (сжато)

- `JOIN` связывает строки по условию; `LEFT JOIN` сохраняет все строки левой таблицы.
- Условие связи — всегда явное (`ON`). Без `ON` — декартово произведение.
- `GROUP BY` схлопывает строки в группы; всё не-агрегатное в SELECT обязано быть в GROUP BY.
- `HAVING` фильтрует группы (после агрегации), `WHERE` — строки (до агрегации).
- `count(*)` считает строки, `count(col)` — не-NULL значения.

## Разбор на примере

Запуск: `./infra/apply-db.sh course/01-sql-foundations/examples/05-joins-group.sql`. Сначала перепримени сид, чтобы данные были эталонными: `./infra/apply-db.sh course/01-sql-foundations/examples/01-seed-katalog.sql`.

**1. JOIN: устройства вместе с линиями:**

```sql
SELECT l.name AS line, d.tag, d.device_type
FROM equipment_lines l
JOIN devices d ON d.line_id = l.id
ORDER BY l.name, d.tag;
```
```
  name   |   tag    |   device_type
---------+----------+-----------------
 ЛИНИЯ-1 | D-01-001 | density_meter
 ЛИНИЯ-1 | M-01-001 | mass_meter
 ЛИНИЯ-1 | W-01-001 | moisture_meter
 ЛИНИЯ-2 | D-02-001 | density_meter
 ЛИНИЯ-2 | M-02-001 | mass_meter
```

**2. LEFT JOIN: линии и число устройств (включая пустые линии):**

```sql
SELECT l.name, count(d.id) AS devices_count
FROM equipment_lines l
LEFT JOIN devices d ON d.line_id = l.id
GROUP BY l.name
ORDER BY l.name;
```
```
  name   | devices_count
---------+---------------
 ЛИНИЯ-1 | 3
 ЛИНИЯ-2 | 2
```

**3. GROUP BY ... HAVING: линии, где >= 2 устройств:**

```sql
SELECT d.line_id, count(*) AS cnt
FROM devices d
GROUP BY d.line_id
HAVING count(*) >= 2
ORDER BY d.line_id;
```
```
 line_id | cnt
---------+-----
       1 |   3
       2 |   2
```

**4. Агрегаты измерений по устройствам:**

```sql
SELECT device_id,
       count(*)             AS n,
       min(value)           AS min_v,
       max(value)           AS max_v,
       round(avg(value), 3) AS avg_v
FROM measurements
GROUP BY device_id
ORDER BY device_id;
```
```
 device_id | n |   min_v    |    max_v    |  avg_v
-----------+---+------------+-------------+----------
         1 | 7 | 990.125000 | 1010.500000 | 1000.304
         2 | 4 | 850.100000 |  850.300000 |  850.188
         3 | 2 |   0.480000 |    0.500000 |    0.490
         4 | 7 | 497.250000 |  503.000000 |  500.357
         5 | 4 | 859.800000 |  860.100000 |  859.963
```

## Как это устроено под капотом

- Планировщик выбирает способ соединения: **Hash Join** (маленькая таблица в хэш-таблицу в памяти), **Nested Loop** (для малых объёмов и точечных выборок), **Merge Join** (обе стороны отсортированы). При малых справочниках — Hash Join.
- `GROUP BY` + агрегаты: PostgreSQL сортирует или хэширует строки по ключам группы, затем вычисляет агрегаты по каждой группе.
- `HAVING` исполняется ПОСЛЕ агрегации, поэтому в нём можно ссылаться на `count(*)` — а в `WHERE` нельзя (там строки ещё не сгруппированы).
- `STRING_AGG`/`array_agg` — агрегаты, собирающие значения в строку/массив: `array_agg(d.tag)` по линии вернёт список тегов.

## Типичные ошибки и грабли

1. **Забытое условие ON** — INNER JOIN без ON = декартово произведение: 3 линии × 5 устройств = 15 строк «мусора».
2. **Колонка не в GROUP BY и не в агрегате** — `SELECT l.name, d.tag ... GROUP BY l.name` упадёт: `column "d.tag" must appear in the GROUP BY clause`. В PostgreSQL это ошибка (в отличие от MySQL).
3. **`count(d.id)` вместо `count(*)` в LEFT JOIN** — для линии без устройств `d.id` = NULL, `count(d.id)` даст 0 (что и нужно), а `count(*)` дал бы 1. Понимание разницы спасает в реальных отчётах.
4. **HAVING для фильтрации строк** — фильтр по обычной колонке лучше держать в WHERE (индекс); HAVING — только по агрегатам.
5. **`avg` float vs numeric** — `avg(numeric)` возвращает numeric, `avg(float8)` — float8; округляй `round(..., 3)` для читаемости.

## Мини-задание

Напиши запрос: средняя масса (device_id = 1) по дням, только за 2026-09-22, с количеством замеров, отсортировано по дню.

<details>
<summary>Ответ</summary>

```sql
SELECT ts::date AS day, count(*) AS n, round(avg(value), 3) AS avg_mass
FROM measurements
WHERE device_id = 1 AND ts::date = '2026-09-22'
GROUP BY ts::date
ORDER BY day;
```
```
    day     | n | avg_mass
------------+---+----------
 2026-09-22 | 4 |  1001.469
```
</details>

## Как это спросят на собеседовании

1. «Чем INNER JOIN отличается от LEFT JOIN и как проверить, что строка не потерялась?»
2. «Почему колонки, не входящие в агрегаты, обязаны быть в GROUP BY?»
3. «Как посчитать число заказов по каждому клиенту, у которого их больше пяти?» (GROUP BY + HAVING)

## Что читать дальше

- JOIN и агрегаты (Tutorial): <https://www.postgresql.org/docs/18/tutorial-join.html>
- GROUP BY / HAVING: <https://www.postgresql.org/docs/18/queries-table-expressions.html>
- Агрегатные функции: <https://www.postgresql.org/docs/18/functions-aggregate.html>