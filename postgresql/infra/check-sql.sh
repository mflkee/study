#!/usr/bin/env bash
# Автопроверка SQL-упражнения курса.
# Применяет файл-решение ученика и файл-ассерты в ОДНОЙ транзакции
# (в конце ROLLBACK) — база не засоряется результатами упражнений.
#
# Использование:
#   infra/check-sql.sh <student.sql> <asserts.sql>
#
# Код выхода:
#   0 — решение применилось и все ассерты прошли
#   2 — неверные аргументы / файл не найден
#   1 — ошибка SQL или ассерт не прошёл (RAISE EXCEPTION)
set -euo pipefail

if [ $# -lt 2 ]; then
    echo "usage: $0 <student.sql> <asserts.sql>" >&2
    exit 2
fi

STUDENT="$1"
ASSERTS="$2"

for f in "$STUDENT" "$ASSERTS"; do
    if [ ! -f "$f" ]; then
        echo "error: file not found: $f" >&2
        exit 2
    fi
done

DR="$(cd "$(dirname "$0")" && pwd)"

# BEGIN / ROLLBACK: вся проверка в одной транзакции.
# ON_ERROR_STOP=1: первая ошибка (включая RAISE EXCEPTION в ассертах) рвёт выполнение.
{
    printf 'BEGIN;\n'
    cat "$STUDENT"
    printf '\n'
    cat "$ASSERTS"
    printf '\nROLLBACK;\n'
} | docker compose -f "$DR/docker-compose.yml" exec -T postgres \
        psql -U course -d course -v ON_ERROR_STOP=1 -f -

echo "ok: проверка пройдена"