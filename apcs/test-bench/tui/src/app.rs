//! Состояние приложения и обработка событий.

use crate::discover::{PortInfo, ScanResult};
use crate::emulator::{DataType, SharedEmulator};
use crate::master::{SerialMaster, TraceEntry};
use crate::tcp_master::TcpMaster;
use crate::worker::{DeviceSnapshot, Event, PollSource, Runtime, TaskPayload};
use std::collections::VecDeque;
use std::sync::mpsc::Sender;
use std::sync::{Arc, Mutex};

/// Табы интерфейса.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Dashboard,
    Ports,
    Registers,
    Sensors,
    Firmware,
    Bus,
    Log,
    Help,
}

impl Tab {
    pub const ALL: [Tab; 8] = [
        Tab::Dashboard,
        Tab::Ports,
        Tab::Registers,
        Tab::Sensors,
        Tab::Firmware,
        Tab::Bus,
        Tab::Log,
        Tab::Help,
    ];
    pub fn title(&self) -> &'static str {
        match self {
            Tab::Dashboard => "Dashboard",
            Tab::Ports => "Ports",
            Tab::Registers => "Registers",
            Tab::Sensors => "Sensors",
            Tab::Firmware => "Firmware",
            Tab::Bus => "Bus",
            Tab::Log => "Log",
            Tab::Help => "Help",
        }
    }
    pub fn next(&self) -> Tab {
        let idx = Tab::ALL.iter().position(|t| t == self).unwrap();
        Tab::ALL[(idx + 1) % Tab::ALL.len()]
    }
    pub fn prev(&self) -> Tab {
        let idx = Tab::ALL.iter().position(|t| t == self).unwrap();
        Tab::ALL[(idx + Tab::ALL.len() - 1) % Tab::ALL.len()]
    }
}

/// Строка лога.
#[derive(Debug, Clone)]
pub struct LogLine {
    pub text: String,
    pub level: u8, // 0=info, 1=ok, 2=warn, 3=err
}

/// Вход в форме регистра (для редактора).
#[derive(Debug, Clone)]
pub struct RegisterForm {
    pub slave_id: String,
    pub start: String,
    pub count: String,
    pub value: String,   // для одиночной записи
    pub addr: String,    // для одиночной записи
    pub reg_type: usize, // 0=input, 1=holding, 2=coil
}

impl Default for RegisterForm {
    fn default() -> Self {
        Self {
            slave_id: "1".into(),
            start: "0".into(),
            count: "10".into(),
            value: "0".into(),
            addr: "0".into(),
            reg_type: 1,
        }
    }
}

/// Виджет фабрики датчиков.
#[derive(Debug, Clone)]
pub struct SensorForm {
    /// Номер устройства в эмуляторе (текст, чтобы можно было редактировать).
    pub device_idx: String,
    pub name: String,
    pub kind: DataType,
    pub base: String,
    pub amplitude: String,
    pub period: String,
}

impl Default for SensorForm {
    fn default() -> Self {
        Self {
            device_idx: "0".into(),
            name: "New Sensor".into(),
            kind: DataType::TemperatureC,
            base: "25.0".into(),
            amplitude: "3.0".into(),
            period: "8.0".into(),
        }
    }
}

/// Полное состояние приложения.
pub struct App {
    pub runtime: Runtime,
    pub tab: Tab,
    pub should_quit: bool,

    pub ports: Vec<PortInfo>,
    pub selected_port: usize,

    /// Источник данных: эмулятор или serial.
    pub emu: SharedEmulator,
    pub serial_master: Option<Arc<Mutex<SerialMaster>>>,
    pub serial_port: Option<String>,
    pub serial_baud: u32,

    /// Текущий снимок данных.
    pub snapshot: Vec<DeviceSnapshot>,

    /// Выбранное устройство на дашборде.
    pub selected_device: usize,

    /// Лог.
    pub logs: VecDeque<LogLine>,

    /// Формы.
    pub reg_form: RegisterForm,
    pub sensor_form: SensorForm,
    pub sensor_selected: usize,

    /// Последняя ошибка опроса (для индикации).
    pub last_poll_error: Option<String>,
    /// Последняя залогированная ошибка опроса (для подавления повторов).
    pub last_logged_poll_error: Option<String>,
    /// Инструмент прошивки, определённый один раз при старте (нужен для UI
    /// и fw-операций; детект через subprocess нельзя дёргать на каждый кадр).
    pub fw_tool: Option<String>,
    /// Время последнего удачного опроса.
    pub last_poll_at: f64,
    /// Таймер UI.
    pub tick: u64,
    /// Быстрое действие в кадре (например, «соединить порт»).
    pub status_line: String,
    /// Текущий порт, выбранный для прошивки.
    pub fw_port: Option<String>,
    /// Переподключение к порту после fw-операции: (порт, был ли подключён).
    pub reconnect_after: Option<(String, bool)>,
    /// Активная фоновая задача (label) — показывает спиннер в шапке.
    pub busy: Option<String>,
    /// Счётчик анимации спиннера.
    pub spin: usize,
    /// Список файлов бэкапов.
    pub backups: Vec<String>,
    pub backup_selected: usize,

    /// Редактирование текстового поля формы (фокус).
    pub editing: Option<FieldEdit>,
    /// Какую строку формы регистров подсвечиваем.
    pub reg_focus: usize,
    /// Какую строку формы датчиков подсвечиваем.
    pub sensor_focus: usize,
    /// Режим управления датчиками (навигация по списку + удаление).
    pub sensor_manage: bool,
    /// Прокрутка лога (вкладка Log).
    pub log_scroll: usize,
    /// Прокрутка справки (вкладка Help).
    pub help_scroll: usize,
    /// Прокрутка лога шины (вкладка Bus). 0 = всегда внизу (последний кадр).
    pub bus_scroll: usize,
    /// Смещение списка устройств дашборда (верх видимой строки).
    pub dash_scroll: usize,
    /// Какое устройство было выбрано на дашборде в прошлом кадре —
    /// нужен, чтобы автопрокрутка к заголовку срабатывала только при
    /// смене устройства, а не забивала ручную прокрутку.
    pub dash_sel_seen: usize,
    /// «Лог шины»: последние Modbus-кадры (вкладка Bus).
    pub trace: VecDeque<TraceEntry>,
    /// Modbus TCP-сервер: отдаёт карту эмулятора по Ethernet (для будущего Zynq).
    pub tcp_server: Option<crate::tcp_server::TcpServerState>,
    /// Порт TCP-сервера (по умолчанию 1502 — dev-порт, именительный к 502).
    pub tcp_port: u16,
    /// Общий журнал TCP-транзакций — дренится в `trace` на каждом тике.
    pub tcp_trace: crate::tcp_server::SharedTrace,

    /// Modbus TCP *master* (клиент): опрашивает внешние устройства (AI-32 и т.п.).
    pub tcp_master: Option<Arc<Mutex<TcpMaster>>>,
    /// Читаемый адрес подключённого TCP-устройства (`host:port`) — для статуса.
    pub tcp_connected: Option<String>,
    /// Адрес/порт цели TCP-мастера (редактируются на вкладке Ports).
    pub tcp_host: String,
    pub tcp_port_str: String,
    /// Какое поле формы TCP-мастера в фокусе (0=host, 1=port).
    pub tcp_focus: usize,
}

