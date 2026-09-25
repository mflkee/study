# Упражнения модуля 13

## Заготовки и решения

- `exercises/ex01_backup/student.sh` — «Стратегия бэкапа» (bash): дамп + ротация 7 + сверка restore; решение `solutions/ex01_backup.sh`.
- `exercises/ex02_monitoring/student.sql` — «Топ-5 медленных запросов» (pg_stat_statements); решение `solutions/ex02_monitoring.sql`.

## Как проверять

```bash
# ex01 — сам скрипт выполняет проверку восстановления (exit 0 при совпадении):
cp course/13-ops-reliability/solutions/ex01_backup.sh course/13-ops-reliability/exercises/ex01_backup/student.sh

# ex02 — автопроверка через check-sql (заготовка падает, решение — ok):
infra/check-sql.sh course/13-ops-reliability/exercises/ex02_monitoring/student.sql \
                   course/13-ops-reliability/exercises/ex02_monitoring/asserts.sql \
                   postgres course_m06
infra/check-sql.sh course/13-ops-reliability/solutions/ex02_monitoring.sql \
                   course/13-ops-reliability/exercises/ex02_monitoring/asserts.sql \
                   postgres course_m06
```

## Контроль качества

Скрипты bash: запускаются с `set -euo pipefail`; SQL: тот же формат, что модули 01–08 (транзакционная проверка).