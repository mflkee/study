# Урок 01: SELECT — выборки по каталогу оборудования

**Кейсы:** 1 (каталог оборудования)
**Время:** 1.5 ч

## Зачем это нужно

SELECT — это 80% ежедневных запросов администратора и разработчика: «что за теги на линии 1», «какие значения пришли за час», «сколько строк в справочнике». Этот урок — база, на которой стоят все остальные (JOIN, агрегаты, окна).

## Ключевые идеи (сжато)

- SELECT = проекция колонок + фильтрация строк; порядок выполнения: `FROM → WHERE → SELECT → ORDER BY → LIMIT`.
- WHERE фильтрует строки ДО проекции; в WHERE нельзя ссылаться на алиас из SELECT.
- NULL — не «ноль», а «нет значения»: сравнения `= NULL` не работают, только `IS NULL`.
- `ORDER BY` может использовать невыбранные колонки; `DISTINCT` + `ORDER BY` — только по выбранным.

## Разбор на примере

Сначала схема и данные (однократно):

```bash
./infra/apply-db.sh course/01-sql-foundations/examples/00-schema-katalog.sql
./infra/apply-db.sh course/01-sql-foundations/examples/01-seed-katalog.sql
```

Дальше — `examples/02-select-katalog.sql` (запуск: `./infra/apply-db.sh course/01-sql-foundations/examples/02-select-katalog.sql`).

**1. Проекция + сортировка.** Какие линии есть в справочнике:

```sql
SELECT id, name FROM equipment_lines ORDER BY id;
```
```
 id |  name
----+---------
  1 | ЛИНИЯ-1
  2 | ЛИНИЯ-2
```

**2. Фильтр по внешнему ключу.** Устройства линии 1 по тегу:

```sql
SELECT tag, device_type, model
FROM devices
WHERE line_id = 1
ORDER BY tag;
```
```
   tag    |   device_type   |  model
----------+-----------------+---------
 D-01-001 | density_meter   | MVD-1
 M-01-001 | mass_meter      | CMF-300
 W-01-001 | moisture_meter  | MVM-2
```

**3. DISTINCT.** Какие типы устройств бывают:

```sql
SELECT DISTINCT device_type FROM devices ORDER BY device_type;
```
```
  device_type
----------------
 density_meter
 mass_meter
 moisture_meter
```

**4. NULL-семантика.** У «ЛИНИЯ-2» колонка `description` = NULL (сид не заполнил):

```sql
SELECT id, name, description FROM equipment_lines ORDER BY id;
-- ЛИНИЯ-2: description | <пусто> (NULL)
SELECT id, name FROM equipment_lines WHERE description IS NULL;
-- -> ЛИНИЯ-2
SELECT count(*) AS total, count(description) AS with_description FROM equipment_lines;
-- total=2, with_description=1   (count(col) не считает NULL!)
```

**5. LIMIT.** Первые 3 измерения:

```sql
SELECT id, device_id, ts, value FROM measurements ORDER BY id LIMIT 3;
```

## Как это устроено под капотом

План запроса строится по цепочке `FROM → WHERE → SELECT → ORDER BY → LIMIT`:

- `WHERE` может использовать индекс (в нашем примере — индекс по `tag` для `WHERE tag = ...`; см. `EXPLAIN` в модуле 02).
- `ORDER BY` на больших данных = сортировка (или обход индекса, если сортировка совпадает с индексом).
- `LIMIT` позволяет остановить чтение рано: PostgreSQL не читает остаток, если первые строки уже найдены.
- Фильтр `WHERE description IS NULL` нельзя записать как `= NULL` — оптимизатор не приводит NULL-сравнения к индексам, а семантика просто неверна.

Можно увидеть план запроса прямо сейчас: `EXPLAIN (COSTS OFF) SELECT tag FROM devices WHERE tag = 'M-01-001';` — PostgreSQL покажет Index Scan по `devices_tag_key`.

## Типичные ошибки и грабли

1. **`WHERE tag = NULL`** — всегда пусто. Правильно: `tag IS NULL` / `IS NOT NULL`.
2. **Алиас в WHERE**: `SELECT value AS v FROM measurements WHERE v > 100` — ошибка: в WHERE алиасы ещё не видны (обработка идёт раньше SELECT).
3. **`count(description)` ≠ `count(*)`** — `count(col)` игнорирует NULL. Частая причина «почему-то меньше строк».
4. **Сравнение VARCHAR-тегов с разным регистром** — `'m-01-001'` ≠ `'M-01-001'` (байтовое сравнение); в работе договорись о регистре заранее.
5. **Забытый `ORDER BY`** — порядок строк не гарантирован без него, даже если "на глаз" стабилен.

## Мини-задание

Выведи первые 3 массомера (`device_type = 'mass_meter'`) линии 2 с сортировкой по тегу по убыванию.

<details>
<summary>Ответ</summary>

```sql
SELECT tag, model
FROM devices
WHERE line_id = 2 AND device_type = 'mass_meter'
ORDER BY tag DESC
LIMIT 3;
```

В сид-данных на линии 2 один массомер (`M-02-001`) — вернётся одна строка: `M-02-001 | CMF-200`.
</details>

## Как это спросят на собеседовании

1. «В каком порядке выполняются части SELECT и почему алиас не работает в WHERE?»
2. «Чем `count(*)` отличается от `count(col)`?»
3. «Какой запрос найдёт строки, где колонка NULL?»

## Что читать дальше

- SELECT (документация 18): <https://www.postgresql.org/docs/18/sql-select.html>
- Порядок выполнения и NULL-логика (Tutorial, глава 2–3): <https://www.postgresql.org/docs/18/tutorial-select.html>