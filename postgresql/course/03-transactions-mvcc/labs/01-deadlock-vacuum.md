# Лаба 01: Deadlock и долгая транзакция — сломай и почини

**Кейсы:** 12 (инциденты), 7 (очередь задач)
**Модуль:** 03-transactions-mvcc
**Время:** 1.5 ч
**Тип:** диагностируй по симптомам

## Цель

Воспроизвести два инцидента эксплуатации и устранить их:

1. **Deadlock при обновлениях** — две транзакции взяли разные строки и ждут друг друга; процесс отваливается, приложение падает, — а «починить» просто (порядок обновления).
2. **Долгая транзакция блокирует VACUUM** — ночью таблица растёт, диски полнятся; сложнейшая диагностика «почему VACUUM не успевает».

## Схема стенда

Два терминала psql к стенду курса (как в примерах модуля). Все «поломки» — в учебных таблицах, каталог курса не трогаем.

## Часть A. Deadlock

### Шаги

1. Подготовь таблицу: `CREATE TABLE dlock (id int PRIMARY KEY, val text); INSERT INTO dlock VALUES (1,'x'),(2,'y');`
2. Терминал 1: `BEGIN; UPDATE dlock SET val='A-1' WHERE id=1;` (не коммить)
3. Терминал 2: `BEGIN; UPDATE dlock SET val='B-2' WHERE id=2;` (не коммить)
4. Терминал 1: `UPDATE dlock SET val='A-2' WHERE id=2;` — **повисло** (ждёт терминал 2)
5. Терминал 2: `UPDATE dlock SET val='B-1' WHERE id=1;` — через ~1 с: **`ERROR: deadlock detected`** (одна транзакция откатилась)
6. Терминал 1: выполнилось; `COMMIT;` — успех.

### Ожидаемый вывод (терминал 2)

```
ERROR:  deadlock detected
DETAIL:  Process ... waits for ShareLock on transaction ...; blocked by process ...
Process ... waits for ShareLock on transaction ...; blocked by process ...
CONTEXT:  while updating tuple (0,2) in relation "dlock"
```

### Починка

Deadlock «лечится» не ретраем вслепую, а устранением причины:

1. **Порядок обновления**: все сессии обновляют строки в одном порядке (по id). Оба процесса идут 1→2, взаимное ожидание невозможно.
2. **Короткие транзакции**: меньше времени на пересечение.
3. **Ретрай в приложении**: поймал `deadlock detected` — повтори транзакцию (в Rust это `retry`-обёртка, модуль 06/10).

## Часть B. Долгая транзакция блокирует VACUUM

### Шаги

1. Таблица 60 000 строк: `CREATE TABLE vac_demo (id int, val text); INSERT INTO vac_demo SELECT i, 'v'||i FROM generate_series(1,60000) i;`
2. Терминал 1 (держит снимок): `BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT count(*) FROM vac_demo;`
3. Терминал 2: `DELETE FROM vac_demo WHERE id <= 40000;` (коммит не нужен — автокоммит)
4. Терминал 2: `VACUUM VERBOSE vac_demo;`

### Ожидаемый вывод (пока терминал 1 держит снимок)

```
tuples: 0 removed, 60000 remain, 40000 are dead but not yet removable
```

Запись `dead but not yet removable` — **симптом**: старые версии удалённых строк не убираются, потому что их «видит» снимок repeatable read в терминале 1. Таблица физически не уменьшается (b4лат), диск растёт.

5. Терминал 1: `ROLLBACK;` (снимок отпущен)
6. Терминал 2: `VACUUM VERBOSE vac_demo;`

### Ожидаемый вывод (после отпускания снимка)

```
tuples: 40000 removed, 6790 remain, 0 are dead but not yet removable
```

Версии удалены — bloat почищен.

### Починка и профилактика

1. Найди виновника: `SELECT pid, xact_start, state, left(query,80) FROM pg_stat_activity WHERE state <> 'idle' ORDER BY xact_start;`
2. Решения в коде: read committed для массовых чтений; repeatable read — только там, где нужен срез; транзакции короткие.
3. Автоматика: `statement_timeout`/`idle_in_transaction_session_timeout` — не дать транзакции висеть часами.
4. Следи: `pg_stat_user_tables.n_dead_tup` и `VACUUM VERBOSE` (статистика n_dead_tup может сбрасываться — верь VERBOSE).

## Критерии успеха

- [ ] Воспроизвёл deadlock, прочитал его сообщение (кто кого ждёт) и объяснил, почему откатилась именно эта транзакция
- [ ] Устранил причину deadlock (порядок обновления) или предложил ретрай
- [ ] Воспроизвёл `dead but not yet removable` в VACUUM VERBOSE при живом снимке repeatable read
- [ ] После закрытия снимка увидел `N removed` и объяснил механизм (MVCC-версии, снимок, VACUUM)
- [ ] Написал запрос поиска самой долгой транзакции по `pg_stat_activity`

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| deadlock «не воспроизводится» | транзакции заканчиваются слишком быстро | добавь `\! sleep 3` между блокировками (hold-окно) |
| VACUUM показывает `0 removed` и без слова not yet removable | снимка нет — VACUUM всё почистил сразу | держи REPEATABLE READ, а не read committed (снимок в RC живёт только в момент оператора) |
| `n_dead_tup` = 0, а таблица большая | статистика сброшена VACUUM | проверяй через VACUUM VERBOSE и размер файла/индексов |
| таблица не уменьшается после VACUUM | удалены строки в начале файла — страницы переиспользуются, файл не усекается | это нормально: пространство в free space map; проверяй «removed N row versions», а не размер файла |

## Задания «со звёздочкой»

1. Добавь третью транзакцию в deadlock (3 участника, цикл A→B→C→A) и посмотри сообщение.
2. Настрой `deadlock_timeout = 200ms` (в сессии: `SET deadlock_timeout = 200;`) и повтори часть A: deadlock поймается быстрее.
3. Найди долгую транзакцию из части B через `pg_stat_activity` и убей её (`pg_terminate_backend(pid)`) — и убедись, что VACUUM тут же очистил строки.