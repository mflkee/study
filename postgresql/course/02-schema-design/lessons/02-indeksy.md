# Урок 02: Индексы — B-tree, BRIN, GIN, partial

**Кейсы:** 1 (каталог оборудования), 5 (отчёты)
**Время:** 2.5 ч

## Зачем это нужно

Индекс — главный рычаг производительности SQL: точечный запрос по тегу или времени может быть в сотни раз быстрее с индексом. Но «просто добавить индекс на всё» — тоже ошибка: каждый индекс стоит диска и замедляет запись. Урок — как выбирать тип индекса под форму запроса и как читать `EXPLAIN`, чтобы не гадать.

## Ключевые идеи (сжато)

- B-tree — универсальный: точные совпадения, диапазоны, сортировка, FK-проверки.
- BRIN — компактный (блочные мин/макс): для больших append-only таблиц и выборок по упорядоченной колонке (ts).
- GIN — инвертированный индекс для jsonb/массивов.
- partial-индекс — B-tree только для подмножества строк (`WHERE ...`).
- `EXPLAIN (ANALYZE, COSTS OFF)` — планы и фактические затраты; `pg_relation_size` — размер индекса.

## Разбор на примере

Запуск: `./infra/apply-db.sh course/02-schema-design/examples/02-indeksy-explain.sql` (внутри — транзакция, 200 000 строк телеметрии в temp-таблице).

**1. Точечный запрос БЕЗ индекса — Seq Scan:**

```sql
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM sensor_large
WHERE device_id = 7 AND ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';
```
```
 Seq Scan on sensor_large (actual time=5.059..11.693 rows=29.00 loops=1)
   Filter: (...)
   Rows Removed by Filter: 199971
   Buffers: local hit=253 read=1021
 Execution Time: 11.714 ms
```
Прочитаны все ~1274 буфера (10 МБ) и отфильтрованы 199 971 строка.

**2. Композитный B-tree `(device_id, ts)` — Bitmap Index + Heap:**

```sql
CREATE INDEX ON sensor_large USING btree (device_id, ts);
```
```
 Bitmap Heap Scan on sensor_large (actual time=0.019..0.025 rows=29.00 loops=1)
   Heap Blocks: exact=10
   Buffers: local hit=10 read=3
   ->  Bitmap Index Scan on sensor_large_device_id_ts_idx
         Index Cond: ((device_id = 7) AND (ts >= ...) AND (ts <= ...))
 Execution Time: 0.037 ms
```
Время — 0.04 мс против 11.7 мс (~300×), буферов — 3 вместо 1274.

**3. BRIN по ts — выборка по времени (все устройства за период):**

```sql
CREATE INDEX sensor_large_ts_brin ON sensor_large USING brin (ts);
```
```
 Bitmap Heap Scan on sensor_large (actual time=0.259..1.050 rows=1441.00 loops=1)
   Rows Removed by Index Recheck: 18655
   Heap Blocks: lossy=128
   ->  Bitmap Index Scan on sensor_large_ts_brin
 Execution Time: 1.099 ms
```
BRIN сократил чтение до ~130 буферов. `lossy=128` — суть BRIN: он хранит мин/макс по диапазонам страниц, поэтому для диапазона «возвращает» целые блоки, а точную фильтрацию делает Heap-перепроверкой.

**4. Размер индексов — почему BRIN привлекателен:**

```sql
SELECT c.relname AS index_name, pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_class c JOIN pg_index i ON i.indexrelid = c.oid
WHERE i.indrelid = 'sensor_large'::regclass
ORDER BY pg_relation_size(c.oid) DESC;
```
```
          index_name           |  size
-------------------------------+---------
 sensor_large_device_id_ts_idx | 6184 kB
 sensor_large_ts_brin          | 24 kB
```
B-tree — 6 МБ, BRIN — 24 КБ (разница **258×**). На гигабайтных таблицах это десятки ГБ экономии.

**Гин и partial** — `examples/03-gin-partial.sql`:

```sql
CREATE INDEX device_meta_attrs_gin ON device_meta USING gin (attrs);
-- Bitmap Index Scan on device_meta_attrs_gin (0.025 ms) — поиск внутри jsonb

CREATE INDEX device_meta_partial ON device_meta(tag) WHERE attrs @> '{"unit":"kg/h"}';
-- Index Only Scan using device_meta_partial (0.010 ms) — работает только для
-- запросов, предикат которых совпадает с WHERE-условием частичного индекса
```

## Как это устроено под капотом

- **B-tree** — страницы-узлы с ключами и указателями; поиск O(log n). Держит точные и диапазонные условия, а также `ORDER BY` по колонке.
- **BRIN** — таблица «последовательность диапазонов страниц × (мин, макс)». Для выборки планировщик отбрасывает диапазоны, не пересекающиеся с условием. Эффективен, когда физический порядок строк близок к порядку значения (время растёт с записью). Для UPDATE и случайных вставок деградирует.
- **GIN** — инвертированные списки для составных значений (jsonb-объекты, массивы): по каждому элементу — список позиций.
- `enable_seqscan = off` в примере — учебный приём «покажи план BRIN»; в проде так не делают, пусть решает стоимость.

## Типичные ошибки и грабли

1. **Индекс на колонке с низкой кардинальностью** (`quality` из 2 значений) — B-tree почти бесполезен: seq scan дешевле; для выборки «только брак» нужен **partial**-индекс по условию.
2. **BRIN на неупорядоченной колонке** — если строки вставляются вперемешку по времени, диапазоны мин/макс раздуваются и BRIN вырождается.
3. **Индекс под запрос, в котором предикат изменён** — `WHERE lower(tag) = ...` не использует индекс по `tag`; нужен expression-индекс.
4. **Слепая вера в стоимость** — смотри `rows` и `actual time` в `EXPLAIN (ANALYZE)`: оценка планировщика может врать на неанализированных таблицах (`ANALYZE` после массовой загрузки обязателен).
5. **Индекс «на всякий случай»** — лишний диск + замедление INSERT; add index to serve a real query.

## Мини-задание

Для таблицы `measurements` (схема каталога, порядок: append-only по ts) предложи индексы под два запроса: (а) «значения массомера за час» и (б) «все измерения за сутки». Назови типы.

<details>
<summary>Ответ</summary>

(а) композитный B-tree `(device_id, ts)` — он уже есть (`idx_measurements_device_ts`);
(б) BRIN по `ts` — выборка по времени из большой append-only таблицы. Partial-индекс — если часто ищешь только `quality = 1`.
</details>

## Как это спросят на собеседовании

1. «Когда BRIN лучше B-tree и почему?»
2. «Что означает lossy-диапазон в BRIN-скане?»
3. «Почему индекс не используется для `WHERE lower(col) = ...`?»

## Что читать дальше

- Индексы (обзор): <https://www.postgresql.org/docs/18/indexes.html>
- BRIN: <https://www.postgresql.org/docs/18/brin-intro.html>
- EXPLAIN: <https://www.postgresql.org/docs/18/using-explain.html>