# Упражнение 02: Разверни TimescaleDB и сравни запросы

**Кейс:** 5 (сменные/суточные отчёты)
**Модуль:** 08-timeseries-partitioning
**Время:** 45 мин
**Сложность:** реши сам

## Условие

На стенде курса поднят профиль `timescale` (PostgreSQL 18 + TimescaleDB 2.30, порт **15433**, база `course`). Твоя задача — на маленьких данных (1000 показаний, 3 устройства) нативно сравнить: **hypertable + непрерывный агрегат** против «ручного» запроса.

Напиши в `student.sql` для контейнера `timescale`:

1. Обычную таблицу `probe_ts (device_id int, ts timestamptz, value numeric(12,4))`;
2. `create_hypertable('probe_ts', 'ts', chunk_time_interval => interval '7 days')`;
3. Вставку 1000 показаний (device_id 1–3, ts каждую минуту, value = i);
4. Continuous aggregate:
   ```sql
   CREATE MATERIALIZED VIEW probe_daily WITH (timescaledb.continuous) AS
   SELECT time_bucket('1 day', ts) AS day, device_id,
          count(*) AS n, sum(value) AS total
     FROM probe_ts GROUP BY day, device_id;
   ```
5. Никаких REFRESH — при создании агрегат уже посчитан (см. пример 04).

Проверка (service `timescale`, база `course`):

```bash
infra/check-sql.sh course/08-timeseries-partitioning/exercises/ex02_timescale/student.sql \
                   course/08-timeseries-partitioning/exercises/ex02_timescale/asserts.sql \
                   timescale course
```

## Критерии ассертов

- `probe_ts` действительно стала гипертаблицей (видна в `timescaledb_information.hypertables`).
- `probe_daily` — continuous aggregate, не пустая.
- Суммы в агрегате совпадают с «сырым» пересчётом по тем же (day, device_id).

Решение — в `../../solutions/ex02_timescale.sql` (после своей попытки).