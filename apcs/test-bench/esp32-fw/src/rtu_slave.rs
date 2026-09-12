/// Modbus RTU Slave — обработка запросов на ESP32
///
/// Принимает кадры, проверяет CRC, отвечает через UART.
/// Управление направлением DE/RE (для RS-485) остаётся за вызывающим кодом.

use std::sync::{Arc, RwLock};

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::uart::UartDriver;

use crate::crc;
use crate::register_map::RegisterMap;

/// Обрабатывает один Modbus RTU кадр.
/// Возвращает Ok(true) если кадр обработан, Ok(false) если это не наш кадр.
pub fn handle_frame(
    frame: &[u8],
    slave_id: u8,
    map: &Arc<RwLock<RegisterMap>>,
    uart: &mut UartDriver,
) -> Result<bool, ()> {
    // Минимальный кадр: addr(1) + fc(1) + crc(2) = 4 байта
    if frame.len() < 4 {
        return Err(());
    }
    
    // Проверяем адрес устройства
    if frame[0] != slave_id && frame[0] != 0 {
        return Ok(false);
    }
    
    // Проверяем CRC
    if !crc::verify_crc(frame) {
        log::warn!("CRC mismatch in frame: {:02X?}", frame);
        return Ok(false);
    }
    
    let function_code = frame[1];
    log::debug!("Request: fc=0x{:02X}", function_code);
    
    // Строим ответ
    let response = match function_code {
        0x01 => handle_read_coils(frame, slave_id, map),
        0x02 => handle_read_discrete_inputs(frame, slave_id, map),
        0x03 => handle_read_holding_registers(frame, slave_id, map),
        0x04 => handle_read_input_registers(frame, slave_id, map),
        0x05 => handle_write_single_coil(frame, slave_id, map),
        0x06 => handle_write_single_register(frame, slave_id, map),
        0x0F => handle_write_multiple_coils(frame, slave_id, map),
        0x10 => handle_write_multiple_registers(frame, slave_id, map),
        _ => {
            log::warn!("Unsupported function code: 0x{:02X}", function_code);
            build_exception_response(slave_id, function_code, 0x01)
        }
    };
    
    // Отправляем ответ
    let write_result = write_all(uart, &response);

    write_result?;
    
    log::debug!("Response sent: {:02X?}", response);
    
    Ok(true)
}

/// Полное записываем данные через UART
fn write_all(uart: &mut UartDriver, data: &[u8]) -> Result<(), ()> {
    for chunk in data.chunks(64) {
        match uart.write(chunk) {
            Ok(_) => {}
            Err(e) => {
                log::error!("UART write error: {:?}", e);
                return Err(());
            }
        }
    }
    FreeRtos::delay_ms(1);
    Ok(())
}

// --- Обработчики функций ---

fn handle_read_coils(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x01, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    
    if count == 0 || count > 2000 {
        return build_exception_response(slave_id, 0x01, 0x03);
    }
    
    let m = map.read().unwrap();
    match m.read_coils(start, count) {
        Ok(coils) => {
            let byte_count = (coils.len() + 7) / 8;
            let mut response = Vec::with_capacity(3 + byte_count);
            response.push(slave_id);
            response.push(0x01);
            response.push(byte_count as u8);
            
            for i in 0..byte_count {
                let mut byte = 0u8;
                for bit in 0..8 {
                    let idx = i * 8 + bit;
                    if idx < coils.len() && coils[idx] {
                        byte |= 1 << bit;
                    }
                }
                response.push(byte);
            }
            
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x01, code),
    }
}

fn handle_read_discrete_inputs(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    // Аналогично Read Coils (FC02)
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x02, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    
    if count == 0 || count > 2000 {
        return build_exception_response(slave_id, 0x02, 0x03);
    }
    
    let m = map.read().unwrap();
    match m.read_coils(start, count) {
        Ok(coils) => {
            let byte_count = (coils.len() + 7) / 8;
            let mut response = Vec::with_capacity(3 + byte_count);
            response.push(slave_id);
            response.push(0x02);
            response.push(byte_count as u8);
            
            for i in 0..byte_count {
                let mut byte = 0u8;
                for bit in 0..8 {
                    let idx = i * 8 + bit;
                    if idx < coils.len() && coils[idx] {
                        byte |= 1 << bit;
                    }
                }
                response.push(byte);
            }
            
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x02, code),
    }
}

