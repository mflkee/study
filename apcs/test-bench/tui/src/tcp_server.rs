//! Modbus TCP server (slave-роль) поверх эмулятора.
//!
//! Отдаёт ту же карту регистров, что видна в TUI (эмулятор), по Ethernet —
//! по протоколу Modbus TCP (MBAP + PDU, без CRC). Позволяет позже опрашивать
//! стенд с любой машины (напр., с Zynq) без проводов RS-485:
//!
//! ```text
//!   Zynq/PLC ── Ethernet TCP ──►  TUI :1502  ──(та же карта)──► эмулятор
//! ```
//!
//! Отличие от RTU: вместо `[slave][fc][pdu][crc]` идёт MBAP-заголовок
//! `[tid][proto=0][len][unit]` + PDU. Регистры/функции — те же.

use std::collections::VecDeque;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use crate::emulator::SharedEmulator;
use crate::frames;
use crate::master::{fc_name, TraceEntry};

/// Порт по умолчанию (502 требует root; 1502 — стандартный «dev» порт Modbus TCP).
pub const DEFAULT_PORT: u16 = 1502;
/// Сколько ждём следующего запроса на уже открытом соединении.
const IDLE_TIMEOUT: Duration = Duration::from_secs(60);
/// Сколько держим записей в общем «логе шины».
const TRACE_CAP: usize = 100;

/// Общий журнал TCP-транзакций — его дренит UI-поток во вкладку Bus.
pub type SharedTrace = Arc<Mutex<VecDeque<TraceEntry>>>;

pub fn new_shared_trace() -> SharedTrace {
    Arc::new(Mutex::new(VecDeque::new()))
}

/// Состояние запущенного TCP-сервера (чтобы остановить и перезапустить).
pub struct TcpServerState {
    /// Читаемый адрес для UI: `127.0.0.1:1502`.
    pub addr: String,
    stop: mpsc::Sender<()>,
    handle: Option<JoinHandle<()>>,
}

