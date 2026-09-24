# Модуль 06: sqlx — пул, транзакции, миграции (sqlx-in-depth)

**Цели модуля:**
- Настроить пул соединений: размер, таймауты, retry транзиентных ошибок.
- Писать транзакции из Rust с правильными границами; savepoints.
- Понимать разницу runtime-запросов и макросов `query_as!`, работать с offline-кэшем `.sqlx/`.
- Ввести миграции `sqlx migrate` и классификацию ошибок БД (thiserror).
- Прокачать кейс **«Эволюция схемы»** (кейс 10): новый тип датчика = миграция + обратная совместимость Rust-кода.

**Пререквизиты:** модули 04–05 (Rust + sqlx-базово + async), стенд PostgreSQL. Миграции и упражнение работают с базой `course_m06` (отдельной от каталога 01–04).

**Подготовка (один раз):**

```bash
# создать базу модуля (идемпотентно)
printf "SELECT 'CREATE DATABASE course_m06 OWNER course' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='course_m06')\\gexec\n" | docker compose exec -T postgres psql -U course -d postgres

# применить миграции 0001–0002 (CLI-вариант; встроенный — пример 03)
DATABASE_URL="postgres://course:course@localhost:15432/course_m06?sslmode=disable" \
  cargo sqlx migrate run --source migrations
```

**План (по `course/PLAN.md`, ~10 ч):**
1. Урок 01: пул — конфигурация, размер, таймауты, retry (~2 ч)
2. Урок 02: транзакции из Rust — границы, savepoints (~2 ч)
3. Урок 03: макросы `query_as!` и offline-кэш (~2 ч)
4. Урок 04: миграции и обработка ошибок БД (~1.5 ч)
5. Упражнение 01: «Эволюция схемы — новый тип датчика» (~2.5 ч)
6. Лабы: «Забытый индекс и N+1», «Долгая транзакция из Rust» (~1.5 ч + ~1.5 ч)

**Чеклист готовности:**
- [ ] База `course_m06` создана, миграции 0001–0002 применены
- [ ] `cargo run --example 01-pool`: 20 задач на пуле из 5, таймаут сработал
- [ ] `cargo run --example 02-tranzakcii`: COMMIT/ROLLBACK/savepoint понятны по выводу
- [ ] `cargo run --example 03-migracii`: миграции применены и перечислены
- [ ] `cargo run --example 05-makrosy`: компилируется без `DATABASE_URL` (offline-кэш)
- [ ] Упражнение 01: `cargo test ex01_` зелёный (написана миграция 0003)
- [ ] Лаба 01: увидел `Seq Scan` → `Index Scan`, объяснил N+1
- [ ] Лаба 02: `dead but not yet removable` с воркером, виновник найден в `pg_stat_activity`
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний; `PROGRESS.md` обновлён

**Следующий модуль:** `07-performance` (EXPLAIN подробно, batch vs COPY, нагрузочный стенд).