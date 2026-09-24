# Теория модуля 08: временные ряды — партиции, downsampling, TimescaleDB

Конспект-справочник. Развёрнутые примеры с фактическими планами — в уроках (`lessons/`).

## Партиционирование (урок 01)

- Мастер `PARTITION BY RANGE (ts)` + партиции `FOR VALUES FROM … TO …` (месяц).
- **Pruning**: фильтр по ts читает только нужные партиции (план — один Append-узел).
- Партиции ≠ индексы: узкий запрос по device_id без индекса сканирует партицию.
- **Ретеншн** = `DROP TABLE партиции` (мгновенно, без VACUUM-хвоста); ловушка — запоздалые данные («no partition of relation found for row», лаба 01).
- **BRIN** — компактный индекс диапазонов страниц (на стенде: 24 кБ против 15 МБ B-tree у одной партиции); планировщик решает сам — проверяй EXPLAIN.

## Downsampling (урок 02)

- `date_bin(interval, ts, origin)` — бакеты с управляемым «нулём»; сменный/суточный отчёт = GROUP BY по бакету.
- Materialized View — предварительно посчитанный результат; требует `REFRESH` (полный пересчёт); «свежесть» — твоя забота.

## TimescaleDB (урок 03, честное сравнение)

- **Hypertable** — обычная таблица, прозрачно разбитая на чанки (`create_hypertable`, `chunk_time_interval`); чанки видны в `timescaledb_information.chunks`; размер — через `hypertable_size()`.
- **Continuous aggregate** — матвью с инкрементальным обновлением (политика); `WITH DATA`/`refresh_continuous_aggregate` — вне транзакций (`WITH NO DATA` в автопроверке).
- **Лицензия**: в 2.30 caggs — только полный образ `latest-pg18` (OSS кг `-oss` недоступны) — стенд переключен и это задокументировано (design/PLAN).
- Сравнение стенд автора: чтение отчёта нативный ~0.44 мс vs cagg ~1.05 мс (на учебных объёмах); выигрыш Timescale — в эксплуатации (инкремент, политики), не в скорости чтения.

## Инструменты модуля

| Файл | Что |
|---|---|
| `examples/00-setup-partitions.sql` | нативная схема (1 М строк, 3 партиции) |
| `examples/01-partition-queries.sql` | pruning, B-tree vs BRIN (фактические планы) |
| `examples/02-retention.sql` | ретеншн на своей таблице hist |
| `examples/03-downsampling.sql` | date_bin, сменные/суточные, matview |
| `examples/04-timescale.sql` | hypertable + cagg + политика (15433) |
| `exercises/ex01_smennyj_otchet` | сменный отчёт (проверка sums) |
| `exercises/ex02_timescale` | развёртывание Timescale (проверка DDL) |
| `labs/01-partition-incidents.md` | партиция без индекса / ретеншн удалил нужное |