/// Какую форму и какое поле редактируем.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FieldEdit {
    /// Поле формы регистров (0=slave,1=start,2=count,3=addr,4=value).
    Reg(usize),
    /// Поле формы датчиков (0=device,1=name,2=base,3=amplitude,4=period).
    Sensor(usize),
    /// Поле формы Modbus TCP master на вкладке Ports (0=host, 1=port).
    Tcp(usize),
}

/// Строка списка датчиков (левой панели вкладки Sensors).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SensorRow {
    /// Заголовок устройства (индекс устройства).
    Device(usize),
    /// Строка датчика (индекс устройства, индекс датчика).
    Sensor(usize, usize),
}

impl App {
    pub fn new() -> Self {
        let mut runtime = Runtime::new();
        runtime.start_scanner();

        let emu = crate::emulator::default_scenario();

        let mut backups = Vec::new();
        if let Ok(rd) = std::fs::read_dir(crate::firmware::backup_dir()) {
            for entry in rd.flatten() {
                backups.push(entry.file_name().to_string_lossy().to_string());
            }
            backups.sort();
        }

        Self {
            runtime,
            tab: Tab::Dashboard,
            should_quit: false,
            ports: Vec::new(),
            selected_port: 0,
            emu,
            serial_master: None,
            serial_port: None,
            serial_baud: 9600,
            snapshot: Vec::new(),
            selected_device: 0,
            logs: VecDeque::new(),
            reg_form: RegisterForm::default(),
            sensor_form: SensorForm::default(),
            sensor_selected: 0,
            last_poll_error: None,
            last_logged_poll_error: None,
            fw_tool: crate::firmware::find_tool(),
            last_poll_at: 0.0,
            tick: 0,
            status_line: String::new(),
            fw_port: None,
            reconnect_after: None,
            busy: None,
            spin: 0,
            backups,
            backup_selected: 0,
            editing: None,
            reg_focus: 0,
            sensor_focus: 0,
            sensor_manage: false,
            log_scroll: 0,
            help_scroll: 0,
            bus_scroll: 0,
            dash_scroll: 0,
            dash_sel_seen: usize::MAX,
            trace: VecDeque::new(),
            tcp_server: None,
            tcp_port: crate::tcp_server::DEFAULT_PORT,
            tcp_trace: crate::tcp_server::new_shared_trace(),
            tcp_master: None,
            tcp_connected: None,
            tcp_host: "127.0.0.1".into(),
            tcp_port_str: crate::tcp_server::DEFAULT_PORT.to_string(),
            tcp_focus: 0,
        }
    }

    // --- Лог ---

    pub fn log(&mut self, level: u8, text: impl Into<String>) {
        let text = text.into();
        self.logs.push_back(LogLine {
            text: text.clone(),
            level,
        });
        if self.logs.len() > 300 {
            self.logs.pop_front();
        }
        // Дублируем в файл ~/esp32-tui.log — чтобы лог можно было посмотреть
        // даже когда TUI уже закрыт.
        if let Some(home) = dirs::home_dir() {
            use std::io::Write;
            if let Ok(mut f) = std::fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(home.join("esp32-tui.log"))
            {
                let _ = writeln!(f, "[{}] {}", level, text);
            }
        }
    }

    // --- Подключение ---

    /// Подключается к эмулятору (тестовый сервер).
    pub fn connect_emulator(&mut self) {
        self.disconnect_serial();
        let emu = self.emu.clone();
        self.runtime.set_source(PollSource::Emu(emu));
        self.log(1, "Started test server (in-process emulator)");
        self.status_line = "Connected: EMULATOR".into();
        // Сразу строим снимок, чтобы список устройств появился мгновенно,
        // а не после первого опроса.
        self.snapshot = crate::worker::emu_snapshot(&self.emu, false).unwrap_or_default();
    }

    /// Подключается к serial порту (в фоне, со спиннером).
    pub fn connect_serial(&mut self, port_name: &str) {
        let port = port_name.to_string();
        let baud = self.serial_baud;
        self.run_task("connect", move |_sink| {
            SerialMaster::open(&port, baud, 1)
                .map(|m| TaskPayload::Master {
                    port: port.clone(),
                    master: Arc::new(Mutex::new(m)),
                })
                .map_err(|e| format!("Failed to open {}: {}", port, e))
        });
    }

    /// Отключается от serial (но НЕ от эмулятора — эмулятор можно оставить).
    pub fn disconnect_serial(&mut self) {
        // Останавливаем поллер, только если сейчас опрашивается именно этот
        // порт (иначе не трогаем активный TCP/эмулятор).
        let port = self.serial_port.clone();
        let is_polled_port = port.is_some()
            && self.runtime.current_source.as_deref() == port.as_deref();
        if is_polled_port {
            self.runtime.stop_poller();
        }
        self.serial_master = None;
        self.serial_port = None;
    }

    pub fn is_emu(&self) -> bool {
        self.runtime.current_source.as_deref() == Some("EMU")
    }

    pub fn is_connected(&self) -> bool {
        self.runtime.current_source.is_some()
    }

    // --- Modbus TCP server ---

    /// Включить/выключить TCP-сервер: отдаёт ту же карту эмулятора по Ethernet.
    pub fn toggle_tcp_server(&mut self) {
        if let Some(mut st) = self.tcp_server.take() {
            st.stop();
            self.log(1, "Modbus TCP server stopped");
            return;
        }
        let emu = self.emu.clone();
        let trace = self.tcp_trace.clone();
        let port = self.tcp_port;
        match crate::tcp_server::spawn(port, emu, trace) {
            Ok((addr, st)) => {
                self.tcp_server = Some(st);
                self.log(
                    1,
                    format!(
                        "Modbus TCP server on {} — serves the emulator register map ([t] to stop)",
                        addr
                    ),
                );
            }
            Err(e) => self.log(3, e),
        }
    }

    // --- Modbus TCP master (клиент) ---

    /// Подключается к внешнему Modbus TCP устройству (в фоне, со спиннером).
    /// Unit ID берём из формы регистров («Slave ID») — это же значение потом
    /// уходит в MBAP-заголовок каждого запроса.
    pub fn connect_tcp(&mut self) {
        if self.busy.is_some() {
            return;
        }
        let host = self.tcp_host.trim().to_string();
        let port = self.tcp_port_str.trim().parse::<u16>().unwrap_or(crate::tcp_server::DEFAULT_PORT);
        let unit = self.reg_form.slave_id.parse::<u8>().unwrap_or(1);
        if host.is_empty() {
            self.log(2, "TCP host is empty (edit on Ports tab)");
            return;
        }
        self.run_task("tcp-connect", move |_sink| {
            TcpMaster::open(&host, port, unit)
                .map(|m| TaskPayload::TcpMaster {
                    host: host.clone(),
                    port,
                    master: Arc::new(Mutex::new(m)),
                })
                .map_err(|e| format!("TCP connect {}:{} failed: {}", host, port, e))
        });
    }

