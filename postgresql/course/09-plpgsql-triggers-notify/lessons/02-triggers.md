# Урок 02: Триггеры и журнал аудита (кейс 4)

**Модуль / кейс:** 09-plpgsql-triggers-notify / кейс 4 (аудит с защитой от подмены)
**Время:** 2 ч

## Зачем это нужно

Триггер — код, который СУБД выполняет сама при изменении строки: нельзя «забыть» обновить журнал или нарушить инвариант из приложения. Кейс 4 — журнал аудита с **защитой от подмены**: запись снимка при каждом изменении + запрет правки журнала + цепочка хэшей, проверяемая из Rust (упражнение 01).

## Ключевые идеи (сжато)

- `BEFORE` — до изменения (валидация/подмена значений), `AFTER` — после (аудит); `FOR EACH ROW`.
- `TG_OP` — INSERT/UPDATE/DELETE; `NEW`/`OLD` — строка до/после (у DELETE `NEW` = NULL!).
- Аудит = `INSERT в audit_log` с `to_jsonb(NEW)`-снимком.
- Защита: триггер `BEFORE UPDATE OR DELETE` на журнале `RAISE EXCEPTION` + цепочка `sha256(prev | …)`.
- Цепочка: `row_hash` каждого ряда ссылается на предыдущий — подмена/вырезание рвут цепочку.

## Разбор на примере

Схема собрана в `examples/00-setup.sql`. Ключевые куски:

**1. Аудит AFTER ROW (фрагмент):**

```sql
CREATE TRIGGER trg_devices_audit
    AFTER INSERT OR UPDATE OR DELETE ON devices
    FOR EACH ROW EXECUTE FUNCTION devices_audit_trigger();
```

**2. Функция аудита — снимок строки и хэш:**

```sql
rh := audit_chain_hash(prev, 'devices', COALESCE(NEW.id, OLD.id), TG_OP,
                       to_jsonb(COALESCE(NEW, OLD)), now());
INSERT INTO audit_log (entity, entity_id, action, data, ts, prev_hash, row_hash)
VALUES ('devices', COALESCE(NEW.id, OLD.id), TG_OP, to_jsonb(COALESCE(NEW, OLD)), now(), prev, rh);
```

**3. Запрет правки журнала:**

```sql
CREATE FUNCTION deny_audit_rewrite() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log защищён';
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_deny_audit_rewrite BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION deny_audit_rewrite();
```

Фактический вывод (стенд): вставил устройство → строка `INSERT` в журнале; попробовал `UPDATE audit_log …` → `ERROR: audit_log защищён …`. `examples/02-triggers.sql` дополнительно показывает BEFORE-валидацию (нельзя удалить последний массомер линии).

## Как это устроено под капотом

- Триггеры вызываются в транзакции того же оператора: если триггер упадёт — оператор откатится.
- `AFTER … FOR EACH ROW` выполняется после изменения строки; `BEFORE` — до (можно заменить NEW/OLD перед вставкой).
- `COALESCE(NEW, OLD)` обязателен на DELETE: NEW = NULL (без этого `to_jsonb(NEW)` = NULL → нарушение not-null, ловушка, пойманная на стенде).
- Цепочка хэшей детерминированна: `sha256` от конкатенации; проверка из Rust — пересчёт и сравнение (пример 05, упражнение 01).

## Типичные ошибки и грабли

1. **`to_jsonb(NEW)` на DELETE — NULL** (грабля стенда!): на DELETE `NEW` пуст; используй `COALESCE(NEW, OLD)`.
2. **Аудит только INSERT/UPDATE, забыл DELETE** — «удалённых» не видно; в триггере — все три операции.
3. **Защита журнала «привилегиями»** — владелец таблицы обойдёт; триггер-запрет работает для всех.
4. **Хэши без prev** — можно подменить отдельную строку; именно prev-связь рвёт цепочку.
5. **Хэш от `data::text` vs переформатирование в Rust** — бери из БД `data::text, ts::text`, не перекодируй (иначе строка склейки разойдётся).

## Мини-задание

Добавь триггер аудита для `equipment_lines` (INSERT/UPDATE/DELETE → audit_log с entity='lines'), используя ту же функцию (параметризуй через TG_TABLE_NAME или отдельную функцию).

<details>
<summary>Ответ</summary>

```sql
CREATE FUNCTION lines_audit_trigger() RETURNS trigger AS $$
DECLARE prev text; rh text;
BEGIN
    SELECT row_hash INTO prev FROM audit_log ORDER BY id DESC LIMIT 1;
    IF prev IS NULL THEN prev := audit_seed(); END IF;
    rh := audit_chain_hash(prev, 'lines', COALESCE(NEW.id, OLD.id), TG_OP,
                           to_jsonb(COALESCE(NEW, OLD)), now());
    INSERT INTO audit_log (entity, entity_id, action, data, prev_hash, row_hash)
    VALUES ('lines', COALESCE(NEW.id, OLD.id), TG_OP, to_jsonb(COALESCE(NEW, OLD)), prev, rh);
    RETURN NEW;
END $$ LANGUAGE plpgsql;
```
</details>

## Как это спросят на собеседовании

1. «Чем BEFORE отличается от AFTER?» — момент выполнения и возможность менять NEW.
2. «Как защитить от подмены журнал аудита?» — запрет UPDATE/DELETE триггером + цепочка хэшей с проверкой из приложения.
3. «Что такое TG_OP / NEW / OLD?» — операция и старые/новые версии строки.

## Что читать дальше

- Триггеры: <https://www.postgresql.org/docs/18/plpgsql-trigger.html>
- CREATE TRIGGER: <https://www.postgresql.org/docs/18/sql-createtrigger.html>
- Упражнение 01 модуля: проверка цепочки из Rust (`exercises/ex01_audit`)