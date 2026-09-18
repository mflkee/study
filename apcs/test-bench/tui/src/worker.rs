//! Фоновые потоки: сканирование портов (hotplug) и опрос источника данных.

use std::sync::mpsc::{channel, Receiver, Sender, TryRecvError};

fn mpsc_channel<T>() -> (Sender<T>, Receiver<T>) {
    channel()
}
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

use crate::discover::{self, ScanResult};
use crate::emulator::SharedEmulator;
use crate::frames;
use crate::master::{fc_name, SerialMaster, TraceEntry};
use crate::tcp_master::TcpMaster;

/// Источник данных для опроса.
pub enum PollSource {
    /// Встроенный эмулятор (тестовый сервер).
    Emu(SharedEmulator),
    /// Реальное устройство на последовательном порту.
    Serial {
        port: String,
        baud: u32,
        master: Arc<Mutex<SerialMaster>>,
    },
    /// Modbus TCP устройство (AI-32, собственный TCP-сервер TUI и т.п.).
    Tcp {
        host: String,
        port: u16,
        master: Arc<Mutex<TcpMaster>>,
    },
}

impl PollSource {
    pub fn key(&self) -> String {
        match self {
            PollSource::Emu(_) => "EMU".to_string(),
            PollSource::Serial { port, .. } => port.clone(),
            PollSource::Tcp { host, port, .. } => format!("tcp://{}:{}", host, port),
        }
    }
}

/// Канал датчика (float32 в паре input-регистров).
#[derive(Debug, Clone)]
pub struct Channel {
    pub label: String,
    pub reg: u16,
    pub value: f32,
    pub unit: String,
}

/// Снимок состояния одного устройства.
#[derive(Debug, Clone)]
pub struct DeviceSnapshot {
    pub slave_id: u8,
    pub name: String,
    pub source: String,
    pub channels: Vec<Channel>,
    pub coils: Vec<bool>,
    pub holding: Vec<u16>,
}

/// Результат фоновой задачи (запускается кнопками, не блокирует UI).
pub enum TaskPayload {
    /// Текстовый результат — строки для лога/статуса.
    Lines(Vec<String>),
    /// Успешно открытый Modbus-мастер (подключение к порту).
    Master {
        port: String,
        master: Arc<Mutex<SerialMaster>>,
    },
    /// Успешно открытый Modbus TCP мастер (подключение к устройству).
    TcpMaster {
        host: String,
        port: u16,
        master: Arc<Mutex<TcpMaster>>,
    },
}

/// Событие, отправляемое в UI-поток.
pub enum Event {
    /// Периодический тик (перерисовка).
    Ticked,
    /// Результат сканирования портов.
    Scan(ScanResult),
    /// Результат пробы прошивки на порту.
    Probe { port: String, slave: Option<u8> },
    /// Свежий снимок данных с текущего источника + кадры, ушедшие в шину.
    Snapshot {
        devices: Vec<DeviceSnapshot>,
        trace: Vec<TraceEntry>,
    },
    /// Ошибка опроса (например, порт выдернули).
    PollError(String),
    /// Строка в лог.
    Log(String),
    /// Живой прогресс фоновой задачи (для статус-строки).
    TaskProgress { text: String },
    /// Завершение фоновой задачи (connect/flash/board-info/probe...).
    TaskDone { label: String, result: Result<TaskPayload, String> },
}

/// Запускает фоновую задачу: выполняется в отдельном потоке, результат
/// приходит в UI-поток как [`Event::TaskDone`]. Пока задача «в работе»,
/// интерфейс не замирает (спиннер в шапке). Через `&Sender<Event>` задача
/// может слать живой прогресс ([`Event::TaskProgress`]) и строки в лог.
pub fn spawn_task<F>(events: Sender<Event>, label: impl Into<String>, job: F)
where
    F: FnOnce(&Sender<Event>) -> Result<TaskPayload, String> + Send + 'static,
{
    let label = label.into();
    thread::spawn(move || {
        let sink = events.clone();
        let result = job(&sink);
        let _ = events.send(Event::TaskDone { label, result });
    });
}