    /// Отключается от Modbus TCP устройства (не трогает serial и эмулятор).
    pub fn disconnect_tcp(&mut self) {
        let was = self.tcp_connected.clone();
        // Останавливаем поллер только если сейчас опрашивается TCP-источник.
        if self.runtime.current_source.as_deref().is_some_and(|s| s.starts_with("tcp://")) {
            self.runtime.stop_poller();
        }
        self.tcp_master = None;
        self.tcp_connected = None;
        if let Some(w) = was {
            self.log(2, format!("Disconnected TCP {}", w));
        }
    }

    /// Дренит журнал TCP-транзакций в общий «лог шины» (вкладка Bus).
    fn drain_tcp_trace(&mut self) {
        let mut lock = match self.tcp_trace.lock() {
            Ok(l) => l,
            Err(_) => return,
        };
        if lock.is_empty() {
            return;
        }
        let entries: Vec<TraceEntry> = lock.drain(..).collect();
        drop(lock);
        self.append_trace_entries(entries);
    }

    /// Добавляет записи в «лог шины», удерживая последние 120 транзакций.
    pub fn append_trace_entries(&mut self, entries: Vec<TraceEntry>) {
        for t in entries {
            self.trace.push_back(t);
        }
        let overflow = self.trace.len().saturating_sub(120);
        for _ in 0..overflow {
            self.trace.pop_front();
        }
    }

    // --- Обработка событий ---

    pub fn handle_event(&mut self, ev: Event) {
        self.drain_tcp_trace();
        match ev {
            Event::Ticked => {
                self.tick = self.tick.wrapping_add(1);
            }
            Event::Scan(ScanResult { ports, .. }) => {
                let prev: Vec<String> = self.ports.iter().map(|p| p.name.clone()).collect();
                let now: Vec<String> = ports.iter().map(|p| p.name.clone()).collect();
                // Hotplug-детект.
                for p in &ports {
                    if !prev.contains(&p.name) {
                        if p.is_esp_like {
                            self.log(1, format!("🔌 ESP32 detected: {}", p.name));
                        } else {
                            self.log(0, format!("+ Port: {}", p.name));
                        }
                    }
                }
                for old in &prev {
                    if !now.contains(old) {
                        self.log(3, format!("- Port removed: {}", old));
                        if self.serial_port.as_deref() == Some(old.as_str()) {
                            self.log(3, format!("{} disconnected", old));
                            self.disconnect_serial();
                        }
                    }
                }
                self.ports = ports;
                if self.selected_port >= self.ports.len() && !self.ports.is_empty() {
                    self.selected_port = self.ports.len() - 1;
                }
            }
            Event::Probe { port, slave } => {
                if let Some(s) = slave {
                    if let Some(p) = self.ports.iter_mut().find(|p| p.name == port) {
                        p.has_firmware = true;
                    }
                    self.log(0, format!("Modbus slave ID {} found on {}", s, port));
                } else {
                    if let Some(p) = self.ports.iter_mut().find(|p| p.name == port) {
                        p.has_firmware = false;
                    }
                }
            }
            Event::Snapshot { devices, trace } => {
                self.snapshot = devices;
                self.last_poll_at = crate::emulator::now_secs();
                self.last_poll_error = None;
                self.append_trace_entries(trace);
            }
            Event::PollError(e) => {
                self.last_poll_error = Some(e.clone());
                // Повторные одинаковые ошибки не засоряют лог «краснотой» —
                // логируем только смену текста ошибки.
                if self.last_logged_poll_error.as_deref() != Some(e.as_str()) {
                    self.last_logged_poll_error = Some(e.clone());
                    self.log(3, format!("Poll error: {}", e));
                }
            }
            Event::Log(text) => self.log(0, text),
            Event::TaskProgress { text } => {
                self.status_line = format!("⏳ {}", text);
            }
            Event::TaskDone { label, result } => {
                self.busy = None;
                match result {
                    Ok(TaskPayload::Master { port, master }) => {
                        self.runtime.stop_poller();
                        let shared = master;
                        self.serial_master = Some(shared.clone());
                        self.serial_port = Some(port.clone());
                        if let Some(p) = self.ports.iter_mut().find(|p| p.name == port) {
                            p.has_firmware = true;
                        }
                        self.runtime.set_source(PollSource::Serial {
                            port: port.clone(),
                            baud: self.serial_baud,
                            master: shared,
                        });
                        self.log(1, format!("Connected to {}", port));
                        self.status_line = format!("Connected: {}", port);
                    }
                    Ok(TaskPayload::TcpMaster { host, port, master }) => {
                        self.runtime.stop_poller();
                        let shared = master;
                        self.tcp_master = Some(shared.clone());
                        let addr = format!("{}:{}", host, port);
                        self.tcp_connected = Some(addr.clone());
                        self.runtime.set_source(PollSource::Tcp {
                            host: host.clone(),
                            port,
                            master: shared,
                        });
                        self.log(
                            1,
                            format!(
                                "TCP master connected to {} (unit {}) — [o] to disconnect",
                                addr, self.reg_form.slave_id
                            ),
                        );
                        self.status_line = format!("Connected: {}", addr);
                    }
                    Ok(TaskPayload::Lines(lines)) => {
                        if label.starts_with("probe ") {
                            self.status_line =
                                lines.first().cloned().unwrap_or_else(|| "probe: done".into());
                        } else {
                            self.status_line = format!("{}: done", label);
                        }
                        if label == "backup" {
                            self.refresh_backups();
                        }
                        for l in lines {
                            self.log(1, l);
                        }
                    }
                    Err(e) => {
                        self.status_line = format!("{}: {}", label, e);
                        self.log(3, format!("{}: {}", label, e));
                    }
                }
                // После fw-операции возвращаем открытый нами порт обратно.
                if let Some((p, true)) = self.reconnect_after.take() {
                    self.connect_serial(&p);
                }
            }
        }
    }

    // --- Действия (по клавишам) ---

    /// Запускает/останавливает эмулятор.
    pub fn toggle_emulator(&mut self) {
        if self.is_emu() {
            self.runtime.stop_poller();
            self.log(2, "Stopped test server");
            self.status_line = "Disconnected".into();
        } else {
            self.connect_emulator();
        }
    }

    /// Выполняет операцию «connect» для выбранного порта.
    pub fn connect_selected_port(&mut self) {
        if self.ports.is_empty() {
            self.log(2, "No ports found");
            return;
        }
        let idx = self.selected_port.min(self.ports.len() - 1);
        let name = self.ports[idx].name.clone();
        self.connect_serial(&name);
    }

