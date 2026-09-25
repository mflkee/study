# Вопросы: устройство PostgreSQL (урок 02)

**Модуль:** 15-interview-prep · **Время:** 1.5 ч
Вопросы «на глубину» (модули 03, 06–09, 13).

## 1. MVCC и почему читатели не блокируют писателей?

<details>
<summary>Эталон</summary>

Каждая строка хранит версии; транзакция видит «снимок» — набор версий, видимых на момент снимка. Читатель читает старую версию (не блокирует), писатель создаёт новую — конфликт только на финальном обновлении той же строки. Хвост версий убирает VACUUM.
</details>

## 2. Read Committed vs Repeatable Read — ключевая разница на практике?

<details>
<summary>Эталон</summary>

RC — снимок на каждый оператор; RR — на всю транзакцию (не меняется между SELECT). Практическое следствие: долгая RR-транзакция держит снимок и блокирует очистку VACUUM (`dead but not yet removable` — модуль 03/06). «Просто открытая» RC-транзакция VACUUM не мешает.
</details>

## 3. Deadlock: когда возникает и как его лечат?

<details>
<summary>Эталон</summary>

Две транзакции ждут ресурсы друг друга (цикл). PostgreSQL через `deadlock_timeout` (1 с) откатывает одну с `ERROR: deadlock detected`. Лечение: единый порядок обновления строк, короткие транзакции, ретрай в приложении (модуль 03/10).
</details>

## 4. За что отвечает VACUUM и почему он «не успевает»?

<details>
<summary>Эталон</summary>

Убирает мёртвые версии (bloat), обновляет статистику и карты видимости. Не успевает при: долгих транзакциях (снимок держит версии), слишком частых UPDATE/DELETE, малом autovacuum-окне. Диагностика: `n_dead_tup` (может врать — верь `VACUUM VERBOSE`), рост pg_wal/файла.
</details>

## 5. WAL: зачем и как связан с репликацией и PITR?

<details>
<summary>Эталон</summary>

Write-Ahead Log — журнал изменений до записи данных (durability). Те же записи читает реплика (streaming) и восстановление из архива (PITR). `wal_level`, `archive_mode`, `archive_command` — настройки рубежей (модуль 13).
</details>

## 6. Партиционирование: что оно даёт и какой подводный камень с UNIQUE?

<details>
<summary>Эталон</summary>

Делит таблицу по диапазону (pruning = читаем только нужное), ретеншн = `DROP PARTITION`. Подводный камень: в UNIQUE партиционированной таблицы обязан входить ключ партиции (0A000) — идемпотентность приходится строить с этим в виду (ADR капстоуна, модуль 14).
</details>

## 7. STRICT-стратегии сериализации: serializable и «could not serialize»?

<details>
<summary>Эталон</summary>

READ COMMITTED/REPEATABLE READ допускают аномалии (lost update, skew); SERIALIZABLE добавляет отслеживание конфликтов и может откатить транзакцию с «could not serialize access due to concurrent update» — приложение обязано ретраить (модуль 03/06/10).
</details>

## 8. Назови три `pg_stat_*`-метрики, сигналящие об инциденте.

<details>
<summary>Эталон</summary>

1) `pg_stat_activity.xact_start` (долгая транзакция → bloat); 2) `pg_stat_user_tables.n_dead_tup` (не подчищенный VACUUM); 3) `pg_stat_replication.*_lag` (отставание реплики). Плюс подключения к лимиту (`pg_stat_activity` count vs `max_connections`) (модуль 13).
</details>

## 9. Роль и строки: как ограничить пользователя «своими» строками?

<details>
<summary>Эталон</summary>

Row Level Security: `ENABLE ROW LEVEL SECURITY` + политики `FOR SELECT TO <role> USING (условие)`. Помни: politics не защищают владельца/BYPASSRLS; не забудь и про GRANT (политика без привилегии = permission denied) (модуль 09).
</details>

## 10. Инцидент «архив сломался, pg_wal вырос» — симптомы и план.

<details>
<summary>Эталон</summary>

Симптомы: `cp: cannot stat` в логе, рост pg_wal при активной нагрузке, в крайней феле PANIC от полного диска. План: починить archive_command/права, `pg_switch_wal()`, следить за освобождением pg_wal; поднять alerting (модуль 13, лаба A).
</details>

## Самопроверка

- [ ] Объясняю MVCC и отличия RC/RR с примером «VACUUM не удаляет»
- [ ] Знаю WAL → репликация → PITR цепочку
- [ ] Знаю, почему партиции требуют ts в UNIQUE