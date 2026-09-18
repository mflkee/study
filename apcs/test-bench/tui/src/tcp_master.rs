//! Modbus TCP master (клиентская роль) поверх `std::net`.
//!
//! Зеркало `tcp_server.rs`: тот модуль *обслуживает* запросы (slave-роль),
//! а этот умеет *опрашивать* любое Modbus TCP устройство — как `master.rs`
//! опрашивает RTU-слейвов по RS-485, только вместо CRC — MBAP-заголовок.
//!
//! ```text
//!   TUI (master) ── TCP MBAP ──►  устройство  (AI-32 / TCP-сервер TUI / стенд)
//! ```
//!
//! Та же картина, что и на шине RTU: request = `[tid][proto=0][len][unit][fc][pdu]`,
//! response = `[tid][proto=0][len][unit][fc][data...]`, без CRC. Исключения —
//! PDU `[fc|0x80][code]`. Это позволяет учащемуся увидеть ОБЕ роли Modbus
//! (мастер и слейв) в одном интерфейсе.

use std::collections::VecDeque;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::{Duration, Instant};

use crate::frames::{self, ModbusError};
use crate::master::{fc_name, TraceEntry};

/// Сколько ждём первый байт ответа.
const RESPONSE_WAIT: Duration = Duration::from_millis(300);
/// Сколько последних транзакций держим в «логе шины» (вкладка Bus).
const TRACE_CAP: usize = 100;

/// Modbus TCP клиент: одно соединение, MBAP-фрейминг, лог транзакций.
pub struct TcpMaster {
    stream: TcpStream,
    /// Unit ID (адрес слейва — в MBAP это часть заголовка, широковещательная
    /// шина RTU здесь неявная: TCP = точка-точка).
    unit: u8,
    /// Transaction ID: мастер нумерует запросы, чтобы сопоставить ответы.
    tid: u16,
    /// «Лог шины» для вкладки Bus.
    trace: VecDeque<TraceEntry>,
}

impl TcpMaster {
    /// Открывает TCP-соединение к Modbus TCP устройству.
    pub fn open(host: &str, port: u16, unit: u8) -> Result<Self, ModbusError> {
        let stream = TcpStream::connect((host, port)).map_err(|e| ModbusError::Io(e.to_string()))?;
        let _ = stream.set_read_timeout(Some(RESPONSE_WAIT));
        let _ = stream.set_write_timeout(Some(Duration::from_secs(5)));
        Ok(Self {
            stream,
            unit,
            tid: 0,
            trace: VecDeque::new(),
        })
    }

    pub fn unit(&self) -> u8 {
        self.unit
    }

    /// Забирает накопленные транзакции («лог шины»).
    pub fn drain_trace(&mut self) -> Vec<TraceEntry> {
        self.trace.drain(..).collect()
    }

    /// Добавляет запись в «лог шины».
    fn push_trace(
        &mut self,
        fc: u8,
        req: &[u8],
        resp: Option<&[u8]>,
        ok: bool,
        err: Option<ModbusError>,
        ms: u64,
    ) {
        if self.trace.len() >= TRACE_CAP {
            self.trace.pop_front();
        }
        self.trace.push_back(TraceEntry {
            transport: "TCP",
            fc: fc_name(fc),
            req: crate::crc::to_hex(req),
            resp: resp.map(crate::crc::to_hex).unwrap_or_default(),
            ok,
            err: err.as_ref().map(|e| e.to_string()),
            ms,
        });
    }

    /// Выполняет один Modbus-запрос, возвращает PDU ответа (после fc).
    pub fn transact(&mut self, fc: u8, pdu: &[u8]) -> Result<Vec<u8>, ModbusError> {
        self.tid = self.tid.wrapping_add(1).max(1);

        // MBAP-заголовок: tid(2) proto=0(2) len(2) unit(1) + PDU (fc + данные).
        // len считает байты ПОСЛЕ себя: unit(1) + fc(1) + данные.
        let mut frame = Vec::with_capacity(7 + 1 + pdu.len());
        frame.extend_from_slice(&self.tid.to_be_bytes());
        frame.extend_from_slice(&0u16.to_be_bytes()); // protocol = Modbus IP
        frame.extend_from_slice(&((1 + 1 + pdu.len()) as u16).to_be_bytes());
        frame.push(self.unit);
        frame.push(fc);
        frame.extend_from_slice(pdu);

        let t0 = Instant::now();
        if let Err(e) = self.stream.write_all(&frame).and_then(|_| self.stream.flush()) {
            self.push_trace(fc, &frame, None, false, Some(ModbusError::Io(e.to_string())), t0.elapsed().as_millis() as u64);
            return Err(ModbusError::Io(e.to_string()));
        }

        // Ответ: 7 байт заголовка затем `len-1` байт PDU (unit уже посчитан).
        let mut hdr = [0u8; 7];
        if !read_n_timeout(&mut self.stream, &mut hdr)? {
            self.push_trace(fc, &frame, None, false, Some(ModbusError::Timeout), t0.elapsed().as_millis() as u64);
            return Err(ModbusError::Timeout);
        }
        let tid_resp = u16::from_be_bytes([hdr[0], hdr[1]]);
        let proto = u16::from_be_bytes([hdr[2], hdr[3]]);
        let len = u16::from_be_bytes([hdr[4], hdr[5]]);
        let unit_resp = hdr[6];
        if tid_resp != self.tid {
            let err = ModbusError::Io(format!("tid mismatch: sent {}, got {}", self.tid, tid_resp));
            self.push_trace(fc, &frame, None, false, Some(err.clone()), t0.elapsed().as_millis() as u64);
            return Err(err);
        }
        if proto != 0 || len < 2 || unit_resp != self.unit || len as usize > 1 + 253 {
            let err = ModbusError::Io("bad MBAP header in response".into());
            self.push_trace(fc, &frame, None, false, Some(err.clone()), t0.elapsed().as_millis() as u64);
            return Err(err);
        }
        let pdu_len = len as usize - 1;
        let mut body = vec![0u8; pdu_len];
        if !read_n_timeout(&mut self.stream, &mut body)? {
            self.push_trace(fc, &frame, None, false, Some(ModbusError::Timeout), t0.elapsed().as_millis() as u64);
            return Err(ModbusError::Timeout);
        }

        let mut resp_frame = Vec::with_capacity(7 + body.len());
        resp_frame.extend_from_slice(&hdr);
        resp_frame.extend_from_slice(&body);

        let resp_fc = body[0];
        let ms = t0.elapsed().as_millis() as u64;
        if resp_fc == fc | 0x80 {
            let code = body[1];
            let err = exception_to_error(code);
            self.push_trace(fc, &frame, Some(&resp_frame), false, Some(err.clone()), ms);
            return Err(err);
        }
        if resp_fc != fc {
            let err = ModbusError::Io(format!(
                "unexpected FC 0x{:02X} (wanted 0x{:02X})",
                resp_fc, fc
            ));
            self.push_trace(fc, &frame, Some(&resp_frame), false, Some(err.clone()), ms);
            return Err(err);
        }
        self.push_trace(fc, &frame, Some(&resp_frame), true, None, ms);
        Ok(body[1..].to_vec())
    }

