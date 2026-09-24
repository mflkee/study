#!/usr/bin/env bash
# Применяет SQL-файл к стенду PostgreSQL одной командой.
#
# Использование:
#   infra/apply-db.sh path/to/migration.sql
#
# Выходной код: 0 при успехе, ненулевой при ошибке SQL
# (ON_ERROR_STOP=1 — psql останавливается на первой ошибке).
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <file.sql>" >&2
    exit 2
fi

FILE="$1"
if [ ! -f "$FILE" ]; then
    echo "error: file not found: $FILE" >&2
    exit 2
fi

# Абсолютный путь: ниже происходит cd в каталог этого скрипта,
# относительные пути от репозитория к этому моменту уже не действуют.
FILE="$(readlink -f "$FILE")"

cd "$(dirname "$0")"

docker compose exec -T postgres \
    psql -U course -d course -v ON_ERROR_STOP=1 -f - < "$FILE"

echo "ok: applied $FILE"