# 05. Потоки и события: `worker.rs` + `discover.rs`

## `worker.rs` — фоновые потоки (background threads) (626 строк)

Модуль управляет двумя фоновыми потоками (сканер портов — port scanner и
поллер данных — data poller), определяет все события и снапшоты (snapshots —
срезы данных), умеет запускать разовые фоновые задачи.

### Типы данных

**`PollSource`** (`worker.rs:18-43`) — активный источник данных, в точности
один в каждый момент:

```rust
pub enum PollSource {
    Emu(SharedEmulator),
    Serial { port: String, baud: u32, master: Arc<Mutex<SerialMaster>> },
    Tcp    { host: String, port: u16, master: Arc<Mutex<TcpMaster>> },
}
```

`key()` даёт читаемую строку (`"EMU"`, имя порта, `tcp://host:port`) — она
становится `current_source` (видно в статус-баре — status bar; используется в
логике `disconnect_serial`/`disconnect_tcp`).

**`Channel`** и **`DeviceSnapshot`** (`worker.rs:46-63`) — снимок состояния
одного устройства для Dashboard:

```rust
pub struct Channel       { pub label: String, pub reg: u16, pub value: f32, pub unit: String }
pub struct DeviceSnapshot {
    pub slave_id: u8, pub name: String, pub source: String,
    pub channels: Vec<Channel>, pub coils: Vec<bool>, pub holding: Vec<u16>,
}
```

**`TaskPayload`** (`worker.rs:66-80`) — результат фоновой задачи: `Lines`,
`Master { port, master }`, `TcpMaster { host, port, master }`. Fw-операции
превращают вывод esptool в `Lines`.

### `Event` — единственный язык общения c UI (`worker.rs:83-103`)

