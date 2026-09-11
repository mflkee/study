/// CRC-16 Modbus calculation (для ESP32)

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

pub fn verify_crc(frame: &[u8]) -> bool {
    if frame.len() < 3 {
        return false;
    }
    
    let data = &frame[..frame.len() - 2];
    let received_crc = u16::from_le_bytes([frame[frame.len() - 2], frame[frame.len() - 1]]);
    let calculated_crc = crc16(data);
    
    received_crc == calculated_crc
}

pub fn append_crc(frame: &mut Vec<u8>) {
    let crc = crc16(frame);
    frame.push(crc as u8);
    frame.push((crc >> 8) as u8);
}