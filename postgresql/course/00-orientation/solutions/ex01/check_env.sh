#!/usr/bin/env bash
# Решение упражнения 01 «Диагностика окружения»: полная версия.
# Возвращает 0, если окружение готово, иначе ненулевой код.
# Работает из любого каталога: путь к инфраструктуре ищется сам.
set -u

# Корень репозитория: ex01 -> solutions -> 00-orientation -> course -> репо
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/infra/docker-compose.yml"

fail=0

check_tool() {
    local name="$1"
    if command -v "$name" >/dev/null 2>&1; then
        local ver
        ver=$("$name" --version 2>&1 | head -1)
        echo "$name: $ver"
    else
        echo "error: $name не найден" >&2
        fail=1
    fi
}

check_docker_compose() {
    if docker compose version >/dev/null 2>&1; then
        echo "docker compose: $(docker compose version | head -1)"
    else
        echo "error: docker compose (плагин) не доступен" >&2
        fail=1
    fi
}

check_postgres() {
    if docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U course -d course -tAc "SELECT 1;" 2>/dev/null | grep -q '^1$'; then
        echo "postgres: стенд отвечает (SELECT 1 = ok)"
    else
        echo "error: стенд PostgreSQL не отвечает — подними: cd infra && docker compose up -d" >&2
        fail=1
    fi
}

check_tool rustc
check_tool cargo
check_tool sqlx
check_tool docker
check_docker_compose
check_postgres

if [ "$fail" -ne 0 ]; then
    echo "ОКРУЖЕНИЕ НЕ ГОТОВО: устрани проблемы выше." >&2
    exit 1
fi
echo "Окружение готово к курсу."