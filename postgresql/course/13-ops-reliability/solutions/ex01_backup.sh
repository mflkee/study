#!/usr/bin/env bash
# Решение упражнения 01 (модуль 13): стратегия бэкапа + проверка восстановления.
# Скопируй в exercises/ex01_backup/student.sh после попытки.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT/infra"

DB="course_m06"
RESTORE_DB="course_m06_restore"
BACKUP_DIR="$ROOT/infra/backups"
mkdir -p "$BACKUP_DIR"

echo "== 1. Логический бэкап"
DUMP="$BACKUP_DIR/course_m06_$(date +%Y%m%d_%H%M%S).dump"
docker compose exec -T postgres pg_dump -U course -d "$DB" -Fc > "$DUMP"
ls -la "$DUMP"

echo "== 2. Ротация: оставить последние 7"
ls -1t "$BACKUP_DIR"/course_m06_*.dump | tail -n +8 | xargs -r rm -f
ls -1 "$BACKUP_DIR"/course_m06_*.dump | wc -l | xargs echo "файлов в backups:"

echo "== 3. Проверка восстановления (сверка строк)"
docker compose exec -T postgres psql -U course -d postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB;" >/dev/null
docker compose exec -T postgres psql -U course -d postgres -c "CREATE DATABASE $RESTORE_DB OWNER course;" >/dev/null
docker compose exec -T postgres pg_restore -U course -d "$RESTORE_DB" < "$DUMP" 2>/dev/null || true
SRC="$(docker compose exec -T postgres psql -U course -d "$DB" -tAc 'SELECT count(*) FROM alarms;')"
RST="$(docker compose exec -T postgres psql -U course -d "$RESTORE_DB" -tAc 'SELECT count(*) FROM alarms;')"
echo "alarms: источник=$SRC restore=$RST"
test "$SRC" = "$RST" || { echo "ОШИБКА: восстановление не совпало с источником"; exit 1; }
docker compose exec -T postgres psql -U course -d postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB;" >/dev/null

echo "== 4. RPO/RTO"
echo "RPO: последняя точка дампа (время начала pg_dump); RTO: время pg_restore (секунды)."
echo "ok: бэкап-стратегия работает"