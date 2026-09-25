# Урок 03: Мониторинг — pg_stat_*, postgres_exporter, Prometheus, Grafana

**Модуль / кейс:** 13-ops-reliability / кейс 12 (инциденты)
**Время:** 2.5 ч

## Зачем это нужно

Инцеденты в проде видны по метрикам ДО того, как «пользователи заметили»: рост pg_wal, долгие транзакции, подключения к лимиту. Стенд курса включает готовый стек `obs`: Prometheus + postgres_exporter + Grafana (порт 3000).

## Ключевые идеи (сжато)

- `pg_stat_activity` — живые транзакции (хто, долго, что ждёт).
- `pg_stat_user_tables` — n_live_tup/n_dead_tup (потенциальный bloat).
- `pg_stat_statements` — производительность запросов (топ по времени).
- Экспорт: postgres_exporter отдаёт `pg_*`-метрики в Prometheus-формате; Prometheus их сгребает; Grafana рисует.
- Алертинг — порог + уведомление (в курсе — ручной поиск по метрикам; прод-алертинг — Pegasus-синтаксис в Grafana).

## Разбор на примере

```bash
cd infra && docker compose --profile obs up -d
curl -s http://localhost:9187/metrics | grep pg_up          # exporter
curl -s http://localhost:9090/targets                       # target postgres = UP
# Grafana: http://localhost:3000 (admin/admin) → «PG обзор (курс)»
```

SQL-поиск аномалий — `examples/05-pg-stat-queries.sql`:

```sql
-- самая старая активная транзакция (виновник bloat)
SELECT pid, application_name, state, xact_start, now()-xact_start AS age,
       left(query,60) FROM pg_stat_activity
 WHERE state <> 'idle' AND xact_start < now() - interval '30 seconds';
-- запас подключений к лимиту
SELECT max_connections, count(*) … FROM pg_stat_activity …
```

Факт со стенда: profile `obs` поднят, targets `postgres/prometheus` — `up`, дашборд «PG обзор (курс)» в Grafana, панель «Активные подключения».

## Как это устроено под капотом

- postgres_exporter: подключается к PG (DATA_SOURCE_NAME в compose), периодически читает `pg_stat_*` views на Servern и отдаёт метрики `/metrics`.
- Prometheus: scrape-интервал (15 с), метки (job/instance/database).
- Grafana: панели на PromQL (`rate(pg_stat_database_xact_commit[1m])` и т.п.); провижининг файлом (`provisioning/dashboards` — подключен в этом модуле).
- Метрики сервера (CPU/диск) — контейнерные cAdvisor/Node exporter (упомянуто; фокус курса — метрики PG).

## Типичные ошибки и грабли

1. **Смотришь «среднее» вместо «суммы»** — топ по total, а не mean (модуль 07).
2. **Нет дашбордов/provisioning** — Grafana пустая; в курсе дашборд подключён файлом (папка provisioning).
3. **Метрика «в моменте» vs rate** — счётчики надо rate'ить (`rate(…[1m])`), иначе график «стены».
4. **postgres_exporter к неверной БД** — `DATA_SOURCE_NAME` должен указывать на живого PG (у нас course:5432 внутри сети compose).
5. **Не фильтруешь по datname** — смешиваешь базы в метриках (в курсе метки datname).

## Мини-задание

Открой дашборд «PG обзор (курс)», запусти `pgbench -c 20 -T 10` против course и понаблюдай за «Активные подключения» и «Транзакции в секунду» в реальном времени.

<details>
<summary>Ответ</summary>

Панель Numbackends должна вырасти до ~20, tx/сек — подняться; это «горячий» live-мониторинг инцидента.
</details>

## Как это спросят на собеседовании

1. «Какие метрики PG мониторишь и почему?» — подключения, долгие транзакции, bloat, топ запросов, задержка реплики.
2. «Чем счётчик отличается от gauge?» — rate против наблюдения за состоянием (Prometheus).
3. «Как настроить алерт?» — порог по метрике + Grafana/Alertmanager-уведомление.

## Что читать дальше

- postgres_exporter: <https://github.com/prometheus-community/postgres_exporter>
- Prometheus: <https://prometheus.io/docs/introduction/overview/>
- Grafana (Provisioning): <https://grafana.com/docs/grafana/latest/administration/provisioning/>