    /// Редактор регистров: чтение.
    pub fn read_registers(&mut self) {
        let slave = self.reg_form.slave_id.parse::<u8>().unwrap_or(1);
        let start = self.reg_form.start.parse::<u16>().unwrap_or(0);
        let count = self.reg_form.count.parse::<u16>().unwrap_or(10).min(125);

        // Если опрашиваем эмулятор — работаем с ним напрямую.
        if self.is_emu() {
            let result = {
                let mut emu = self.emu.lock().unwrap();
                match self.reg_form.reg_type {
                    0 => emu.read_input_regs(slave, start, count),
                    1 => emu.read_holding_regs(slave, start, count),
                    2 => emu.read_coils(slave, start, count).map(|b| {
                        b.into_iter()
                            .map(|x| if x { 1 } else { 0 })
                            .collect::<Vec<u16>>()
                    }),
                    _ => Err(0x02),
                }
            };
            match result {
                Ok(regs) => {
                    self.log(1, format!("Read {} regs from slave {}", regs.len(), slave));
                    self.status_line = format!("{:?}", regs);
                }
                Err(code) => {
                    self.log(3, format!("Read failed (exception 0x{:02X})", code));
                    self.status_line = format!("Read failed: exception 0x{:02X}", code);
                }
            }
            return;
        }

        // Иначе — через подключённый мастер: TCP (если есть) или serial.
        let result = if let Some(master) = self.tcp_master.clone() {
            let mut m = master.lock().unwrap();
            match self.reg_form.reg_type {
                0 => m.read_input_registers(start, count),
                1 => m.read_holding_registers(start, count),
                2 => m.read_coils(start, count).map(|b| {
                    b.into_iter().map(|x| if x { 1 } else { 0 }).collect()
                }),
                _ => return,
            }
        } else if let Some(master) = self.serial_master.clone() {
            let mut m = master.lock().unwrap();
            match self.reg_form.reg_type {
                0 => m.read_input_registers(start, count),
                1 => m.read_holding_registers(start, count),
                2 => m.read_coils(start, count).map(|b| {
                    b.into_iter().map(|x| if x { 1 } else { 0 }).collect()
                }),
                _ => return,
            }
        } else {
            self.log(2, "Not connected");
            self.status_line = "Not connected: press [e] for emulator, or [c] in Ports".into();
            return;
        };
        match result {
            Ok(regs) => {
                self.log(1, format!("Read {} regs", regs.len()));
                self.status_line = format!("{:?}", regs);
            }
            Err(e) => {
                self.log(3, format!("Read failed: {}", e));
                self.status_line = format!("Read failed: {}", e);
            }
        }
    }

    /// Редактор регистров: запись.
    pub fn write_register(&mut self) {
        let slave = self.reg_form.slave_id.parse::<u8>().unwrap_or(1);
        let addr = self.reg_form.addr.parse::<u16>().unwrap_or(0);
        let value = self.reg_form.value.parse::<u16>().unwrap_or(0);

        if self.is_emu() {
            let result = {
                let mut emu = self.emu.lock().unwrap();
                match self.reg_form.reg_type {
                    1 => emu.write_holding_reg(slave, addr, value),
                    2 => {
                        let v = value != 0;
                        emu.write_coil(slave, addr, v)
                    }
                    _ => Err(0x02),
                }
            };
            match result {
                Ok(()) => {
                    let what = match self.reg_form.reg_type {
                        1 => "holding reg",
                        2 => "coil",
                        _ => "reg",
                    };
                    self.log(1, format!("Wrote {} to {} {} (slave {})", value, addr, what, slave));
                    self.status_line = format!("Wrote {} -> {} {}", value, addr, what);
                }
                Err(code) => {
                    self.log(3, format!("Write failed (exception 0x{:02X})", code));
                    self.status_line = format!("Write failed: exception 0x{:02X}", code);
                }
            }
            return;
        }

        let result = if let Some(master) = self.tcp_master.clone() {
            let mut m = master.lock().unwrap();
            match self.reg_form.reg_type {
                1 => m.write_single_register(addr, value),
                2 => m.write_single_coil(addr, value != 0),
                _ => return,
            }
        } else if let Some(master) = self.serial_master.clone() {
            let mut m = master.lock().unwrap();
            match self.reg_form.reg_type {
                1 => m.write_single_register(addr, value),
                2 => m.write_single_coil(addr, value != 0),
                _ => return,
            }
        } else {
            self.log(2, "Not connected");
            self.status_line = "Not connected: press [e] for emulator, or [c] in Ports".into();
            return;
        };
        match result {
            Ok(()) => {
                self.log(1, format!("Wrote {} to {} (slave 1)", value, addr));
                self.status_line = format!("Wrote {} -> reg {}", value, addr);
            }
            Err(e) => {
                self.log(3, format!("Write failed: {}", e));
                self.status_line = format!("Write failed: {}", e);
            }
        }
    }

    /// Индекс выбранного устройства (без выхода за пределы снимка).
    pub fn selected_device(&self) -> usize {
        if self.snapshot.is_empty() {
            0
        } else {
            self.selected_device.min(self.snapshot.len() - 1)
        }
    }

    /// Переход к следующему устройству на дашборде.
    pub fn select_dev_next(&mut self) {
        if !self.snapshot.is_empty() {
            self.selected_device = (self.selected_device + 1).min(self.snapshot.len() - 1);
        }
    }

    /// Переход к предыдущему устройству на дашборде.
    pub fn select_dev_prev(&mut self) {
        if !self.snapshot.is_empty() {
            self.selected_device = self.selected_device.saturating_sub(1);
        }
    }

    /// Прямой выбор устройства дашборда по клику.
    pub fn set_selected_device(&mut self, di: usize) {
        if di < self.snapshot.len() {
            self.selected_device = di;
        }
    }

    /// Постраничный выбор устройства (PgDn/PgUp) — со страницей в 6 строк.
    pub fn select_dev_paged(&mut self, dir: i8) {
        if self.snapshot.is_empty() {
            return;
        }
        if dir > 0 {
            self.selected_device = (self.selected_device + 6).min(self.snapshot.len() - 1);
        } else {
            self.selected_device = self.selected_device.saturating_sub(6);
        }
    }

    /// Прямой выбор порта по клику.
    pub fn set_selected_port(&mut self, idx: usize) {
        if idx < self.ports.len() {
            self.selected_port = idx;
        }
    }

    /// Действие колеса мыши по текущей вкладке: выбор/фокус/скролл.
    pub fn mouse_scroll(&mut self, dir: i8) {
        match self.tab {
            Tab::Dashboard => {
                // Линейная прокрутка списка устройств/датчиков (см. draw_dashboard:
                // выбор устройства ↑/↓, а колесо двигает саму страницу).
                self.dash_scroll = if dir > 0 {
                    self.dash_scroll + 1
                } else {
                    self.dash_scroll.saturating_sub(1)
                };
            }
            Tab::Ports => {
                if !self.ports.is_empty() {
                    self.selected_port = if dir > 0 {
                        (self.selected_port + 1).min(self.ports.len() - 1)
                    } else {
                        self.selected_port.saturating_sub(1)
                    };
                }
            }
            Tab::Registers => self.move_focus(dir),
            Tab::Sensors => {
                if self.sensor_manage {
                    self.sensor_nav(dir);
                } else {
                    self.move_focus(dir);
                }
            }
            Tab::Firmware => {
                if !self.backups.is_empty() {
                    self.backup_selected = if dir > 0 {
                        (self.backup_selected + 1).min(self.backups.len() - 1)
                    } else {
                        self.backup_selected.saturating_sub(1)
                    };
                }
            }
            Tab::Bus => {
                // 0 = «в хвосте» (показаны последние кадры); прокрутка вверх
                // отходит на страницу вглубь истории, вниз — к живому хвосту.
                if dir > 0 {
                    self.bus_scroll = self.bus_scroll.saturating_sub(1);
                } else {
                    self.bus_scroll += 1;
                }
            }
            Tab::Log => {
                self.log_scroll = if dir > 0 {
                    self.log_scroll + 1
                } else {
                    self.log_scroll.saturating_sub(1)
                };
            }
            Tab::Help => {
                self.help_scroll = if dir > 0 {
                    self.help_scroll + 1
                } else {
                    self.help_scroll.saturating_sub(1)
                };
            }
        }
    }

