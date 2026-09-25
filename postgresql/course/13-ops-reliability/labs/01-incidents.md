# Лаба 01: Инциденты — сломай и почини (модуль 13)

**Кейс:** 12 (инциденты)
**Модуль:** 13-ops-reliability
**Время:** 2.5 ч
**Тип:** диагностируй по симптомам

## Цель

Четыре инцидента эксплуатации, каждый — со своим симптомом, диагностикой и починкой. Всё воспроизводится на стенде курса; критерии — измеримые.

## Часть A. Забился «диск WAL» (архив недоставлен)

1. **Слом**: сделай архив недоступным для записи:

   ```bash
   docker run --rm -v pg-course_wal_archive:/archive postgres:18 chown root:root /archive
   ```

2. **Нагрузка**: `docker compose exec -T postgres pgbench -c 8 -j 4 -T 10 -U course -d course`

3. **Симптом**: в логе postgres — `cp: cannot stat|permission denied` строки archive_command; при интенсивной записи `pg_wal` растёт (не удаляется, т.к. архив не принял сегменты):
   - `du -sh …/pg_wal` (внутри контейнера: `/var/lib/postgresql/18/docker/pg_wal`);
   - при долгом простое — диск закончится и сервер уйдёт в **PANIC/PENDING_RESTART**.
4. **Починка**: вернуть права архиву, вызвать `pg_switch_wal()`/дождаться — `pg_wal` начнёт освобождаться:

   ```bash
   docker run --rm -v pg-course_wal_archive:/archive postgres:18 chown postgres:postgres /archive
   docker compose exec -T postgres psql -U course -d course -c "SELECT pg_switch_wal();"
   ```

**Критерий A**: увидел ошибки cp в логе; объяснил, почему pg_wal не уменьшается при сломанном архиве; починил — размер стабилизировался/упал.

## Часть B. Долгая транзакция против VACUUM

1. Терминал 1 (Rust-воркер модуля 06 или psql): `BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT count(*) FROM measurements_part;` — держим снимок.
2. Терминал 2: `DELETE FROM measurements_part WHERE …; VACUUM VERBOSE measurements_part;` → `40000 are dead but not yet removable`.
3. Найди виновника: запрос из `examples/05-pg-stat-queries.sql` (пункт 1, порядок по `xact_start`).
4. **Починка**: закрыть транзакцию; VACUUM снова — `N removed`. Меры: короткие транзакции, `idle_in_transaction_session_timeout`.

**Критерий B**: воспроизвёл `dead but not yet removable`; в `pg_stat_activity` нашёл воркера; после закрытия — почистилось.

## Часть C. Потеря пула соединений

1. **Слом**: слишком много конкурентных соединений: `pgbench -c 150 -j 8 -T 3 -U course -d course` (лимит сервера 100).
2. **Симптом**: `FATAL: sorry, too many clients already`; приложение падает в очередь/ретраи.
3. **Починка**: осознанный лимит приложения (пул ≤ серверного лимита минус запас — модуль 06/07), `max_connections` поднять осознанно (память!), `connection_limit` на pool.
4. Проверь запас: запрос из `examples/05-…` (пункт 2) — `max_connections` vs `used`.

**Критерий C**: воспроизвёл `too many clients`; объяснил причину и починил на уровне приложения (не «просто поднял лимит»).

## Часть D. Восстановление PITR

1. `00-setup.sql` (alarms), затем физический бэкап: `bash examples/02-pgbasebackup.sh`.
2. Внеси «плохие» изменения (DROP TABLE alarms) и сымитируй потерю.
3. Восстанови из бэкапа (последнее состояние) — скрипт `examples/03-pitr-restore.sh` (восстановление в отдельном контейнере) — и убедись, что данные на месте.
4. **Точный PITR «до момента времени»**: описан выше; на стенде автора докат WAL-архива до заданного времени воспроизводился нестабильно (окружение docker) — смотри пометку UNVERIFIED в `03-pitr-restore.sh` и применяй шаги на классическом pg_ctl-стенде/VM.

**Критерий D**: после восстановления из basebackup критичные данные на месте и сервер стартует (is_in_recovery=false после промоушена).

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| `cp: cannot stat …` в логе postgres | archive не пишется (права/диск) | права на /archive, место; pg_wal в этом случае растёт |
| `too many clients already` | приложение превысило лимит | пул приложения < лимит сервера; мониторинг `pg_stat_activity` |
| `dead but not yet removable` | снимок repeatable read жив | закрыть транзакцию; предотвращать idle-in-transaction |
| recovery «застревает»/ошибки restore_command | WAL-архив неполон/права | проверить archive чер-паемость, pg_switch_wal, целостность |
| восстановление «не то состояние» | неверное recovery_target_time | убедись, что конфиг реально прочитан (см. PITR), время с зоной |

## Задания «со звёздочкой»

1. Полноценный PITR на локальном `pg_ctl`-стенде (не docker): basebackup + `archive_mode` + `recovery_target_time` — сверь с пометкой UNVERIFIED.
2. `pg_stat_statements` до/после инцидента: какие запросы «съели» время (пример 04 модуля 07).
3. Алертинг: напиши скрипт-проверку «archive не писался N минут» по содержимому /archive и логам (задел CI модуля 13/урок 04).

## Что дальше

- Урок 04: базовый CI — в этот же конвейер тесты модуля 12 (`cargo test`) и сборка докер-образа шлюза.