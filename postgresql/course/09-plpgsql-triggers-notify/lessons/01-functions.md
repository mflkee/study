# Урок 01: Функции PL/pgSQL

**Модуль / кейс:** 09-plpgsql-triggers-notify / кейс 4 (аудит)
**Время:** 2 ч

## Зачем это нужно

Валидация и логика, живущие в БД (триггеры, проверки, генерация значений), пишутся на PL/pgSQL — встроенном процедурном языке PostgreSQL. Это не «второй язык ради языка»: правила, которые работают при любом клиенте и не могут быть обойдены приложением, обязаны жить в БД.

## Ключевые идеи (сжато)

- `CREATE FUNCTION ... RETURNS ... LANGUAGE plpgsql` — тело в `$$ ... $$`.
- Переменные `DECLARE`, присваивание `:=`, циклы `FOR ... IN`, `RETURN QUERY` (возврат таблицы).
- `BEGIN ... EXCEPTION WHEN <код> THEN ...` — перехват ошибок (unique_violation, check_violation, foreign_key_violation).
- Функции бывают `VOLATILE` (по умолчанию, по-разному от вызова к вызову) и `IMMUTABLE`/`STABLE` (для индексов/оптимизаций); `LANGUAGE sql` — простые однострочные.

## Разбор на примере

```bash
docker compose exec -T postgres psql -U course -d course_m06 -f - < course/09-plpgsql-triggers-notify/examples/01-plpgsql.sql
```

**Возврат таблицы** (урок: `RETURNS TABLE`):

```sql
CREATE FUNCTION device_info(p_tag text)
RETURNS TABLE (id int, tag text, device_type text, model text) AS $$
BEGIN
    RETURN QUERY SELECT d.id, d.tag, d.device_type, d.model
                   FROM devices d WHERE d.tag = p_tag;
END $$ LANGUAGE plpgsql;
```

**Обработка ошибок** (урок: «не роняй запрос из-за ожидаемой ошибки»):

```sql
CREATE FUNCTION insert_device_safe(...) RETURNS text AS $$
BEGIN
    INSERT INTO devices (...) VALUES (...);
    RETURN 'ok';
EXCEPTION WHEN unique_violation THEN
    RETURN 'уже существует';
WHEN check_violation THEN
    RETURN 'неверный тип/ограничение';
END $$ LANGUAGE plpgsql;
```

Фактический вывод: `device_info('M-01-001')` возвращает строку; `insert_device_safe` для дубля возвращает «уже существует», для неверного типа — «неверный тип/ограничение».

## Как это устроено под капотом

- PL/pgSQL — интерпретируемый язык поверх SQL: каждая команда выполняется как обычный SQL с подстановкой переменных.
- `EXCEPTION` создаёт неявный блок с savepoint: при ошибке откатывается только команда, вызвавшая её, остальное в блоке сохраняется.
- Коды ошибок — по `SQLSTATE` (23505 = unique, 23514 = check, 23503 = FK) — те же, что sqlx видит как `ErrorKind` (модуль 06).

## Типичные ошибки и грабли

1. **Ловить ВСЁ `WHEN OTHERS THEN`** — маскирует баги; лови конкретику (`unique_violation` и т.п.).
2. **Забытый `RETURN`** — функция вернёт null; компилятор не предупредит.
3. **`RETURNS TABLE` + цикл вместо `RETURN QUERY`** — медленно и читается хуже.
4. **Функция в индексе должна быть IMMUTABLE** — иначе «functions in index expression must be marked IMMUTABLE».

## Мини-задание

Напиши функцию `toggle_model(p_tag text, p_new_model text)`: обновляет model устройства по тегу и возвращает статус 'updated'/'not found'; при отсутствии строки — возвращай 'not found' (не ошибку).

<details>
<summary>Ответ</summary>

```sql
CREATE OR REPLACE FUNCTION toggle_model(p_tag text, p_new_model text) RETURNS text AS $$
DECLARE n int;
BEGIN
    UPDATE devices SET model = p_new_model WHERE tag = p_tag;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN CASE WHEN n > 0 THEN 'updated' ELSE 'not found' END;
END $$ LANGUAGE plpgsql;
```
</details>

## Как это спросят на собеседовании

1. «Зачем PL/pgSQL, если есть приложение?» — инварианты, которые нельзя обойти (триггеры, проверки), обязаны жить в БД.
2. «Как поймать ошибку в функции?» — `EXCEPTION WHEN <SQLSTATE-имя>`.
3. «Чем LANGUAGESql-функция отличается?» — тела правки: sql — однострочный запрос, plpgsql — процедурная логика.

## Что читать дальше

- PL/pgSQL: <https://www.postgresql.org/docs/18/plpgsql.html>
- Обработка ошибок: <https://www.postgresql.org/docs/18/plpgsql-control-structures.html>