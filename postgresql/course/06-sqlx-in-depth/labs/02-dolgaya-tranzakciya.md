# Лаба 02: Долгая транзакция из Rust — виновник bloat

**Кейс:** 12 (инциденты), 3 (точность)
**Модуль:** 06-sqlx-in-depth
**Время:** 1.5 ч
**Тип:** диагностируй по симптомам (повторение сценария модуля 03, но виновник — Rust-процесс)

## Цель

В модуле 03 виновником bloat был psql-терминал с `REPEATABLE READ`. Теперь то же самое делает **Rust-воркер** — а ты диагност: находишь процесс по `pg_stat_activity`, понимаешь механизм, чинишь (короткие транзакции в коде — модуль 10).

## Схема стенда

- База `course_m06`, миграции применены.
- Два терминала: Т1 — Rust (`cargo run --example 06-long-tx`), Т2 — psql.
- Стол для демо — в `course_m06` (создастся в шаге 1).

## Шаги

1. Т2, таблица-жертва (60 000 строк; «жирная» val, чтобы файл был заметно больше нуля):

   ```sql
   DROP TABLE IF EXISTS vac_demo;
   CREATE TABLE vac_demo (id int PRIMARY KEY, val text);
   INSERT INTO vac_demo SELECT i, repeat('x', 100) FROM generate_series(1, 60000) i;
   ```

2. Т1: запусти воркера на 30 секунд:

   ```bash
   cargo run --example 06-long-tx -- --seconds 30
   ```

   Вывод воркера: «транзакция открыта: вижу N измерений; держу снимок 30 с…».

3. Т2 (пока воркер держит снимок): удали часть строк и попробуй VACUUM:

   ```sql
   DELETE FROM vac_demo WHERE id <= 40000;
   VACUUM VERBOSE vac_demo;
   ```

### Ожидаемый вывод (пока воркер жив)

```
tuples: 0 removed, 60000 remain, 40000 are dead but not yet removable
```

`dead but not yet removable` — удалённые строки видит снимок **repeatable read** Rust-воркера. (Та же ловушка, что в модуле 03: «просто открытая» транзакция read committed не мешает — держит снимок именно RR.)

4. Т2: найди виновника в `pg_stat_activity`:

   ```sql
   SELECT pid, application_name, state, xact_start,
          left(query, 60) AS query
     FROM pg_stat_activity
    WHERE state <> 'idle' AND pid <> pg_backend_pid();
   ```

   Должен увидеть `application_name = long_tx_worker` (мы задали его в коде) с активной транзакцией и запросом воркера.

5. Дождись завершения воркера (30 с) → Т2 снова VACUUM:

   ```sql
   VACUUM VERBOSE vac_demo;
   ```

### Ожидаемый вывод (после закрытия снимка)

```
tuples: 40000 removed, 0 remain, 0 are dead but not yet removable
```

Транзакция закрыта — снимок отпущен, bloat почищен.

## Критерии успеха

- [ ] Воспроизвёл `dead but not yet removable` при живом Rust-воркере
- [ ] Нашёл воркера в `pg_stat_activity` по `application_name` и `xact_start`
- [ ] После закрытия транзакции VACUUM убрал строки (`N removed`)
- [ ] Объяснил механизм: снимок RR → старые версии строк нужны ему → VACUUM не может их удалить
- [ ] Назвал фиксирующий мёр: короткие транзакции решают, `statement_timeout`/`idle_in_transaction_session_timeout` — страховка

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| VACUUM сразу «removed» (нет not yet removable) | воркер ещё не открыл снимок или уже закрыл | запускай воркер ЗАРАНЕЕ; проверь вывод «транзакция открыта» |
| воркера нет в pg_stat_activity | другой pid/база/имя | фильтр по `application_name = 'long_tx_worker'`; проверь подключение к course_m06 |
| `pg_relation_size` не уменьшается после VACUUM | удалены строки в начале файла — страницы переиспользуются | верь `VACUUM VERBOSE` («removed N row versions»), а не размеру файла (ловушка модуля 03) |
| «а в read committed такого нет» | снимок живёт только на оператор | это и есть правильное понимание: держит снимок именно repeatable read |

## Задания «со звёздочкой»

1. **Принудительно убей воркера**: `SELECT pg_terminate_backend(pid)` по найденному pid — VACUUM тут же дочистит (объясни, почему).
2. **Страховка**: `SET idle_in_transaction_session_timeout = 5000;` и повтори — воркер упадёт через 5 с (обработка ошибки в коде — тема модуля 10).
3. **Счётчик ожидания**: в `pg_stat_activity` посмотри `wait_event_type = 'ClientRead'` и `state = 'idle in transaction'` — как выглядят висящие транзакции в проде.

## Что дальше

- Модуль 07: `pg_stat_statements` и мониторинг долгих транзакций по метрикам.
- Модуль 10: границы транзакций в Rust-воркерах + ретраи (idempotency).