# 01. Структура проекта

## Команды

```bash
cd test-bench/tui

cargo run                 # отладка
cargo run --release       # релиз
cargo build --release     # собрать бинарник target/release/esp32-tui
cargo test                # 35 тестов + 2 ignored (живые: порт, сканер)
cargo test tcp_           # только TCP-интеграционные
cargo clippy --all-targets -- -D warnings   # 0 предупреждений
cargo fmt                 # форматирование (если вносите правки)
```

Зависимости (`Cargo.toml`):

| Крейт | Версия | Зачем |
|-------|--------|-------|
| `ratatui` | 0.30.1 | терминальный UI: виджеты (widgets), layout (раскладка), кадры (frames) |
| `crossterm` | 0.29 | события клавиатуры/мыши (events), цвет, fullscreen |
| `serialport` | 4.10 | COM-порты (последовательные порты, serial ports): открытие/чтение/запись, кроссплатформенно (cross-platform) |
| `anyhow` | 1 | `Result<()>` в `main` |
| `thiserror` | 1 | трейт `Error` для `ModbusError` (сделан руками через `impl`, не derive) |
| `log` | 0.4 | лог-макроинтерфейс (используется редко) |
| `crossbeam-channel` | 0.5 | (задел; в коде — обычный `std::sync::mpsc`) |
| `dirs` | 5 | домашняя папка для файла лога `~/esp32-tui.log` и бэкапов (backup — резервная копия) |
| dev: `tempfile` | 3 | временные файлы (в текущих тестах не нужен) |
| opt: `crc32fast` | — | CRC32, не используется (CRC-16 сделан руками) |

Релиз: `[profile.release] opt-level = 2`.

## Карта модулей

```
test-bench/tui/src/
├── main.rs        — точка входа, главный цикл, хоткеи, мышь
├── app.rs         — struct App: ВСЁ состояние + действия (1705 строк)
├── ui.rs          — отрисовка: draw(), каждая вкладка своей функцией
├── worker.rs      — фоновые потоки: scanner (порты) + poller (данные), события
├── discover.rs    — список портов, hotplug-детект (горячее подключение) ESP32, probe_modbus() (пробинг — проверка порта запросом)
├── master.rs      — Modbus RTU мастер (master — инициатор запросов) по serial: запросы (requests), CRC, таймауты (timeouts)
├── tcp_master.rs  — Modbus TCP мастер (клиент, client): заголовок MBAP (Modbus Application Protocol)
├── tcp_server.rs  — Modbus TCP сервер (server) в роли slave: отдаёт карту эмулятора
├── emulator.rs    — встроенный эмулятор (emulator): устройства (devices), датчики (sensors), slave-роль
├── frames.rs      — сборка/разбор PDU (Protocol Data Unit) и RTU-кадров (frames; RTU — Remote Terminal Unit), ModbusError, float32
├── crc.rs         — CRC-16 Modbus (Cyclic Redundancy Check — циклический избыточный код)
└── firmware.rs    — обёртки над espflash/esptool: board-info (сведения о плате), flash (прошивка), backup (резервная копия)
```

Модульная схема по слоям (от «железа» к «экрану»):

```
┌────────────────────────────────────────────────────────────┐
│ ui.rs + main.rs   — что рисуется и какие клавиши что делают │
├────────────────────────────────────────────────────────────┤
│ app.rs            — struct App: состояние + действия        │
├────────────────────────────────────────────────────────────┤
│ worker.rs         — потоки: scanner, poller; канал событий  │
│ discover.rs       — что за порты сейчас в системе           │
├──────────────┬────────────────────────────┬─────────────────┤
│ RTU side     │   TCP side                 │  Emulator side  │
│ master.rs    │   tcp_master.rs (client)   │  emulator.rs    │
│              │   tcp_server.rs (server)   │  (slave role)   │
├──────────────┴────────────┬────────────────┴─────────────────┤
│ frames.rs + crc.rs        — общий протокольный фундамент     │
└───────────────────────────┴─────────────────────────────────┘
```

Ключевая идея — **один и тот же протокольный фундамент** (`frames.rs`/`crc.rs`)
используют все роли: и RTU-мастер, и TCP-мастер, и TCP-сервер, и эмулятор.
Поэтому байты во вкладке Bus одинаково «настоящие» и на эмуляторе, и на железе.

