// Упражнение 4.2: Modbus PDU Parser
//
// Задача: Реализуйте парсер Modbus PDU на Python.
// Функция принимает байты и возвращает структуру с кодом функции и данными.

use std::fmt;

#[derive(Debug, Clone)]
pub enum ModbusFunction {
    ReadCoils,
    ReadDiscreteInputs,
    ReadHoldingRegisters,
    ReadInputRegisters,
    WriteSingleCoil,
    WriteSingleRegister,
    WriteMultipleCoils,
    WriteMultipleRegisters,
    Unknown(u8),
}

impl ModbusFunction {
    pub fn from_u8(code: u8) -> Self {
        match code {
            0x01 => Self::ReadCoils,
            0x02 => Self::ReadDiscreteInputs,
            0x03 => Self::ReadHoldingRegisters,
            0x04 => Self::ReadInputRegisters,
            0x05 => Self::WriteSingleCoil,
            0x06 => Self::WriteSingleRegister,
            0x0F => Self::WriteMultipleCoils,
            0x10 => Self::WriteMultipleRegisters,
            _ => Self::Unknown(code),
        }
    }
}

#[derive(Debug, Clone)]
pub struct ModbusPdu {
    pub function: ModbusFunction,
    pub data: Vec<u8>,
}

#[derive(Debug, Clone)]
pub struct ReadRequest {
    pub address: u16,
    pub quantity: u16,
}

#[derive(Debug, Clone)]
pub struct WriteSingleRequest {
    pub address: u16,
    pub value: u16,
}

#[derive(Debug)]
pub enum ParseError {
    EmptyPdu,
    InvalidLength { expected: usize, actual: usize },
    InvalidFunction(u8),
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::EmptyPdu => write!(f, "PDU is empty"),
            Self::InvalidLength { expected, actual } => {
                write!(f, "Invalid length: expected {}, got {}", expected, actual)
            }
            Self::InvalidFunction(code) => write!(f, "Invalid function code: 0x{:02X}", code),
        }
    }
}

/// Парсит PDU из байтов
pub fn parse_pdu(bytes: &[u8]) -> Result<ModbusPdu, ParseError> {
    if bytes.is_empty() {
        return Err(ParseError::EmptyPdu);
    }

    let function = ModbusFunction::from_u8(bytes[0]);
    let data = bytes[1..].to_vec();

    Ok(ModbusPdu { function, data })
}

/// Извлекает Read Request из PDU (для функций 0x01-0x04)
pub fn parse_read_request(pdu: &ModbusPdu) -> Result<ReadRequest, ParseError> {
    if pdu.data.len() < 4 {
        return Err(ParseError::InvalidLength {
            expected: 4,
            actual: pdu.data.len(),
        });
    }

    let address = u16::from_be_bytes([pdu.data[0], pdu.data[1]]);
    let quantity = u16::from_be_bytes([pdu.data[2], pdu.data[3]]);

    Ok(ReadRequest { address, quantity })
}

/// Извлекает Write Single Request из PDU (для функций 0x05, 0x06)
pub fn parse_write_single_request(pdu: &ModbusPdu) -> Result<WriteSingleRequest, ParseError> {
    if pdu.data.len() < 4 {
        return Err(ParseError::InvalidLength {
            expected: 4,
            actual: pdu.data.len(),
        });
    }

    let address = u16::from_be_bytes([pdu.data[0], pdu.data[1]]);
    let value = u16::from_be_bytes([pdu.data[2], pdu.data[3]]);

    Ok(WriteSingleRequest { address, value })
}

/// Формирует PDU ответа для Read Holding Registers
pub fn build_read_holding_response(values: &[u16]) -> Vec<u8> {
    let byte_count = values.len() * 2;
    let mut pdu = Vec::with_capacity(2 + byte_count);
    
    pdu.push(0x03); // Function code
    pdu.push(byte_count as u8);
    
    for &value in values {
        pdu.extend_from_slice(&value.to_be_bytes());
    }
    
    pdu
}

/// Формирует PDU исключения
pub fn build_exception_response(function_code: u8, exception_code: u8) -> Vec<u8> {
    vec![function_code | 0x80, exception_code]
}

fn main() {
    // Пример: Read Holding Registers (адрес 0, 10 регистров)
    let request_bytes = [0x03, 0x00, 0x00, 0x00, 0x0A];
    
    match parse_pdu(&request_bytes) {
        Ok(pdu) => {
            println!("Function: {:?}", pdu.function);
            
            match parse_read_request(&pdu) {
                Ok(req) => {
                    println!("Address: {}, Quantity: {}", req.address, req.quantity);
                    
                    // Имитируем чтение регистров
                    let values: Vec<u16> = (0..req.quantity).map(|i| i * 10).collect();
                    let response = build_read_holding_response(&values);
                    println!("Response: {:?}", response);
                }
                Err(e) => println!("Error parsing request: {}", e),
            }
        }
        Err(e) => println!("Error parsing PDU: {}", e),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_pdu() {
        let bytes = [0x03, 0x00, 0x00, 0x00, 0x0A];
        let pdu = parse_pdu(&bytes).unwrap();
        
        assert!(matches!(pdu.function, ModbusFunction::ReadHoldingRegisters));
        assert_eq!(pdu.data.len(), 4);
    }

    #[test]
    fn test_parse_read_request() {
        let bytes = [0x03, 0x00, 0x64, 0x00, 0x03]; // адрес 100, 3 регистра
        let pdu = parse_pdu(&bytes).unwrap();
        let req = parse_read_request(&pdu).unwrap();
        
        assert_eq!(req.address, 100);
        assert_eq!(req.quantity, 3);
    }

    #[test]
    fn test_build_response() {
        let values = vec![0x1234, 0x5678];
        let response = build_read_holding_response(&values);
        
        assert_eq!(response[0], 0x03);
        assert_eq!(response[1], 4); // 2 регистра × 2 байта
        assert_eq!(&response[2..4], &[0x12, 0x34]);
        assert_eq!(&response[4..6], &[0x56, 0x78]);
    }

    #[test]
    fn test_exception() {
        let exc = build_exception_response(0x03, 0x02);
        assert_eq!(exc, vec![0x83, 0x02]);
    }
}
