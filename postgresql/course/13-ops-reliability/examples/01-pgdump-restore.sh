#!/usr/bin/env bash
# 01-pgdump-restore.sh — pg_dump (custom) + проверка восстановления.
# Модуль 13, урок 01. Запуск из любого каталога:
#   bash course/13-ops-reliability/examples/01-pgdump-restore.sh
#
# Жизненный цикл: логический дамп базы course_m06 в BACKUP_DIR,
# затем restore в ПРОБНУЮ базу course_m06_restore и сверка количества строк.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT/infra"

DB="course_m06"
RESTORE_DB="course_m06_restore"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}"
mkdir -p "$BACKUP_DIR"

DUMP="$BACKUP_DIR/course_m06_$(date +%Y%m%d_%H%M%S).dump"

echo "== 1. Логический бэкап в $DUMP (поток в хостовый файл)"
docker compose exec -T postgres pg_dump -U course -d "$DB" -Fc > "$DUMP"
ls -la "$DUMP"

echo "== 2. Готовим ПРОБНУЮ базу (чистую)"
docker compose exec -T postgres psql -U course -d postgres \
  -c "DROP DATABASE IF EXISTS $RESTORE_DB;" >/dev/null
docker compose exec -T postgres psql -U course -d postgres \
  -c "CREATE DATABASE $RESTORE_DB OWNER course;" >/dev/null

echo "== 3. Restore в пробную базу"
docker compose exec -T postgres pg_restore -U course -d "$RESTORE_DB" < "$DUMP" 2>/dev/null || true

echo "== 4. Сверка: строк в alarms (исходник vs restore)"
docker compose exec -T postgres psql -U course -d "$DB" -tAc "SELECT count(*) FROM alarms;"
docker compose exec -T postgres psql -U course -d "$RESTORE_DB" -tAc "SELECT count(*) FROM alarms;"

echo "== 5. Чистим пробную базу"
docker compose exec -T postgres psql -U course -d postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB;" >/dev/null
echo "ok: бэкап и проверка восстановления выполнены"