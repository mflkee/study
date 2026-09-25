# Теория модуля 13: эксплуатация

Конспект-справочник. Развёрнутые практики — в уроках (`lessons/`) и скриптах `examples/`.

## Бэкап и WAL (урок 01)

- Логический: `pg_dump -Fc` → restore в пробную БД + сверка (скрипт 01).
- Физический: `pg_basebackup -Fp -X stream` (скрипт 02) — основа реплики и PITR.
- WAL-архив: `archive_mode=on` + `archive_command` (стенд: `cp %p /archive/%f`, том `pg-course_wal_archive`).
- PITR: снимок + докат WAL до `recovery_target_time` (в docker — нестабилен, UNVERIFIED; скрипт 03 восстанавливает снимок).
- RPO/RTO: потери времени vs время подъёма; определяют частоту бэкапов.

## Репликация (урок 02)

- Streaming: `pg_basebackup -R` → standby (`recovery.signal`+`primary_conninfo`), walreceiver тянет WAL; `pg_is_in_recovery`=t; промоушен `pg_promote()`.
- pg_hba: нужна `replication`-запись; суперпользователь в docker — `course`.
- Logical — изменения подпиской (обзор).

## Мониторинг (урок 03)

- `pg_stat_activity`/`pg_stat_user_tables`/`pg_stat_statements` — живые/накопленные метрики (запросы в скрипте 05).
- Стек: postgres_exporter (9187) → Prometheus (9090, target pg = UP) → Grafana (3000, дашборд «PG обзор (курс)» — провижининг файлом).
- Счётчики — rate; gauge — в моменте.

## CI (урок 04)

- `docker compose config` — дешёвый gate; testcontainers (модуль 12) тестирует без общего стенда.
- Шаблон `.github/workflows/ci.yml` (validate + test-m12).

## Инструменты модуля

| Файл | Что |
|---|---|
| `examples/00-setup.sql` | таблица alarms для демо |
| `examples/01-pgdump-restore.sh` | dump + restore + сверка (работает) |
| `examples/02-pgbasebackup.sh` | физический снимок (работает) |
| `examples/03-pitr-restore.sh` | восстановление из снимка (PITR-to-time — UNVERIFIED) |
| `examples/04-setup-replica.sh` | streaming standby + промоушен (работает) |
| `examples/05-pg-stat-queries.sql` | запросы аномалий |
| `exercises/ex01_backup` / `ex02_monitoring` | стратегия бэкапа (bash) / топ-медленных (SQL) |
| `labs/01-incidents.md` | WAL-диск, долгая транзакция, пул, PITR |
| `../12-testing` тесты | testcontainers в CI |
| `../infra/…` | compose (obs: prometheus/grafana), дашборд |