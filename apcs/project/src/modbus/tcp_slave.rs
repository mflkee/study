use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::net::TcpListener;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use log::{info, error, debug};

use super::register_map::RegisterMap;
use super::pdu::{self, ModbusFunction, ModbusPdu};
use super::exceptions;

/// Modbus TCP Slave (сервер)
pub struct TcpSlave {
    address: String,
    port: u16,
    register_map: Arc<RwLock<RegisterMap>>,
}

const MBAP_HEADER_SIZE: usize = 7;

impl TcpSlave {
    pub fn new(address: String, port: u16, register_map: Arc<RwLock<RegisterMap>>) -> Self {
        Self {
            address,
            port,
            register_map,
        }
    }
    
    pub async fn run(&self) -> anyhow::Result<()> {
        let listener = TcpListener::bind(format!("{}:{}", self.address, self.port)).await?;
        info!("Modbus TCP server listening on {}:{}", self.address, self.port);
        
        loop {
            let (stream, addr) = listener.accept().await?;
            info!("New connection from {}", addr);
            
            let register_map = self.register_map.clone();
            
            tokio::spawn(async move {
                if let Err(e) = handle_client(stream, register_map).await {
                    error!("Client handler error: {}", e);
                }
            });
        }
    }
}

async fn handle_client(
    mut stream: tokio::net::TcpStream,
    register_map: Arc<RwLock<RegisterMap>>,
) -> anyhow::Result<()> {
    let mut buf = [0u8; 256];
    
    loop {
        // Читаем MBAP Header (7 байт)
        match stream.read_exact(&mut buf[..MBAP_HEADER_SIZE]).await {
            Ok(_) => {},
            Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                debug!("Client disconnected");
                break;
            }
            Err(e) => {
                error!("Read error: {}", e);
                break;
            }
        }
        
        // Парсим MBAP
        let tx_id = u16::from_be_bytes([buf[0], buf[1]]);
        let proto_id = u16::from_be_bytes([buf[2], buf[3]]);
        let length = u16::from_be_bytes([buf[4], buf[5]]) as usize;
        let unit_id = buf[6];
        
        // Проверяем Protocol ID
        if proto_id != 0 {
            error!("Invalid protocol ID: {}", proto_id);
            break;
        }
        
        // Проверяем длину
        if length > buf.len() {
            error!("Frame too long: {}", length);
            break;
        }
        
        // Читаем Unit ID + PDU
        if stream.read_exact(&mut buf[..length]).await.is_err() {
            break;
        }
        
        // Парсим PDU
        let unit_id_in_frame = buf[0];
        let pdu_bytes = &buf[1..length];
        
        debug!("Received: tx_id=0x{:04X}, unit_id={}, pdu={:?}", 
               tx_id, unit_id_in_frame, pdu_bytes);
        
        // Обрабатываем запрос
        let response_pdu = match pdu::parse_pdu(pdu_bytes) {
            Ok(pdu) => {
                handle_request(pdu, &register_map).await
            }
            Err(e) => {
                error!("PDU parse error: {}", e);
                exceptions::build_exception_response(pdu_bytes[0], 0x01)
            }
        };
        
        // Собираем MBAP для ответа
        let resp_length = 1 + response_pdu.len(); // Unit ID + PDU
        let mut response = Vec::with_capacity(MBAP_HEADER_SIZE + resp_length);
        
        // Transaction ID
        response.extend_from_slice(&tx_id.to_be_bytes());
        // Protocol ID = 0
        response.extend_from_slice(&[0x00, 0x00]);
        // Length
        response.extend_from_slice(&(resp_length as u16).to_be_bytes());
        // Unit ID
        response.push(unit_id_in_frame);
        // PDU
        response.extend_from_slice(&response_pdu);
        
        debug!("Response: {:?}", response);
        
        // Отправляем
        if stream.write_all(&response).await.is_err() {
            error!("Write error");
            break;
        }
    }
    
    Ok(())
}