/// Управление фоновыми потоками.
pub struct Runtime {
    pub events: Sender<Event>,
    pub events_rx: Receiver<Event>,
    scan_handle: Option<JoinHandle<()>>,
    poll_handle: Option<JoinHandle<()>>,
    poll_stop: Option<Sender<()>>,
    /// Текущий опрашиваемый источник (для UI).
    pub current_source: Option<String>,
    /// Порт, который сейчас занят полноценным опросом (поллером).
    /// Сканер не должен открывать/щупать этот же порт — иначе он
    /// перезагружает плату (DTR/RTS auto-reset) и ломает поллинг.
    poll_port: Arc<Mutex<Option<String>>>,
    pub poll_interval_ms: u64,
}

impl Runtime {
    pub fn new() -> Self {
        let (tx, rx) = mpsc_channel();
        Self {
            events: tx,
            events_rx: rx,
            scan_handle: None,
            poll_handle: None,
            poll_stop: None,
            current_source: None,
            poll_port: Arc::new(Mutex::new(None)),
            poll_interval_ms: 500,
        }
    }

    /// Запускает поток сканирования портов (каждые 2 с).
    pub fn start_scanner(&mut self) {
        if self.scan_handle.is_some() {
            return;
        }
        let tx = self.events.clone();
        let poll_port = self.poll_port.clone();
        self.scan_handle = Some(thread::spawn(move || {
            // Порты, для которых проба уже запущена (или выполнена) в этом сеансе.
            // Пробу открывает порт — это дёргает DTR/RTS и перезагружает плату,
            // поэтому делаем её ровно один раз за сеанс и только в фоне.
            let mut tried: std::collections::HashSet<String> = Default::default();
            loop {
                let ports = discover::list_ports();
                // Список шлётся немедленно — вкладка Ports заполняется всегда,
                // даже если проба на каком-то порту зависнет.
                if tx
                    .send(Event::Scan(ScanResult::new(ports.clone())))
                    .is_err()
                {
                    break;
                }

                // Какой порт занят поллером — его не трогаем (не открываем!).
                let busy = poll_port.lock().unwrap().clone();
                for p in &ports {
                    if !p.is_esp_like || tried.contains(&p.name) {
                        continue;
                    }
                    if busy.as_deref() == Some(p.name.as_str()) {
                        continue;
                    }
                    // Нативный USB-JTAG-порт ESP32 — это не Modbus-таргет.
                    let is_jtag = p.product.as_deref().is_some_and(|pr| {
                        pr.to_lowercase().contains("jtag") || pr.to_lowercase().contains("debug unit")
                    });
                    if is_jtag {
                        tried.insert(p.name.clone());
                        continue;
                    }
                    tried.insert(p.name.clone());
                    let tx = tx.clone();
                    let name = p.name.clone();
                    thread::spawn(move || {
                        let slave = discover::probe_modbus(&name, 9600);
                        let _ = tx.send(Event::Probe {
                            port: name,
                            slave,
                        });
                    });
                }

                // Вместо сна — проверяем, не закрыли ли канал (UI ушёл),
                // отправляя лёгкие тики; при ошибке — выходим.
                for _ in 0..20 {
                    thread::sleep(std::time::Duration::from_millis(100));
                    if tx.send(Event::Ticked).is_err() {
                        return;
                    }
                }
            }
        }));
    }

    /// Перезапускает опрос с новым источником (останавливает старый).
    pub fn set_source(&mut self, source: PollSource) {
        self.stop_poller();
        let (stop_tx, stop_rx) = mpsc_channel();
        let tx = self.events.clone();
        let interval = self.poll_interval_ms;
        // Сканеру сообщаем, какой физический порт теперь занят.
        *self.poll_port.lock().unwrap() = match &source {
            PollSource::Serial { port, .. } => Some(port.clone()),
            PollSource::Emu(_) | PollSource::Tcp { .. } => None,
        };
        self.poll_stop = Some(stop_tx);
        self.current_source = Some(source.key());
        let handle = thread::spawn(move || poll_loop(source, stop_rx, tx, interval));
        self.poll_handle = Some(handle);
    }

    pub fn stop_poller(&mut self) {
        if let Some(stop) = self.poll_stop.take() {
            let _ = stop.send(());
        }
        if let Some(h) = self.poll_handle.take() {
            let _ = h.join();
        }
        *self.poll_port.lock().unwrap() = None;
        self.current_source = None;
    }
}