    /// Переключает катушку (например, насос).
    pub fn toggle_coil(&mut self, device: usize, coil: u16) {
        let slave = self
            .snapshot
            .get(device)
            .map(|d| d.slave_id)
            .unwrap_or(1);
        if self.is_emu() {
            let (current, result) = {
                let mut emu = self.emu.lock().unwrap();
                let current = emu.read_coils(slave, coil, 1).map(|v| v[0]).unwrap_or(false);
                let result = emu.write_coil(slave, coil, !current);
                (current, result)
            };
            match result {
                Ok(()) => self.log(1, format!("Coil {}={} (slave {})", coil, !current, slave)),
                Err(code) => self.log(3, format!("Coil write failed (0x{:02X})", code)),
            }
            return;
        }
        // Катушку переключаем через подключённый мастер: TCP (если есть) или serial.
        let result = if let Some(master) = self.tcp_master.clone() {
            let mut m = master.lock().unwrap();
            let current = m.read_coils(coil, 1).map(|v| v[0]).unwrap_or(false);
            m.write_single_coil(coil, !current)
        } else if let Some(master) = self.serial_master.clone() {
            let mut m = master.lock().unwrap();
            let current = m.read_coils(coil, 1).map(|v| v[0]).unwrap_or(false);
            m.write_single_coil(coil, !current)
        } else {
            return;
        };
        match result {
            Ok(()) => self.log(1, format!("Coil {} toggled", coil)),
            Err(e) => self.log(3, format!("Coil write failed: {}", e)),
        }
    }

    /// Добавляет датчик в эмулятор.
    pub fn add_sensor(&mut self) {
        let (base, amp, period, name, device_idx, kind) = {
            let base = self.sensor_form.base.parse::<f32>().unwrap_or(25.0);
            let amp = self.sensor_form.amplitude.parse::<f32>().unwrap_or(3.0);
            let period = self.sensor_form.period.parse::<f32>().unwrap_or(8.0);
            let device_idx = self.sensor_form.device_idx.parse::<usize>().unwrap_or(0);
            let name = if self.sensor_form.name.trim().is_empty() {
                "Sensor".to_string()
            } else {
                self.sensor_form.name.trim().to_string()
            };
            (base, amp, period, name, device_idx, self.sensor_form.kind)
        };
        // Если имя дефолтное и в устройстве уже есть такие датчики — нумеруем,
        // чтобы одинаковые "New Sensor" не путались.
        let name = if name == "New Sensor" {
            let emu = self.emu.lock().unwrap();
            let n = emu
                .devices()
                .get(device_idx)
                .map(|d| d.sensors.len() + 1)
                .unwrap_or(1);
            format!("New Sensor {}", n)
        } else {
            name
        };

        let result = {
            let mut emu = self.emu.lock().unwrap();
            emu.add_sensor(device_idx, &name, kind, base, amp, period)
        };
        match result {
            Ok(()) => {
                self.log(1, format!("Added sensor '{}' to device {}", name, device_idx));
            }
            Err(e) => self.log(3, format!("Add sensor failed: {}", e)),
        }
    }

    // --- Редактирование форм ---

    pub fn move_focus(&mut self, dir: i8) {
        match self.tab {
            Tab::Registers => {
                let n = 5usize;
                self.reg_focus = ((self.reg_focus as i64 + dir as i64).rem_euclid(n as i64)) as usize;
            }
            Tab::Sensors => {
                let n = 5usize;
                self.sensor_focus =
                    ((self.sensor_focus as i64 + dir as i64).rem_euclid(n as i64)) as usize;
            }
            _ => {}
        }
    }

    // --- Управление существующими датчиками ---

    /// Включает/выключает режим управления списком датчиков; при входе
    /// курсор встаёт на первый датчик (заголовки устройств пропускаем —
    /// входить в manage нужно безопасно, чтобы случайно не удалить устройство).
    pub fn toggle_sensor_manage(&mut self) {
        self.sensor_manage = !self.sensor_manage;
        if self.sensor_manage && self.editing.is_none() {
            let rows = self.sensor_rows();
            self.sensor_selected = rows
                .iter()
                .position(|r| matches!(r, SensorRow::Sensor(..)))
                .unwrap_or(0);
        }
    }

    /// Строки левой панели Sensors: заголовки устройств и датчики (в том же
    /// порядке, в котором они рисуются). Используется и для навигации, и для
    /// рендера — поэтому они не расходятся.
    pub fn sensor_rows(&self) -> Vec<SensorRow> {
        let mut rows = Vec::new();
        let emu = self.emu.lock().unwrap();
        for (di, dev) in emu.devices().iter().enumerate() {
            rows.push(SensorRow::Device(di));
            for si in 0..dev.sensors.len() {
                rows.push(SensorRow::Sensor(di, si));
            }
        }
        rows
    }

    /// Сдвигает курсор списка строк в manage-режиме. Строки — заголовки
    /// устройств (◆ Slave N) и датчики (●), курсор свободно ходит по всем,
    /// заворачиваясь через край: под последним датчиком — заголовок
    /// следующего устройства (и наоборот с первой строки вверх — последняя).
    pub fn sensor_nav(&mut self, dir: i8) {
        let rows = self.sensor_rows();
        let n = rows.len() as i64;
        if n == 0 {
            self.sensor_selected = 0;
            return;
        }
        // Если курсор вне списка (например, только что добавили устройство) —
        // начинаем с края: вниз → первая строка, вверх → последняя.
        let cur = if self.sensor_selected < rows.len() {
            self.sensor_selected as i64
        } else {
            if dir > 0 {
                -1
            } else {
                n
            }
        };
        let ni = ((cur + dir as i64).rem_euclid(n)) as usize;
        self.sensor_selected = ni;
    }

    /// Добавляет новое (пустое) устройство в эмулятор с уникальным slave id
    /// и автоименем "Device N".
    pub fn add_device(&mut self) {
        let (slave, name) = {
            let mut emu = self.emu.lock().unwrap();
            let slave = emu.devices().iter().map(|d| d.slave_id).max().unwrap_or(0) + 1;
            let n = emu
                .devices()
                .iter()
                .filter(|d| d.name.starts_with("Device "))
                .count()
                + 1;
            let name = format!("Device {}", n);
            emu.add_device(slave, &name, "user-defined device");
            (slave, name)
        };
        self.log(1, format!("Added device '{}' (slave {})", name, slave));
    }

