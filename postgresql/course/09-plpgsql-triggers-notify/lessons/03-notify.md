# Урок 03: LISTEN/NOTIFY — события из триггера в Rust (кейс 6)

**Модуль / кейс:** 09-plpgsql-triggers-notify / кейс 6 (тревоги в реальном времени)
**Время:** 2 ч

## Зачем это нужно

Приложение должно реагировать на изменения БД «в моменте»: тревога по датчику, журнал событий, инвалидация кэша. Периодический опрос таблицы — расточительно; PostgreSQL умеет **push-уведомления**: `pg_notify` из триггера → `LISTEN` в приложении.

## Ключевые идеи (сжато)

- `pg_notify(channel, payload)` — отправить; приложение `LISTEN channel` принимает.
- **Транзакционность**: NOTIFY доставляется только на `COMMIT`; при `ROLLBACK` — не доставляется вовсе (ловушка!).
- Payload — строка (обычно JSON: `jsonb_build_object(...)`).
- sqlx: `PgListener::connect` → `listen(channel)` → `recv() -> PgNotification` (`.channel()`, `.payload()`, `.pid()`).
- Кейс 6: триггер на devices → NOTIFY → Rust-слушатель → запись в `device_events` + квитирование (status).

## Разбор на примере

Триггер (в 00-setup.sql):

```sql
CREATE FUNCTION devices_notify_trigger() RETURNS trigger AS $$
DECLARE dev devices%ROWTYPE; payload jsonb;
BEGIN
    dev := COALESCE(NEW, OLD);
    payload := jsonb_build_object('id', dev.id, 'tag', dev.tag, 'op', TG_OP, 'ts', now());
    PERFORM pg_notify('device_changed', payload::text);
    RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;
```

Rust-слушатель (`examples/06-listener.rs`):

```rust
let mut listener = PgListener::connect(&url).await?;
listener.listen("device_changed").await?;
loop {
    let notif = listener.recv().await?;                 // ждёт уведомление
    let ev: DeviceEvent = serde_json::from_str(notif.payload())?;
    sqlx::query("INSERT INTO device_events (channel, payload, status) VALUES ($1, $2::jsonb, 'new')")
        .bind(notif.channel()).bind(notif.payload()).execute(&pool).await?;
}
```

Сквозной прогон (два «терминала»):

```
[слушатель] слушаю канал device_changed …
[psql]      INSERT INTO devices ... (триггер шлёт NOTIFY после COMMIT)
[слушатель] событие #1: op=INSERT tag=W-09-live id=20 ...
[psql]      DELETE ... 
[слушатель] событие #2: op=DELETE tag=W-09-live id=20 ...
[psql]      SELECT ... FROM device_events → 2 строки (INSERT, DELETE)
```

## Как это устроено под капотом

- NOTIFY — сообщение в общем журнале (не в таблице): доставка гарантирована, но «не упорядочена строго» между каналами/сессиями.
- Наслушивание идёт через тот же процесс соединения: `PgListener` держит отдельное соединение в режиме приёма.
- В транзакции NOTIFY «копится» и рассылается на COMMIT — поэтому слушатель видит только закоммиченные события (консистентно с данными).
- Payload передаётся текстом; для структуры — JSON (jsonb_build_object в триггере, serde_json в Rust).

## Типичные ошибки и грабли

1. **NOTIFY в незакоммиченной транзакции** — слушатель «молчит» до COMMIT; после ROLLBACK не придёт вовсе (урок 03 — демонстрация в `03-notify.sql`).
2. **Payload не JSON** — усложняет разбор; всегда строй JSON в триггере.
3. **Подключение к неверному каналу** — «слушаю `device_changed`», а триггер шлёт в другой; сверяйся с `pg_listening_channels()`.
4. **Слушатель без обработки ошибки** — разрыв соединения = потеря событий; reconnect (модуль 10/13).
5. **jsonb-колонка, а bind текст** — `$2::jsonb` (поймано на стенде).

## Мини-задание

Добавь второй канал `quality_alert` и триггер «плохой quality измерений» (в `telemetry_raw` или своей таблице); в Rust слушай оба канала (`listen` дважды).

<details>
<summary>Ответ</summary>

`listener.listen("device_changed"); listener.listen("quality_alert");` — оба события приходят в один `recv()`; различай по `notif.channel()`. Триггер на своей таблице: `PERFORM pg_notify('quality_alert', json_build_object(...)::text);`
</details>

## Как это спросят на собеседовании

1. «Чем NOTIFY отличается от поллинга?» — push без нагрузки на опрос; но не гарантирует порядок/дублирование — для «хотя бы одного раза».
2. «Когда NOTIFY НЕ дойдёт?» — ROLLBACK, потеря соединения, не тот канал.
3. «Как сделать надёжную очередь поверх NOTIFY?» — события в таблицу + квитирование (модуль 10).

## Что читать дальше

- LISTEN/NOTIFY: <https://www.postgresql.org/docs/18/sql-listen.html>, <https://www.postgresql.org/docs/18/sql-notify.html>
- sqlx PgListener: <https://docs.rs/sqlx/latest/sqlx/postgres/struct.PgListener.html>
- Упражнение 02 и лаба «Обход триггера / отключённый NOTIFY»