## Потоки

Работающая программа — это несколько потоков:

| Поток (thread) | Что делает | Код |
|-------|-----------|-----|
| **Главный (UI)** | рисует кадр (frame) раз в ~50 мс, обрабатывает клавиши/мышь, дренит события | `main.rs:42-64` |
| **Scanner** (сканер портов) | каждые ~2 с опрашивает список портов, шлёт `Event::Scan`, раз в сеанс «пробивает» каждый новый ESP-порт | `worker.rs:159-213` |
| **Poller** (поллер — опросчик данных) | каждые 500 мс опрашивает активный источник (эмулятор / serial / TCP) и шлёт `Event::Snapshot` | `worker.rs:246-284` |
| **Задачи** | connect, probe, flash, backup… — каждый раз отдельный поток; `worker.rs:109-119` | `app.rs:1157-1169` |
| **TCP-сервер** | принимает соединения (connections); на каждое — свой поток | `tcp_server.rs:74-100` |

Потоки не общаются напрямую с UI. Все они шлют события (events) в **один канал**
(channel) `events: Sender<Event>` (создан в `Runtime::new`, `worker.rs:138-150`).
Главный поток на каждом шаге цикла дренит приёмник (receiver; try_recv — забрать
всё, что успело прийти, вместо ожидания):

```rust
// main.rs:55-57
while let Ok(ev) = app.runtime.events_rx.try_recv() {
    app.handle_event(ev);
}
```

`Event` (все 7 вариантов — `worker.rs:83-103`):

- `Ticked` — просто тик (tick — квант времени; прогресс анимации, дренаж TCP-трафика);
- `Scan(ScanResult)` — новый список портов;
- `Probe { port, slave }` — ответ на Modbus-пробинг порта (probe — проверка);
- `Snapshot { devices, trace }` — свежие данные с источника (снапшот, snapshot) + кадры шины (bus frames);
- `PollError(String)` — ошибка опроса (poll error);
- `Log(String)` — строка в лог;
- `TaskProgress { text }` — живой прогресс фоновой задачи;
- `TaskDone { label, result }` — задача завершена (успех/ошибка).

## Путь одного кадра через систему (пример)

Возьмём «прочитать 60 input-регистров (Input Registers, IR) у slave 1»:

1. `App::connect_emulator()` (`app.rs:320-329`) ставит `PollSource::Emu` → `set_source` стартует poller.
2. `poll_loop` (`worker.rs:246`) вызывает `poll_devices` → для эмулятора `poll_emulator` → `emu_snapshot(emu, true)` (`worker.rs:298-344`): `emu.tick()` (продвинуть синусы) + собрать `DeviceSnapshot`.
3. Для реального порта это `poll_serial` (`worker.rs:350-401`): `read_input_registers(0, 60)` → `SerialMaster::read_input_registers` (`master.rs:188`) → `transact(FC_READ_INPUT, pdu)` (`master.rs:111`) → `frames::build_request` (собирает `[addr][fc][pdu][crc]`), запись в порт, ожидание, проверка CRC, разбор.
4. По пути каждая транзакция (transaction — обмен «запрос+ответ») кладётся в `trace` (лог шины, `master.rs:94-107`), оттуда её забирает `drain_trace` и уезжает вместе с `Event::Snapshot`.
5. `App::handle_event` (`app.rs:507-512`) записывает `self.snapshot` и вливает трейс во вкладку Bus.
6. `ui::draw` рисует Dashboard (приборная панель) по `app.snapshot`, Bus — по `app.trace`.

Из этого главное: **все данные в UI приходят только через `Event`, а все
действия из UI — только вызовы методов `App`**. Состояние не размазано по
потокам: у каждого жёстко своё место (где — узнаем в [07](07-app.md)).

## Прежде чем читать дальше

Проверьте, что вы найдёте в коде:

- `MAX_FRAME` (максимальная длина кадра, frame) и зачем он; где ответ проверяется на длину;
- имя функции «READ INPUT REGISTERS (0x04)» и где оно формируется;
- какой `Event` приносит данные на Dashboard (приборная панель);
- зачем `poll_port: Arc<Mutex<Option<String>>>` общий у scanner и poller
  (ответ: чтобы сканер не открывал занятый порт и не перезагружал плату —
  видят в `worker.rs:131-133` и `worker.rs:223-226`).