impl TcpServerState {
    pub fn stop(&mut self) {
        let _ = self.stop.send(());
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

/// Запускает TCP-сервер на 127.0.0.1:port. Возвращает адрес + handle остановки.
pub fn spawn(
    port: u16,
    emu: SharedEmulator,
    trace: SharedTrace,
) -> Result<(String, TcpServerState), String> {
    let listener = TcpListener::bind(("127.0.0.1", port))
        .map_err(|e| format!("TCP bind 127.0.0.1:{} failed: {}", port, e))?;
    listener
        .set_nonblocking(true)
        .map_err(|e| e.to_string())?;
    let addr = listener
        .local_addr()
        .map_err(|e| e.to_string())?
        .to_string();

    let (stop_tx, stop_rx) = mpsc::channel();
    let handle = thread::spawn(move || {
        loop {
            match listener.accept() {
                Ok((stream, _peer)) => {
                    let emu = emu.clone();
                    let trace = trace.clone();
                    thread::spawn(move || handle_conn(stream, emu, trace));
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {}
                Err(e) => {
                    eprintln!("TCP accept error: {}", e);
                    break;
                }
            }
            if stop_rx.try_recv().is_ok() {
                break;
            }
            thread::sleep(Duration::from_millis(100));
        }
    });

    Ok((addr.clone(), TcpServerState {
        addr,
        stop: stop_tx,
        handle: Some(handle),
    }))
}

/// Обслуживает одно соединение: цикл «прочитай MBAP+PDU → ответь».
fn handle_conn(mut stream: TcpStream, emu: SharedEmulator, trace: SharedTrace) {
    let _ = stream.set_read_timeout(Some(IDLE_TIMEOUT));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(5)));

    let mut hdr = [0u8; 7];
    // Ждём следующий запрос, пока клиент жив. Ok(false) = пауза > IDLE_TIMEOUT,
    // EOF или клиент разорвал — выходим.
    while let Ok(true) = read_exact_timeout(&mut stream, &mut hdr) {
        let tid = u16::from_be_bytes([hdr[0], hdr[1]]);
        let proto = u16::from_be_bytes([hdr[2], hdr[3]]);
        let len = u16::from_be_bytes([hdr[4], hdr[5]]);
        let unit = hdr[6];
        // len = 1 (unit) + PDU; протокол обязан быть 0 (Modbus IP).
        if proto != 0 || len < 2 || len as usize > 1 + 253 {
            break;
        }
        let pdu_len = len as usize - 1;
        let mut pdu = vec![0u8; pdu_len];
        if !matches!(read_exact_timeout(&mut stream, &mut pdu), Ok(true)) {
            break;
        }
        if pdu.is_empty() {
            continue;
        }

        let t0 = Instant::now();
        let fc = pdu[0];
        let resp_pdu = match handle_pdu(&emu, unit, fc, &pdu[1..]) {
            Ok(body) => {
                let mut out = vec![fc];
                out.extend_from_slice(&body);
                out
            }
            Err(code) => vec![fc | 0x80, code], // exception response
        };

        let mut frame: Vec<u8> = Vec::with_capacity(7 + resp_pdu.len());
        frame.extend_from_slice(&tid.to_be_bytes());
        frame.extend_from_slice(&0u16.to_be_bytes()); // proto
        frame.extend_from_slice(&((1 + resp_pdu.len()) as u16).to_be_bytes());
        frame.push(unit);
        frame.extend_from_slice(&resp_pdu);

        let mut req_frame: Vec<u8> = Vec::with_capacity(7 + pdu.len());
        req_frame.extend_from_slice(&hdr);
        req_frame.extend_from_slice(&pdu);

        let ms = t0.elapsed().as_millis() as u64;
        let is_exception = resp_pdu.len() == 2 && resp_pdu[0] & 0x80 != 0;
        let err = if is_exception {
            Some(format!("exception 0x{:02X}", resp_pdu[1]))
        } else {
            None
        };
        push_trace(&trace, fc, &req_frame, &frame, !is_exception, err, ms);

        if stream.write_all(&frame).is_err() {
            break;
        }
        if stream.flush().is_err() {
            break;
        }
    }
}

/// Читает ровно `buf.len()` байт. Ok(true)=успех, Ok(false)=таймаут/EOF, Err=IO.
fn read_exact_timeout(stream: &mut TcpStream, buf: &mut [u8]) -> std::io::Result<bool> {
    let mut n = 0usize;
    while n < buf.len() {
        match stream.read(&mut buf[n..]) {
            Ok(0) => return Ok(false), // EOF
            Ok(read) => n += read,
            Err(ref e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                return Ok(false); // пауза между запросами
            }
            Err(e) => return Err(e),
        }
    }
    Ok(true)
}

/// Диспетчер PDU: дергает эмулятор как slave, возвращает тело ответа (после fc).
fn handle_pdu(emu: &SharedEmulator, unit: u8, fc: u8, data: &[u8]) -> Result<Vec<u8>, u8> {
    // Лимиты из спеки Modbus.
    fn read_count(data: &[u8], max: u16) -> Result<(u16, u16), u8> {
        if data.len() != 4 {
            return Err(0x03);
        }
        let start = u16::from_be_bytes([data[0], data[1]]);
        let count = u16::from_be_bytes([data[2], data[3]]);
        if count == 0 || count > max {
            return Err(0x03);
        }
        if start as u32 + count as u32 > 0x1_0000 {
            return Err(0x02);
        }
        Ok((start, count))
    }

    match fc {
        frames::FC_READ_COILS | frames::FC_READ_DISCRETE_INPUTS => {
            let (start, count) = read_count(data, 2000)?;
            let mut emu = emu.lock().map_err(|_| 0x04)?;
            let bits = match fc {
                frames::FC_READ_COILS => emu.read_coils(unit, start, count)?,
                _ => emu.read_discrete_inputs(unit, start, count)?,
            };
            let byte_count = (count as usize).div_ceil(8);
            let mut out = Vec::with_capacity(1 + byte_count);
            out.push(byte_count as u8);
            let mut byte = 0u8;
            for (i, b) in bits.iter().enumerate() {
                if *b {
                    byte |= 1 << (i % 8);
                }
                if i % 8 == 7 {
                    out.push(byte);
                    byte = 0;
                }
            }
            if !(count as usize).is_multiple_of(8) {
                out.push(byte);
            }
            Ok(out)
        }
        frames::FC_READ_HOLDING | frames::FC_READ_INPUT => {
            let (start, count) = read_count(data, 2000)?;
            let mut emu = emu.lock().map_err(|_| 0x04)?;
            let regs = match fc {
                frames::FC_READ_HOLDING => emu.read_holding_regs(unit, start, count)?,
                _ => emu.read_input_regs(unit, start, count)?,
            };
            let mut out = Vec::with_capacity(1 + regs.len() * 2);
            out.push((regs.len() * 2) as u8);
            for r in &regs {
                out.extend_from_slice(&r.to_be_bytes());
            }
            Ok(out)
        }
        frames::FC_WRITE_SINGLE_COIL => {
            if data.len() != 4 {
                return Err(0x03);
            }
            let addr = u16::from_be_bytes([data[0], data[1]]);
            let raw = u16::from_be_bytes([data[2], data[3]]);
            let value = match raw {
                0x0000 => false,
                0xFF00 => true,
                _ => return Err(0x03),
            };
            emu.lock().map_err(|_| 0x04)?.write_coil(unit, addr, value)?;
            Ok(data[..4].to_vec()) // эхо
        }
        frames::FC_WRITE_SINGLE_REG => {
            if data.len() != 4 {
                return Err(0x03);
            }
            let addr = u16::from_be_bytes([data[0], data[1]]);
            let value = u16::from_be_bytes([data[2], data[3]]);
            emu.lock()
                .map_err(|_| 0x04)?
                .write_holding_reg(unit, addr, value)?;
            Ok(data[..4].to_vec())
        }
        frames::FC_WRITE_MULTI_COILS => {
            if data.len() < 6 {
                return Err(0x03);
            }
            let start = u16::from_be_bytes([data[0], data[1]]);
            let qty = u16::from_be_bytes([data[2], data[3]]);
            let byte_count = data[4] as usize;
            if qty == 0 || qty > 1968
                || byte_count != (qty as usize).div_ceil(8)
                || data.len() != 5 + byte_count
            {
                return Err(0x03);
            }
            let mut values = Vec::with_capacity(qty as usize);
            for i in 0..qty as usize {
                values.push(data[5 + i / 8] >> (i % 8) & 1 == 1);
            }
            emu.lock()
                .map_err(|_| 0x04)?
                .write_coils(unit, start, &values)?;
            let mut out = Vec::with_capacity(4);
            out.extend_from_slice(&start.to_be_bytes());
            out.extend_from_slice(&qty.to_be_bytes());
            Ok(out)
        }
        frames::FC_WRITE_MULTI_REGS => {
            if data.len() < 6 {
                return Err(0x03);
            }
            let start = u16::from_be_bytes([data[0], data[1]]);
            let qty = u16::from_be_bytes([data[2], data[3]]);
            let byte_count = data[4] as usize;
            if qty == 0 || qty > 123 || byte_count != qty as usize * 2 || data.len() != 5 + byte_count
            {
                return Err(0x03);
            }
            let mut values = Vec::with_capacity(qty as usize);
            let (data, _) = data[5..].as_chunks::<2>();
            for pair in data {
                values.push(u16::from_be_bytes([pair[0], pair[1]]));
            }
            emu.lock()
                .map_err(|_| 0x04)?
                .write_holding_regs(unit, start, &values)?;
            let mut out = Vec::with_capacity(4);
            out.extend_from_slice(&start.to_be_bytes());
            out.extend_from_slice(&qty.to_be_bytes());
            Ok(out)
        }
        _ => Err(0x01), // illegal function
    }
}

/// Пишет транзакцию в общий «лог шины» (вкладка Bus).
fn push_trace(
    trace: &SharedTrace,
    fc: u8,
    req: &[u8],
    resp: &[u8],
    ok: bool,
    err: Option<String>,
    ms: u64,
) {
    let mut t = trace.lock().unwrap();
    if t.len() >= TRACE_CAP {
        t.pop_front();
    }
    t.push_back(TraceEntry {
        transport: "TCP",
        fc: fc_name(fc),
        req: crate::crc::to_hex(req),
        resp: crate::crc::to_hex(resp),
        ok,
        err,
        ms,
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::emulator::default_scenario;
    use std::net::TcpStream;
    use std::time::Duration;

    /// Буферизованный клиент: не теряет байты при фрагментации TCP-сегментов.
    struct Rx {
        stream: TcpStream,
        buf: Vec<u8>,
    }

    impl Rx {
        fn connect(port: u16) -> Self {
            let stream = TcpStream::connect(("127.0.0.1", port)).unwrap();
            stream.set_read_timeout(Some(Duration::from_secs(1))).unwrap();
            let mut out = Self {
                stream,
                buf: Vec::new(),
            };
            out.fill();
            out
        }

        /// Добирает данные с сервера до `n` байт.
        fn read_exact(&mut self, n: usize) -> Vec<u8> {
            let deadline = Instant::now() + Duration::from_secs(2);
            while self.buf.len() < n {
                if Instant::now() > deadline {
                    panic!("timeout waiting for {n} bytes, have {}", self.buf.len());
                }
                self.fill();
            }
            self.buf.drain(..n).collect()
        }

        /// Читает всё, что уже пришло (блокирует до первого байта).
        fn fill(&mut self) {
            let mut tmp = [0u8; 512];
            match self.stream.read(&mut tmp) {
                Ok(read) => self.buf.extend_from_slice(&tmp[..read]),
                Err(_) => thread::sleep(Duration::from_millis(5)),
            }
        }
    }

    fn send_frame(rx: &mut Rx, unit: u8, fc: u8, pdu: &[u8]) -> Vec<u8> {
        let mut frame: Vec<u8> = Vec::new();
        frame.extend_from_slice(&0x1234u16.to_be_bytes()); // tid
        frame.extend_from_slice(&0u16.to_be_bytes()); // proto
        // len = unit(1) + fc(1) + данные.
        frame.extend_from_slice(&((2 + pdu.len()) as u16).to_be_bytes());
        frame.push(unit);
        frame.push(fc);
        frame.extend_from_slice(pdu);
        rx.stream.write_all(&frame).unwrap();
        rx.stream.flush().unwrap();

        // Ответ: MBAP-заголовок [tid proto len unit] + PDU. len считается
        // от unit до конца PDU, поэтому после заголовка остаётся len-1 байт.
        let hdr = rx.read_exact(7);
        assert_eq!(u16::from_be_bytes([hdr[2], hdr[3]]), 0, "protocol must be 0");
        let pdu_len = u16::from_be_bytes([hdr[4], hdr[5]]) as usize - 1;
        rx.read_exact(pdu_len)
    }

    #[test]
    fn tcp_server_roundtrip_and_exceptions() {
        let emu = default_scenario();
        emu.lock().unwrap().tick();

        // Ищем свободный порт.
        let mut state = None;
        let mut port = 0u16;
        for try_port in 15030..15040 {
            if let Ok((addr, st)) = spawn(try_port, emu.clone(), new_shared_trace()) {
                port = addr.rsplit(':').next().unwrap().parse().unwrap();
                state = Some(st);
                break;
            }
        }
        let mut st = state.expect("no free test port");

        let mut rx = Rx::connect(port);

        // READ INPUT REGISTERS (0x04), slave 1, addr 0, count 2.
        let resp = send_frame(&mut rx, 1, 0x04, &[0x00, 0x00, 0x00, 0x02]);
        assert_eq!(resp.len(), 1 + 1 + 4);
        assert_eq!(resp[0], 0x04);
        assert_eq!(resp[1], 4);
        // Два регистра float32 не нулевые после tick.
        let hi = u16::from_be_bytes([resp[2], resp[3]]);
        let lo = u16::from_be_bytes([resp[4], resp[5]]);
        let v = frames::float_from_regs(hi, lo);
        assert!((v - 22.0).abs() < 6.0, "unexpected first temp {}", v);

        // WRITE SINGLE REGISTER 0x06 → эхо.
        let resp = send_frame(&mut rx, 1, 0x06, &[0x00, 0x00, 0x04, 0xD2]);
        assert_eq!(resp, vec![0x06, 0x00, 0x00, 0x04, 0xD2]);

        // И это значение теперь читается по TCP.
        let resp = send_frame(&mut rx, 1, 0x03, &[0x00, 0x00, 0x00, 0x01]);
        assert_eq!(resp, vec![0x03, 0x02, 0x04, 0xD2]);

        // Неизвестная функция → exception 0x01.
        let resp = send_frame(&mut rx, 1, 0x7F, &[]);
        assert_eq!(resp[0], 0x7F | 0x80);
        assert_eq!(resp[1], 0x01);

        // Чтение у несуществующего slave → exception 0x02.
        let resp = send_frame(&mut rx, 9, 0x04, &[0x00, 0x00, 0x00, 0x02]);
        assert_eq!(resp[0], 0x84);
        assert_eq!(resp[1], 0x02);

        st.stop();
    }
}