async fn handle_request(
    pdu: ModbusPdu,
    register_map: &Arc<RwLock<RegisterMap>>,
) -> Vec<u8> {
    match pdu.function {
        ModbusFunction::ReadCoils => {
            match pdu::parse_read_request(&pdu) {
                Ok(req) => {
                    let map = register_map.read().await;
                    match map.read_coils(req.address, req.quantity) {
                        Ok(coils) => {
                            // Упаковка битов в байты
                            let byte_count = (req.quantity as usize + 7) / 8;
                            let mut response = Vec::with_capacity(2 + byte_count);
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
                            
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x01, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x01, 0x03)
                }
            }
        }
        
        ModbusFunction::ReadDiscreteInputs => {
            // Аналогично ReadCoils
            match pdu::parse_read_request(&pdu) {
                Ok(req) => {
                    let map = register_map.read().await;
                    match map.read_coils(req.address, req.quantity) {
                        Ok(coils) => {
                            let byte_count = (req.quantity as usize + 7) / 8;
                            let mut response = Vec::with_capacity(2 + byte_count);
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
                            
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x02, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x02, 0x03)
                }
            }
        }
        
        ModbusFunction::ReadHoldingRegisters => {
            match pdu::parse_read_request(&pdu) {
                Ok(req) => {
                    let map = register_map.read().await;
                    match map.read_holding_registers(req.address, req.quantity) {
                        Ok(values) => {
                            let mut response = Vec::with_capacity(2 + values.len() * 2);
                            response.push(0x03);
                            response.push((values.len() * 2) as u8);
                            for v in values {
                                response.extend_from_slice(&v.to_be_bytes());
                            }
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x03, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x03, 0x03)
                }
            }
        }
        
        ModbusFunction::ReadInputRegisters => {
            match pdu::parse_read_request(&pdu) {
                Ok(req) => {
                    let map = register_map.read().await;
                    match map.read_input_registers(req.address, req.quantity) {
                        Ok(values) => {
                            let mut response = Vec::with_capacity(2 + values.len() * 2);
                            response.push(0x04);
                            response.push((values.len() * 2) as u8);
                            for v in values {
                                response.extend_from_slice(&v.to_be_bytes());
                            }
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x04, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x04, 0x03)
                }
            }
        }
        
        ModbusFunction::WriteSingleCoil => {
            match pdu::parse_write_single_request(&pdu) {
                Ok(req) => {
                    // Value: 0xFF00 = ON, 0x0000 = OFF
                    let value = match req.value {
                        0xFF00 => true,
                        0x0000 => false,
                        _ => return exceptions::build_exception_response(0x05, 0x03),
                    };
                    
                    let mut map = register_map.write().await;
                    match map.write_coil(req.address, value) {
                        Ok(()) => {
                            // Эхо запроса
                            let mut response = Vec::with_capacity(5);
                            response.push(0x05);
                            response.extend_from_slice(&req.address.to_be_bytes());
                            response.extend_from_slice(&req.value.to_be_bytes());
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x05, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x05, 0x03)
                }
            }
        }
        
        ModbusFunction::WriteSingleRegister => {
            match pdu::parse_write_single_request(&pdu) {
                Ok(req) => {
                    let mut map = register_map.write().await;
                    match map.write_holding_register(req.address, req.value) {
                        Ok(()) => {
                            // Эхо запроса
                            let mut response = Vec::with_capacity(5);
                            response.push(0x06);
                            response.extend_from_slice(&req.address.to_be_bytes());
                            response.extend_from_slice(&req.value.to_be_bytes());
                            response
                        }
                        Err(code) => exceptions::build_exception_response(0x06, code),
                    }
                }
                Err(e) => {
                    error!("Parse error: {}", e);
                    exceptions::build_exception_response(0x06, 0x03)
                }
            }
        }
        
        ModbusFunction::WriteMultipleCoils => {
            // TODO: Implement
            exceptions::build_exception_response(0x0F, 0x01)
        }
        
        ModbusFunction::WriteMultipleRegisters => {
            // TODO: Implement
            exceptions::build_exception_response(0x10, 0x01)
        }
        
        ModbusFunction::Unknown(code) => {
            error!("Unsupported function code: 0x{:02X}", code);
            exceptions::build_exception_response(code, 0x01)
        }
    }
}
