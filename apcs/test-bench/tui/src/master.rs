//! Modbus RTU Master поверх serialport.
//!
//! Пул-запросы к slave-устройствам по последовательному порту.

use std::io;
use std::time::{Duration, Instant};

use crate::frames::{self, ModbusError};

/// Как долго ждём первый байт ответа.
const RESPONSE_WAIT: Duration = Duration::from_millis(200);
/// Мин. пауза между кадрами (3.5 символа на 9600 ≈ 4 мс).
const INTER_FRAME_GAP: Duration = Duration::from_millis(10);

/// Обёртка над открытым последовательным портом.
pub struct SerialMaster {
    port: Box<dyn serialport::SerialPort>,
    slave_id: u8,
}

impl SerialMaster {
    /// Открывает порт и настраивает 8N1 с заданной скоростью.
    pub fn open(port_name: &str, baud: u32, slave_id: u8) -> Result<Self, ModbusError> {
        let port = serialport::new(port_name, baud)
            .timeout(RESPONSE_WAIT)
            .data_bits(serialport::DataBits::Eight)
            .stop_bits(serialport::StopBits::One)
            .parity(serialport::Parity::None)
            .open()
            .map_err(|e| ModbusError::Io(e.to_string()))?;
        Ok(Self {
            port: Box::from(port),
            slave_id,
        })
    }

    pub fn slave_id(&self) -> u8 {
        self.slave_id
    }

    pub fn set_slave_id(&mut self, id: u8) {
        self.slave_id = id;
    }

    /// Выполняет один Modbus-запрос и возвращает PDU ответа.
    pub fn transact(&mut self, fc: u8, pdu: &[u8]) -> Result<Vec<u8>, ModbusError> {
        let frame = frames::build_request(self.slave_id, fc, pdu);

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
            if len == 0 {
                if Instant::now() >= deadline {
                    return Err(ModbusError::Timeout);
                }
            }
            match self.port.read(&mut buf[len..]) {
                Ok(0) => continue,
                Ok(n) => {
                    len += n;
                    // Сколько ждём продолжения кадра: дедлайн от последнего байта
                    // уже обеспечен таймаутом порта (RESPONSE_WAIT), поэтому после
                    // паузы INTER_FRAME_GAP считаем кадр завершённым.
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
            return Err(ModbusError::Timeout);
        }

        let frame = &buf[..len];
        if !crate::crc::verify(frame) {
            // Возможно, прилипли хвосты от предыдущего кадра — попробуем найти
            // кадр в буфере.
            return Err(ModbusError::BadCrc);
        }

        frames::parse_response(frame, self.slave_id, fc)
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

    pub fn write_multiple_registers(
        &mut self,
        start: u16,
        values: &[u16],
    ) -> Result<(), ModbusError> {
        let pdu = frames::write_multi_regs_pdu(start, values);
        let resp = self.transact(frames::FC_WRITE_MULTI_REGS, &pdu)?;
        if resp.len() < 4 {
            return Err(ModbusError::Io("short multi write echo".into()));
        }
        Ok(())
    }

    pub fn write_multiple_coils(&mut self, start: u16, values: &[bool]) -> Result<(), ModbusError> {
        let pdu = frames::write_multi_coils_pdu(start, values);
        let resp = self.transact(frames::FC_WRITE_MULTI_COILS, &pdu)?;
        if resp.len() < 4 {
            return Err(ModbusError::Io("short coil write echo".into()));
        }
        Ok(())
    }

    /// Читает float32 из пары input registers.
    pub fn read_input_float(&mut self, start: u16) -> Result<f32, ModbusError> {
        let regs = self.read_input_registers(start, 2)?;
        Ok(frames::float_from_regs(regs[0], regs[1]))
    }
}