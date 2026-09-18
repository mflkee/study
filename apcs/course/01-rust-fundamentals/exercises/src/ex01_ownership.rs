//! Упражнение 01: Ownership.
//!
//! Задание: напишите функцию, которая считает контрольную сумму Array
//! из байт, не копируя его, и не требует собственности (актуально для
//! `crc16(&[u8])` и `to_hex(&[u8])` в проекте).

/// # Задание
/// Посчитать «контрольную сумму» — сумму всех байт принятого среза.
/// Срез приходит по ссылке — владение вызывающего не страдает.
pub fn checksum(data: &[u8]) -> u32 {
    data.iter().map(|&b| b as u32).sum()
}

/// # Дополнительно
/// Обработать данные, переместив их внутри — но вернуть обратно, чтобы
/// вызывающий не потерял значение (аналог `read_holding_registers`,
/// который возвращает `Ok(Vec)`).
pub fn append_byte(mut frame: Vec<u8>, b: u8) -> Vec<u8> {
    frame.push(b);
    frame
}

/// # Данные для main/tests
pub struct RegisterFrame {
    pub data: Vec<u8>,
}

impl RegisterFrame {
    pub fn new(data: Vec<u8>) -> Self {
        Self { data }
    }

    /// Владельца нельзя скопировать — но поля можно читать.
    pub fn first_byte(&self) -> u8 {
        self.data[0]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checksum_borrows() {
        assert_eq!(checksum(&[1, 2, 3]), 6);
        assert_eq!(checksum(&[0]), 0);
    }

    #[test]
    fn append_moves_and_returns() {
        let f = append_byte(vec![0x01, 0x03], 0x84);
        assert_eq!(f, vec![0x01, 0x03, 0x84]);
    }

    #[test]
    fn register_frame_field_read() {
        let f = RegisterFrame::new(vec![0x01, 0x03]);
        assert_eq!(f.first_byte(), 0x01);
        assert_eq!(f.data.len(), 2); // доступ к полю — borrow, не move
    }
}