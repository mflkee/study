//! Урок 07: Error Handling.
//!
//! Запуск: `cargo run --example 07_errors`
//!
//! Реальный код: `frames.rs:18-33` (ModbusError enum), `master.rs:111-174`
//! (transact возвращает Result<Vec<u8>, ModbusError>).

use std::fmt;

/// Кастомный тип ошибок — как frames.rs:18-33.
#[derive(Debug, Clone, PartialEq)]
pub enum ModbusError {
    Timeout,
    BadCrc,
    IllegalFunction,
    IllegalAddress,
    IllegalValue,
    Io(String),
}

// impl std::fmt::Display для ModbusError (frames.rs:35-52)
impl fmt::Display for ModbusError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ModbusError::Timeout => write!(f, "timeout (no response)"),
            ModbusError::BadCrc => write!(f, "bad CRC"),
            ModbusError::IllegalFunction => write!(f, "illegal function (0x01)"),
            ModbusError::IllegalAddress => write!(f, "illegal data address (0x02)"),
            ModbusError::IllegalValue => write!(f, "illegal data value (0x03)"),
            ModbusError::Io(e) => write!(f, "IO: {}", e),
        }
    }
}

// Реализация std::error::Error — маркер, позволяет использовать в generic.
impl std::error::Error for ModbusError {}

/// Функция возвращающая Result. `?` пробрасывает ошибку.
fn read_two_registers(regs: &[u16], count: usize) -> Result<Vec<u16>, ModbusError> {
    if count != 2 {
        return Err(ModbusError::IllegalValue);
    }
    if regs.len() < count {
        return Err(ModbusError::Timeout); // не стало данных
    }
    Ok(regs[..count].to_vec())
}

/// Option → Result: когда "нет значения" = "ошибка у себя".
fn first_or_error(items: &[u16]) -> Result<u16, ModbusError> {
    match items.first().copied() {
        Some(v) => Ok(v),
        None => Err(ModbusError::IllegalAddress),
    }
}

/// Композиция: while let, map_err.
fn fetch_value(config: &[u16]) -> Result<u16, ModbusError> {
    let raw = read_two_registers(config, 2)?; // ? пробрасывает
    Ok(raw[0] + raw[1])
}

/// Идиома для Map/Unwrap — в реальном коде встречается в app.rs/worker.rs.
fn option_combinations() {
    let maybe: Option<u16> = Some(12);
    assert_eq!(maybe.map(|v| v * 2).unwrap_or(0), 24);
    assert_eq!(None::<u16>.map(|v| v * 2).unwrap_or(7), 7);
}

fn main() {
    // Result в match
    let regs = [0x00, 0x7B];
    match read_two_registers(&regs, 2) {
        Ok(data) => assert_eq!(data, vec![0x0000, 0x007B]),
        Err(e) => panic!("shouldn't happen: {e}"),
    }

    // Err путь — тоже match
    assert_eq!(read_two_registers(&regs, 3).unwrap_err(), ModbusError::IllegalValue);

    // ? в main можно через envelope (panic! наверху), но для примеров
    // достаточно вызывать в помощнике:
    assert_eq!(first_or_error(&[5]).unwrap(), 5);
    assert_eq!(fetch_value(&[10, 20]).unwrap(), 30);

    // Отображаем ошибку через Display
    let e = ModbusError::BadCrc;
    println!("ModbusError Display: {e}");

    option_combinations();
    println!("07_errors: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn propagates_via_qmark() {
        let wrong = read_two_registers(&[1, 2], 5);
        assert!(wrong.is_err());
    }

    #[test]
    fn display_format() {
        assert_eq!(format!("{}", ModbusError::IllegalAddress), "illegal data address (0x02)");
    }
}