#!/usr/bin/env bash
# Заготовка упражнения 01 (модуль 13): стратегия бэкапа + проверка восстановления.
# Заполни TODO. Образец: examples/01-pgdump-restore.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT/infra"

DB="course_m06"
RESTORE_DB="course_m06_restore"
BACKUP_DIR="$ROOT/infra/backups"
mkdir -p "$BACKUP_DIR"

echo "== 1. Логический бэкап"
DUMP="$BACKUP_DIR/course_m06_$(date +%Y%m%d_%H%M%S).dump"
# TODO: docker compose exec -T postgres pg_dump -U course -d "$DB" -Fc > "$DUMP"

echo "== 2. Ротация: оставить последние 7"
# TODO: ls -1t "$BACKUP_DIR"/course_m06_*.dump | tail -n +8 | xargs -r rm -f
ls -1 "$BACKUP_DIR"/course_m06_*.dump | wc -l | xargs echo "файлов в backups:"

echo "== 3. Проверка восстановления (пробная база)"
# TODO: создание course_m06_restore, pg_restore, сверка count(*)

# Пока TODO не заполнен — честный фейл:
test -f "$DUMP" || { echo "ОШИБКА: дамп не создан (заполни TODO в шаге 1)"; exit 1; }

echo "== 4. RPO/RTO: (напиши в консоли, чему равен твой RPO)"
