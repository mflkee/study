//! Формирование/парсинг кадров Modbus RTU (master-сторона).

use crate::crc;

pub const FC_READ_COILS: u8 = 0x01;
pub const FC_READ_DISCRETE_INPUTS: u8 = 0x02;
pub const FC_READ_HOLDING: u8 = 0x03;
pub const FC_READ_INPUT: u8 = 0x04;
pub const FC_WRITE_SINGLE_COIL: u8 = 0x05;
pub const FC_WRITE_SINGLE_REG: u8 = 0x06;
pub const FC_WRITE_MULTI_COILS: u8 = 0x0F;
pub const FC_WRITE_MULTI_REGS: u8 = 0x10;

/// Максимальный размер RTU-кадра.
pub const MAX_FRAME: usize = 256;

/// Ошибки Modbus-уровня.
#[derive(Debug, Clone, PartialEq)]
pub enum ModbusError {
    Timeout,
    BadCrc,
    IllegalFunction,
    IllegalAddress,
    IllegalValue,
    ServerFailure,
    Ack,
    Busy,
    Nack,
    GatewayNoRoute,
    GatewayTargetFailed,
    Unknown(u8),
    Io(String),
}

impl std::fmt::Display for ModbusError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ModbusError::Timeout => write!(f, "timeout (no response)"),
            ModbusError::BadCrc => write!(f, "bad CRC"),
            ModbusError::IllegalFunction => write!(f, "illegal function (0x01)"),
            ModbusError::IllegalAddress => write!(f, "illegal data address (0x02)"),
            ModbusError::IllegalValue => write!(f, "illegal data value (0x03)"),
            ModbusError::ServerFailure => write!(f, "server device failure (0x04)"),
            ModbusError::Ack => write!(f, "acknowledge (0x05)"),
            ModbusError::Busy => write!(f, "server busy (0x06)"),
            ModbusError::Nack => write!(f, "negative acknowledge (0x07)"),
            ModbusError::GatewayNoRoute => write!(f, "gateway no route (0x0A)"),
            ModbusError::GatewayTargetFailed => write!(f, "gateway target failed (0x0B)"),
            ModbusError::Unknown(c) => write!(f, "exception 0x{:02X}", c),
            ModbusError::Io(e) => write!(f, "IO: {}", e),
        }
    }
}

impl std::error::Error for ModbusError {}

/// Построение кадра запроса: [addr, fc, ...pdu, crc_lo, crc_hi].
pub fn build_request(slave_id: u8, fc: u8, pdu: &[u8]) -> Vec<u8> {
    let mut frame = Vec::with_capacity(pdu.len() + 4);
    frame.push(slave_id);
    frame.push(fc);
    frame.extend_from_slice(pdu);
    crc::append(&mut frame);
    frame
}

/// Разбор ответа. Возвращает PDU-часть (без addr/fc/crc) либо ошибку.
pub fn parse_response(frame: &[u8], slave_id: u8, fc: u8) -> Result<Vec<u8>, ModbusError> {
    if frame.len() < 5 {
        return Err(ModbusError::Timeout);
    }
    if !crc::verify(frame) {
        return Err(ModbusError::BadCrc);
    }
    if frame[0] != slave_id {
        return Err(ModbusError::Io(format!("unexpected slave 0x{:02X}", frame[0])));
    }
    let response_fc = frame[1];
    if response_fc == fc | 0x80 {
        let code = frame[2];
        return Err(match code {
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
        });
    }
    if response_fc != fc {
        return Err(ModbusError::Io(format!(
            "unexpected FC 0x{:02X} (wanted 0x{:02X})",
            response_fc, fc
        )));
    }
    Ok(frame[2..frame.len() - 2].to_vec())
}

/// PDU для чтения регистров/количеств.
pub fn read_pdu(start: u16, count: u16) -> Vec<u8> {
    let mut pdu = Vec::with_capacity(4);
    pdu.extend_from_slice(&start.to_be_bytes());
    pdu.extend_from_slice(&count.to_be_bytes());
    pdu
}

/// Разбор ответа Read Registers: [byte_count, ...data]. Возвращает регистры BE.
pub fn parse_read_registers(pdu: &[u8], expected: usize) -> Result<Vec<u16>, ModbusError> {
    if pdu.len() < 1 {
        return Err(ModbusError::Io("empty read response".into()));
    }
    let byte_count = pdu[0] as usize;
    if byte_count != expected * 2 || pdu.len() < 1 + byte_count {
        return Err(ModbusError::Io(format!(
            "bad read response: byte_count={}, data={}",
            byte_count,
            pdu.len().saturating_sub(1)
        )));
    }
    let mut regs = Vec::with_capacity(expected);
    for chunk in pdu[1..1 + byte_count].chunks_exact(2) {
        regs.push(u16::from_be_bytes([chunk[0], chunk[1]]));
    }
    Ok(regs)
}

