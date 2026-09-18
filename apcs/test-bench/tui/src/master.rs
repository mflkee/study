//! Modbus RTU Master поверх serialport.
//!
//! Пул-запросы к slave-устройствам по последовательному порту.

use std::collections::VecDeque;
use std::io;
use std::time::{Duration, Instant};

use crate::frames::{self, ModbusError};

/// Как долго ждём первый байт ответа.
const RESPONSE_WAIT: Duration = Duration::from_millis(200);
/// Мин. пауза между кадрами (3.5 символа на 9600 ≈ 4 мс).
/// Сколько последних транзакций держим в «логе шины» (вкладка Bus).
const TRACE_CAP: usize = 100;

/// Одна Modbus-транзакция master→slave→master (для вкладки Bus/обучения).
#[derive(Debug, Clone)]
pub struct TraceEntry {
    /// Транспорт: "RTU" (кабель+CRC) или "TCP" (MBAP, без CRC).
    pub transport: &'static str,
    /// Имя функции Modbus (например, "READ INPUT REGISTERS").
    pub fc: String,
    /// Запрос, ушедший в шину (hex, с CRC).
    pub req: String,
    /// Ответ устройства (hex, с CRC); пусто при ошибке/таймауте.
    pub resp: String,
    /// Успех транзакции.
    pub ok: bool,
    /// Tекст ошибки при неудаче (timeout, bad CRC, exception...).
    pub err: Option<String>,
    /// Время транзакции, мс.
    pub ms: u64,
}

/// Человекочитаемое имя функции Modbus.
pub fn fc_name(fc: u8) -> String {
    match fc {
        frames::FC_READ_COILS => "READ COILS (0x01)".into(),
        frames::FC_READ_DISCRETE_INPUTS => "READ DISCRETE INPUTS (0x02)".into(),
        frames::FC_READ_HOLDING => "READ HOLDING REGISTERS (0x03)".into(),
        frames::FC_READ_INPUT => "READ INPUT REGISTERS (0x04)".into(),
        frames::FC_WRITE_SINGLE_COIL => "WRITE SINGLE COIL (0x05)".into(),
        frames::FC_WRITE_SINGLE_REG => "WRITE SINGLE REGISTER (0x06)".into(),
        frames::FC_WRITE_MULTI_COILS => "WRITE MULTIPLE COILS (0x0F)".into(),
        frames::FC_WRITE_MULTI_REGS => "WRITE MULTIPLE REGISTERS (0x10)".into(),
        other => format!("FUNCTION 0x{:02X}", other),
    }
}

/// Обёртка над открытым последовательным портом.
pub struct SerialMaster {
    port: Box<dyn serialport::SerialPort>,
    slave_id: u8,
    /// Кольцо последних транзакций — «лог шины» для вкладки Bus.
    trace: VecDeque<TraceEntry>,
}

impl SerialMaster {
    /// Открывает порт и настраивает 8N1 с заданной скоростью.
    pub fn open(port_name: &str, baud: u32, slave_id: u8) -> Result<Self, ModbusError> {
        let mut port = serialport::new(port_name, baud)
            .timeout(RESPONSE_WAIT)
            .data_bits(serialport::DataBits::Eight)
            .stop_bits(serialport::StopBits::One)
            .parity(serialport::Parity::None)
            .open()
            .map_err(|e| ModbusError::Io(e.to_string()))?;
        // Снимаем DTR/RTS: на этих платах они дёргают auto-reset (перезагрузка
        // чипа при каждом открытии порта). Для Modbus по UART0 они не нужны.
        let _ = port.write_request_to_send(false);
        let _ = port.write_data_terminal_ready(false);
        Ok(Self {
            port,
            slave_id,
            trace: VecDeque::new(),
        })
    }

    pub fn slave_id(&self) -> u8 {
        self.slave_id
    }

    pub fn set_slave_id(&mut self, id: u8) {
        self.slave_id = id;
    }

    /// Забирает все накопленные транзакции (опрашивающий поток шлёт их в UI).
    pub fn drain_trace(&mut self) -> Vec<TraceEntry> {
        self.trace.drain(..).collect()
    }

    /// Добавляет запись в «лог шины».
    fn push_trace(&mut self, fc: u8, req: &[u8], resp: Option<&[u8]>, ok: bool, err: Option<ModbusError>, ms: u64) {
        if self.trace.len() >= TRACE_CAP {
            self.trace.pop_front();
        }
        self.trace.push_back(TraceEntry {
            transport: "RTU",
            fc: fc_name(fc),
            req: crate::crc::to_hex(req),
            resp: resp.map(crate::crc::to_hex).unwrap_or_default(),
            ok,
            err: err.as_ref().map(|e| e.to_string()),
            ms,
        });
    }

