# Урок 02: Транзакции из Rust — границы, COMMIT, savepoints

**Модуль / кейс:** 06-sqlx-in-depth / кейс 1 (каталог), 10 (эволюция схемы)
**Время:** 2 ч

## Зачем это нужно

Из Rust транзакция — это НЕ «обёртка над INSERT». Это способ сделать **бизнес-операцию атомарной**: записать несколько строк, и если что-то упало — откатить всё, а не часть. Правильные границы транзакции решают половину проблем concurrency (модуль 03): долго держать нельзя, коротко — тоже (нет атомарности для составной операции).

## Ключевые идеи (сжато)

- `pool.begin()` → `Transaction`; внутри — `execute(&mut *tx)`, в конце `commit()` или `rollback()`.
- `Transaction` — «всё или ничего»: упал запрос — транзакция в «сломанном» состоянии, надо `rollback` (или `commit` вернёт ошибку).
- **Savepoint** — точка отката внутри транзакции (`SAVEPOINT sp` / `ROLLBACK TO sp` / `RELEASE sp`): откатили «хвост», транзакция жива.
- **Границы**: транзакция = бизнес-операция (write + валидация + связанные записи), не «один INSERT» и не «вся ночь».

## Разбор на примере

```bash
cargo run --example 02-tranzakcii
```

Код — `examples/02-tranzakcii.rs` (демо на `equipment_lines`; можно параллельно смотреть в psql: `docker compose exec -T postgres psql -U course -d course_m06`).

**1. COMMIT — строка сохраняется:**

```rust
let mut tx = pool.begin().await?;
sqlx::query("INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T1', 'демо COMMIT')")
    .execute(&mut *tx).await?;
tx.commit().await?;
```

**2. ROLLBACK — строка не сохраняется** (тот же код, но `tx.rollback().await?;`).

**3. Savepoint — откат «хвоста», транзакция жива** (как в модуле 03, но из Rust):

```rust
sqlx::query("SAVEPOINT sp").execute(&mut *tx).await?;
sqlx::query("INSERT INTO ... 'спойлер'").execute(&mut *tx).await?;
sqlx::query("ROLLBACK TO sp").execute(&mut *tx).await?;
sqlx::query("RELEASE sp").execute(&mut *tx).await?;
tx.commit().await?;
```

Фактический вывод:

```
после COMMIT: ЛИНИЯ-T1 существует = true
после ROLLBACK: ЛИНИЯ-T2 существует = false
savepoint: T3 = true, спойлер = false, T3-2 = true
всё-или-ничего: T4-1 существует = false
```

Последняя строка — «всё-или-ничего»: внутри транзакции первая вставка успешна, вторая падает на UNIQUE — и **первая тоже откатывается** (T4-1 не существует).

## Как это устроено под капотом

- `pool.begin()` берёт соединение из пула и отправляет `BEGIN`; `Transaction` владеет соединением до `commit`/`rollback`/`drop`, затем соединение возвращается в пул.
- Пока транзакция открыта, **соединение занято** — другие запросы не могут его использовать (у пула на одно соединение всё встанет).
- `ROLLBACK TO sp` снимает блокировки, взятые после savepoint (модуль 03).
- **Ошибка внутри транзакции** — сервер помечает транзакцию «aborted»: последующие запросы в ней будут падать с `current transaction is aborted` до rollback. Поэтому: лови ошибку → решай (rollback или savepoint) — не «просто continue».

## Типичные ошибки и грабли

1. **Транзакция = «весь запрос цикла»** — держишь соединение и снимок дольше нужного: растёт bloat (модуль 03). Транзакция — только на бизнес-операцию.
2. **`transaction is aborted`** — не обработал ошибку внутри tx, продолжаешь слать запросы. После ошибки — обязательно `rollback`.
3. **Забытый `commit`** — drop транзакции делает `rollback`: «вроде записал, а нет».
4. **Savepoint через несуществующий API** — в sqlx 0.9 вложенный API скрыт; используй явный SQL `SAVEPOINT/ROLLBACK TO/RELEASE` (портативно и наглядно).
5. **Транзакция вокруг одного SELECT** — если только читаешь снимок (repeatable read) — ок и объяснимо; если можно без — бери outside.

## Мини-задание

Напиши функцию `create_line_with_devices(pool, line, devices: &[(&str,&str)])`: в одной транзакции вставляет линию и N устройств; при ошибке — откатывается всё.

<details>
<summary>Ответ</summary>

```rust
let mut tx = pool.begin().await?;
let line_id: i32 = sqlx::query_scalar(
    "INSERT INTO equipment_lines (name) VALUES ($1) RETURNING id")
    .bind(line.name).fetch_one(&mut *tx).await?;
for (device_type, tag) in devices {
    sqlx::query("INSERT INTO devices (line_id, device_type, tag) VALUES ($1,$2,$3)")
        .bind(line_id).bind(device_type).bind(tag)
        .execute(&mut *tx).await?;   // ошибка → ? → tx дроп → ROLLBACK
}
tx.commit().await?;
```
</details>

## Как это спросят на собеседовании

1. «Какая граница транзакции правильная?» — бизнес-операция, не одиночный запрос и не фоновая задача.
2. «Что будет, если забыть commit?» — drop = rollback; данные не сохранятся молча.
3. «Как откатить часть и продолжить транзакцию?» — savepoint (`ROLLBACK TO sp`).
4. «Почему после ошибки «всё падает»?» — PostgreSQL помечает aborted-транзакцию; нужен rollback или savepoint.

## Что читать дальше

- Транзакции в sqlx (документация модуля `Transaction`): <https://docs.rs/sqlx/latest/sqlx/struct.Transaction.html>
- Savepoint в PG: <https://www.postgresql.org/docs/18/sql-savepoint.html>
- Границы транзакций в приложениях: <https://www.postgresql.org/docs/18/tutorial-transactions.html>