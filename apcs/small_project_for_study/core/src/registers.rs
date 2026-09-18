//! Карта регистров мини-полигона и диспетчер Modbus-запросов.
//!
//! # Карта
//!
//! | Адрес | Тип     | Что                          | Чтение | Запись |
//! |-------|---------|------------------------------|--------|--------|
//! | 0     | COIL    | светодиод (вкл/выкл)        | FC01   | FC05   |
//! | 1     | COIL    | кнопка (нажата ли сейчас)   | FC01   | —      |
//! | 0     | HOLDING | счётчик нажатий             | FC03   | —      |
//!
//! Адреса **0-based**, без «4xxxx»-смещений (соглашение всего курса).
//!
//! # Идея диспетчера
//!
//! `dispatch(fc, pdu, state)` — чистая функция: по коду функции и телу PDU
//! меняет состояние и возвращает PDU **ответа** (или код исключения).
//! Железо (GPIO/UART) здесь не участвует вообще — поэтому всё проверяется
//! unit-тестами без ESP.

use crate::crc::append_crc;

/// Адрес устройства в сети RTU.
pub const SLAVE_ID: u8 = 1;

// Адреса регистров.
pub const COIL_LED: u16 = 0;
pub const COIL_BUTTON: u16 = 1;
pub const REG_PRESSES: u16 = 0;

// Коды функций Modbus, которые мы понимаем.
pub const FC_READ_COILS: u8 = 0x01;
pub const FC_READ_HOLDING: u8 = 0x03;
pub const FC_WRITE_COIL: u8 = 0x05;
pub const FC_WRITE_HOLDING: u8 = 0x06;

// Коды исключений Modbus.
pub const EX_ILLEGAL_FUNCTION: u8 = 0x01;
pub const EX_ILLEGAL_ADDRESS: u8 = 0x02;
pub const EX_DEVICE_FAILURE: u8 = 0x04;

/// Состояние мини-полигона — общее «окно данных».
///
/// Поток железа (fw) пишет сюда состояние кнопки и счётчик нажатий,
/// диспетчер Modbus читает/пишет светодиод, а RTU-цикл слушает запросы.
#[derive(Debug, Clone, Default)]
pub struct MiniState {
    pub led: bool,
    pub button: bool,
    pub presses: u32,
}

impl MiniState {
    fn coil(&self, addr: u16) -> Result<bool, u8> {
        match addr {
            COIL_LED => Ok(self.led),
            COIL_BUTTON => Ok(self.button),
            _ => Err(EX_ILLEGAL_ADDRESS),
        }
    }

    fn set_coil(&mut self, addr: u16, value: bool) -> Result<(), u8> {
        match addr {
            // Светодиод писать можно, кнопку — нет.
            COIL_LED => {
                self.led = value;
                Ok(())
            }
            COIL_BUTTON => Err(EX_ILLEGAL_ADDRESS),
            _ => Err(EX_ILLEGAL_ADDRESS),
        }
    }
}

/// Обрабатывает один PDU Modbus-запроса (без адреса и CRC).
///
/// Возвращает PDU ответа или **код исключения** (`Err`). Сборка кадра и
/// проверка CRC — дело RTU-цикла (`rtu` в `fw`), здесь только диспетчер.
pub fn dispatch(fc: u8, pdu: &[u8], state: &mut MiniState) -> Result<Vec<u8>, u8> {
    match fc {
        FC_READ_COILS => {
            let (addr, count) = read_addr_count(pdu)?;
            if count > 8 {
                return Err(EX_ILLEGAL_ADDRESS);
            }
            let mut bits = 0u16;
            for i in 0..count {
                if state.coil(addr + i)? {
                    bits |= 1 << i;
                }
            }
            let byte = (bits & 0xFF) as u8;
            Ok(vec![0x01, byte]) // [byte_count=1][coil byte]
        }
        FC_READ_HOLDING => {
            let (addr, count) = read_addr_count(pdu)?;
            if count != 1 || addr != REG_PRESSES {
                return Err(EX_ILLEGAL_ADDRESS);
            }
            let v = state.presses.min(u16::MAX as u32) as u16;
            Ok(vec![0x02, (v >> 8) as u8, (v & 0xFF) as u8]) // [byte_count=2][hi][lo]
        }
        FC_WRITE_COIL => {
            let (addr, value) = read_addr_value(pdu)?;
            state.set_coil(addr, value)?;
            // Ответ FC05 зеркалит запрос.
            Ok(vec![
                FC_WRITE_COIL,
                (addr >> 8) as u8,
                (addr & 0xFF) as u8,
                if value { 0xFF } else { 0x00 },
                0x00,
            ])
        }
        FC_WRITE_HOLDING => Err(EX_DEVICE_FAILURE), // счётчик read-only
        _ => Err(EX_ILLEGAL_FUNCTION),
    }
}

