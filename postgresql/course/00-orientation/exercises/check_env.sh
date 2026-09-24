#!/usr/bin/env bash
# Диагностика окружения курса «Rust + PostgreSQL для СИКН».
# TODO: реализуй функции check_* ниже. Каркас менять не нужно.
# Пока функция не реализована, она «валит» проверку (fail=1).
# Твоя задача: вписать реальные проверки вместо TODO-сообщений.
set -u

fail=0

# Проверяет, что утилита установлена, и печатает её версию.
# Вызови check_tool для rustc, cargo, sqlx, docker.
check_tool() {
    local name="$1"
    echo "TODO: реализуй проверку '$name' (command -v, вывод версии)" >&2
    fail=1
}

# Проверяет, что работает `docker compose version`.
check_docker_compose() {
    echo "TODO: реализуй проверку 'docker compose version'" >&2
    fail=1
}

# Проверяет, что стенд PostgreSQL отвечает:
#   docker compose exec -T postgres psql -U course -d course -c "SELECT 1;"
# Подсказка: сначала перейди в каталог infra/ репозитория курса
# (или используй docker compose -f <путь>/infra/docker-compose.yml).
check_postgres() {
    echo "TODO: реализуй проверку стенда PostgreSQL" >&2
    fail=1
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