# 07. Состояние приложения: `app.rs` (1705 строк)

Самый большой модуль. В нём живёт **единственная копия всего состояния**
(state) — `struct App` — и все действия, которые UI может выполнить
(чтение/запись регистров, запуск эмулятора, подключение, управление
датчиками, задачи по прошивке). `ui.rs`/`main.rs` только читают `App` и зовут
его методы.

## `Tab` — переключатель вкладок (tab) (`app.rs:13-56`)

`Dashboard, Ports, Registers, Sensors, Firmware, Bus, Log, Help` с `ALL`
(ярлык массива — чистый `match` в методах `title/next/prev`). `next()/prev()`
ходят по кругу через модульную арифметику массива (modular arithmetic —
остаток от деления на длину).

## Небольшие структуры

- `LogLine { text, level }` (`app.rs:60-63`): уровень 0=info, 1=ok, 2=warn,
  3=err — это влияет на цвет строки во вкладке Log.
- `RegisterForm` (`app.rs:66-87`): поля `slave_id/start/count/value/addr/reg_type`.
  Дефолт — slave 1, `reg_type=1` (holding).
- `SensorForm` (`app.rs:90-112`): `device_idx/name/kind/base/amplitude/period`.
  Дефолт — «New Sensor», T°C, 25±3, период 8.
- `FieldEdit` (`app.rs:209-217`): какой именно текстовый редактор активен —
  `Reg(i)`, `Sensor(i)`, `Tcp(i)`.
- `SensorRow` (`app.rs:220-226`): элемент левого списка Sensors —
  `Device(di)` (заголовок ◆) или `Sensor(di, si)` (датчик ●).

## `struct App` — все поля (`app.rs:114-206`)

Поля легко сгруппировать по обязанностям:

| Группа | Поля |
|--------|------|
| Ядро | `runtime: Runtime`, `tab`, `should_quit` |
| Порт | `ports`, `selected_port` |
| Источник | `emu`, `serial_master`, `serial_port`, `serial_baud` |
| Данные | `snapshot: Vec<DeviceSnapshot>`, `selected_device` |
| Лог/статус | `logs`, `last_poll_error`, `last_logged_poll_error`, `status_line`, `tick`, `spin` |
| Формы | `reg_form`, `sensor_form`, `sensor_selected` |
| FW | `fw_tool`, `fw_port`, `reconnect_after`, `busy`, `backups`, `backup_selected` |
| Редактирование | `editing`, `reg_focus`, `sensor_focus`, `sensor_manage` |
| Скроллы | `log_scroll`, `help_scroll`, `bus_scroll`, `dash_scroll`, `dash_sel_seen` |
| Шина/TCP | `trace`, `tcp_server`, `tcp_port`, `tcp_trace`, `tcp_master`, `tcp_connected`, `tcp_host`, `tcp_port_str`, `tcp_focus` |

Из них:

- `fw_tool` определяется **один раз в `App::new`** (`app.rs:261`) — детект
  через subprocess (дочерний процесс) нельзя дёргать на каждый кадр.
- `dash_sel_seen` хранит «какое устройство выбрано в прошлом кадре», чтобы
  автоскролл дашборда (scroll — прокрутка) срабатывал только при смене
  устройства (`app.rs:184-187`).
- `reconnect_after: Option<(String, bool)>` — запоминает «подключены ли мы
  были» перед fw-операцией (firmware — прошивка), чтобы после неё
  переподключиться (`app.rs:158-159`, `app.rs:585-587`).

## `App::new` (`app.rs:228-290`)

Собирает `Runtime` (запускает сканер!), `default_scenario()` эмулятора,
список бэкапов (backup — резервные копии) из `firmware::backup_dir()`,
заполняет все поля стандартными значениями. Н: `dash_sel_seen = usize::MAX` —
чтобы первый кадр тоже считался «сменой» (автоскролл при самом первом селекте
сработает).

## `log` — лог + файл (`app.rs:294-315`)

Кольцо 300 записей (ring buffer — кольцевой буфер) в памяти +
**дублирование в `~/esp32-tui.log`** (append — дописывать). Так лог виден даже
после закрытия TUI.

## Подключение источников (`app.rs:319-366`)

- `connect_emulator()`: `set_source(PollSource::Emu(emu))` и сразу строит
  снимок `emu_snapshot(.., false)` — чтобы Dashboard наполнился мгновенно,
  а не после первого опроса (`app.rs:320-329`).