/// Поток опроса источника данных.
fn poll_loop(
    source: PollSource,
    stop: Receiver<()>,
    events: Sender<Event>,
    interval_ms: u64,
) {
    loop {
        match stop.try_recv() {
            Ok(()) | Err(TryRecvError::Disconnected) => break,
            Err(TryRecvError::Empty) => {}
        }

        match poll_devices(&source) {
            Ok((devs, trace)) => {
                if events
                    .send(Event::Snapshot { devices: devs, trace })
                    .is_err()
                {
                    break;
                }
            }
            Err(e) => {
                if events.send(Event::PollError(e)).is_err() {
                    break;
                }
                // После ошибки даём время (например, порт занят) — не зацикливаемся.
                thread::sleep(std::time::Duration::from_millis(interval_ms));
                continue;
            }
        }
        // Пауза с учётом команды остановки.
        for _ in 0..10 {
            thread::sleep(std::time::Duration::from_millis(interval_ms / 10));
            if matches!(stop.try_recv(), Ok(()) | Err(TryRecvError::Disconnected)) {
                return;
            }
        }
    }
}

fn poll_devices(source: &PollSource) -> Result<(Vec<DeviceSnapshot>, Vec<TraceEntry>), String> {
    match source {
        PollSource::Emu(emu) => {
            let devs = poll_emulator(emu)?;
            Ok((devs.clone(), emu_traces(&devs)))
        }
        PollSource::Serial { master, port, baud } => poll_serial(master, port, *baud),
        PollSource::Tcp { master, host, port } => poll_tcp(master, host, *port),
    }
}

/// Срез эмулятора в `DeviceSnapshot`-ы. При `tick=true` сначала advance-им симуляцию.
pub(crate) fn emu_snapshot(emu: &SharedEmulator, tick: bool) -> Result<Vec<DeviceSnapshot>, String> {
    let mut emu = emu.lock().map_err(|e| e.to_string())?;
    if tick {
        emu.tick();
    }
    let devices = emu.devices().to_vec();
    drop(emu);
    let mut out = Vec::new();
    for dev in &devices {
        let mut channels = Vec::new();
        for s in &dev.sensors {
            let unit = match s.data_type {
                crate::emulator::DataType::TemperatureC => "°C".into(),
                crate::emulator::DataType::PressureKPa => "kPa".into(),
                crate::emulator::DataType::Flow => "%".into(),
                crate::emulator::DataType::Test => "".into(),
                crate::emulator::DataType::LevelM => "m".into(),
                crate::emulator::DataType::HumidityPct => "%".into(),
            };
            channels.push(Channel {
                label: s.name.clone(),
                reg: s.input_reg,
                value: s.last_value,
                unit,
            });
        }
        let n_coils = 8;
        let mut coils = Vec::with_capacity(n_coils);
        for c in 0..n_coils {
            coils.push(dev.coils.get(&(c as u16)).copied().unwrap_or(false));
        }
        let n_holding = 32;
        let mut holding = Vec::with_capacity(n_holding);
        for h in 0..n_holding {
            holding.push(dev.holding_regs.get(&(h as u16)).copied().unwrap_or(0));
        }
        out.push(DeviceSnapshot {
            slave_id: dev.slave_id,
            name: dev.name.clone(),
            source: "EMU".into(),
            channels,
            coils,
            holding,
        });
    }
    Ok(out)
}

fn poll_emulator(emu: &SharedEmulator) -> Result<Vec<DeviceSnapshot>, String> {
    emu_snapshot(emu, true)
}

fn poll_serial(
    master: &Arc<Mutex<SerialMaster>>,
    port: &str,
    baud: u32,
) -> Result<(Vec<DeviceSnapshot>, Vec<TraceEntry>), String> {
    let mut m = master.lock().map_err(|e| e.to_string())?;
    let slave = m.slave_id();
    let _ = baud;

    // Читаем карту как в прошивке ESP32:
    //   Input Registers 0-29  → 15 температур (float32)
    //   Input Registers 30-59 → 15 давлений  (float32)
    //   Coils 0-1              → 2 насоса
    //   Holding 0-31          → конфигурация
    let input = m
        .read_input_registers(0, 60)
        .map_err(|e| format!("{}: input read failed: {}", port, e))?;
    let coils = m.read_coils(0, 2).map_err(|e| e.to_string())?;
    let holding = m
        .read_holding_registers(0, 16)
        .map_err(|e| e.to_string())?;
    let trace = m.drain_trace();

    let mut channels = Vec::new();
    let (pairs, _) = input.as_chunks::<2>();
    for (i, pair) in pairs.iter().enumerate() {
        let value = frames::float_from_regs(pair[0], pair[1]);
        let (label, unit) = if i < 15 {
            (format!("T-{:02}", i), "°C")
        } else {
            (format!("P-{:02}", i - 15), "kPa")
        };
        channels.push(Channel {
            label,
            reg: (i * 2) as u16,
            value,
            unit: unit.into(),
        });
    }

    Ok((
        vec![DeviceSnapshot {
            slave_id: slave,
            name: format!("ESP32 @ {}", port),
            source: port.to_string(),
            channels,
            coils,
            holding,
        }],
        trace,
    ))
}

