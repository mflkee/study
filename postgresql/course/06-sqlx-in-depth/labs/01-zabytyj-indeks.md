# Лаба 01: Забытый индекс и N+1 — сломай и почини

**Кейс:** 11 (нагрузочный стенд), 2 (телеметрия)
**Модуль:** 06-sqlx-in-depth
**Время:** 1.5 ч
**Тип:** диагностируй по симптомам

## Цель

Воспроизвести классику «всё медленно»: таблица растёт, индекс забыли (или его нет под наш запрос) — PostgreSQL ходит сканом всей таблицы. Найти узкое место через `EXPLAIN (ANALYZE, BUFFERS)`, починить индексом, измерить эффект. Побочная тема — **N+1**: запрос на каждую строку/сущность в цикле вместо одного.

## Схема стенда

База модуля `course_m06` (поднята, миграции 0001–0002 применены — см. README модуля). Работаем в psql:

```bash
docker compose exec -T postgres psql -U course -d course_m06
```

## Часть A. Забытый индекс

1. **Тяжёлая таблица без индекса** — «события телеметрии»:

   ```sql
   CREATE TABLE IF NOT EXISTS events (
       id        bigserial PRIMARY KEY,
       device_id int NOT NULL,
       ts        timestamptz NOT NULL,
       value     numeric(12,4) NOT NULL
   );
   TRUNCATE events;
   INSERT INTO events (device_id, ts, value)
   SELECT i % 5, '2026-09-24 00:00:00+00' + (i || ' seconds')::interval, i
     FROM generate_series(1, 500000) i;
   ANALYZE events;
   ```

2. **Запрос «события устройства за сутки»** и его план:

   ```sql
   EXPLAIN (ANALYZE, BUFFERS)
   SELECT * FROM events WHERE device_id = 1 AND ts >= '2026-09-24 00:00:00+00'
                         AND ts <  '2026-09-24 01:00:00+00';
   ```

   Ожидаем: **`Seq Scan`** на 500 000 строк, большое `Buffers` и `Execution Time`.

3. **Чиним индексом** (как это выглядело бы миграцией 0003 лабы — здесь `CREATE INDEX` напрямую):

   ```sql
   CREATE INDEX idx_events_device_ts ON events (device_id, ts);
   EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM events WHERE device_id = 1 AND ts >= '2026-09-24 00:00:00+00' AND ts < '2026-09-24 01:00:00+00';
   ```

   Ожидаем: **`Index Scan using idx_events_device_ts`**, `Buffers` в разы меньше, время — на порядок меньше.

### Ожидаемый контраст (эталонные цифры со стенда автора)

```
─ Seq Scan (Processing → ^^)   Buffers: shared hit=274 рад все 500k...
  Execution Time: ~140 ms
─ Index Scan using idx_events_device_ts   Buffers: shared hit=6
  Execution Time: ~0.05 ms
```

Точные цифры зависят от машины — важно соотношение и чтение плана, а не абсолюты.

## Часть B. N+1 (и как его увидеть в Rust)

**N+1** — паттерн, когда для N сущностей делается 1 + N запросов («возьми список устройств, потом для каждого — последнее измерение»). При 1000 устройств — 1001 запрос к БД.

SQL-эквивалент «последнее измерение на каждое устройство» **одним запросом** (DISTINCT ON / оконные функции, модуль 01):

```sql
SELECT DISTINCT ON (d.id) d.tag, m.ts, m.value
  FROM devices d JOIN measurements m ON m.device_id = d.id
 ORDER BY d.id, m.ts DESC;
```

В Rust-коде это:
- **плохо** — цикл по устройствам, внутри `SELECT … WHERE device_id = $1` (N round-trip'ов);
- **хорошо** — один `fetch_all` с JOIN/DISTINCT ON (1 round-trip).

Напиши свой мини-пример N+1 (по желанию) и сравни время для 5–50 устройств; правило: **никогда не делай запрос в цикле, если можно одним SQL**.

## Критерии успеха

- [ ] Увидел `Seq Scan` до индекса и `Index Scan` после; записал `Buffers` и время до/после
- [ ] Объяснил, почему индекс (device_id, ts) подходит именно под этот запрос (порядок колонок!)
- [ ] Показал N+1 на словах и написал однозапросный вариант (DISTINCT ON / JOIN)
- [ ] Занёс выводы в `PROGRESS.md`

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| индекс есть, а `Seq Scan` | индекс не под фильтр (другой порядок колонок, не та селективность) | подбирай (device_id, ts) против (ts) — смотри урок 02 модуля 02 |
| `EXPLAIN` без `(ANALYZE)` — оценки, а не факты | статистика примерная | `EXPLAIN (ANALYZE, BUFFERS)` |
| «индекс не работает на функции» | `WHERE ts >= now() - …` — ок, а `date_trunc('day', ts)` — нет | не оборачивай колонку в функцию (или expression index) |
| статистика устарела | не запускали `ANALYZE` | `ANALYZE events;` перед замерами |

## Задания «со звёздочкой»

1. Сравни `(device_id, ts)` и `(ts, device_id)` на этом запросе — почему разница?
2. Частичный индекс (`WHERE device_id = 1`) — когда оправдан?
3. Измерь «запрос в цикле» vs «один JOIN» в Rust: напиши пример `n1_compare` (5 устройств → 6 запросов vs 1; замерь `Instant`).

## Что дальше

- Модуль 07: `EXPLAIN` глубже, `pg_stat_statements`, batch и COPY.
- Модуль 08: BRIN на временных рядах (когда B-tree невыгоден).