- `connect_serial(port_name)`: фоновая задача (`run_task("connect", ..)`)
  открывает `SerialMaster`, результат — `TaskPayload::Master`
  (`app.rs:332-343`).
- `disconnect_serial()`: стоп поллера **только если опрашивается именно этот
  порт** (`app.rs:346-357`) — чтобы не трогать активный эмулятор/TCP.
- `is_emu()`: `current_source == "EMU"`.

## TCP (`app.rs:370-458`)

- `toggle_tcp_server()`: `take()` сервер → `stop()`, либо `spawn` и держать в
  `self.tcp_server` (`app.rs:370-392`).
- `connect_tcp()`: берёт `tcp_host`/`tcp_port_str` (правка на Ports), unit из
  `reg_form.slave_id`; фоновая задача `TcpMaster::open`
  (`app.rs:399-419`).
- `disconnect_tcp()`: стоп поллера только если источник `tcp://…`
  (`app.rs:422-433`).
- `drain_tcp_trace()`: `tcp_trace.lock()` → drain в Bus; вызывается из
  `handle_event` каждый раз (в том числе на `Ticked` — поэтому Bus не
  задерживает кадры даже без опроса, `app.rs:436-447`).
- `append_trace_entries`: держит последние **120** транзакций (`app.rs:450-458`).

## `handle_event` — реакция на все события (`app.rs:462-590`)

Разбирает 8 вариантов `Event` (см. [05](05-threads-and-events.md)):

- `Ticked` — `tick = tick.wrapping_add(1)` (анимация, анонсы таймера).
- `Scan` — **hotplug-детект** (горячее подключение): сравнивает прошлый и
  новый списки портов, логирует появление/исчезновение, отключается, если
  исчез опрашиваемый (`app.rs:468-494`).
- `Probe` — обновляет `has_firmware` и логирует найденного slave
  (`app.rs:495-506`).
- `Snapshot` — сохраняет снимок (snapshot — срез данных), `last_poll_at`,
  сбрасывает ошибку, вливает трейс (`app.rs:507-512`).
- `PollError` — `last_poll_error` + **дедупликация** логов (deduplication —
  исключение дублей; тот же текст — не спамим краснотой, `app.rs:513-521`).
- `Log` — просто строка.
- `TaskProgress` — статус-строка `⏳ …`.
- `TaskDone` — самый большой разбор (`app.rs:526-588`): успех задачи
  распаковывается в `TaskPayload`:
  - `Master` — остановить поллер, сохранить `serial_master`, пометить порт
    `has_firmware`, переключить источник на `PollSource::Serial`;
  - `TcpMaster` — то же для TCP;
  - `Lines` — статус-строка + бэк-бэкап `refresh_backups()` при label == "backup";
  - `Err` — статус-строка + лог-ошибка.
  В конце — переподключение по `reconnect_after` (`app.rs:585-587`).

## Действия по клавишам (`app.rs:594-905`)

Классическая схема: `main.rs` ловит клавишу, зовёт метод `App`, метод меняет
состояние и пишет в лог/статус.

- `toggle_emulator()`: эмулятор вкл/выкл (`[e]`).
- `connect_selected_port()`: коннект выбранного порта из списка.
- `read_registers()` (`app.rs:617-686`): три ветки источника — эмулятор
  напрямую, иначе TCP-мастер, иначе serial-мастер; тип из `reg_form.reg_type`
  (0=input, 1=holding, 2=coil — катушки мапятся в `Vec<u16>` символами 1/0).
- `write_register()` (`app.rs:689-753`): то же для записи; катушки получают
  `value != 0`.
- `selected_device()` / `select_dev_next/prev` / `set_selected_device` /
  `select_dev_paged(dir)` — навигация по дашборду (страница = 6 устройств).
- `set_selected_port(idx)` — клик по порту.
- `mouse_scroll(dir)` (`app.rs:805-866`): колесо по всем вкладкам — по сути
  одна диспетчер-функция (dispatcher; дашборд скролл, Ports/Firmware — выбор
  строки, Registers/Sensors — фокус, Bus/Log/Help — скроллы). Для Bus
  направление инвертировано: вверх = вглубь истории, вниз = к хвосту.
- `toggle_coil(device, coil)` (`app.rs:869-904`): читает текущее значение
  катушки, пишет инверсию; на эмуляторе — напрямую, иначе через мастер
  (TCP или serial). Вызывается пробелом (Dashboard).

