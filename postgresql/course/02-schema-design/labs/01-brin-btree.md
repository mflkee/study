# Лаба 01: BRIN vs B-tree под запросы телеметрии

**Кейсы:** 1 (каталог), 5 (отчёты)
**Модуль:** 02-schema-design
**Время:** 1 ч
**Тип:** диагностируй по симптомам

## Цель

На синтетической таблице телеметрии (1 000 000 замеров, 50 устройств, шаг 1 минута ≈ 1.4 года) построить B-tree `(device_id, ts)` и BRIN `(ts)`, измерить `EXPLAIN (ANALYZE, BUFFERS)` для двух классов запросов и **сделать вывод без предубеждений**: где какой индекс реально выигрывает и почему планировщик выбрал именно его.

## Схема стенда

```
lab02_tel (temp, 1M строк, в транзакции):
  device_id int,      // (i % 50) + 1
  ts timestamptz,     // 2026-01-01 + i минут (физический порядок ~ хронологический)
  value numeric(20,6)
```

## Шаги

### Шаг 1. Подготовка данных

```sql
BEGIN;
CREATE TEMP TABLE lab02_tel (
    id bigserial, device_id int NOT NULL,
    ts timestamptz NOT NULL, value numeric(20,6) NOT NULL
);
INSERT INTO lab02_tel (device_id, ts, value)
SELECT (i % 50) + 1,
       '2026-01-01 00:00:00+00'::timestamptz + (i * interval '1 minute'),
       round((random() * 100)::numeric, 3)
FROM generate_series(1, 1000000) AS i;
ANALYZE lab02_tel;   -- autovacuum не трогает temp-таблицы — ANALYZE обязателен
```

### Шаг 2. Точечный запрос без индекса

```sql
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM lab02_tel
WHERE device_id = 7 AND ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';
```
На стенде автора: **Seq Scan, 46.029 ms, 7353 буфера**, 29 строк (прочитаны все 1M).

### Шаг 3. Индексы и точечный запрос с B-tree

```sql
CREATE INDEX lab02_tel_dev_ts ON lab02_tel USING btree (device_id, ts);
CREATE INDEX lab02_tel_ts_brin ON lab02_tel USING brin (ts);

EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM lab02_tel
WHERE device_id = 7 AND ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-02 00:00:00+00';
```
На стенде автора: **Bitmap Heap Scan (Bitmap Index Scan на B-tree), 0.056 ms, 14 буферов** — в ~800 раз быстрее, буферов почти в 500 раз меньше. Точечный запрос — это зона B-tree.

### Шаг 4. Диапазон по времени (сутки, все устройства) — честный выбор планировщика

```sql
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM lab02_tel
WHERE ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-01 23:59:59+00';
```
На стенде автора: **Index Scan using lab02_tel_dev_ts, 0.484 ms, ~718 буферов** — планировщик выбрал B-tree (обход всех 50 устройств с диапазоном ts), а не BRIN!

### Шаг 5. Тот же диапазон, BRIN-план (принудительно)

```sql
SET LOCAL enable_seqscan = off;
SET LOCAL enable_indexscan = off;   -- оставить bitmap: BRIN работает через Bitmap
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM lab02_tel
WHERE ts BETWEEN '2026-02-01 00:00:00+00' AND '2026-02-01 23:59:59+00';
RESET enable_seqscan; RESET enable_indexscan;
```
На стенде автора: **Bitmap Heap Scan (Bitmap Index Scan на BRIN), 1.494 ms, 136 буферов, `Heap Blocks: lossy=128`, `Rows Removed by Index Recheck: 15968`** — меньше чтений, но время больше: BRIN «отдаёт» блоки целиком (lossy) и перепроверяет строки в куче.

### Шаг 6. Размер индексов — сильная сторона BRIN

```sql
SELECT c.relname AS index_name, pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_class c JOIN pg_index i ON i.indexrelid = c.oid
WHERE i.indrelid = 'lab02_tel'::regclass
ORDER BY pg_relation_size(c.oid) DESC;
```
```
    index_name     |  size
-------------------+--------
 lab02_tel_dev_ts  | 30 MB
 lab02_tel_ts_brin | 24 kB
```
B-tree — 30 МБ, BRIN — 24 КБ (**≈1250×**). Не забудь `ROLLBACK;` в конце — таблица временная.

## Критерии успеха

- [ ] Точечный запрос (шаг 2 vs 3): увидел переход Seq Scan → Bitmap Index Scan, зафиксировал разницу времени и буферов
- [ ] Диапазонный запрос (шаг 4): объяснил, почему планировщик выбрал B-tree, а не BRIN
- [ ] Шаг 5: назвал причину lossy и recheck в BRIN (`pages_per_range`, блочные мин/макс)
- [ ] Шаг 6: сформулировал, в каких случаях BRIN всё же выигрывает (широкие диапазоны по времени, размер индекса, большие таблицы)
- [ ] Сделал вывод: выбор индекса = форма запроса + селективность + размер; проверка — только через EXPLAIN (ANALYZE), а не через «общеизвестное»

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| BRIN «не используется» планировщиком | селективность высокая (узкий диапазон) — B-tree дешевле | расширь диапазон; сравни честные планы, не форсируй |
| time BRIN > B-tree при меньших буферах | lossy-перепроверка строк в куче | для точечных запросов BRIN не нужен; BRIN — про размер и широкий охват |
| «Планировщик странный» | таблица не проанализирована | `ANALYZE` (temp-таблицы autovacuum не обслуживает) |
| Индекс по time не помогает точке | нужен префиксный столбец (`device_id, ts`) | при `WHERE device_id = ... AND ts ...` композит с совпадающим порядком |

## Задания «со звёздочкой»

1. Прогони диапазон «месяц» (`2026-02-01` … `2026-03-01`): как изменится выбор планировщика и время? Предскажи, потом измерь.
2. Узнай `pages_per_range` BRIN-индекса (`CREATE INDEX ... WITH (pages_per_range = 64)`), повтори шаг 5: как изменится lossy и время recheck?
3. Добавь запрос «последние значения всех устройств» (`DISTINCT ON (device_id) ... ORDER BY device_id, ts DESC`) и посмотри, через что он пойдёт (похоже на модуль 01, урок 05).