    // --- Высокоуровневые функции чтения/записи (аналог SerialMaster) ---

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
        // Ответ — эхо PDU (addr+val).
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

/// Код исключения Modbus → тип ошибки (та же таблица, что в `frames`).
fn exception_to_error(code: u8) -> ModbusError {
    match code {
        0x01 => ModbusError::IllegalFunction,
        0x02 => ModbusError::IllegalAddress,
        0x03 => ModbusError::IllegalValue,
        0x04 => ModbusError::ServerFailure,
        0x05 => ModbusError::Ack,
        0x06 => ModbusError::Busy,
        0x07 => ModbusError::Nack,
        0x0A => ModbusError::GatewayNoRoute,
        0x0B => ModbusError::GatewayTargetFailed,
        other => ModbusError::Unknown(other),
    }
}

/// Читает ровно `buf.len()` байт. Ok(true)=успех, Ok(false)=таймаут/EOF, Err=IO.
fn read_n_timeout(stream: &mut TcpStream, buf: &mut [u8]) -> Result<bool, ModbusError> {
    let mut n = 0usize;
    while n < buf.len() {
        match stream.read(&mut buf[n..]) {
            Ok(0) => return Ok(false), // EOF
            Ok(read) => n += read,
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                return Ok(false); // пауза между запросами — кадр неполный
            }
            Err(e) => return Err(ModbusError::Io(e.to_string())),
        }
    }
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Интеграционный тест: TCP-мастер против собственного TCP-сервера TUI.
    /// Это же и самотест пары «клиент ⇄ сервер»: если TUI опрашивает сам себя —
    /// значит оба MBAP-пути (входящий и исходящий) собраны верно.
    #[test]
    fn tcp_master_polls_own_tcp_server() {
        use crate::emulator::default_scenario;
        use crate::tcp_server;

        let emu = default_scenario();
        emu.lock().unwrap().tick();

        // Ищем свободный порт (отдельный диапазон от тестов tcp_server).
        let mut state = None;
        let mut port = 0u16;
        for try_port in 15050..15060 {
            if let Ok((addr, st)) = tcp_server::spawn(try_port, emu.clone(), tcp_server::new_shared_trace()) {
                port = addr.rsplit(':').next().unwrap().parse().unwrap();
                state = Some(st);
                break;
            }
        }
        let mut st = state.expect("no free test port");

        let mut m = TcpMaster::open("127.0.0.1", port, 1).unwrap();

        // READ INPUT REGISTERS: первые два регистра — float32 с температурой.
        let regs = m.read_input_registers(0, 2).unwrap();
        assert_eq!(regs.len(), 2);
        let v = frames::float_from_regs(regs[0], regs[1]);
        assert!((v - 22.0).abs() < 6.0, "unexpected first temp {}", v);

        // WRITE SINGLE REGISTER: запись видна на следующем чтении.
        m.write_single_register(0, 0x04D2).unwrap();
        let regs = m.read_holding_registers(0, 1).unwrap();
        assert_eq!(regs, vec![0x04D2]);

        // COILS: 2 катушки слейва 1 читаются.
        let coils = m.read_coils(0, 2).unwrap();
        assert_eq!(coils.len(), 2);

        // Исключение: несуществующий slave → 0x02.
        let mut m2 = TcpMaster::open("127.0.0.1", port, 9).unwrap();
        assert_eq!(
            m2.read_input_registers(0, 1),
            Err(ModbusError::IllegalAddress)
        );

        // Неизвестная функция → 0x01 через низкоуровневый вызов.
        let resp = m.transact(0x7F, &[]);
        assert_eq!(resp, Err(ModbusError::IllegalFunction));

        // Каждая транзакция попала в «лог шины» с транспортом TCP.
        let trace = m.drain_trace();
        assert!(trace.len() >= 4, "expected >=4 traced transactions, got {}", trace.len());
        assert!(trace.iter().all(|t| t.transport == "TCP"));

        st.stop();
    }
}