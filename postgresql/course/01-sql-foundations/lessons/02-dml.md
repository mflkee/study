# Урок 02: INSERT / UPDATE / DELETE и ограничения

**Кейсы:** 1 (каталог оборудования)
**Время:** 1.5 ч

## Зачем это нужно

Справочники и телеметрия не только читаются — они наполняются: приёмная кампания добавляет линии и устройства, интеграция пишет измерения, оператор правит модель. Ошибка здесь стоит дорого: лишний DELETE без WHERE уносит данные. Этот урок — аккуратная запись и понимание, что именно защищает схему от мусора.

## Ключевые идеи (сжато)

- `INSERT ... RETURNING` возвращает созданные строки — удобно для аудита и тестов.
- Уникальность (`UNIQUE`), ссылки (`REFERENCES`) и допустимые значения (`CHECK`) — защита на уровне БД, а не «на всякий случай».
- `UPDATE`/`DELETE` без `WHERE` — массовая операция; ограничение для теста применяется в транзакции.
- `ON ERROR` реальной жизни: нарушение ограничения — это НЕ «поймаю в коде», это нарушение контракта схемы.

## Разбор на примере

**1. INSERT с RETURNING — добавить резервную линию:**

```sql
INSERT INTO equipment_lines (name, description)
VALUES ('ЛИНИЯ-3', 'резервная линия')
RETURNING id, name;
```
```
 id |  name
----+---------
  3 | ЛИНИЯ-3
```

`RETURNING` — единственный способ получить сгенерированный `serial` без второго запроса.

**2. INSERT ... SELECT с защитой от дублей** (сид можно применять повторно):

```sql
INSERT INTO devices (line_id, device_type, tag, model)
SELECT 1, 'density_meter', 'D-01-002', 'MVD-2'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE tag = 'D-01-002');
```

**3. UPDATE с RETURNING — заменить модель:**

```sql
UPDATE devices SET model = 'CMF-300/2' WHERE tag = 'M-01-001'
RETURNING tag, model;
```
```
   tag    |   model
----------+-----------
 M-01-001 | CMF-300/2
```

**4. DELETE по условию** (старые данные вне окна):

```sql
DELETE FROM measurements WHERE ts < '2026-09-01 00:00:00+00';
```

**5. Ограничения в деле** — что произойдёт при нарушении. Дубль тега:

```sql
INSERT INTO devices (line_id, device_type, tag, model)
VALUES (1, 'mass_meter', 'M-01-001', 'x');
```
```
ERROR:  duplicate key value violates unique constraint "devices_tag_key"
DETAIL:  Key (tag)=(M-01-001) already exists.
```

Ссылка на несуществующую линию:

```sql
INSERT INTO devices (line_id, device_type, tag, model) VALUES (999, 'mass_meter', 'X-99', 'x');
```
```
ERROR:  insert or update on table "devices" violates foreign key constraint
        "devices_line_id_fkey"
DETAIL:  Key (line_id)=(999) is not present in table "equipment_lines".
```

## Как это устроено под капотом

- `UNIQUE` создаёт индекс: проверка уникальности — это поиск по индексу перед вставкой.
- `REFERENCES` (FK) проверяет существование родительской строки; `ON DELETE CASCADE` в схеме означает: удалена линия — удалены её устройства и измерения (каскадом), иначе удаление родителя упало бы.
- `CHECK` — произвольное условие, проверяется при INSERT/UPDATE.
- Любое нарушение ограничения откатывает весь текущий стейтмент (в транзакции — стейтмент, не всю транзакцию, если не использован `SAVEPOINT`).

## Типичные ошибки и грабли

1. **`DELETE FROM table` без WHERE** — удаляет всё. В бою всегда сначала `SELECT count(*)` по тому же условию, затем DELETE в транзакции.
2. **INSERT повторно по одному и тому же ТЗ** — падает на UNIQUE. Паттерн «INSERT ... SELECT ... WHERE NOT EXISTS» (см. пример 2) делает перезапуск безопасным.
3. **FK без `ON DELETE`** — попытка удалить линию с устройствами упадёт с FK-ошибкой; реши заранее: каскад, запрет или `SET NULL` — по смыслу данных.
4. **`RETURNING` игнорируется** — новички пишут `INSERT ... RETURNING id` и удивляются, что «ничего не вернулось»: в psql результат печатается, в коде его надо забирать (в sqlx это `fetch_one` — модуль 04).
5. **UPDATE подмножества строк по неверному WHERE** — обновил не то. Всегда сначала SELECT по тому же WHERE.

## Мини-задание

В одной транзакции: добавь линию «ЛИНИЯ-4» и влагомер `W-04-001` на неё, затем удали (`ROLLBACK`, чтобы не менять справочник; или без транзакции — и проверь результаты запросами).

<details>
<summary>Ответ</summary>

```sql
BEGIN;
INSERT INTO equipment_lines (name) VALUES ('ЛИНИЯ-4') RETURNING id;
-- id = 3 (после сида: ЛИНИЯ-1=1, ЛИНИЯ-2=2; не хардкодь id — бери из RETURNING!)
INSERT INTO devices (line_id, device_type, tag, model)
VALUES (3, 'moisture_meter', 'W-04-001', 'MVM-3');
SELECT * FROM devices WHERE line_id = 3;
ROLLBACK;  -- откат: данные учебные, справочник остаётся чистым
```

Без ROLLBACK после проверки на стенде каталог получит ЛИНИЮ-4 — для упражнений это не проблема (схема идемпотентна, сид можно переприменить).
</details>

## Как это спросят на собеседовании

1. «Что вернёт INSERT ... RETURNING и зачем это нужно?»
2. «Чем уникальный индекс отличается от ограничения CHECK?»
3. «Что произойдёт при нарушении внешнего ключа и как выбрать поведение ON DELETE?»

## Что читать дальше

- INSERT: <https://www.postgresql.org/docs/18/sql-insert.html>
- Ограничения (constraints): <https://www.postgresql.org/docs/18/ddl-constraints.html>
- Проверка и откат транзакций: <https://www.postgresql.org/docs/18/tutorial-transactions.html>