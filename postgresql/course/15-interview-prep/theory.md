# Теория модуля 15: карта вопросов

Конспект-карта: по каждому разделу — ключевые темы для закрытия. Развёрнутые вопрос-ответы — в уроках.

## SQL и проектирование (урок 01)
- Диапазоны (BETWEEN vs полуоткрытые), типы (numeric/float8), JOIN/GROUP/HAVING, оконные и `DISTINCT ON`, CTE/подзапросы.
- Справочники «устройство—линия», тип-ряд (timestamptz, партиции, идемпотентность).
- Индексы B-tree/BRIN/GIN — по EXPLAIN, а не «на глаз».

## Устройство PostgreSQL (урок 02)
- MVCC, снимок, RC vs RR (VACUUM!), deadlock, WAL → репликация → PITR.
- Партиционирование (+UNIQUE с ключом партиции), serializable, RLS, pg_stat-метрики.

## Rust + БД (урок 03)
- sqlx vs ORM; runtime vs macros; AssertSqlSafe; маппинг типов (numeric/Decimal, timestamptz/Utc, Option).
- Пул и границы транзакций; retry-политика; SKIP LOCKED-очередь; батч/COPY + идемпотентность; NOTIFY/outbox; testcontainers.

## Инциденты и проектирование (урок 04)
- Кейсы: «ночью всё медленно» (RR → VACUUM), «too many clients», «ретеншн съел данные», «PITR в docker».
- Задача «архив телеметрии на 5 лет» — проект с обоснованиями.

## Инструменты

| Файл | Что |
|---|---|
| `lessons/01…04-*.md` | вопросники с эталонными ответами в `<details>` |
| `exercises/interview.md` | «собеседование вслух» с чеклистом |
| `labs/01-incident-case.md` | разбор аварии по журналу и метрикам |