/// Опрашивает Modbus TCP устройство: та же карта, что у serial-поллера
/// (input-регистры как float32, coils, holding), но без CRC — MBAP.
///
/// Читаем input-регистры по максимуму (64 → 32 float-канала; модуль AI-32
/// отдаёт ровно их). Если устройство умеет меньше (например, 30 регистров) —
/// сервер пришлёт exception 0x02, и плавно откатываемся к 60 (30 каналов).
fn poll_tcp(
    master: &Arc<Mutex<TcpMaster>>,
    host: &str,
    port: u16,
) -> Result<(Vec<DeviceSnapshot>, Vec<TraceEntry>), String> {
    let mut m = master.lock().map_err(|e| e.to_string())?;
    let slave = m.unit();

    let input = match m.read_input_registers(0, 64) {
        Ok(v) => v,
        Err(frames::ModbusError::IllegalAddress) => m
            .read_input_registers(0, 60)
            .map_err(|e| format!("{}:{}: input read failed: {}", host, port, e))?,
        Err(e) => return Err(format!("{}:{}: input read failed: {}", host, port, e)),
    };
    let coils = m.read_coils(0, 2).map_err(|e| e.to_string())?;
    let holding = m
        .read_holding_registers(0, 16)
        .map_err(|e| e.to_string())?;
    let trace = m.drain_trace();

    let (pairs, _) = input.as_chunks::<2>();
    let mut channels = Vec::with_capacity(pairs.len());
    for (i, pair) in pairs.iter().enumerate() {
        let value = frames::float_from_regs(pair[0], pair[1]);
        channels.push(Channel {
            label: format!("CH-{:02}", i),
            reg: (i * 2) as u16,
            value,
            // Единица измерения зависит от устройства (мА у AI-32, °C/kPa
            // у эмулятора) — оставляем пустой, не угадывая.
            unit: String::new(),
        });
    }

    Ok((
        vec![DeviceSnapshot {
            slave_id: slave,
            name: format!("Modbus TCP {}:{}", host, port),
            source: "TCP".to_string(),
            channels,
            coils,
            holding,
        }],
        trace,
    ))
}

/// Собирает «образцовые» кадры для эмулятора: те же три запроса, что ходят
/// по реальной шине (input/coils/holding), с данными из состояния эмулятора.
/// Чтобы вкладка Bus одинаково учила и на эмуляторе, и на железе.
fn emu_traces(devices: &[DeviceSnapshot]) -> Vec<TraceEntry> {
    let mut out = Vec::new();
    for dev in devices {
        let slave = dev.slave_id;

        // READ INPUT REGISTERS (0x04): 60 регистров = 30 float32 (15 T + 15 P).
        let req = frames::build_request(slave, frames::FC_READ_INPUT, &frames::read_pdu(0, 60));
        let mut resp: Vec<u8> = vec![slave, frames::FC_READ_INPUT, 120];
        for i in 0..60u16 {
            let bits = dev
                .channels
                .iter()
                .find(|c| c.reg == i)
                .map(|c| c.value.to_bits())
                .unwrap_or(0);
            resp.extend_from_slice(&(((bits >> 16) & 0xFFFF) as u16).to_be_bytes());
            resp.extend_from_slice(&((bits & 0xFFFF) as u16).to_be_bytes());
        }
        crate::crc::append(&mut resp);
        out.push(TraceEntry {
            transport: "RTU",
            fc: fc_name(frames::FC_READ_INPUT),
            req: crate::crc::to_hex(&req),
            resp: crate::crc::to_hex(&resp),
            ok: true,
            err: None,
            ms: 5,
        });

        // READ COILS (0x01): 2 катушки.
        let req = frames::build_request(slave, frames::FC_READ_COILS, &frames::read_pdu(0, 2));
        let mut bits = 0u8;
        for (ci, c) in dev.coils.iter().take(2).enumerate() {
            if *c {
                bits |= 1 << ci;
            }
        }
        let mut resp: Vec<u8> = vec![slave, frames::FC_READ_COILS, 1, bits];
        crate::crc::append(&mut resp);
        out.push(TraceEntry {
            transport: "RTU",
            fc: fc_name(frames::FC_READ_COILS),
            req: crate::crc::to_hex(&req),
            resp: crate::crc::to_hex(&resp),
            ok: true,
            err: None,
            ms: 4,
        });

        // READ HOLDING (0x03): 16 регистров.
        let req = frames::build_request(slave, frames::FC_READ_HOLDING, &frames::read_pdu(0, 16));
        let mut resp: Vec<u8> = vec![slave, frames::FC_READ_HOLDING, 32];
        for v in dev.holding.iter().take(16) {
            resp.extend_from_slice(&v.to_be_bytes());
        }
        crate::crc::append(&mut resp);
        out.push(TraceEntry {
            transport: "RTU",
            fc: fc_name(frames::FC_READ_HOLDING),
            req: crate::crc::to_hex(&req),
            resp: crate::crc::to_hex(&resp),
            ok: true,
            err: None,
            ms: 4,
        });
    }
    out
}

