#!/usr/bin/env bash
# 03-pitr-restore.sh — восстановление на момент времени (PITR).
# Модуль 13, урок 01/лаба 01. Запуск:
#   bash course/13-ops-reliability/examples/03-pitr-restore.sh ["2026-09-25 09:05:00+00"] [basebackup_dir]
#
# Сценарий: берём последний pg_basebackup + WAL-архив и восстанавливаем базу
# до заданного момента (recovery_target_time). Основной контейнер НЕ трогаем:
# восстановление — во временном контейнере на хостовом каталоге.
#
# <!-- UNVERIFIED (частично) -->: восстановление ИЗ СНИМКА (последнее состояние)
# проверено и работает; докат WAL-архива ДО заданного времени на docker-стенде
# автора нестабилен (восстановительный конфиг читался не во всех сценариях,
# права/тома). На классическом pg_ctl/VM-стенде шаги PITR стандартны; применяй
# их там, а здесь считай этот скрипт проверкой «восстановления из снимка» +
# лабораторным заданием на PITR (см. labs/01-incidents.md, Часть D).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT/infra"

DB="course_m06"
ARCHIVE_VOL="pg-course_wal_archive"
TARGET_TIME="${1:-now}"   # момент времени, до которого восстанавливаем
BB_DIR="${2:-$(ls -dt "$ROOT/infra/backups"/basebackup_* | head -1)}"
RESTORE_ROOT="$ROOT/infra/restore_pitr"

echo "== 1. Исходный бэкап: $BB_DIR"
test -d "$BB_DIR" || { echo "нет basebackup: $BB_DIR"; exit 2; }

echo "== 2. Готовим каталог восстановления (копия бэкапа)"
# Возврат владельца (если прошлый прогон оставил postgres-uid на хосте).
docker run --rm -v "$RESTORE_ROOT:/pgdata" postgres:18 \
  chown -R "$(id -u):$(id -g)" /pgdata >/dev/null 2>&1 || true
rm -rf "$RESTORE_ROOT" && mkdir -p "$RESTORE_ROOT"
cp -a "$BB_DIR/." "$RESTORE_ROOT/"

echo "== 3. Пишем restore-настройки (WAL из /archive, цель времени)"
cat >> "$RESTORE_ROOT/postgresql.conf" <<EOF

# --- PITR (модуль 13) ---
restore_command = 'cp /archive/%f %p'
recovery_target_time = '$TARGET_TIME'
EOF

echo "== 4. Восстановление во временном контейнере (порт 15436)"
docker rm -f pg-restore 2>/dev/null || true
docker run --rm -d --name pg-restore \
  -v "$RESTORE_ROOT:/pgdata" \
  -v "$ARCHIVE_VOL:/archive:ro" \
  -p 15436:5432 \
  --entrypoint bash \
  postgres:18 \
  -c "chown -R postgres:postgres /pgdata && chmod 700 /pgdata && exec gosu postgres postgres -D /pgdata" >/dev/null

echo "== 5. Ждём готовности и выводим результат"
for i in $(seq 1 30); do
  if docker exec pg-restore pg_isready -U course >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec pg-restore psql -U course -d "$DB" -c \
  "SELECT 'recovery_finished', pg_is_in_recovery();" 2>/dev/null || true
docker exec pg-restore psql -U course -d "$DB" -c \
  "SELECT count(*) AS alarms_rows FROM alarms;" 2>/dev/null || \
  echo "таблица alarms отсутствует на этом моменте восстановления (ждём более поздний/ранний target)"

echo "== 6. Чистим временный контейнер"
docker rm -f pg-restore >/dev/null
# Возвращаем владельца хосту (postgres-uid → текущий пользователь).
docker run --rm -v "$RESTORE_ROOT:/pgdata" postgres:18 \
  chown -R "$(id -u):$(id -g)" /pgdata >/dev/null 2>&1 || true
echo "ok: PITR-восстановление отображено"