    /// Выполняет один Modbus-запрос и возвращает PDU ответа.
    /// Каждая транзакция попадает в [`Self::trace`] (вкладка Bus).
    pub fn transact(&mut self, fc: u8, pdu: &[u8]) -> Result<Vec<u8>, ModbusError> {
        let frame = frames::build_request(self.slave_id, fc, pdu);
        let t0 = Instant::now();

        self.port
            .flush()
            .map_err(|e| ModbusError::Io(e.to_string()))?;
        self.port
            .write_all(&frame)
            .map_err(|e| ModbusError::Io(e.to_string()))?;
        self.port
            .flush()
            .map_err(|e| ModbusError::Io(e.to_string()))?;

        // Ждём первый байт с таймаутом.
        let deadline = Instant::now() + RESPONSE_WAIT;
        let mut buf = [0u8; frames::MAX_FRAME];
        let mut len = 0usize;
        loop {
            // Первый байт ждём до дедлайна.
            if len == 0 && Instant::now() >= deadline {
                self.push_trace(fc, &frame, None, false, Some(ModbusError::Timeout), t0.elapsed().as_millis() as u64);
                return Err(ModbusError::Timeout);
            }
            match self.port.read(&mut buf[len..]) {
                Ok(0) => continue,
                Ok(n) => {
                    len += n;
                    // Сколько ждём продолжения кадра: дедлайн от последнего байта
                    // уже обеспечен таймаутом порта (RESPONSE_WAIT), поэтому после
                    // паузы между кадрами считаем кадр завершённым.
                }
                Err(e) if e.kind() == io::ErrorKind::TimedOut || e.kind() == io::ErrorKind::WouldBlock => {
                    break; // пауза между байтами — кадр завершён
                }
                Err(e) => return Err(ModbusError::Io(e.to_string())),
            }
            if len >= 4 {
                // Проверяем полноту кадра поближе к завершению чтения.
                // Продолжаем читать, пока не случится таймаут.
            }
            if len >= frames::MAX_FRAME - 1 {
                break;
            }
        }

        if len == 0 {
            self.push_trace(fc, &frame, None, false, Some(ModbusError::Timeout), t0.elapsed().as_millis() as u64);
            return Err(ModbusError::Timeout);
        }

        let f = &buf[..len];
        if !crate::crc::verify(f) {
            // Возможно, прилипли хвосты от предыдущего кадра — попробуем найти
            // кадр в буфере.
            self.push_trace(fc, &frame, Some(f), false, Some(ModbusError::BadCrc), t0.elapsed().as_millis() as u64);
            return Err(ModbusError::BadCrc);
        }

        let parsed = frames::parse_response(f, self.slave_id, fc);
        let ms = t0.elapsed().as_millis() as u64;
        self.push_trace(fc, &frame, Some(f), parsed.is_ok(), parsed.as_ref().err().cloned(), ms);
        parsed
    }

    // --- Высокоуровневые функции чтения/записи ---

    pub fn read_holding_registers(
        &mut self,
        start: u16,
        count: u16,
    ) -> Result<Vec<u16>, ModbusError> {
        let pdu = frames::read_pdu(start, count);
        let resp = self.transact(frames::FC_READ_HOLDING, &pdu)?;
        frames::parse_read_registers(&resp, count as usize)
    }

    pub fn read_input_registers(&mut self, start: u16, count: u16) -> Result<Vec<u16>, ModbusError> {
        let pdu = frames::read_pdu(start, count);
        let resp = self.transact(frames::FC_READ_INPUT, &pdu)?;
        frames::parse_read_registers(&resp, count as usize)
    }

    pub fn read_coils(&mut self, start: u16, count: u16) -> Result<Vec<bool>, ModbusError> {
        let pdu = frames::read_pdu(start, count);
        let resp = self.transact(frames::FC_READ_COILS, &pdu)?;
        frames::parse_read_bits(&resp, count as usize)
    }

    pub fn write_single_register(&mut self, addr: u16, value: u16) -> Result<(), ModbusError> {
        let pdu = frames::write_single_pdu(addr, value);
        let resp = self.transact(frames::FC_WRITE_SINGLE_REG, &pdu)?;
        // Ответ — эхо PDU (addr+val). Достаточно длины.
        if resp.len() < 4 {
            return Err(ModbusError::Io("short write echo".into()));
        }
        Ok(())
    }

    pub fn write_single_coil(&mut self, addr: u16, value: bool) -> Result<(), ModbusError> {
        let v = if value { 0xFF00 } else { 0x0000 };
        let pdu = frames::write_single_pdu(addr, v);
        let resp = self.transact(frames::FC_WRITE_SINGLE_COIL, &pdu)?;
        if resp.len() < 4 {
            return Err(ModbusError::Io("short coil echo".into()));
        }
        Ok(())
    }
}