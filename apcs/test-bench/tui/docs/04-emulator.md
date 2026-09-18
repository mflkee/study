# 04. Эмулятор: `emulator.rs` (474 строки)

Эмулятор (emulator) — это **Modbus slave-роль в памяти**, без железа. Когда вы
жмёте `[e]`, TUI сам становится источником данных: 4 виртуальных устройства,
у каждого свои датчики-синусоиды (sine wave sensors), катушки (coils) и
holding-регистры (Holding Registers, HR). Опрашивается он точно так же, как
реальный порт — только запросы идут не на COM-порт, а в функции внутри
процесса (`emu.read_input_regs(...)`, `emu.write_coil(...)`).

## `DataType` — вид датчика (`emulator.rs:11-32`)

`TemperatureC`, `PressureKPa`, `Flow`, `Test`, `LevelM`, `HumidityPct` + метод
`label()` для вкладки Sensors. В `worker.rs:309-316` на него завязаны единицы
измерения (`°C`, `kPa`, `%`, `m`), в `ui.rs`/`app.rs` — номера клавиш `1..6`.

## `VirtualSensor` (`emulator.rs:35-61`)

Поля: `name`, `data_type`, `base`, `amplitude`, `period_s`, `input_reg`,
`enabled`, `last_value`. Главное — формула значения:

```rust
fn value(&self, now_secs: f64) -> f32 {
    if !self.enabled { return self.base; }
    let t = (now_secs / self.period_s as f64) as f32;
    self.base + self.amplitude * t.sin()
}
```

Замечание: аргумент синуса — `t / T` (без `2π`). Sin в Rust — в радианах,
поэтому реальный период колебания `2π·T` секунд; «период» в форме — масштаб
времени. Это осознанное упрощение, описанное в `DOCUMENTATION.md`, но здесь
важно читать _код_, а не формулу из учебника: датчик с `base=22, amp=4, T=8`
на старте выдаст `22 + 4·sin(0/8) = 22.0`, через 2 с — `22 + 4·sin(0.25) ≈ 22.99`.

## `EmuDevice` — карта регистров одного slave (`emulator.rs:64-104`)

```rust
pub struct EmuDevice {
    pub slave_id: u8,
    pub name: String,
    pub description: String,
    pub input_regs:  BTreeMap<u16, u16>,
    pub holding_regs: BTreeMap<u16, u16>,
    pub coils:       BTreeMap<u16, bool>,
    pub discrete:    BTreeMap<u16, bool>,
    pub sensors:     Vec<VirtualSensor>,
}
```

`BTreeMap<u16, u16>` (а не `Vec`) — это «разреженный» словарь (sparse map):
запись только туда, куда что-то писали. Чтение недостающего адреса даёт
`0`/`false`. Отсюда, кстати, и «ASCII-трюк» на дашборде (dashboard — приборная
панель): отсутствующие катушки — просто «выключены».

### `tick` — обновить датчики в регистрах (`emulator.rs:91-103`)

```rust
fn tick(&mut self, now_secs: f64) {
    let mut to_update = Vec::new();
    for s in &mut self.sensors {
        let v = s.value(now_secs);
        s.last_value = v;
        to_update.push((s.input_reg, v));
    }
    for (reg, v) in to_update {
        let bits = v.to_bits();
        self.input_regs.insert(reg,     (bits >> 16) as u16);
        self.input_regs.insert(reg + 1, (bits & 0xFFFF) as u16);
    }
}
```

- Зачем второй цикл? Чтобы не держать `&mut` на `self.sensors` одновременно с
  мутацией `self.input_regs` — borrow-checker (механизм проверки заимствований
  в Rust). Сначала собрали список обновлений, потом применили. Это частый
  паттерн в Rust.
- `v.to_bits()` — сырые биты IEEE-754. float32 = 32 бита = два 16-битных
  слова: **старшее слово в чётный регистр** (high word — старшие биты `bits >>
  16`), младшее — в `reg+1`. Ровно так же читает обратно
  `frames::float_from_regs` (`frames.rs:201-204`).

## `Emulator` — весь набор устройств (`emulator.rs:107-142`)

```rust
pub struct Emulator {
    devices: Vec<EmuDevice>,
    last_tick: f64,
}
```

- `add_device(slave_id, name, desc) -> usize` — пушит устройство (push —
  добавить в конец), возвращает его индекс (используется в `app.rs` для
  датчиков).
- `remove_device(idx)` / `device(idx)` / `device_mut(idx)` / `devices()`.
- Поля приватные: менять можно только через методы — это и есть
  инкапсуляция (encapsulation).

### `add_sensor` — авто-поиск свободной пары регистров (`emulator.rs:152-182`)

```rust
let mut reg = 0u16;
while self.any_input_reg_in_use(dev_idx, reg) || self.any_input_reg_in_use(dev_idx, reg + 1) {
    reg += 2;
}
```