/// Разбор ответа Read Coils: [byte_count, ...bits].
pub fn parse_read_bits(pdu: &[u8], expected: usize) -> Result<Vec<bool>, ModbusError> {
    if pdu.len() < 1 {
        return Err(ModbusError::Io("empty bits response".into()));
    }
    let byte_count = pdu[0] as usize;
    if pdu.len() < 1 + byte_count {
        return Err(ModbusError::Io("bits response too short".into()));
    }
    let mut bits = Vec::with_capacity(expected);
    for byte in &pdu[1..1 + byte_count] {
        for bit in 0..8 {
            if bits.len() >= expected {
                break;
            }
            bits.push((byte >> bit) & 1 == 1);
        }
    }
    Ok(bits)
}

/// PDU для Write Single Register/Coil.
pub fn write_single_pdu(addr: u16, value: u16) -> Vec<u8> {
    let mut pdu = Vec::with_capacity(4);
    pdu.extend_from_slice(&addr.to_be_bytes());
    pdu.extend_from_slice(&value.to_be_bytes());
    pdu
}

/// PDU для Write Multiple Coils (0x0F).
pub fn write_multi_coils_pdu(start: u16, values: &[bool]) -> Vec<u8> {
    let byte_count = (values.len() + 7) / 8;
    let mut pdu = Vec::with_capacity(5 + byte_count);
    pdu.extend_from_slice(&start.to_be_bytes());
    pdu.extend_from_slice(&(values.len() as u16).to_be_bytes());
    pdu.push(byte_count as u8);
    for i in 0..byte_count {
        let mut b = 0u8;
        for bit in 0..8 {
            let idx = i * 8 + bit;
            if idx < values.len() && values[idx] {
                b |= 1 << bit;
            }
        }
        pdu.push(b);
    }
    pdu
}

/// PDU для Write Multiple Registers (0x10).
pub fn write_multi_regs_pdu(start: u16, values: &[u16]) -> Vec<u8> {
    let mut pdu = Vec::with_capacity(5 + values.len() * 2);
    pdu.extend_from_slice(&start.to_be_bytes());
    pdu.extend_from_slice(&(values.len() as u16).to_be_bytes());
    pdu.push((values.len() * 2) as u8);
    for v in values {
        pdu.extend_from_slice(&v.to_be_bytes());
    }
    pdu
}

/// Регистры float32 из пары регистров (big-endian order как в прошивке).
pub fn float_from_regs(hi: u16, lo: u16) -> f32 {
    let raw = ((hi as u32) << 16) | (lo as u32);
    f32::from_bits(raw)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn request_frame_matches_known() {
        let frame = build_request(0x01, FC_READ_HOLDING, &read_pdu(0x0000, 0x0001));
        // 01 03 00 00 00 01 84 0A
        assert_eq!(frame, vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x01, 0x84, 0x0A]);
    }

    #[test]
    fn parse_valid_response() {
        // Ответ: 01 03 02 00 64 (CRC)
        let pdu = parse_response(&[0x01, 0x03, 0x02, 0x00, 0x64, 0xB9, 0xAF], 0x01, FC_READ_HOLDING)
            .unwrap();
        assert_eq!(pdu, vec![0x02, 0x00, 0x64]);
        let data = parse_read_registers(&pdu, 1).unwrap();
        assert_eq!(data, vec![100]);
    }

    #[test]
    fn parse_exception() {
        let err = parse_response(&[0x01, 0x83, 0x02, 0xC0, 0xF1], 0x01, FC_READ_HOLDING).unwrap_err();
        assert_eq!(err, ModbusError::IllegalAddress);
    }

    #[test]
    fn float64_from_regs() {
        // 101.325 kPa в IEEE754 = 0x42CA A666
        let f = float_from_regs(0x42CA, 0xA666);
        assert!((f - 101.325).abs() < 0.001);
    }

    #[test]
    fn multi_regs_pdu() {
        let pdu = write_multi_regs_pdu(0x0000, &[0x000A, 0x0014]);
        assert_eq!(pdu, vec![0x00, 0x00, 0x00, 0x02, 0x04, 0x00, 0x0A, 0x00, 0x14]);
    }
}