# Теория модуля 09: PL/pgSQL, триггеры, аудит, LISTEN/NOTIFY

Конспект-справочник. Развёрнутые примеры с фактическими выводами — в уроках (`lessons/`).

## Функции PL/pgSQL (урок 01)

- `CREATE FUNCTION … RETURNS TABLE(…) … LANGUAGE plpgsql`; переменные, `RETURN QUERY`, циклы.
- `EXCEPTION WHEN unique_violation/check_violation …` — локализованная обработка ошибок (savepoint на команду).
- LANGUAGESql — однострочные выражения; платятся `IMMUTABLE` для индексов.

## Триггеры и аудит (урок 02, кейс 4)

- `BEFORE`/`AFTER`, `FOR EACH ROW`, `TG_OP`, `NEW`/`OLD`; на DELETE `NEW` = NULL → `to_jsonb(COALESCE(NEW, OLD))`.
- Аудит: AFTER-триггер пишет снимок строки в `audit_log`; защита: триггер-запрет UPDATE/DELETE + цепочка sha256 (prev|…), зерно `audit_seed()`.
- Проверка из Rust (пример 05/упражнение 01): пересчёт хэшей; подмена/вырезание рвут цепочку. Важно: полнота (не пропущен ли триггер) ≠ связность (хэши сходятся).

## LISTEN/NOTIFY (урок 03, кейс 6)

- Триггер → `pg_notify(channel, jsonb)`; приложение `LISTEN` + `PgListener::recv()`.
- **Транзакционность**: NOTIFY на COMMIT; на ROLLBACK не доставляется.
- sqlx: `PgListener::connect`, `listen`, `recv()`; payload `$2::jsonb` при вставке в jsonb-колонку.
- Квитирование: события в `device_events` со статус-флагом.

## Роли и RLS (урок 04)

- Роли с минимальными привилегиями (`GRANT SELECT ON …`), разделение «шлюз/аудитор/репортёр».
- RLS: `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY … FOR SELECT TO <role> USING (…)`; владельцы и BYPASSRLS обходят политики.
- Диагностика: `pg_policy`, `pg_get_expr`; не забудь GRANT (политика без привилегии SELECT = permission denied).

## Инструменты модуля

| Файл | Что |
|---|---|
| `00-setup.sql` | аудит-цепочка + триггеры + NOTIFY + device_events + роли/RLS |
| `01-plpgsql.sql` … `04-rls.sql` | примеры уроков 01–04 |
| `05-audit-verify.rs` | проверка цепочки из Rust (эталон) |
| `06-listener.rs` | слушатель NOTIFY → device_events |
| `exercises/ex01_audit` / `ex02_events` | упражнения: цепочка хэшей, разбор payload |
| `labs/01-obhod-triggera.md` | DISABLE TRIGGER / NOTIFY-потери |