## Датчики и устройства (`app.rs:907-1095`)

- `add_sensor()` (`app.rs:907-944`): парсит форму (fallback-значения через
  `unwrap_or`; fallback — значения по умолчанию), автонумерация «New Sensor N»,
  вызов `emu.add_sensor(device_idx, name, kind, base, amp, period)`.
- `move_focus(dir)` (`app.rs:948-961`): смещение фокуса формы с заворотом
  (`rem_euclid`, чтобы не уйти в минус).
- `toggle_sensor_manage()` (`app.rs:968-977`): вкл/выкл режима управления
  списком; при входе курсор (cursor — указатель) — на первом датчике.
- `sensor_rows()` (`app.rs:982-992`): плоский список `SensorRow` в том же
  порядке, что рисует UI — источник истины и для навигации, и для рендера.
- `sensor_nav(dir)` (`app.rs:998-1018`): сдвиг курсора с заворотом через край
  (последняя строка ↔ первая).
- `add_device()` (`app.rs:1022-1037`): новый slave с `max(slave_id)+1` и
  именем «Device N».
- `remove_selected()` (`app.rs:1042-1095`): удаление строки под курсором;
  **отказывается удалять последнее устройство** (`devices.len() <= 1` →
  лог «Refusing to delete the last device»). После удаления корректирует
  курсор (упирает в границы, поднимает с заголовка на первый датчик).

## Редактирование форм (`app.rs:1097-1130`)

`begin_edit_focused()` определяет `FieldEdit` по вкладке; `edit_char/backspace`
дергают нужное поле через helper-функции `reg_form_field_mut` /
`sensor_form_field_mut` / `tcp_form_field_mut` (`app.rs:1304-1331`). `cancel_edit`
просто снимает `editing`. Текстовые операции в режиме редактирования
перехватывает `main.rs:70-78`.

## Фоновые задачи и прошивка (`app.rs:1132-1301`)

- `run_task(label, job)` (`app.rs:1157-1169`): защита «не больше одной
  фоновой задачи» (`busy.is_some()` → игнор), `worker::spawn_task`, ставит
  `busy` (спиннер — spinner — в шапке, см. [08](08-ui.md)).
- `prepare_serial_task(port)` (`app.rs:1174-1180`): «отдать порт esptool'у» —
  если мы к нему подключены, отсоединиться и запомнить в `reconnect_after`.
- `fw_board_info` / `fw_flash_test` / `fw_backup` / `fw_restore_selected`
  (`app.rs:1183-1262`) — обёртки над `firmware.rs` в фоне; порт из `fw_port`
  или `serial_port`; результаты приходят `Event::TaskDone`.
- `task_progress(sink, pct, line)` (`app.rs:1149-1154`): статус-строка (status
  line) всегда, в лог — каждые 5% и при 100%.
- `refresh_backups()` (`app.rs:1264-1273`): перечитать `backup_dir()` после
  backup.
- `probe_selected_port()` (`app.rs:1281-1300`): фоновая `probe_modbus` выбранного
  порта; защита: «порт уже опрашивается — щупать нельзя».

## Тесты (`app.rs:1344-1705`)

`register_form_edit_focus_and_write_flow`, `sensor_form_edit_add_flow`,
`move_focus_wraps_edit_stays_inactive`, `sensor_manage_toggle_moves_to_first_sensor`,
`sensor_manage_nav_and_delete`, `remove_device_via_manage_deletes_whole_slave`,
`refuses_to_delete_last_device`, `add_device_creates_unique_slave`,
`many_devices_can_be_added`, `select_dev_paged_steps_by_page`,
`set_selected_device_and_port_clamp`, `mouse_scroll_drives_tabs`.

---

**Вопросы для самопроверки**

1. Где в `App` «единственная копия состояния» и почему это удобно? (все поля одним местом, потоки присылают только события).
2. Зачем `last_logged_poll_error`? (дедупликация логов одинаковых ошибок).
3. Что делает `disconnect_serial`, если опрашивается эмулятор? (ничего с поллером — стоп только под опрашиваемый порт).
4. Какая есть защита у `remove_selected`? (не удалить последнее устройство).
5. Почему `fw_tool` вычисляют один раз? (subprocess-детект дорогой и не нужен на каждом кадре).
6. Что происходит с портом перед fw-операцией и после? (disconnect → esptool →
   reconnect по `reconnect_after`).