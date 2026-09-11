#!/usr/bin/env python3
"""
Modbus RTU Test Client — тестирование ESP32 через ZK-U485

Использование:
    python3 modbus_client.py [port] [baudrate]

Примеры:
    python3 modbus_client.py /dev/ttyUSB0 9600
    python3 modbus_client.py COM3 115200
"""

import sys
import struct
import serial
import time

# CRC-16 Modbus
def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc

def append_crc(frame: bytes) -> bytes:
    crc = crc16(frame)
    return frame + bytes([crc & 0xFF, (crc >> 8) & 0xFF])

def verify_crc(frame: bytes) -> bool:
    if len(frame) < 4:
        return False
    data = frame[:-2]
    received_crc = frame[-2] | (frame[-1] << 8)
    return crc16(data) == received_crc

class ModbusClient:
    def __init__(self, port: str, baudrate: int = 9600):
        self.ser = serial.Serial(
            port=port,
            baudrate=baudrate,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=1
        )
        print(f"Connected to {port} @ {baudrate} baud")
    
    def close(self):
        self.ser.close()
    
    def send_request(self, slave_id: int, function_code: int, data: bytes = b'') -> bytes:
        """Send Modbus RTU request and wait for response"""
        frame = bytes([slave_id, function_code]) + data
        frame = append_crc(frame)
        
        # Очищаем буфер
        self.ser.reset_input_buffer()
        
        # Отправляем
        self.ser.write(frame)
        self.ser.flush()
        
        # Ждём inter-frame gap
        time.sleep(0.05)
        
        # Читаем ответ
        response = self.ser.read(256)
        
        if not response:
            print("  No response (timeout)")
            return b''
        
        if not verify_crc(response):
            print(f"  CRC error! Got: {response.hex()}")
            return b''
        
        return response
    
    def read_holding_registers(self, slave_id: int, start: int, count: int):
        """FC03: Read Holding Registers"""
        data = struct.pack('>HH', start, count)
        response = self.send_request(slave_id, 0x03, data)
        
        if len(response) < 5:
            return None
        
        # Check for exception
        if response[1] & 0x80:
            print(f"  Exception: code 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        registers = []
        for i in range(0, byte_count, 2):
            reg = struct.unpack('>H', response[3+i:5+i])[0]
            registers.append(reg)
        
        return registers
    
    def read_input_registers(self, slave_id: int, start: int, count: int):
        """FC04: Read Input Registers"""
        data = struct.pack('>HH', start, count)
        response = self.send_request(slave_id, 0x04, data)
        
        if len(response) < 5:
            return None
        
        if response[1] & 0x80:
            print(f"  Exception: code 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        registers = []
        for i in range(0, byte_count, 2):
            reg = struct.unpack('>H', response[3+i:5+i])[0]
            registers.append(reg)
        
        return registers
    
    def read_coils(self, slave_id: int, start: int, count: int):
        """FC01: Read Coils"""
        data = struct.pack('>HH', start, count)
        response = self.send_request(slave_id, 0x01, data)
        
        if len(response) < 4:
            return None
        
        if response[1] & 0x80:
            print(f"  Exception: code 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        coils = []
        for i in range(byte_count):
            for bit in range(8):
                if len(coils) >= count:
                    break
                coils.append(bool(response[3 + i] & (1 << bit)))
        
        return coils
    
    def write_single_register(self, slave_id: int, address: int, value: int):
        """FC06: Write Single Register"""
        data = struct.pack('>HH', address, value)
        response = self.send_request(slave_id, 0x06, data)
        
        if len(response) < 6:
            return False
        
        if response[1] & 0x80:
            print(f"  Exception: code 0x{response[2]:02X}")
            return False
        
        return True
    
    def write_single_coil(self, slave_id: int, address: int, value: bool):
        """FC05: Write Single Coil"""
        coil_value = 0xFF00 if value else 0x0000
        data = struct.pack('>HH', address, coil_value)
        response = self.send_request(slave_id, 0x05, data)
        
        if len(response) < 6:
            return False
        
        if response[1] & 0x80:
            print(f"  Exception: code 0x{response[2]:02X}")
            return False
        
        return True

def registers_to_float(reg0: int, reg1: int) -> float:
    """Convert two Modbus registers to float32"""
    raw = (reg0 << 16) | reg1
    return struct.unpack('>f', struct.pack('>I', raw))[0]

def main():
    port = sys.argv[1] if len(sys.argv) > 1 else '/dev/ttyUSB0'
    baudrate = int(sys.argv[2]) if len(sys.argv) > 2 else 9600
    
    print("=" * 60)
    print("Modbus RTU Test Client")
    print("=" * 60)
    
    client = ModbusClient(port, baudrate)
    
    try:
        slave_id = 1
        
        # Тест 1: Read Input Registers (температуры)
        print("\n[Test 1] Read Input Registers (FC04) - Temperature sensors")
        regs = client.read_input_registers(slave_id, 0, 30)
        if regs:
            print(f"  Raw registers: {regs}")
            for i in range(0, min(len(regs), 30), 2):
                if i + 1 < len(regs):
                    temp = registers_to_float(regs[i], regs[i+1])
                    print(f"  Sensor {i//2 + 1}: {temp:.2f}")
        
        # Тест 2: Read Holding Registers
        print("\n[Test 2] Read Holding Registers (FC03)")
        regs = client.read_holding_registers(slave_id, 0, 10)
        if regs:
            print(f"  Registers: {regs}")
        
        # Тест 3: Read Coils (насосы)
        print("\n[Test 3] Read Coils (FC01) - Pump status")
        coils = client.read_coils(slave_id, 0, 2)
        if coils:
            print(f"  Pump 1: {'ON' if coils[0] else 'OFF'}")
            print(f"  Pump 2: {'ON' if coils[1] else 'OFF'}")
        
        # Тест 4: Write Single Register
        print("\n[Test 4] Write Single Register (FC06)")
        success = client.write_single_register(slave_id, 0, 12345)
        print(f"  Write 12345 to register 0: {'OK' if success else 'FAILED'}")
        
        # Проверяем запись
        regs = client.read_holding_registers(slave_id, 0, 1)
        if regs:
            print(f"  Read back: {regs[0]} (expected: 12345)")
        
        # Тест 5: Write Single Coil
        print("\n[Test 5] Write Single Coil (FC05)")
        success = client.write_single_coil(slave_id, 1, True)
        print(f"  Turn ON pump 2: {'OK' if success else 'FAILED'}")
        
        coils = client.read_coils(slave_id, 0, 2)
        if coils:
            print(f"  Pump 1: {'ON' if coils[0] else 'OFF'}")
            print(f"  Pump 2: {'ON' if coils[1] else 'OFF'}")
        
        # Тест 6: Exception (несуществующий адрес)
        print("\n[Test 6] Exception - Non-existent register")
        regs = client.read_holding_registers(slave_id, 9999, 1)
        print(f"  Result: {regs}")
        
        print("\n" + "=" * 60)
        print("All tests completed!")
        print("=" * 60)
    
    finally:
        client.close()

if __name__ == '__main__':
    main()