fn handle_read_holding_registers(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x03, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    
    if count == 0 || count > 125 {
        return build_exception_response(slave_id, 0x03, 0x03);
    }
    
    let m = map.read().unwrap();
    match m.read_holding_registers(start, count) {
        Ok(values) => {
            let mut response = Vec::with_capacity(3 + values.len() * 2);
            response.push(slave_id);
            response.push(0x03);
            response.push((values.len() * 2) as u8);
            
            for v in values {
                response.extend_from_slice(&v.to_be_bytes());
            }
            
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x03, code),
    }
}

fn handle_read_input_registers(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x04, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    
    if count == 0 || count > 125 {
        return build_exception_response(slave_id, 0x04, 0x03);
    }
    
    let m = map.read().unwrap();
    match m.read_input_registers(start, count) {
        Ok(values) => {
            let mut response = Vec::with_capacity(3 + values.len() * 2);
            response.push(slave_id);
            response.push(0x04);
            response.push((values.len() * 2) as u8);
            
            for v in values {
                response.extend_from_slice(&v.to_be_bytes());
            }
            
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x04, code),
    }
}

fn handle_write_single_coil(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x05, 0x03);
    }
    
    let coil_addr = u16::from_be_bytes([frame[2], frame[3]]);
    let value = u16::from_be_bytes([frame[4], frame[5]]);
    
    let coil_value = match value {
        0xFF00 => true,
        0x0000 => false,
        _ => return build_exception_response(slave_id, 0x05, 0x03),
    };
    
    let mut m = map.write().unwrap();
    match m.write_coil(coil_addr, coil_value) {
        Ok(()) => {
            log::info!("Coil {} set to {}", coil_addr, coil_value);
            let mut response = frame[..6].to_vec();
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x05, code),
    }
}

fn handle_write_single_register(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 8 {
        return build_exception_response(slave_id, 0x06, 0x03);
    }
    
    let reg_addr = u16::from_be_bytes([frame[2], frame[3]]);
    let value = u16::from_be_bytes([frame[4], frame[5]]);
    
    let mut m = map.write().unwrap();
    match m.write_holding_register(reg_addr, value) {
        Ok(()) => {
            log::info!("Register {} set to {}", reg_addr, value);
            let mut response = frame[..6].to_vec();
            crc::append_crc(&mut response);
            response
        }
        Err(code) => build_exception_response(slave_id, 0x06, code),
    }
}

fn handle_write_multiple_coils(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 9 {
        return build_exception_response(slave_id, 0x0F, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    let byte_count = frame[6] as usize;
    
    if frame.len() < 7 + byte_count + 2 {
        return build_exception_response(slave_id, 0x0F, 0x03);
    }
    
    let mut m = map.write().unwrap();
    for i in 0..count {
        let byte_idx = 7 + (i as usize) / 8;
        let bit_idx = (i as usize) % 8;
        let value = (frame[byte_idx] >> bit_idx) & 1 == 1;
        
        if let Err(code) = m.write_coil(start + i, value) {
            return build_exception_response(slave_id, 0x0F, code);
        }
    }
    
    log::info!("Wrote {} coils starting at {}", count, start);
    
    let mut response = vec![slave_id, 0x0F];
    response.extend_from_slice(&start.to_be_bytes());
    response.extend_from_slice(&count.to_be_bytes());
    crc::append_crc(&mut response);
    response
}

fn handle_write_multiple_registers(frame: &[u8], slave_id: u8, map: &Arc<RwLock<RegisterMap>>) -> Vec<u8> {
    if frame.len() < 9 {
        return build_exception_response(slave_id, 0x10, 0x03);
    }
    
    let start = u16::from_be_bytes([frame[2], frame[3]]);
    let count = u16::from_be_bytes([frame[4], frame[5]]);
    let byte_count = frame[6] as usize;
    
    if frame.len() < 7 + byte_count + 2 {
        return build_exception_response(slave_id, 0x10, 0x03);
    }
    
    if byte_count != (count as usize) * 2 {
        return build_exception_response(slave_id, 0x10, 0x03);
    }
    
    let mut m = map.write().unwrap();
    for i in 0..count {
        let offset = 7 + (i as usize) * 2;
        let value = u16::from_be_bytes([frame[offset], frame[offset + 1]]);
        
        if let Err(code) = m.write_holding_register(start + i, value) {
            return build_exception_response(slave_id, 0x10, code);
        }
    }
    
    log::info!("Wrote {} registers starting at {}", count, start);
    
    let mut response = vec![slave_id, 0x10];
    response.extend_from_slice(&start.to_be_bytes());
    response.extend_from_slice(&count.to_be_bytes());
    crc::append_crc(&mut response);
    response
}

fn build_exception_response(slave_id: u8, function_code: u8, exception_code: u8) -> Vec<u8> {
    let mut response = vec![slave_id, function_code | 0x80, exception_code];
    crc::append_crc(&mut response);
    response
}