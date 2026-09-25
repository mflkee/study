# Упражнение 02: «Подними мониторинг и найди аномалию по метрикам»

**Кейс:** 12 (инциденты), тема мониторинга
**Модуль:** 13-ops-reliability
**Время:** 45 мин
**Сложность:** реши сам

## Часть А. Подними profile `obs`

```bash
cd infra && docker compose --profile obs up -d
```

Проверь:
- `curl http://localhost:9187/metrics | grep pg_up` — exporter жив;
- `curl http://localhost:9090/targets` — target `postgres` в статусе UP;
- Grafana `http://localhost:3000` (admin/admin) → дашборд «PG обзор (курс)» — панели с данными.

## Часть B. SQL-аномалия

По сценарию лабы 01: долгая транзакция (repeatable read, модуль 06), в параллели VACUUM показывает `dead but not yet removable`, а `examples/05-pg-stat-queries.sql` находит виновника по `pg_stat_activity`.

Напиши в `student.sql` запрос, который **по `pg_stat_statements`** вернёт топ-5 медленных запросов по суммарному времени и положит результат во временную таблицу `top_slow` (колонки `calls`, `total_ms`, `mean_ms`, `query`). Ассерты проверят: таблица непустая и поля корректны.

## Как проверить

```bash
infra/check-sql.sh course/13-ops-reliability/exercises/ex02_monitoring/student.sql \
                   course/13-ops-reliability/exercises/ex02_monitoring/asserts.sql \
                   postgres course_m06
```

Решение — в `../../solutions/ex02_monitoring.sql`.