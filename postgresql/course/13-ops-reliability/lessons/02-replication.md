# Урок 02: Репликация — streaming и logical

**Модуль / кейс:** 13-ops-reliability / кейс 12 (инциденты)
**Время:** 2.5 ч

## Зачем это нужно

Один сервер — точка отказа. Реплика даёт «горячий» резерв: второй экземпляр применяет WAL с primary и (при сбое) промоутится в primary. Streaming — для резерва/чтения; logical — для миграций и раздельных систем.

## Ключевые идеи (сжато)

- **Streaming**: standby читает WAL у primary по сети (walreceiver); `pg_basebackup -R` создаёт `recovery.signal` + `primary_conninfo`.
- **Проверка**: `pg_is_in_recovery` = t; `pg_stat_replication` (state=streaming, sync_state).
- **Промоушен**: `SELECT pg_promote()` — standby становится primary (без потери применённого WAL).
- **Logical**: подписка (`pg_create_subscription`) переносит ИЗМЕНЕНИЯ через WAL-логику — для данных между независимыми БД (не резерв).
- Лаба D + скрипт `examples/04-setup-replica.sh` — реальный streaming-standby на порту 15435.

## Разбор на примере

```bash
bash course/13-ops-reliability/examples/04-setup-replica.sh
```

Фактический вывод (стенд):

```
standby? true
 client_addr |   state   | sync_state |    flush_lag
-------------+-----------+------------+-----------------
 172.30.0.4  | streaming | async      | 00:00:00.000071
t                      # pg_promote()
после promote: in_recovery=false
```

Ключевые шаги скрипта: bootstrap-контейнер делает `pg_basebackup -R` (генерит `recovery.signal` + `primary_conninfo`), затем standby стартует и стримит; промоушен — одна команда.

## Как это устроено под капотом

- Standby запускается с `standby.signal` (PG12+: файл `standby.signal` → режим горячего стэнда) + `primary_conninfo` в `postgresql.auto.conf`.
- walreceiver тянет сегменты WAL у primary и применяет; чтения на standby отдаются (hot standby), записи — нельзя до промоушена.
- `pg_promote()` завершает recovery, создаёт timeline-форк и стартует как primary.
- Logical требует `wal_level=logical` и publisher/subscriber; в курсе — обзор + команды (глубокая практика — калапочный scope 14 опционально).

## Типичные ошибки и грабли

1. **pg_hba блокирует репликацию** — нужна `replication`-запись (в курсе добавлена: `host replication all all scram-sha-256`).
2. **Пароль в `primary_conninfo`** — pg_basebackup -R его не пишет; добавь вручную (`password=…`).
3. **superuser vs POSTGRES_USER** — в docker superuser называется `course`; брать «postgres» для базы — ошибка auth.
4. **Промоушен без подготовки** — транзакции, «зависшие» на standby, падают; кортежи чистятся — у каждого промоушена свой план отказа (модуль 13/14).
5. **Забыл убрать recovery_target_time** из конфига — standby «не достиг цели» и падает при промоуте (поймано на стенде!).

## Мини-задание

Заведи реплику, прогони on-primary INSERT, посмотри в `pg_stat_replication` задержку — и промоутни; проверь, что запись появилась на бывшем standby.

<details>
<summary>Ответ</summary>

`pg_stat_replication.flush_lag` — задержка; после промоута данные на месте (streaming применяет WAL почти мгновенно на локальной сети).
</details>

## Как это спросят на собеседовании

1. «Чем streaming отличается от logical?» — физический WAL-копии (полный резерв) vs логические изменения (данные между системами).
2. «Как промоутнуть standby?» — `SELECT pg_promote()`/`pg_ctl promote`.
3. «Что видно в pg_stat_replication?» — состояние реплики, задержка (write/flush/replay lag).

## Что читать дальше

- Warm standby/streaming: <https://www.postgresql.org/docs/18/warm-standby.html>
- Logical replication: <https://www.postgresql.org/docs/18/logical-replication.html>
- pg_promote: <https://www.postgresql.org/docs/18/functions-admin.html>