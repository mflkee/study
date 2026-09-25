# Вопросы: Rust + БД (урок 03)

**Модуль:** 15-interview-prep · **Время:** 1.5 ч
По профилю «backend на Rust + PostgreSQL» (модули 04–12).

## 1. Почему sqlx, а не ORM (Diesel/SeaORM) для временных рядов?

<details>
<summary>Эталон</summary>

Временные ряды и аудит — это SQL: оконные функции, COPY, UNNEST, триггеры. ORM-слой их прячет или делает костыли. sqlx — «драйвер + типизация + миграции + пул» без потери контроля над SQL (D2 курса).
</details>

## 2. Runtime-запросы `query_as::<_, T>` vs макросы `query_as!` — когда что?

<details>
<summary>Эталон</summary>

Макросы проверяют SQL на компиляции (нужны DATABASE_URL/offline-кэш `.sqlx`); runtime — без этого, удобны для динамических частей (собранный WHERE). Правило курса: runtime по умолчанию, макросы для стабильного ядра (модуль 06; AssertSqlSafe — для осознанной динамики).
</details>

## 3. Маппинг типов: какие ошибки ты на них ловил?

<details>
<summary>Эталон</summary>

`numeric → Decimal` (не f64!); `timestamptz → DateTime<Utc>` (не NaiveDateTime); NULL → `Option<T>`; `int4` vs `int8` (SELECT 1 = int4 — ловил decode-ошибку); jsonb → Value; bind текста в numeric — «expression is of type text» (модули 04/06/14).
</details>

## 4. Как устроен пул соединений и что бывает при исчерпании?

<details>
<summary>Эталон</summary>

Пул = N живых соединений, очередь ожидания + `acquire_timeout`. При исчерпании — `PoolTimedOut`/«too many clients» (перегрузка сервера). Симптомы, тюнинг, отдельный пул для тяжёлых запросов (модули 06/07/13).
</details>

## 5. Границы транзакций: что такое «бизнес-операция» как транзакция?

<details>
<summary>Эталон</summary>

Транзакция покрывает операцию целиком (не одиночный INSERT и не «всю ночь»): write + валидация + связанные записи; ошибка → rollback всего. Типовая ошибка — «обёртка на каждый запрос» или удержание соединения/снимка (модули 06/10).
</details>

## 6. Retry-политика: какие ошибки крутить, какие нет?

<details>
<summary>Эталон</summary>

Крутить транзиентное: serialization failure, таймауты пула, обрывы — с экспоненциальным бэкoff. НЕ крутить: UniqueViolation/CheckViolation (баги данных) — ретрай только замаскирует (модуль 06/10).
</details>

## 7. Очередь задач на PostgreSQL: почему SKIP LOCKED и как без двойной выдачи?

<details>
<summary>Эталон</summary>

`FOR UPDATE SKIP LOCKED` в CTE — захват пачки без ожидания чужих блокировок; отметка `processing` в той же транзакции исключает двойную выдачу; ретраи через `next_run_at`+бэкoff; dead-letter после max_attempts (модули 03/10).
</details>

## 8. Как принимаешь телеметрию быстро и без дублей?

<details>
<summary>Эталон</summary>

Батч/COPY на приём (один round-trip); идемпотентность — UNIQUE (device_id, seq[, ts]) + ON CONFLICT DO NOTHING; буфер при отказе БД (локальный файл) с доставкой после восстановления (модули 07/10/14).
</details>

## 9. NOTIFY/LISTEN из Rust: когда использовать, а когда — таблица событий?

<details>
<summary>Эталон</summary>

LISTEN/NOTIFY — лёгкие push-уведомления (инвалидация кэша, «разбуди воркера»); гарантия «хотя бы один раз», без дубликатов-обещаний. Для надёжности/аудита событий — таблица + квитирование + релей (модуль 09, outbox в 10).
</details>

## 10. Интеграционные тесты с БД: testcontainers vs общий стенд?

<details>
<summary>Эталон</summary>

Testcontainers — контейнер на тест (изоляция, параллельность, CI); общий стенд — конфликты между тестами и «прод-зависимость». В капстоуне/курсе: testcontainers для миграций/аудита/API (модуль 12/14).
</details>

## Самопроверка

- [ ] Объясняю runtime vs macros и зачем AssertSqlSafe
- [ ] Знаю `numeric→Decimal`, `timestamptz→DateTime<Utc>`, NULL→Option
- [ ] Объясняю SKIP LOCKED и ретраи на словах и моделью