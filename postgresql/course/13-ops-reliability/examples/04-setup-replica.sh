#!/usr/bin/env bash
# 04-setup-replica.sh — streaming standby (модуль 13, урок 02).
# Запуск: bash course/13-ops-reliability/examples/04-setup-replica.sh
#
# Создаёт контейнер pg-standby (порт 15435) как hot standby к primary (postgres:15432):
#  1) bootstrap-контейнер выполняет pg_basebackup -R на сеть compose;
#  2) standby-контейнер стартует с этим PGDATA в режиме реплики;
#  3) проверки: pg_is_in_recovery, синхронизация (pg_stat_replication);
#  4) промоушен-демо: SELECT pg_promote() → standby становится primary.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT/infra"

NET="pg-course_default"
STANDBY_DB="course"   # база для проверок на standby (та же, что у primary)
STANDBY_PORT=15435

echo "== 1. Чистим прошлый standby"
docker rm -f pg-standby pg-standby-bootstrap 2>/dev/null || true
docker volume rm pg_standby_data 2>/dev/null || true

echo "== 1.5 Обеспечиваем replication-правило в pg_hba (идемпотентно)"
docker compose exec -T postgres bash -c '
  HBA=$(ls -d /var/lib/postgresql/18/docker/pg_hba.conf)
  grep -q "^host *replication *all *all *scram-sha-256" "$HBA" \
    || echo "host replication all all scram-sha-256" >> "$HBA"
  psql -U course -d course -c "SELECT pg_reload_conf();" >/dev/null
'

echo "== 2. Bootstrap: pg_basebackup -R (recovery.signal + primary_conninfo)"
docker run --rm \
  --name pg-standby-bootstrap \
  --network "$NET" \
  -v pg_standby_data:/var/lib/postgresql \
  --entrypoint bash \
  postgres:18 \
  -c '
    set -e
    mkdir -p /var/lib/postgresql/18
    PGPASSWORD=course pg_basebackup -U course -h postgres \
      -D /var/lib/postgresql/18/docker -Fp -X stream -R -c fast
    # Пароль для primary (pg_basebackup -R не записывает его):
    sed -i "s/primary_conninfo = .*/primary_conninfo = '\''host=postgres port=5432 user=course password=course sslmode=disable'\''/" \
      /var/lib/postgresql/18/docker/postgresql.auto.conf
    ls /var/lib/postgresql/18/docker/PG_VERSION /var/lib/postgresql/18/docker/standby.signal
    echo "bootstrap ok"
  '

echo "== 3. Старт standby (порт $STANDBY_PORT)"
docker run -d --name pg-standby \
  --network "$NET" \
  -v pg_standby_data:/var/lib/postgresql \
  -p "$STANDBY_PORT:5432" \
  postgres:18 >/dev/null

echo "== 4. Ждём готовности и проверяем режим реплики"
for i in $(seq 1 20); do
  if docker exec pg-standby pg_isready -U course >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec pg-standby psql -U course -d "$STANDBY_DB" -tAc "SELECT 'standby? ' || pg_is_in_recovery();"
echo "-- синхронизация (на primary):"
docker compose exec -T postgres psql -U course -d course -c \
  "SELECT client_addr, state, sync_state, flush_lag FROM pg_stat_replication;" 2>&1 | tail -6

echo "== 5. Промоушен (безопасно: standby берёт на себя роль primary)"
docker exec pg-standby psql -U course -d "$STANDBY_DB" -tAc "SELECT pg_promote();"
sleep 2
docker exec pg-standby psql -U course -d "$STANDBY_DB" -tAc "SELECT 'after promote: in_recovery=' || pg_is_in_recovery();"

echo "ok: реплика создана и промоутнута; удалить: docker rm -f pg-standby && docker volume rm pg_standby_data"