impl Default for Runtime {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hex_to_bytes(s: &str) -> Vec<u8> {
        s.split_whitespace()
            .filter_map(|h| u8::from_str_radix(h, 16).ok())
            .collect()
    }

    #[test]
    fn emu_traces_produce_valid_frames() {
        let emu = crate::emulator::default_scenario();
        let devs = emu_snapshot(&emu, false).unwrap();
        assert_eq!(devs.len(), 4);

        let traces = emu_traces(&devs);
        // По 3 транзакции на устройство (input/coils/holding).
        assert_eq!(traces.len(), devs.len() * 3);

        // Каждый кадр: валидный CRC, корректный slave-адрес, имя функции логично.
        for (i, t) in traces.iter().enumerate() {
            let dev = &devs[i / 3];
            let req = hex_to_bytes(&t.req);
            let resp = hex_to_bytes(&t.resp);
            assert!(crate::crc::verify(&req), "bad CRC in request {i}");
            assert!(crate::crc::verify(&resp), "bad CRC in response {i}");
            assert_eq!(req[0], dev.slave_id);
            assert_eq!(resp[0], dev.slave_id);
            assert!(t.ok);
            assert!(t.err.is_none());
        }
    }

    #[test]
    fn emu_trace_input_regs_match_snapshot() {
        let emu = crate::emulator::default_scenario();
        let devs = emu_snapshot(&emu, false).unwrap();
        let traces = emu_traces(&devs);

        // Первый slave (Pump Station): первый input-кадр должен декодироваться
        // в те же float32, что и первый канал снимка.
        let t = &traces[0];
        let resp = hex_to_bytes(&t.resp);
        assert_eq!(resp[0], 1);
        assert_eq!(resp[1], frames::FC_READ_INPUT);
        let byte_count = resp[2] as usize;
        assert_eq!(byte_count, 120);
        let mut regs = Vec::new();
        let (data, _) = resp[3..3 + byte_count].as_chunks::<2>();
        for pair in data {
            regs.push(u16::from_be_bytes([pair[0], pair[1]]));
        }
        let first = frames::float_from_regs(regs[0], regs[1]);
        let expected = devs[0].channels[0].value;
        assert!((first - expected).abs() < 0.001, "got {first}, expected {expected}");
    }
}
#[cfg(test)]
mod scan_live_tests {
    use super::{Event, Runtime};

    #[test]
    #[ignore]
    fn scan_events_flow() {
        let mut rt = Runtime::new();
        rt.start_scanner();
        let mut scans = 0;
        let t0 = std::time::Instant::now();
        while t0.elapsed() < std::time::Duration::from_secs(8) {
            match rt.events_rx.recv_timeout(std::time::Duration::from_secs(8)) {
                Ok(Event::Scan(s)) => {
                    scans += 1;
                    let esp = s.ports.iter().filter(|p| p.is_esp_like).count();
                    eprintln!(
                        "[{}ms] Scan #{} ports={} esp_like={}",
                        t0.elapsed().as_millis(),
                        scans,
                        s.ports.len(),
                        esp
                    );
                    if scans >= 2 {
                        break;
                    }
                }
                Ok(_) => {}
                Err(_) => break,
            }
        }
        assert!(scans >= 2, "expected >=2 Scan events, got {}", scans);
    }
}