`any_input_reg_in_use` (`emulator.rs:201-214`) проверяет: занят ли `reg` каким-то
датчиком или «уже существует в карте и чётный» (float-сегмент). Так новые
датчики не наезжают на уже занятые адреса.

`period_s.max(0.1)` — защита от деления на ноль/бесконечности при периоде 0.

### `remove_sensor` (`emulator.rs:185-199`)

Индексы проверяются явно; возвращает `Result` с текстовой ошибкой, которая
попадает в лог UI.

### `tick` — общий ход времени (`emulator.rs:217-226`)

```rust
pub fn tick(&mut self) {
    let now = now_secs();
    if now - self.last_tick < 0.05 { return; }
    self.last_tick = now;
    for dev in &mut self.devices { dev.tick(now); }
}
```

`0.05` — **троттлинг** (throttling — «не чаще 20 Гц»), а не «тик каждые 50 мс».
Настоящий темп задаёт вызывающая сторона — поллер (poller) worker'а, раз в 500
мс (`worker.rs:148`).

Модель времени: `Emulator::new()` ставит `last_tick = 0.0`, поэтому первый
`tick()` считает `now_secs() - 0.0` — «время с момента старта процесса». Синус
берётся в этом фазовом положении; «прыжка из неизвестности» не возникает —
см. `DOCUMENTATION.md`.

## Slave-обработка запросов (`emulator.rs:230-331`)

Каждая функция: найти устройство по `slave_id` (нет → `Err(0x02)`, это
исключение (exception) «illegal data address»), затем прочитать/записать в
BTreeMap. Чтение недостающего адреса даёт `0`/`false` (`unwrap_or`). Именно
этими методами пользуются:

- редактор регистров в `app.rs` (чтение/запись напрямую из UI-потока,
  `app.rs:623-705`);
- TCP-сервер в `tcp_server.rs` (`handle_pdu`, `tcp_server.rs:188-321`) — то
  есть эмулятор «из коробки» отдаёт свою карту по Ethernet.

Список: `read_input_regs`, `read_holding_regs`, `write_holding_reg`,
`write_holding_regs`, `read_coils`, `read_discrete_inputs`, `write_coil`,
`write_coils`. Заметьте симметрию с высокоуровневым API мастера
(`master.rs:178-218`) — одна и та же семантика регистров в обеих ролях.

## `now_secs` и `SharedEmulator` (`emulator.rs:334-342`)

- `now_secs()` — монотонные секунды (monotonic — необратимые часы) от
  UNIX-эпохи.
- `pub type SharedEmulator = Arc<Mutex<Emulator>>` — так эмулятор живёт в
  нескольких потоках сразу (UI, поллер, TCP-сервер), а одновременно пишет
  только один. Разделяемое состояние — за `Mutex` (mutual exclusion — мьютекс,
  взаимное исключение), поэтому все держатели `clone()`-ят `Arc` (atomic
  reference counting — счётчик ссылок), а не сам `Emulator`.

## `default_scenario` — «сценарий из коробки» (`emulator.rs:345-411`)

4 устройства:

| Slave | Имя | Датчики | Катушки/регистры |
|-------|-----|---------|------------------|
| 1 | Pump Station | T-101, T-202, P-101 | coil0=ON (насос), coil1=OFF; holding0=30 (setpoint — уставка) |
| 2 | Test Device | Sine A, Sine B, Flow C | input_regs[60] |
| 3 | Electric Boiler | B-1, B-2, L-1 | coil0/coil1=OFF; holding0=70 |
| 4 | Weather Station | W, H, T | holding0=15 (alarm — аварийный порог) |

Каждое устройство имеет свою «карту», чтобы изучать протокол на живом
примере: различия в адресах и типах данных заметны во вкладке Bus.

## Тесты (`emulator.rs:413-474`)

`scenario_creates_devices`, `remove_sensor_frees_device_list`,
`sensors_write_input_regs`, `regs_read_write`, `missing_device_returns_illegal_address`.
Первый и последний — отличные шаблоны для новых тестов (см. практику).

---

**Вопросы для самопроверки**

1. Какой тип у карт регистров и почему не `Vec`? (`BTreeMap<u16,u16>` — разрежённые; чтение пропуска → 0/false).
2. Почему в `tick` два цикла `for`? (borrow-checker: нельзя держать `&mut` и мутировать одновременно).
3. Куда кладётся старшее слово float32? (чётный регистр; старшие биты — `bits >> 16`).
4. Что возвращает чтение у несуществующего slave? (`Err(0x02)` — exception).
5. Чем `tick`-троттлинг отличается от «тик раз в 50 мс»? (троттлинг — «не чаще»; темп задаёт вызывающий).
6. Зачем `Arc<Mutex<..>>`, а не просто передача по значению? (общий ресурс в нескольких потоках).