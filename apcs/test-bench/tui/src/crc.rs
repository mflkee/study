//! CRC-16 (Modbus variant) — общий для master/slave.

/// Вычисляет CRC-16 Modbus для буфера.
pub fn crc16(data: &[u8]) -> u16 {
    let mut crc: u16 = 0xFFFF;
    for &byte in data {
        crc ^= byte as u16;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    crc
}

/// Проверяет CRC кадра RTU (последние 2 байта — CRC LE).
pub fn verify(frame: &[u8]) -> bool {
    if frame.len() < 4 {
        return false;
    }
    let data = &frame[..frame.len() - 2];
    let rcvd = u16::from_le_bytes([frame[frame.len() - 2], frame[frame.len() - 1]]);
    rcvd == crc16(data)
}

/// Добавляет CRC в конец кадра.
pub fn append(frame: &mut Vec<u8>) {
    let crc = crc16(frame);
    frame.extend_from_slice(&crc.to_le_bytes());
}

/// Проверка, что кадр адресован нашему slave (или broadcast).
pub fn is_for_us(frame: &[u8], slave_id: u8) -> bool {
    frame.first().copied() == Some(slave_id) || frame.first().copied() == Some(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crc_known_values() {
        // Для "01 03 00 00 00 01" CRC = 0x0A84; в кадр RTU добавляется
        // младшим байтом вперёд: 84 0A.
        let frame = [0x01, 0x03, 0x00, 0x00, 0x00, 0x01];
        assert_eq!(crc16(&frame), 0x0A84);
    }

    #[test]
    fn append_and_verify_roundtrip() {
        let mut frame = vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x01];
        let base_len = frame.len();
        append(&mut frame);
        assert_eq!(frame.len(), base_len + 2);
        assert!(verify(&frame));
        frame[0] = 0xFF;
        assert!(!verify(&frame));
    }

    #[test]
    fn is_for_us_works() {
        assert!(is_for_us(&[0x01, 0x03, 0, 0, 0, 1, 0, 0], 0x01));
        assert!(is_for_us(&[0x00, 0x10, 0, 0, 0, 1, 2, 0, 0, 0, 0], 0x01));
        assert!(!is_for_us(&[0x02, 0x03, 0, 0, 0, 1, 0, 0], 0x01));
    }
}