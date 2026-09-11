/// Modbus Exception responses

/// Строит PDU исключения
pub fn build_exception_response(function_code: u8, exception_code: u8) -> Vec<u8> {
    vec![function_code | 0x80, exception_code]
}

/// Коды исключений Modbus
pub struct ExceptionCodes;

impl ExceptionCodes {
    pub const ILLEGAL_FUNCTION: u8 = 0x01;
    pub const ILLEGAL_DATA_ADDRESS: u8 = 0x02;
    pub const ILLEGAL_DATA_VALUE: u8 = 0x03;
    pub const FAILURE_IN_ASSOCIATED_DEVICE: u8 = 0x04;
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_exception_response() {
        let response = build_exception_response(0x03, 0x02);
        assert_eq!(response, vec![0x83, 0x02]);
    }
    
    #[test]
    fn test_exception_codes() {
        assert_eq!(ExceptionCodes::ILLEGAL_FUNCTION, 0x01);
        assert_eq!(ExceptionCodes::ILLEGAL_DATA_ADDRESS, 0x02);
        assert_eq!(ExceptionCodes::ILLEGAL_DATA_VALUE, 0x03);
    }
}
