# Модуль 13: Эксплуатация (ops-reliability)

**Цели модуля:**
- Бэкапы: `pg_dump`, `pg_basebackup`, WAL-архив, PITR; RPO/RTO.
- Репликация: streaming (готовый standby + промоушен), logical (обзор).
- Мониторинг: `pg_stat_*`, postgres_exporter → Prometheus → Grafana (дашборд).
- Docker и базовый CI (GitHub Actions, тесты модуля 12 на контейнере).
- Лабы-инциденты «сломай и почини»: WAL-диск, долгая транзакция, потеря пула, PITR.

**Пререквизиты:** модули 03–12 (SQL, миграции, тесты, контейнеры). Это SQL/ops-модуль без cargo-проекта.

**Подготовка (один раз):**

```bash
cd infra
docker compose up -d                        # primary с WAL-архивом (archive_mode=on)
docker compose --profile obs up -d          # Prometheus + postgres_exporter + Grafana
docker compose exec -T postgres psql -U course -d course_m06 -f - < ../course/13-ops-reliability/examples/00-setup.sql
# расширение pg_stat_statements уже включено (модуль 07)
# репликация: replication-правило pg_hba добавляет сам скрипт 04-setup-replica.sh (идемпотентно)
```

**План (по `course/PLAN.md`, ~10 ч):**
1. Урок 01: бэкап и WAL (pg_dump/pg_basebackup/PITR) (~2.5 ч)
2. Урок 02: репликация streaming/logical (~2.5 ч)
3. Урок 03: мониторинг (pg_stat_*/exporters/Grafana) (~2.5 ч)
4. Урок 04: Docker и базовый CI (~2 ч)
5. Упражнение 01: стратегия бэкапа скриптами (~45 мин)
6. Упражнение 02: топ-медленных запросов (~45 мин)
7. Лаба 01: инциденты ~2.5 ч

**Чеклист готовности:**
- [ ] `01-pgdump-restore.sh`: дамп + сверка (100=100); `02-pgbasebackup.sh`: снимок есть
- [ ] `03-pitr-restore.sh`: восстановление из снимка работает (PITR-to-time — смотри UNVERIFIED)
- [ ] `04-setup-replica.sh`: standby → streaming → промоушен (in_recovery=false)
- [ ] Grafana 3000: дашборд «PG обзор (курс)» с данными; targets UP
- [ ] Упражнения: ex01 (скрипт с ротацией и сверкой), ex02 (`check-sql.sh` → ok)
- [ ] Лаба: WAL-рост, «too many clients», «dead but not yet removable» — воспроизведены и починены
- [ ] `PROGRESS.md` обновлён

**Следующий модуль:** `14-capstone-historian` (итоговый проект).