//! Упражнение 07: Error Handling.
//!
//! Задание: реализуйте enum ошибок с Display + std::error::Error и
//! функцию, пробрасывающую ошибки через `?` (как `ModbusError`
//! в frames.rs / master.rs).

/// # Задание 1
/// Собственный тип ошибок, реализующий Display.
#[derive(Debug, Clone, PartialEq)]
pub enum SensorError {
    NotConfigured(u8),
    OutOfRange(f32),
    Io(String),
}

impl std::fmt::Display for SensorError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SensorError::NotConfigured(id) => write!(f, "sensor {id} not configured"),
            SensorError::OutOfRange(v) => write!(f, "value {v} out of range"),
            SensorError::Io(msg) => write!(f, "I/O: {msg}"),
        }
    }
}

impl std::error::Error for SensorError {}

/// # Задание 2
/// Функция, которая проверяет значение и возвращает Result.
/// Используйте `?`-аналог (через if let return Err).
pub fn read_scaled(id: u8, raw: i16) -> Result<f32, SensorError> {
    if id == 0 {
        return Err(SensorError::NotConfigured(id));
    }
    let v = raw as f32 / 10.0;
    if !(0.0..=100.0).contains(&v) {
        return Err(SensorError::OutOfRange(v));
    }
    Ok(v)
}

/// # Плюс: map_err для конвертации io::Error → SensorError.
pub fn convert(e: std::io::Error) -> SensorError {
    SensorError::Io(e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ok_path() {
        assert_eq!(read_scaled(1, 123).unwrap(), 12.3);
    }

    #[test]
    fn not_configured() {
        assert!(matches!(
            read_scaled(0, 10),
            Err(SensorError::NotConfigured(0))
        ));
    }

    #[test]
    fn out_of_range() {
        assert!(matches!(
            read_scaled(2, 2000),
            Err(SensorError::OutOfRange(_))
        ));
    }

    #[test]
    fn display_is_readable() {
        let e = SensorError::OutOfRange(999.0);
        assert_eq!(format!("{e}"), "value 999 out of range");
    }
}