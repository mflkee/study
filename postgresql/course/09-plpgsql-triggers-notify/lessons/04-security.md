# Урок 04: Роли, права и Row Level Security

**Модуль / кейс:** 09-plpgsql-triggers-notify / кейс 4 (аудит)
**Время:** 2 ч

## Зачем это нужно

«Все подключения одним суперпользователем» — прямой путь к катастрофе при любой ошибке. Грамотная модель: роли с **минимальными привилегиями**, а для «разных прав на строки одной таблицы» — **Row Level Security** (политики строк).

## Ключевые идеи (сжато)

- Роли: `CREATE ROLE app_user LOGIN PASSWORD …`; `GRANT SELECT ON … TO app_user` — минимально необходимый.
- **RLS**: `ALTER TABLE … ENABLE ROW LEVEL SECURITY` + `CREATE POLICY … FOR SELECT TO <role> USING (<условие>)`.
- Права «писать/читать» разделяются: приложение-шлюз — INSERT/UPDATE, аудитор — SELECT (и ничего больше).
- Проверка: `SET ROLE app_user; SELECT …` — видишь только разрешённые строки; попытка запрещённого действия — `permission denied`.

## Разбор на примере

```bash
docker compose exec -T postgres psql -U course -d course_m06 -f - < course/09-plpgsql-triggers-notify/examples/04-rls.sql
```

**Политики на devices (00-setup/урок 04):**

```sql
GRANT SELECT ON devices TO app_user, auditor;
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
CREATE POLICY devices_select_all ON devices FOR SELECT TO auditor USING (true);
CREATE POLICY devices_app_user ON devices FOR SELECT TO app_user
    USING (device_type <> 'level_meter');      -- app_user не видит уровнемеры
```

Фактический вывод (стенд):

```
SET ROLE auditor; SELECT count(*) …   -- видит все строки
SET ROLE app_user; SELECT count(*) …  -- меньше (уровнемеры скрыты политикой)
SET ROLE app_user; INSERT …           -- ERROR: permission denied (нет INSERT-привилегии)
```

## Как это устроено под капотом

- Без RLS права — на уровне таблицы целиком; RLS добавляет фильтр по строкам в план (как невидимый WHERE).
- Политика применяется к ролям; несколько политик объединяются OR для разрешённых строк.
- Владелец таблицы и/или роли с `BYPASSRLS` обходят RLS — учитывай в проектировании ролей.
- `pg_policy` и `pg_get_expr(polqual, …)` — посмотреть активные политики (пример 04).

## Типичные ошибки и грабли

1. **Все в одной роли суперпользователя** — нет изоляции; минимальные гранты — первый шаг.
2. **Политики без GRANT** — «не работает RLS» часто = забыли GRANT SELECT (поймано на стенде: policy есть, SELECT=permission denied).
3. **`BYPASSRLS`/владелец** — политики не защищают от владельца таблицы; для жёсткой модели — отдельные роли-владельцы.
4. **Разные требования на INSERT и SELECT** — создавай раздельные политики per-command (FOR INSERT / FOR SELECT).
5. **Не смотришь `pg_policy`** — «почему строки не фильтруются?» → проверь активные политики.

## Мини-задание

Создай роль `reporter`, дай ей SELECT на `audit_log` (только чтение, без изменения), и политику: reporter видит записи за последние 7 дней (`ts > now() - interval '7 days'`).

<details>
<summary>Ответ</summary>

```sql
CREATE ROLE reporter LOGIN PASSWORD 'reporter';
GRANT SELECT ON audit_log TO reporter;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_week ON audit_log FOR SELECT TO reporter
    USING (ts > now() - interval '7 days');
```
</details>

## Как это спросят на собеседовании

1. «Что даёт RLS и когда нужен?» — разные права на строки одной таблицы (мульти-тенант, аудитор vs приложение).
2. «Минимальные привилегии — это что?» — GRANT только на необходимые операции; роли по назначению.
3. «Кто обходит RLS?» — владелец таблицы и BYPASSRLS-роли.

## Что читать дальше

- GRANT/REVOKE: <https://www.postgresql.org/docs/18/sql-grant.html>
- RLS: <https://www.postgresql.org/docs/18/ddl-rowsecurity.html>
- Капстоун (модуль 14): роли для шлюза/аудитора/репортера