    /// Удаляет объект под курсором списка (manage-режим): отдельный датчик или
    /// целое устройство вместе с его датчиками. Отказывается удалять
    /// последнее устройство, чтобы эмулятору было что обслуживать.
    pub fn remove_selected(&mut self) {
        let rows = self.sensor_rows();
        let Some(row) = rows.get(self.sensor_selected).cloned() else {
            self.log(2, "Nothing under cursor");
            return;
        };
        match row {
            SensorRow::Device(di) => {
                let deleted = {
                    let mut emu = self.emu.lock().unwrap();
                    if emu.devices().len() <= 1 {
                        None
                    } else {
                        let name = emu.devices()[di].name.clone();
                        emu.remove_device(di);
                        Some(name)
                    }
                };
                match deleted {
                    Some(name) => self.log(1, format!("Deleted device '{}'", name)),
                    None => {
                        self.log(3, "Refusing to delete the last device");
                        return;
                    }
                }
            }
            SensorRow::Sensor(di, si) => {
                let result = {
                    let mut emu = self.emu.lock().unwrap();
                    emu.remove_sensor(di, si)
                };
                match result {
                    Ok(()) => {
                        self.log(1, format!("Deleted sensor on device {}", di + 1));
                    }
                    Err(e) => {
                        self.log(3, format!("Delete failed: {}", e));
                        return;
                    }
                }
            }
        }
        // После удаления индекс мог выйти за границы — упираем, а если встали
        // на заголовок устройства — сдвигаемся на его первый датчик.
        let after = self.sensor_rows();
        if after.is_empty() {
            self.sensor_selected = 0;
            return;
        }
        self.sensor_selected = self.sensor_selected.min(after.len() - 1);
        if matches!(after.get(self.sensor_selected), Some(SensorRow::Device(_))) {
            self.sensor_nav(1);
        }
    }

    pub fn begin_edit_focused(&mut self) {
        self.editing = Some(match self.tab {
            Tab::Registers => FieldEdit::Reg(self.reg_focus),
            Tab::Ports => FieldEdit::Tcp(self.tcp_focus),
            _ => FieldEdit::Sensor(self.sensor_focus),
        });
    }

    pub fn cancel_edit(&mut self) {
        self.editing = None;
    }

    pub fn edit_char(&mut self, c: char) {
        match self.editing {
            Some(FieldEdit::Reg(i)) => reg_form_field_mut(self, i).push(c),
            Some(FieldEdit::Sensor(i)) => sensor_form_field_mut(self, i).push(c),
            Some(FieldEdit::Tcp(i)) => tcp_form_field_mut(self, i).push(c),
            None => {}
        }
    }

    pub fn edit_backspace(&mut self) {
        let s = match self.editing {
            Some(FieldEdit::Reg(i)) => reg_form_field_mut(self, i),
            Some(FieldEdit::Sensor(i)) => sensor_form_field_mut(self, i),
            Some(FieldEdit::Tcp(i)) => tcp_form_field_mut(self, i),
            None => return,
        };
        s.pop();
    }

    pub fn is_editing(&self) -> bool {
        self.editing.is_some()
    }

    // --- Фоновые задачи (не блокируют UI) ---

    /// Конвертирует результат firmware-операции в `TaskPayload`.
    fn fw_result(p: crate::firmware::FwResult) -> Result<TaskPayload, String> {
        if p.ok {
            let lines = p.output.lines().map(|s| s.trim_end().to_string()).collect();
            Ok(TaskPayload::Lines(lines))
        } else {
            Err(if p.error.trim().is_empty() {
                p.output
            } else {
                p.error
            })
        }
    }

    /// Живой прогресс из esptool: строка в статус, в лог — каждые 5%.
fn task_progress(sink: &Sender<Event>, pct: u32, line: String) {
    let _ = sink.send(Event::TaskProgress { text: line.clone() });
    if pct.is_multiple_of(5) || pct >= 100 {
        let _ = sink.send(Event::Log(format!("[{}%] {}", pct, line)));
    }
}

/// Запускает фоновую задачу с индикацией спиннера.
    fn run_task<F>(&mut self, label: &str, job: F)
    where
        F: FnOnce(&Sender<Event>) -> Result<TaskPayload, String> + Send + 'static,
    {
        if self.busy.is_some() {
            return; // предыдущая задача ещё выполняется — игнорируем
        }
        let tx = self.runtime.events.clone();
        crate::worker::spawn_task(tx, label.to_string(), job);
        self.busy = Some(label.to_string());
        self.status_line = format!("⏳ {}…", label);
        self.log(0, format!("⏳ {}…", label));
    }

    /// Перед операцией с прошивкой освобождает занятый нами порт,
    /// чтобы esptool смог его открыть (иначе EBUSY). После операции
    /// handle_event сам переподключится, если мы были подключены.
    fn prepare_serial_task(&mut self, port: &str) {
        let was_connected = self.serial_port.as_deref() == Some(port);
        self.reconnect_after = Some((port.to_string(), was_connected));
        if was_connected {
            self.disconnect_serial();
        }
    }

    /// Прошивка: board-info текущего порта.
    pub fn fw_board_info(&mut self) {
        if self.busy.is_some() {
            return;
        }
        let port = self.fw_port.clone().or_else(|| {
            self.serial_port.clone()
        });
        let Some(port) = port else {
            self.log(2, "No port selected");
            return;
        };
        self.prepare_serial_task(&port);
        let fw_port = port.clone();
        self.run_task("board-info", move |_sink| {
            Self::fw_result(crate::firmware::board_info(&fw_port))
        });
    }

    pub fn fw_flash_test(&mut self) {
        if self.busy.is_some() {
            return;
        }
        let port = self.fw_port.clone().or_else(|| self.serial_port.clone());
        let Some(port) = port else {
            self.log(2, "No port selected");
            return;
        };
        let Some(bin) = crate::firmware::test_firmware_bin() else {
            self.log(2, "Firmware image not found (test-bench/firmware/esp32-modbus-fw.bin).");
            return;
        };
        self.prepare_serial_task(&port);
        let fw_port = port.clone();
        self.run_task("flash", move |sink| {
            let mut p = |pct: u32, line: String| Self::task_progress(sink, pct, line);
            Self::fw_result(crate::firmware::flash(&fw_port, &bin, &mut p))
        });
    }

    pub fn fw_backup(&mut self) {
        if self.busy.is_some() {
            return;
        }
        let port = self.fw_port.clone().or_else(|| self.serial_port.clone());
        let Some(port) = port else {
            self.log(2, "No port selected");
            return;
        };
        let stamp = crate::emulator::now_secs() as u64;
        let path = crate::firmware::backup_dir().join(format!("esp32_backup_{}.bin", stamp));
        self.prepare_serial_task(&port);
        self.log(0, "backup: reading full flash (up to 16MB) — takes ~1–3 min, spinner stays on until done, don't quit…");
        let fw_port = port.clone();
        self.run_task("backup", move |sink| {
            let mut p = |pct: u32, line: String| Self::task_progress(sink, pct, line);
            Self::fw_result(crate::firmware::backup(&fw_port, &path, 16, &mut p))
        });
    }

    pub fn fw_restore_selected(&mut self) {
        if self.busy.is_some() {
            return;
        }
        let port = self.fw_port.clone().or_else(|| self.serial_port.clone());
        let Some(port) = port else {
            self.log(2, "No port selected");
            return;
        };
        let Some(backup) = self.backups.get(self.backup_selected.min(self.backups.len().saturating_sub(1))) else {
            self.log(2, "No backup selected");
            return;
        };
        let path = crate::firmware::backup_dir().join(backup.clone());
        self.prepare_serial_task(&port);
        let fw_port = port.clone();
        self.run_task("restore", move |sink| {
            let mut p = |pct: u32, line: String| Self::task_progress(sink, pct, line);
            Self::fw_result(crate::firmware::restore(&fw_port, &path, &mut p))
        });
    }

