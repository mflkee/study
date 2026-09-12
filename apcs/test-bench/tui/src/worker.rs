//! Фоновые потоки: сканирование портов (hotplug) и опрос источника данных.

use std::sync::mpsc::{channel, Receiver, Sender, TryRecvError};

fn mpsc_channel<T>() -> (Sender<T>, Receiver<T>) {
    channel()
}
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

use crate::discover::{self, ScanResult};
use crate::emulator::SharedEmulator;
use crate::master::SerialMaster;

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
}

impl PollSource {
    pub fn key(&self) -> String {
        match self {
            PollSource::Emu(_) => "EMU".to_string(),
            PollSource::Serial { port, .. } => port.clone(),
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
    pub at: f64,
}

/// Событие, отправляемое в UI-поток.
pub enum Event {
    /// Периодический тик (перерисовка).
    Ticked,
    /// Результат сканирования портов.
    Scan(ScanResult),
    /// Результат пробы прошивки на порту.
    Probe { port: String, slave: Option<u8> },
    /// Свежий снимок данных с текущего источника.
    Snapshot { source: String, devices: Vec<DeviceSnapshot> },
    /// Ошибка опроса (например, порт выдернули).
    PollError(String),
    /// Строка в лог.
    Log(String),
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
            poll_interval_ms: 500,
        }
    }

    /// Запускает поток сканирования портов (каждые 2 с).
    pub fn start_scanner(&mut self) {
        if self.scan_handle.is_some() {
            return;
        }
        let tx = self.events.clone();
        self.scan_handle = Some(thread::spawn(move || {
            loop {
                let ports = discover::list_ports();
                let mut ports = ports;
                // Проба прошивки только для esp-like портов — лёгкая, по быстрому таймауту.
                for p in &mut ports {
                    if p.is_esp_like {
                        let _ = discover::probe_modbus(&p.name, 9600).map(|slave| {
                            let _ = tx.send(Event::Probe {
                                port: p.name.clone(),
                                slave: Some(slave),
                            });
                            p.has_firmware = true;
                        });
                    }
                }
                let result = ScanResult::new(ports);
                if tx.send(Event::Scan(result)).is_err() {
                    break;
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
    let source_key = source.key();
    loop {
        match stop.try_recv() {
            Ok(()) | Err(TryRecvError::Disconnected) => break,
            Err(TryRecvError::Empty) => {}
        }

        let devices = poll_devices(&source);
        match devices {
            Ok(devs) => {
                if events
                    .send(Event::Snapshot {
                        source: source_key.clone(),
                        devices: devs,
                    })
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

fn poll_devices(source: &PollSource) -> Result<Vec<DeviceSnapshot>, String> {
    match source {
        PollSource::Emu(emu) => poll_emulator(emu),
        PollSource::Serial { master, port, baud } => poll_serial(master, port, *baud),
    }
}

fn poll_emulator(emu: &SharedEmulator) -> Result<Vec<DeviceSnapshot>, String> {
    let mut emu = emu.lock().map_err(|e| e.to_string())?;
    emu.tick();
    let devices = emu.devices().to_vec();
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
            at: crate::emulator::now_secs(),
        });
    }
    Ok(out)
}

fn poll_serial(
    master: &Arc<Mutex<SerialMaster>>,
    port: &str,
    baud: u32,
) -> Result<Vec<DeviceSnapshot>, String> {
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

    let mut channels = Vec::new();
    for (i, pair) in input.chunks_exact(2).enumerate() {
        let value = crate::frames::float_from_regs(pair[0], pair[1]);
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

    Ok(vec![DeviceSnapshot {
        slave_id: slave,
        name: format!("ESP32 @ {}", port),
        source: port.to_string(),
        channels,
        coils,
        holding,
        at: crate::emulator::now_secs(),
    }])
}

impl Default for Runtime {
    fn default() -> Self {
        Self::new()
    }
}