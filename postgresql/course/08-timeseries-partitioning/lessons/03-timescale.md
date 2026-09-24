# Урок 03: TimescaleDB — hypertable и continuous aggregates (честное сравнение)

**Модуль / кейс:** 08-timeseries-partitioning / кейс 5 (отчёты), 2 (телеметрия)
**Время:** 2.5 ч

## Зачем это нужно

TimescaleDB — расширение PostgreSQL: та же схема на SQL, но партиционирование, материализованные агрегаты и политики «из коробки». Прежде чем тащить зависимость в СИКН, нужно честно ответить: где выигрыш, где лишний слой (design D3).

## Ключевые идеи (сжато)

- **Hypertable** — обычная таблица, прозрачно разбитая на чанки по времени (`create_hypertable`), чанки видны в `timescaledb_information.chunks`.
- **Continuous aggregate** — матвью, которую TimescaleDB обновляет **инкрементально** (по расписанию-политике): в отличие от `REFRESH MATERIALIZED VIEW`, не пересчитывает всё.
- В стенде: profile `timescale` — PostgreSQL 18 + TimescaleDB 2.30 на порту **15433**, база `course`.
- **Лицензионный сюрприз**: в 2.30 continuous aggregates доступны только в полном образе (`timescale/timescaledb:latest-pg18`); OSS-образ (`-oss`) их не содержит — честный пункт сравнения (мы на стенде переключили образ, design зафиксирован).

## Разбор на примере

```bash
docker compose exec -T timescale psql -U course -d course -f - < course/08-timeseries-partitioning/examples/04-timescale.sql
```

**1. Гипертаблица** (те же 1 М строк):

```sql
CREATE TABLE measurements_ts (device_id int, ts timestamptz, value numeric(12,4));
SELECT create_hypertable('measurements_ts', 'ts', chunk_time_interval => interval '7 days');
```

**2. Continuous aggregate** (суточники): создаётся как матвью; при создании уже посчитан (или `WITH NO DATA` в транзакции + `CALL refresh_continuous_aggregate` вне её):

```sql
CREATE MATERIALIZED VIEW daq_daily_tsc WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', ts) AS day, device_id,
       count(*) AS n, sum(value) AS total
  FROM measurements_ts GROUP BY day, device_id;
CALL refresh_continuous_aggregate('daq_daily_tsc', '2026-08-01', '2026-10-01');
SELECT add_continuous_aggregate_policy(
    continuous_aggregate => 'daq_daily_tsc',
    start_offset => interval '1 month',
    end_offset   => interval '1 hour',
    schedule_interval => interval '1 hour');
```

Фактический вывод (стенд автора): отчёт из cagg за 3 дня — `Time: 1.054 ms`; гипертаблица — 78 МБ.

**Честное сравнение (урок 03 — главное):**

| Критерий | Нативный PG (course_m06) | TimescaleDB (15433) |
|---|---|---|
| Чтение отчёта (3 дня) | `daq_daily` матвью — 0.44 мс | `daq_daily_tsc` cagg — 1.05 мс |
| Обновление агрегата | ручной `REFRESH` (полный пересчёт) | политика: инкрементальный, частичный |
| Управление устаревшим | свои скрипты (DROP PARTITION) | политики retention |
| Доп. зависимость | нет | расширение + лицензия (полный образ) |
| Гибкость схемы | стандартный SQL | стандартный SQL + API расширения |

Вывод: на учебных объёмах скорость чтения сопоставима; **экономия — в эксплуатации** (инкрементальные агрегаты, политики). За это платишь лишней зависимостью и лицензионной политикой (OSS без caggs). Для СИКН-проекта это осознанный выбор, а не «TimescaleDB всегда лучше».

## Как это устроено под капотом

- Hypertable = партиционирование по времени (чанкам) плюс метаданные; INSERT/селект прозрачны (SQL тот же).
- Continuous aggregate хранит материализованные бакеты и «следит» за новым данными: при новом INSERT политика (или ручной refresh) до-обновляет только затронутые бакеты — отсюда скорость против полного `REFRESH`.
- `refresh_continuous_aggregate` (и `WITH DATA` при создании) — **вне транзакций** (ограничение TimescaleDB); в автопроверке упражнений — `WITH NO DATA`.

## Типичные ошибки и грабли

1. **OSS-образ с caggs** — 2.30 `-oss` не поддерживает continuous aggregates («not supported under the apache license»); бери полный образ и помни про лицензию.
2. **`WITH DATA`/`refresh_continuous_aggregate` в транзакции** — ошибка «cannot run inside a transaction block» (упражнение 02 показывает обход: `WITH NO DATA` + refresh вне автопроверки).
3. **Считаешь размер основной таблицы** — у гипертаблицы данные в чанках: `hypertable_size()`/`timescaledb_information.chunks`, не `pg_total_relation_size('…')` (покажет 8 кБ).
4. **Забыл политику** — cagg «застывает», как и обычная матвью; политика — это и есть «свежесть».
5. **«Timescale всегда быстрее»** — нет: чтение сопоставимо; выигрыш в эксплуатации и на больших объёмах; мерь сам (упражнение 02, лаба).

## Мини-задание

В базе `course` (15433) создай свою гипертаблицу `probe2` (1000 строк, 3 устройства), cagg с `WITH NO DATA`, отрефрешь вне транзакции и сравни время отчёта из cagg с «сырым» GROUP BY (SQL в упражнении 02 — тот же приём).

<details>
<summary>Ответ</summary>

См. решение `exercises/ex02_timescale` (student+asserts). Разница времени на 1000 строк незначима; главное — увидели, что cagg после `WITH NO DATA` требует явного refresh (вне транзакции).
</details>

## Как это спросят на собеседовании

1. «Чем cagg отличается от матвью?» — инкрементальное обновление, политики, real-time; сложный в refresh-циклах.
2. «Когда TimescaleDB оправдан?» — большие ряды с частыми интервальными агрегатами; когда нативный PG начинает мучать refresh/lеẩth... на малых — оверхед.
3. «Какие риски?» — лицензия (полный образ), зависимость расширения, миграция схемы капстоуна (модуль 14 evaluates).

## Что читать дальше

- TimescaleDB docs: <https://docs.timescale.com/>
- Continuous aggregates и политики: <https://docs.timescale.com/use-timescale/latest/continuous-aggregates/>
- Пример и настройка стенда: `infra/docker-compose.yml` (profile `timescale`, порт 15433), `examples/04-timescale.sql`