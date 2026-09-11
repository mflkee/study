/// Modbus PDU (Protocol Data Unit) parsing and building

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

#[derive(Debug, Clone)]
pub struct WriteMultipleRequest {
    pub address: u16,
    pub quantity: u16,
    pub values: Vec<u16>,
}

#[derive(Debug)]
pub enum ParseError {
    EmptyPdu,
    InvalidLength { expected: usize, actual: usize },
}

impl std::fmt::Display for ParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::EmptyPdu => write!(f, "PDU is empty"),
            Self::InvalidLength { expected, actual } => {
                write!(f, "Invalid length: expected at least {}, got {}", expected, actual)
            }
        }
    }
}

impl std::error::Error for ParseError {}

/// Парсит PDU из байтов
pub fn parse_pdu(bytes: &[u8]) -> Result<ModbusPdu, ParseError> {
    if bytes.is_empty() {
        return Err(ParseError::EmptyPdu);
    }
    
    let function = ModbusFunction::from_u8(bytes[0]);
    let data = bytes[1..].to_vec();
    
    Ok(ModbusPdu { function, data })
}

/// Извлекает Read Request (для функций 0x01-0x04)
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

/// Извлекает Write Single Request (для функций 0x05, 0x06)
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

/// Извлекает Write Multiple Request (для функций 0x0F, 0x10)
pub fn parse_write_multiple_request(pdu: &ModbusPdu) -> Result<WriteMultipleRequest, ParseError> {
    if pdu.data.len() < 5 {
        return Err(ParseError::InvalidLength {
            expected: 5,
            actual: pdu.data.len(),
        });
    }
    
    let address = u16::from_be_bytes([pdu.data[0], pdu.data[1]]);
    let quantity = u16::from_be_bytes([pdu.data[2], pdu.data[3]]);
    let byte_count = pdu.data[4] as usize;
    
    if pdu.data.len() < 5 + byte_count {
        return Err(ParseError::InvalidLength {
            expected: 5 + byte_count,
            actual: pdu.data.len(),
        });
    }
    
    let mut values = Vec::with_capacity(quantity as usize);
    for i in 0..quantity as usize {
        let offset = 5 + i * 2;
        let value = u16::from_be_bytes([pdu.data[offset], pdu.data[offset + 1]]);
        values.push(value);
    }
    
    Ok(WriteMultipleRequest { address, quantity, values })
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
        let bytes = [0x03, 0x00, 0x64, 0x00, 0x03];
        let pdu = parse_pdu(&bytes).unwrap();
        let req = parse_read_request(&pdu).unwrap();
        
        assert_eq!(req.address, 100);
        assert_eq!(req.quantity, 3);
    }
    
    #[test]
    fn test_parse_write_single() {
        let bytes = [0x06, 0x00, 0x01, 0x00, 0x2A];
        let pdu = parse_pdu(&bytes).unwrap();
        let req = parse_write_single_request(&pdu).unwrap();
        
        assert_eq!(req.address, 1);
        assert_eq!(req.value, 42);
    }
}
