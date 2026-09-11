/// Modbus RTU Slave — listens on serial port and responds to requests

use std::sync::Arc;
use tokio::sync::RwLock;
use tokio_serial::SerialPortBuilderExt;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::time::{sleep, Duration};
use log::{info, error, debug, warn};

use crate::register_map::RegisterMap;
use crate::crc;

pub struct RtuSlave {
    port: String,
    baud_rate: u32,
    slave_id: u8,
    register_map: Arc<RwLock<RegisterMap>>,
}

impl RtuSlave {
    pub fn new(
        port: String,
        baud_rate: u32,
        slave_id: u8,
        register_map: Arc<RwLock<RegisterMap>>,
    ) -> Self {
        Self {
            port,
            baud_rate,
            slave_id,
            register_map,
        }
    }
    
    pub async fn run(&self) -> anyhow::Result<()> {
        let mut port = tokio_serial::new(&self.port, self.baud_rate)
            .open_native_async()?;
        
        info!("Serial port opened: {} @ {} baud, slave_id={}", 
              self.port, self.baud_rate, self.slave_id);
        
        let mut buf = [0u8; 256];
        
        loop {
            // Читаем запрос (ждём данные)
            match port.read(&mut buf).await {
                Ok(n) if n > 0 => {
                    debug!("Received {} bytes: {:02X?}", n, &buf[..n]);
                    
                    // Обрабатываем запрос
                    if let Some(response) = self.handle_request(&buf[..n]).await {
                        // Ждём inter-frame gap (3.5 символа)
                        // При 9600 baud: ~4 мс
                        let gap_ms = (35 * 11 * 1000) / (self.baud_rate as u64) + 1;
                        sleep(Duration::from_millis(gap_ms)).await;
                        
                        // Отправляем ответ
                        debug!("Sending {} bytes: {:02X?}", response.len(), &response);
                        if let Err(e) = port.write_all(&response).await {
                            error!("Write error: {}", e);
                        }
                    }
                }
                Ok(_) => {
                    // 0 bytes — таймаут, продолжаем
                }
                Err(e) if e.kind() == std::io::ErrorKind::TimedOut => {
                    // Таймаут чтения — нормально
                }
                Err(e) => {
                    error!("Read error: {}", e);
                    sleep(Duration::from_millis(100)).await;
                }
            }
        }
    }
    
    async fn handle_request(&self, frame: &[u8]) -> Option<Vec<u8>> {
        // Минимальный кадр: addr(1) + fc(1) + crc(2) = 4 байта
        if frame.len() < 4 {
            warn!("Frame too short: {} bytes", frame.len());
            return None;
        }
        
        // Проверяем CRC
        if !crc::verify_crc(frame) {
            warn!("CRC mismatch");
            return None;
        }
        
        let slave_id = frame[0];
        let function_code = frame[1];
        
        // Проверяем адрес устройства
        if slave_id != self.slave_id && slave_id != 0 {
            debug!("Ignoring frame for device {}", slave_id);
            return None;
        }
        
        info!("Request: slave_id={}, fc=0x{:02X}", slave_id, function_code);
        
        // Обрабатываем по коду функции
        let response = match function_code {
            0x01 => self.handle_read_coils(&frame).await,
            0x02 => self.handle_read_discrete_inputs(&frame).await,
            0x03 => self.handle_read_holding_registers(&frame).await,
            0x04 => self.handle_read_input_registers(&frame).await,
            0x05 => self.handle_write_single_coil(&frame).await,
            0x06 => self.handle_write_single_register(&frame).await,
            0x0F => self.handle_write_multiple_coils(&frame).await,
            0x10 => self.handle_write_multiple_registers(&frame).await,
            _ => {
                warn!("Unsupported function code: 0x{:02X}", function_code);
                self.build_exception_response(function_code, 0x01) // Illegal Function
            }
        };
        
        Some(response)
    }
    