    fn refresh_backups(&mut self) {
        self.backups.clear();
        if let Ok(rd) = std::fs::read_dir(crate::firmware::backup_dir()) {
            for entry in rd.flatten() {
                self.backups.push(entry.file_name().to_string_lossy().to_string());
            }
            self.backups.sort();
        }
        self.backup_selected = self.backups.len().saturating_sub(1);
    }

    /// Текущий выбранный порт (из списка Ports).
    pub fn selected_port(&self) -> Option<&PortInfo> {
        self.ports.get(self.selected_port.min(self.ports.len().saturating_sub(1)))
    }

    /// Проба Modbus-ответа выбранного порта (в фоне).
    pub fn probe_selected_port(&mut self) {
        let Some(name) = self.selected_port().map(|p| p.name.clone()) else {
            self.log(2, "No ports found");
            return;
        };
        // Порт уже опрашивается поллером — щупать его нельзя (сброс платы).
        if self.runtime.current_source.as_deref() == Some(name.as_str()) {
            self.log(2, format!("{} is already polled — no need to probe", name));
            return;
        }
        let port_name = name.clone();
        self.run_task("probe", move |_sink| {
            let r = crate::discover::probe_modbus(&port_name, 9600);
            let text = match r {
                Some(s) => format!("{} responds as slave {}", port_name, s),
                None => format!("{} no Modbus response", port_name),
            };
            Ok(TaskPayload::Lines(vec![text]))
        });
    }
}

/// Возвращает ссылку на выбранное поле формы регистров.
fn reg_form_field_mut(app: &mut App, i: usize) -> &mut String {
    match i {
        0 => &mut app.reg_form.slave_id,
        1 => &mut app.reg_form.start,
        2 => &mut app.reg_form.count,
        3 => &mut app.reg_form.addr,
        _ => &mut app.reg_form.value,
    }
}

/// Возвращает ссылку на выбранное поле формы датчиков.
fn sensor_form_field_mut(app: &mut App, i: usize) -> &mut String {
    match i {
        0 => &mut app.sensor_form.device_idx,
        1 => &mut app.sensor_form.name,
        2 => &mut app.sensor_form.base,
        3 => &mut app.sensor_form.amplitude,
        _ => &mut app.sensor_form.period,
    }
}

/// Возвращает ссылку на выбранное поле формы Modbus TCP master (вкладка Ports).
fn tcp_form_field_mut(app: &mut App, i: usize) -> &mut String {
    match i {
        0 => &mut app.tcp_host,
        _ => &mut app.tcp_port_str,
    }
}

impl Default for App {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn register_form_edit_focus_and_write_flow() {
        let mut app = App::new();
        app.connect_emulator();
        app.tab = Tab::Registers;
        assert_eq!(app.reg_focus, 0);

        // Фокус до поля Write value (индекс 4).
        app.move_focus(1);
        app.move_focus(1);
        app.move_focus(1);
        app.move_focus(1);
        assert_eq!(app.reg_focus, 4);

        // Начало редактирования, ввод "42" поверх пустого-дефолта... reg_form.value = "0" -> "042".
        app.begin_edit_focused();
        assert!(app.is_editing());
        app.edit_char('4');
        app.edit_char('2');
        assert_eq!(app.reg_form.value, "042");
        app.cancel_edit();
        assert!(!app.is_editing());

        // Запись в holding регистр 0 value=42 (reg_type по умолчанию = 1, HOLDING).
        app.write_register();
        let got = app
            .emu
            .lock()
            .unwrap()
            .read_holding_regs(1, 0, 1)
            .expect("holding reg readable");
        assert_eq!(got, vec![42]);

        // Чтение той же строки через форму.
        app.reg_form.value = String::new();
        app.read_registers();
        assert!(
            app.status_line.contains("[42,"),
            "status_line должен содержать результат чтения: {}",
            app.status_line
        );
    }

    #[test]
    fn sensor_form_edit_add_flow() {
        let mut app = App::new();
        app.connect_emulator();
        app.tab = Tab::Sensors;

        // Редактируем Device idx: редактирование поверх "0" даёт "01" -> устройство с индексом 1.
        app.sensor_focus = 0;
        app.begin_edit_focused();
        assert!(matches!(app.editing, Some(FieldEdit::Sensor(0))));
        app.edit_char('1');
        assert_eq!(app.sensor_form.device_idx, "01");
        app.cancel_edit();

        // Редактируем Name (дефолт "New Sensor", ввод дописывается).
        app.sensor_focus = 1;
        app.begin_edit_focused();
        app.edit_char('X');
        app.edit_char('-');
        app.edit_char('1');
        app.cancel_edit();
        assert_eq!(app.sensor_form.name, "New SensorX-1");

        // Backspace удаляет последний символ.
        app.sensor_focus = 2;
        app.begin_edit_focused();
        app.edit_char('9');
        assert_eq!(app.sensor_form.base, "25.09");
        app.edit_backspace();
        assert_eq!(app.sensor_form.base, "25.0");
        app.cancel_edit();

        // Добавляем датчик: device_idx "01" -> устройство с индексом 1 (Test Device).
        let binding = app.emu.lock().unwrap();
        let sensors_before = binding.devices()[1].sensors.len();
        drop(binding);
        app.add_sensor();
        let binding = app.emu.lock().unwrap();
        let dev = &binding.devices()[1];
        assert_eq!(dev.sensors.len(), sensors_before + 1);
        assert_eq!(app.sensor_form.name, "New SensorX-1");
    }

    #[test]
    fn move_focus_wraps_edit_stays_inactive() {
        let mut app = App::new();
        app.tab = Tab::Registers;
        // Регистры: 0..=4, вниз больше 4 -> зацикливание на 0.
        for _ in 0..3 {
            app.move_focus(1);
        }
        assert_eq!(app.reg_focus, 3);
        app.move_focus(1);
        assert_eq!(app.reg_focus, 4);
        app.move_focus(1);
        assert_eq!(app.reg_focus, 0);
        // Датчики: 0..=4.
        app.tab = Tab::Sensors;
        assert_eq!(app.sensor_focus, 0);
        app.move_focus(-1);
        assert_eq!(app.sensor_focus, 4);
        app.move_focus(-1);
        assert_eq!(app.sensor_focus, 3);
        assert!(!app.is_editing());
    }

