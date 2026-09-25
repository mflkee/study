#!/usr/bin/env bash
# 02-pgbasebackup.sh — физический полный бэкап (pg_basebackup).
# Модуль 13, урок 01/лаба. Запуск:
#   bash course/13-ops-reliability/examples/02-pgbasebackup.sh
#
# Снимок файлов данных + WAL: основа для реплики и PITR (03).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT/infra"

BACKUP_DIR="${BACKUP_DIR:-$ROOT/infra/backups}"
TS="$(date +%Y%m%d_%H%M%S)"
INNER="/tmp/basebackup_$TS"

echo "== 1. pg_basebackup внутри контейнера (в $INNER)"
PGPASSWORD=course docker compose exec -T postgres \
  pg_basebackup -U course -h localhost -D "$INNER" -Fp -X stream

echo "== 2. Копируем на хост в $BACKUP_DIR/basebackup_$TS"
docker cp "pg-course-postgres:$INNER" "$BACKUP_DIR/basebackup_$TS" >/dev/null
docker compose exec -T postgres rm -rf "$INNER"

echo "== 3. Контроль: размер и файл recovery.signal должен отсутствовать"
du -sh "$BACKUP_DIR/basebackup_$TS"
ls "$BACKUP_DIR/basebackup_$TS" | head -3
ls "$BACKUP_DIR/basebackup_$TS/recovery.signal" 2>/dev/null && echo "ВНИМАНИЕ: recovery.signal (standby бэкап)" || echo "ok: не standby"