    async fn handle_read_holding_registers(&self, frame: &[u8]) -> Vec<u8> {
        // FC03: [addr][fc][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
        if frame.len() < 8 {
            return self.build_exception_response(0x03, 0x03);
        }
        
        let start = u16::from_be_bytes([frame[2], frame[3]]);
        let count = u16::from_be_bytes([frame[4], frame[5]]);
        
        if count == 0 || count > 125 {
            return self.build_exception_response(0x03, 0x03);
        }
        
        let map = self.register_map.read().await;
        match map.read_holding_registers(start, count) {
            Ok(values) => {
                let mut response = Vec::with_capacity(3 + values.len() * 2);
                response.push(self.slave_id);
                response.push(0x03);
                response.push((values.len() * 2) as u8);
                
                for v in values {
                    response.extend_from_slice(&v.to_be_bytes());
                }
                
                crc::append_crc(&mut response);
                response
            }
            Err(code) => self.build_exception_response(0x03, code),
        }
    }
    
    async fn handle_read_input_registers(&self, frame: &[u8]) -> Vec<u8> {
        // FC04: [addr][fc][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
        if frame.len() < 8 {
            return self.build_exception_response(0x04, 0x03);
        }
        
        let start = u16::from_be_bytes([frame[2], frame[3]]);
        let count = u16::from_be_bytes([frame[4], frame[5]]);
        
        if count == 0 || count > 125 {
            return self.build_exception_response(0x04, 0x03);
        }
        
        let map = self.register_map.read().await;
        match map.read_input_registers(start, count) {
            Ok(values) => {
                let mut response = Vec::with_capacity(3 + values.len() * 2);
                response.push(self.slave_id);
                response.push(0x04);
                response.push((values.len() * 2) as u8);
                
                for v in values {
                    response.extend_from_slice(&v.to_be_bytes());
                }
                
                crc::append_crc(&mut response);
                response
            }
            Err(code) => self.build_exception_response(0x04, code),
        }
    }
    
