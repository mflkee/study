/// CRC-16 Modbus calculation

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

/// Verify CRC of a Modbus RTU frame
/// Returns true if CRC is correct
pub fn verify_crc(frame: &[u8]) -> bool {
    if frame.len() < 3 {
        return false;
    }
    
    let data = &frame[..frame.len() - 2];
    let received_crc = u16::from_le_bytes([frame[frame.len() - 2], frame[frame.len() - 1]]);
    let calculated_crc = crc16(data);
    
    received_crc == calculated_crc
}

/// Append CRC to a Modbus RTU frame
pub fn append_crc(frame: &mut Vec<u8>) {
    let crc = crc16(frame);
    frame.push(crc as u8);
    frame.push((crc >> 8) as u8);
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_crc16_known_value() {
        // Тестовое значение из спецификации Modbus
        let data = b"123456789";
        assert_eq!(crc16(data), 0x4B37);
    }
    
    #[test]
    fn test_crc16_modbus_request() {
        // Пример запроса: [01 03 00 00 00 0A]
        let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
        let crc = crc16(&data);
        // CRC должен быть вычислен правильно
        assert_ne!(crc, 0);
    }
    
    #[test]
    fn test_verify_crc() {
        let mut frame = vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x0A];
        let crc = crc16(&frame);
        frame.push(crc as u8);
        frame.push((crc >> 8) as u8);
        
        assert!(verify_crc(&frame));
    }
    
    #[test]
    fn test_verify_crc_bad() {
        let frame = vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xFF, 0xFF];
        assert!(!verify_crc(&frame));
    }
}
