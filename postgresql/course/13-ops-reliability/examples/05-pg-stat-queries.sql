-- 05-pg-stat-queries.sql — pg_stat_* для поиска аномалий (модуль 13, урок 03).
-- База: course_m06. Запуск в psql.

-- 1. Долгие транзакции (виновник bloat): активные дольше 30 секунд.
SELECT pid, application_name, state, xact_start,
       now() - xact_start AS age,
       left(query, 60) AS query
  FROM pg_stat_activity
 WHERE state <> 'idle'
   AND xact_start < now() - interval '30 seconds'
 ORDER BY xact_start;

-- 2. Запас подключений к лимиту.
SELECT max_conns.setting AS max_connections,
       count(a.*)        AS used,
       max_conns.setting::int - count(a.*) AS free
  FROM pg_stat_activity a
  CROSS JOIN (SELECT setting FROM pg_settings WHERE name = 'max_connections') max_conns
 GROUP BY max_conns.setting;

-- 3. Потенциальный bloat и неподчищенные версии строк.
SELECT relname, n_live_tup, n_dead_tup,
       round(100.0 * n_dead_tup / nullif(n_live_tup + n_dead_tup, 0), 1) AS dead_pct
  FROM pg_stat_user_tables
 WHERE n_dead_tup > 1000
 ORDER BY n_dead_tup DESC;

-- 4. Топ-5 по суммарному времени (pg_stat_statements).
SELECT calls, round(total_exec_time::numeric, 1) AS total_ms,
       round(mean_exec_time::numeric, 2) AS mean_ms,
       left(query, 60) AS query
  FROM pg_stat_statements
 ORDER BY total_exec_time DESC LIMIT 5;

-- 5. Кто ждёт блокировок (deadlock-профиль).
SELECT pid, wait_event_type, wait_event, left(query, 60) AS query
  FROM pg_stat_activity
 WHERE wait_event_type IN ('Lock', 'LWLock')
 ORDER BY wait_event_type;