    async fn handle_read_coils(&self, frame: &[u8]) -> Vec<u8> {
        // FC01: [addr][fc][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
        if frame.len() < 8 {
            return self.build_exception_response(0x01, 0x03);
        }
        
        let start = u16::from_be_bytes([frame[2], frame[3]]);
        let count = u16::from_be_bytes([frame[4], frame[5]]);
        
        if count == 0 || count > 2000 {
            return self.build_exception_response(0x01, 0x03);
        }
        
        let map = self.register_map.read().await;
        match map.read_coils(start, count) {
            Ok(coils) => {
                let byte_count = (coils.len() + 7) / 8;
                let mut response = Vec::with_capacity(3 + byte_count);
                response.push(self.slave_id);
                response.push(0x01);
                response.push(byte_count as u8);
                
                // Упаковка битов
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
            Err(code) => self.build_exception_response(0x01, code),
        }
    }
    
    async fn handle_read_discrete_inputs(&self, frame: &[u8]) -> Vec<u8> {
        // Аналогично read_coils (FC02)
        self.handle_read_coils(frame).await
    }
    
    async fn handle_write_single_coil(&self, frame: &[u8]) -> Vec<u8> {
        // FC05: [addr][fc][coil_hi][coil_lo][value_hi][value_lo][crc_lo][crc_hi]
        if frame.len() < 8 {
            return self.build_exception_response(0x05, 0x03);
        }
        
        let coil_addr = u16::from_be_bytes([frame[2], frame[3]]);
        let value = u16::from_be_bytes([frame[4], frame[5]]);
        
        // Value: 0xFF00 = ON, 0x0000 = OFF
        let coil_value = match value {
            0xFF00 => true,
            0x0000 => false,
            _ => return self.build_exception_response(0x05, 0x03),
        };
        
        let mut map = self.register_map.write().await;
        match map.write_coil(coil_addr, coil_value) {
            Ok(()) => {
                // Эхо запроса
                let mut response = frame[..6].to_vec();
                crc::append_crc(&mut response);
                response
            }
            Err(code) => self.build_exception_response(0x05, code),
        }
    }
    
    async fn handle_write_single_register(&self, frame: &[u8]) -> Vec<u8> {
        // FC06: [addr][fc][reg_hi][reg_lo][value_hi][value_lo][crc_lo][crc_hi]
        if frame.len() < 8 {
            return self.build_exception_response(0x06, 0x03);
        }
        
        let reg_addr = u16::from_be_bytes([frame[2], frame[3]]);
        let value = u16::from_be_bytes([frame[4], frame[5]]);
        
        let mut map = self.register_map.write().await;
        match map.write_holding_register(reg_addr, value) {
            Ok(()) => {
                // Эхо запроса
                let mut response = frame[..6].to_vec();
                crc::append_crc(&mut response);
                response
            }
            Err(code) => self.build_exception_response(0x06, code),
        }
    }
    
    async fn handle_write_multiple_coils(&self, frame: &[u8]) -> Vec<u8> {
        // FC0F: [addr][fc][start_hi][start_lo][count_hi][count_lo][byte_count][values...][crc_lo][crc_hi]
        if frame.len() < 9 {
            return self.build_exception_response(0x0F, 0x03);
        }
        
        let start = u16::from_be_bytes([frame[2], frame[3]]);
        let count = u16::from_be_bytes([frame[4], frame[5]]);
        let byte_count = frame[6] as usize;
        
        if frame.len() < 7 + byte_count + 2 {
            return self.build_exception_response(0x0F, 0x03);
        }
        
        let mut map = self.register_map.write().await;
        for i in 0..count {
            let byte_idx = 7 + (i as usize) / 8;
            let bit_idx = (i as usize) % 8;
            let value = (frame[byte_idx] >> bit_idx) & 1 == 1;
            
            if let Err(code) = map.write_coil(start + i, value) {
                return self.build_exception_response(0x0F, code);
            }
        }
        
        // Ответ: [addr][fc][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
        let mut response = vec![self.slave_id, 0x0F];
        response.extend_from_slice(&start.to_be_bytes());
        response.extend_from_slice(&count.to_be_bytes());
        crc::append_crc(&mut response);
        response
    }
    
    async fn handle_write_multiple_registers(&self, frame: &[u8]) -> Vec<u8> {
        // FC10: [addr][fc][start_hi][start_lo][count_hi][count_lo][byte_count][values...][crc_lo][crc_hi]
        if frame.len() < 9 {
            return self.build_exception_response(0x10, 0x03);
        }
        
        let start = u16::from_be_bytes([frame[2], frame[3]]);
        let count = u16::from_be_bytes([frame[4], frame[5]]);
        let byte_count = frame[6] as usize;
        
        if frame.len() < 7 + byte_count + 2 {
            return self.build_exception_response(0x10, 0x03);
        }
        
        if byte_count != (count as usize) * 2 {
            return self.build_exception_response(0x10, 0x03);
        }
        
        let mut map = self.register_map.write().await;
        for i in 0..count {
            let offset = 7 + (i as usize) * 2;
            let value = u16::from_be_bytes([frame[offset], frame[offset + 1]]);
            
            if let Err(code) = map.write_holding_register(start + i, value) {
                return self.build_exception_response(0x10, code);
            }
        }
        
        // Ответ: [addr][fc][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
        let mut response = vec![self.slave_id, 0x10];
        response.extend_from_slice(&start.to_be_bytes());
        response.extend_from_slice(&count.to_be_bytes());
        crc::append_crc(&mut response);
        response
    }
    
    fn build_exception_response(&self, function_code: u8, exception_code: u8) -> Vec<u8> {
        let mut response = vec![self.slave_id, function_code | 0x80, exception_code];
        crc::append_crc(&mut response);
        response
    }
}