/// Сборка полного RTU-кадра (адрес + PDU + CRC) для отправки.
pub fn build_response(pdu: &[u8]) -> Vec<u8> {
    let mut frame = vec![SLAVE_ID];
    frame.extend_from_slice(pdu);
    append_crc(&frame)
}

/// Исключительный PDU: `[fc | 0x80][code]`.
pub fn exception_pdu(fc: u8, code: u8) -> Vec<u8> {
    vec![fc | 0x80, code]
}

fn read_addr_count(pdu: &[u8]) -> Result<(u16, u16), u8> {
    if pdu.len() < 4 {
        return Err(EX_ILLEGAL_ADDRESS);
    }
    let addr = u16::from_be_bytes([pdu[0], pdu[1]]);
    let count = u16::from_be_bytes([pdu[2], pdu[3]]);
    Ok((addr, count))
}

fn read_addr_value(pdu: &[u8]) -> Result<(u16, bool), u8> {
    if pdu.len() < 4 {
        return Err(EX_ILLEGAL_ADDRESS);
    }
    let addr = u16::from_be_bytes([pdu[0], pdu[1]]);
    let value = match u16::from_be_bytes([pdu[2], pdu[3]]) {
        0x0000 => false,
        0xFF00 => true,
        _ => return Err(EX_DEVICE_FAILURE),
    };
    Ok((addr, value))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn req(mut body: Vec<u8>) -> Vec<u8> {
        let mut f = vec![SLAVE_ID];
        f.append(&mut body);
        append_crc(&f)
    }

    #[test]
    fn read_coils_default() {
        let state = MiniState::default();
        // FC01, addr 0, count 2.
        let pdu = dispatch(FC_READ_COILS, &[0x00, 0x00, 0x00, 0x02], &mut state.clone()).unwrap();
        assert_eq!(pdu, [0x01, 0x00]); // ни LED ни кнопка не включены
    }

    #[test]
    fn read_coils_bits() {
        let mut state = MiniState::default();
        state.led = true;
        let pdu = dispatch(FC_READ_COILS, &[0x00, 0x00, 0x00, 0x02], &mut state).unwrap();
        assert_eq!(pdu, [0x01, 0b0000_0001]); // бит 0 (LED)
    }

    #[test]
    fn write_led_then_reads() {
        let mut state = MiniState::default();
        // FC05 addr 0 value 0xFF00 → «вкл».
        let pdu = dispatch(FC_WRITE_COIL, &[0x00, 0x00, 0xFF, 0x00], &mut state).unwrap();
        // Ответ FC05 — «эхо» запроса.
        assert_eq!(pdu, [0x05, 0x00, 0x00, 0xFF, 0x00]);
        assert!(state.led);
        let pdu = dispatch(FC_READ_COILS, &[0x00, 0x00, 0x00, 0x01], &mut state).unwrap();
        assert_eq!(pdu, [0x01, 0x01]);
    }

    #[test]
    fn cannot_write_button() {
        let mut state = MiniState::default();
        let err = dispatch(FC_WRITE_COIL, &[0x00, 0x01, 0xFF, 0x00], &mut state).unwrap_err();
        assert_eq!(err, EX_ILLEGAL_ADDRESS);
    }

    #[test]
    fn read_presses() {
        let mut state = MiniState::default();
        state.presses = 0x1234;
        let pdu = dispatch(FC_READ_HOLDING, &[0x00, 0x00, 0x00, 0x01], &mut state).unwrap();
        assert_eq!(pdu, [0x02, 0x12, 0x34]);
    }

    #[test]
    fn cannot_write_holding() {
        let mut state = MiniState::default();
        let err = dispatch(FC_WRITE_HOLDING, &[0x00, 0x00, 0x00, 0x01], &mut state).unwrap_err();
        assert_eq!(err, EX_DEVICE_FAILURE);
    }

    #[test]
    fn unknown_function_is_illegal() {
        let mut state = MiniState::default();
        let err = dispatch(0x64, &[], &mut state).unwrap_err();
        assert_eq!(err, EX_ILLEGAL_FUNCTION);
    }

    #[test]
    fn full_frame_roundtrip() {
        // Готовый кадр запроса FC01 (адрес 0, count 2) — как его увидит RTU-цикл.
        let frame = req(vec![
            FC_READ_COILS,
            0x00,
            0x00,
            0x00,
            0x02,
        ]);
        assert!(crate::crc::verify_crc(&frame));
        let state = MiniState::default();
        let pdu = dispatch(FC_READ_COILS, &frame[1..frame.len() - 2], &mut state.clone()).unwrap();
        assert_eq!(pdu, [0x01, 0x00]);
    }
}