    #[test]
    fn sensor_manage_toggle_moves_to_first_sensor() {
        let mut app = App::new();
        app.tab = Tab::Sensors;
        assert!(!app.sensor_manage);
        app.toggle_sensor_manage();
        assert!(app.sensor_manage);
        // Курсор встал на первый датчик (row 1 у default scenario).
        assert_eq!(app.sensor_selected, 1);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Sensor(0, 0)
        ));
        app.toggle_sensor_manage();
        assert!(!app.sensor_manage);
    }

    #[test]
    fn sensor_manage_nav_and_delete() {
        let mut app = App::new();
        app.connect_emulator();
        app.tab = Tab::Sensors;

        // Строки: [Device(0), S(0,0), S(0,1), S(0,2), Device(1), S(1,0), ...].
        // Курсор изначально на заголовке первого устройства.
        assert_eq!(app.sensor_selected, 0);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Device(0)
        ));

        // Вниз ходим по всем строкам, включая заголовки, с заворотом.
        app.sensor_nav(1);
        app.sensor_nav(1);
        app.sensor_nav(1);
        assert!(
            matches!(app.sensor_rows()[app.sensor_selected], SensorRow::Sensor(0, 2)),
            "3 шага -> последний датчик устройства 0 (idx {})",
            app.sensor_selected
        );

        // Шаг вниз за последний датчик -> заголовок следующего устройства.
        app.sensor_nav(1);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Device(1)
        ));

        // Вверх возвращает на последний датчик предыдущего устройства.
        app.sensor_nav(-1);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Sensor(0, 2)
        ));

        // С первой строки вверх -> заворачиваемся на последнюю (датчик Weather).
        app.sensor_selected = 0;
        app.sensor_nav(-1);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Sensor(3, 2)
        ));

        // С последней строки вниз -> первая (заголовок Pump Station).
        app.sensor_nav(1);
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Device(0)
        ));

        // Удаление датчика под курсором (последний датчик устройства 0).
        app.sensor_selected = 3;
        app.remove_selected();
        let binding = app.emu.lock().unwrap();
        let sensors = &binding.devices()[0].sensors;
        assert_eq!(sensors.len(), 2, "девайс 0: было 3, стало 2");
        let names: Vec<&str> = sensors.iter().map(|s| s.name.as_str()).collect();
        assert_eq!(names, vec!["T-101 Inlet", "T-202 Outlet"]);
        drop(binding);

        // Курсор после удаления указывает на валидный датчик (не заголовок).
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Sensor(..),
        ));
    }

    #[test]
    fn remove_device_via_manage_deletes_whole_slave() {
        let mut app = App::new();
        app.connect_emulator();
        app.tab = Tab::Sensors;

        // Встаём на заголовок Test Device (device 1) и удаляем всё устройство.
        app.sensor_selected = 4; // Device(1)
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Device(1)
        ));
        app.remove_selected();

        let binding = app.emu.lock().unwrap();
        let devs = binding.devices();
        assert_eq!(devs.len(), 3, "устройство Test Device удалено: 4 -> 3");
        let ids: Vec<u8> = devs.iter().map(|d| d.slave_id).collect();
        assert_eq!(ids, vec![1, 3, 4], "slave 2 ушёл, порядок сохранён");
        drop(binding);

        // Курсор встал на первый датчик нового "занявшего место" устройства.
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Sensor(..)
        ));
    }

    #[test]
    fn refuses_to_delete_last_device() {
        let mut app = App::new();
        app.connect_emulator();

        // Убираем все устройства, кроме одного (Weather Station славы).
        {
            let mut e = app.emu.lock().unwrap();
            e.remove_device(0);
            e.remove_device(0);
            e.remove_device(0);
        }
        assert_eq!(app.emu.lock().unwrap().devices().len(), 1);

        app.sensor_selected = 0; // единственный заголовок устройства
        assert!(matches!(
            app.sensor_rows()[app.sensor_selected],
            SensorRow::Device(0)
        ));
        app.remove_selected();
        assert_eq!(
            app.emu.lock().unwrap().devices().len(),
            1,
            "последнее устройство не удаляется"
        );
    }

    #[test]
    fn add_device_creates_unique_slave() {
        let mut app = App::new();
        app.connect_emulator();
        app.add_device();
        app.add_device();

        let binding = app.emu.lock().unwrap();
        let devs = binding.devices();
        assert_eq!(devs.len(), 6, "4 + 2 новых");
        let ids: Vec<u8> = devs.iter().map(|d| d.slave_id).collect();
        assert_eq!(ids, vec![1, 2, 3, 4, 5, 6], "slave id уникальны и растут");
        let names: Vec<&str> = devs.iter().map(|d| d.name.as_str()).collect();
        assert_eq!(names[4], "Device 1");
        assert_eq!(names[5], "Device 2");
        drop(binding);

        // В новое устройство можно сразу добавлять датчики.
        app.sensor_form.device_idx = "4".to_string();
        let before = app.emu.lock().unwrap().devices()[4].sensors.len();
        app.add_sensor();
        let after = app.emu.lock().unwrap().devices()[4].sensors.len();
        assert_eq!(after, before + 1);
    }

    #[test]
    fn many_devices_can_be_added() {
        let emu = crate::emulator::default_scenario();
        let e = emu.lock().unwrap();
        assert_eq!(e.devices().len(), 4);
        let ids: Vec<u8> = e.devices().iter().map(|d| d.slave_id).collect();
        assert_eq!(ids, vec![1, 2, 3, 4]);
    }

    #[test]
    fn select_dev_paged_steps_by_page() {
        let mut app = App::new();
        app.connect_emulator();
        app.selected_device = 1;
        app.select_dev_paged(1); // 1 + 6, но всего 4 → последний
        assert_eq!(app.selected_device, 3);
        app.select_dev_paged(-1); // 3 - 6 → 0
        assert_eq!(app.selected_device, 0);
    }

    #[test]
    fn set_selected_device_and_port_clamp() {
        let mut app = App::new();
        app.set_selected_device(2);
        assert_eq!(app.selected_device, 0); // пустой снапшот — игнор
        app.connect_emulator();
        app.set_selected_device(99);
        assert_eq!(app.selected_device, 0); // вне диапазона — игнор
        app.set_selected_device(2);
        assert_eq!(app.selected_device, 2);
        app.set_selected_port(7);
        assert_eq!(app.selected_port, 0);
    }

    #[test]
    fn mouse_scroll_drives_tabs() {
        let mut app = App::new();
        app.connect_emulator();
        app.tab = Tab::Dashboard;
        // Колесо на Dashboard листает список устройств/датчиков построчно.
        app.mouse_scroll(1);
        assert_eq!(app.dash_scroll, 1);
        app.mouse_scroll(1);
        assert_eq!(app.dash_scroll, 2);
        app.mouse_scroll(-1);
        assert_eq!(app.dash_scroll, 1);
        app.mouse_scroll(-1);
        assert_eq!(app.dash_scroll, 0);
        app.mouse_scroll(-1);
        assert_eq!(app.dash_scroll, 0); // не уходить в минус

        app.tab = Tab::Ports;
        app.ports.push(PortInfo {
            name: "/dev/ttyACM0".into(),
            description: String::new(),
            is_esp_like: false,
            has_firmware: false,
            product: None,
        });
        app.ports.push(PortInfo {
            name: "/dev/ttyACM1".into(),
            description: String::new(),
            is_esp_like: false,
            has_firmware: false,
            product: None,
        });
        app.mouse_scroll(1);
        app.mouse_scroll(1);
        assert_eq!(app.selected_port, 1); // не выходит за границы
        app.mouse_scroll(-1);
        app.mouse_scroll(99);
        assert_eq!(app.selected_port, 1);

        app.tab = Tab::Log;
        app.log(0, "line");
        app.mouse_scroll(1);
        app.mouse_scroll(-1);
        app.mouse_scroll(-1); // не уходит в минус
        assert_eq!(app.log_scroll, 0);

        app.tab = Tab::Help;
        app.mouse_scroll(1);
        app.mouse_scroll(1);
        app.mouse_scroll(1);
        assert_eq!(app.help_scroll, 3);
    }
}