См. оглавление в [01](01-structure.md#путь-одного-кадра-через-систему). Семь
вариантов: `Ticked`, `Scan`, `Probe`, `Snapshot`, `PollError`, `Log`,
`TaskProgress`, `TaskDone`. Все они уходят в один канал
`events: Sender<Event>`.

### `spawn_task` — разовая фоновая задача (`worker.rs:109-119`)

```rust
pub fn spawn_task<F>(events: Sender<Event>, label: impl Into<String>, job: F)
where F: FnOnce(&Sender<Event>) -> Result<TaskPayload, String> + Send + 'static
```

- `thread::spawn` — отдельный поток на задачу.
- Задача получает `&Sender<Event>` — может слать `TaskProgress` и `Log` вживую.
- Финал — всегда `Event::TaskDone { label, result }`. `App::handle_event`
  (`app.rs:526-588`) его разбирает.

### `Runtime` — хозяин потоков (`worker.rs:122-243`)

```rust
pub struct Runtime {
    pub events: Sender<Event>,
    pub events_rx: Receiver<Event>,
    scan_handle:  Option<JoinHandle<()>>,
    poll_handle:  Option<JoinHandle<()>>,
    poll_stop:    Option<Sender<()>>,
    pub current_source: Option<String>,
    poll_port:    Arc<Mutex<Option<String>>>,
    pub poll_interval_ms: u64,   // 500
}
```

Ключевой трюк — `poll_port`. Это «какой физический порт занят поллером».
Сканер и поллер живут в разных потоках; если оба откроют один и тот же порт,
DTR/RTS усканера сотрут поллинг (и перезагрузят плату). Поэтому:

- поллер при `set_source(Serial..)` пишет имя порта в `poll_port`
  (`worker.rs:223-226`);
- сканер перед пробой проверяет `poll_port` и пропускает занятый (`worker.rs:181-183`);
- `stop_poller` снова ставит `None` (`worker.rs:240`).

**`start_scanner`** (`worker.rs:153-214`):

1. Каждые ~2 с: `discover::list_ports()` → `Event::Scan`.
2. Для каждого нового ESP-порта, который ещё не пробовали (`tried`), и не
   занят поллером, и не JTAG — в отдельном потоке `discover::probe_modbus`
   (`worker.rs:195-201`). Проба делает `ровно один раз за сеанс`: она открывает
   порт → дёргает DTR/RTS → перезагружает плату.
3. JTAG (Joint Test Action Group — отладочный интерфейс)/USB-debug unit
   пропускается (`worker.rs:185-191`): это не Modbus-таргет.
4. Вместо `sleep(2с)` — цикл `20 × (sleep 100мс + Ticked)` (`worker.rs:206-210`).
   Так сканер «спит», но продолжает капать тиками (tick — квант времени) и
   мгновенно останавливается, когда UI закрыл канал (ошибка `send` → `return`).

**`set_source`** (`worker.rs:217-231`): останавливает старый поллер, создаёт
новый `poll_stop`/канал, обновляет `poll_port`, стартует `poll_loop`.

**`stop_poller`** (`worker.rs:233-242`): шлёт `stop`, джойнит поток (join —
дождаться завершения), снимает `poll_port`.

### `poll_loop` — главный цикл опроса (`worker.rs:246-284`)

```
loop:
  1. try_recv stop → выйти
  2. poll_devices(&source) → Ok → Event::Snapshot
     Err  → Event::PollError + sleep(interval) (не зацикливаться)
  3. пауза 10 × sleep(interval/10), на каждом шаге снова проверка stop
```

Обратите внимание: пауза разбита на 10 кусков, чтобы можно было выйти из
цикла почти мгновенно (не ждать все 500 мс).

### `poll_devices` — диспетчер источников (`worker.rs:286-295`)

Эмулятор → `poll_emulator` → `emu_snapshot(emu, true)` (с `tick`!); serial →
`poll_serial`; tcp → `poll_tcp`.

### `emu_snapshot` — срез эмулятора в DeviceSnapshot (`worker.rs:298-344`)

Важные детали:

- `if tick { emu.tick(); }` — продвинуть синусы перед снятием среза.
- Единицы измерения датчиков мапятся (map) на `DataType` (`worker.rs:309-316`).
  `Flow` уходит как `%`, есть отдельный `Test` (без единицы).
- Фиксированные размеры среза: 8 катушек, 32 holding-регистра
  (`worker.rs:324-333`). Это «окно» для дашборда (dashboard), не весь словарь.
- `drop(emu)` перед построением `out` — чтобы не держать `Mutex` (быстрее и
  безопаснее), затем строит снаружи.

### `poll_serial` — чтение реального устройства (`worker.rs:350-401`)

Жёсткая карта ESP32-прошивки:

- `input 0..60` — 30 float32: 15 температур `T-00..T-14` (°C) + 15 давлений
  `P-00..P-14` (kPa) (`worker.rs:377-381`);
- `coils 0..2` — 2 насоса;
- `holding 0..16` — конфигурация.

`input.as_chunks::<2>()` + `frames::float_from_regs` — из 60 регистров получаем
30 каналов. Любая ошибка оборачивается текстом с именем порта — он попадает
в `Event::PollError` и на красную строку статуса.

### `poll_tcp` — опрос TCP-устройства (`worker.rs:409-455`)

Та же карта, что у serial (input/coils/holding). Отличие — устойчивость к
размеру: сначала пробуем `read_input_registers(0, 64)` (AI-32 отдаёт 32
канала), а если сервер ответил exception 0x02 `IllegalAddress` — плавно
откатываемся (fallback — запасной вариант) к 60 регистрам
(`worker.rs:417-423`). Единицы измерения оставляем пустыми, не угадывая
(`worker.rs:438-440`).

### `emu_traces` — образцовые кадры «как на шине» (`worker.rs:460-527`)

Чтобы вкладка Bus на эмуляторе показывала **те же байты, что пойдут по
реальному кабелю**, для каждого устройства генерируются 3 RTU-транзакции:
`READ INPUT (60)`, `READ COILS (2)`, `READ HOLDING (16)`. CRC добавляется
`crc::append`, данные — из `DeviceSnapshot`. Проверка: тесты
`emu_traces_produce_valid_frames` и `emu_trace_input_regs_match_snapshot`
(`worker.rs:546-591`) вскрывают валидность CRC и равенство float с живым
срезом.

## `discover.rs` — порты и hotplug (горячее подключение) (144 строки)

### `ESP_USB_VENDORS` (`discover.rs:7-17`)

VID (Vendor ID — идентификатор производителя) известных USB-UART-мостов на
ESP-платах: CP210x (0x10C4), CH340 (0x1A86), FT232 (0x0403), нативный USB
(native USB) ESP32-S3 (0x303A) и др. Список сознательно «широкий» — точнее не
получится без реального PID (Product ID — идентификатор изделия).

### `PortInfo` (`discover.rs:20-30`) и `status_label`

```rust
pub struct PortInfo {
    pub name: String,
    pub description: String,
    pub is_esp_like: bool,
    pub has_firmware: bool,
    pub product: Option<String>,
}
```

`status_label()` (`discover.rs:33-43`) даёт метку: `ESP32 + FW` / `ESP32 (no FW)`
/ `Modbus` / `port`. `has_firmware` ставится событием `Probe` из сканера
(`app.rs:495-506`).

### `list_ports` / `to_port_info` (`discover.rs:47-75`)

`serialport::available_ports()` (кросс-платформенно, cross-platform: Linux
/dev/tty*, macOS /dev/cu.*, Windows COM*). `is_esp_like` — по VID *или* по
имени, содержащему `esp`/`usb`. `describe_port` строит человекочитаемое
описание.

### `probe_modbus` (`discover.rs:92-104`)

Открывает порт как `SerialMaster` (slave=1) и перебирает адреса 1..15,
каждый — `read_holding_registers(0, 1)`. Первый ответивший → `Some(slave)`,
иначе `None`.

### `ScanResult` (`discover.rs:107-116`)

Обёртка результата сканирования для `Event::Scan`.

---

**Вопросы для самопроверки**

1. Зачем `poll_port` общий у сканера и поллера? (сканер не открывает порт поллера → нет DTR/RTS-сброса).
2. Почему пауза поллера разбита на 10 кусков? (быстрый выход через stop).
3. Что делает сканер с JTAG-портами? (пропускает — это не Modbus-таргет).
4. Сколько раз за сеанс пробивается ESP-порт? (один; проба дёргает DTR/RTS).
5. Чему равно `byte_count` в ответе на `READ INPUT 60`? (120 — 60 регистров × 2 байта).
6. Почему `emu_snapshot` делает `drop(emu)` до построения `out`? (не держать Mutex дольше нужного).