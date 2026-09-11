#!/usr/bin/env python3
"""
Modbus RTU Simulator — симулирует несколько устройств на шине RS-485

Этот скрипт работает ВМЕСТО реальных устройств.
Он отвечает на запросы как Modbus Slave с несколькими ID.

Использование:
    python3 simulator.py [port] [baudrate]

Примеры:
    python3 simulator.py /dev/ttyUSB0 9600
    python3 simulator.py COM3 115200
"""

import sys
import struct
import serial
import time
import threading
import math

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

class ModbusSimulator:
    def __init__(self, port: str, baudrate: int = 9600):
        self.ser = serial.Serial(
            port=port,
            baudrate=baudrate,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=0.1
        )
        self.running = True
        
        # Данные для разных Slave ID
        self.devices = {
            1: {  # Temperature sensors
                'name': 'Temperature Sensors',
                'input_registers': {},  # addr -> value
            },
            2: {  # Pressure sensors
                'name': 'Pressure Sensors',
                'input_registers': {},
            },
            3: {  # Pump 1
                'name': 'Pump 1',
                'coils': {0: True, 1: False},  # running, alarm
                'holding_registers': {0: 100},  # speed
            },
            4: {  # Pump 2
                'name': 'Pump 2',
                'coils': {0: False, 1: False},
                'holding_registers': {0: 0},
            },
        }
        
        # Инициализация тестовых данных
        self._init_test_data()
        
        # Поток для обновления данных (имитация датчиков)
        self.update_thread = threading.Thread(target=self._update_data_loop, daemon=True)
        
    def _init_test_data(self):
        """Initialize test data for all devices"""
        # Temperature sensors (ID=1): 15 датчиков
        for i in range(15):
            temp = 20.0 + i * 0.5  # 20.0, 20.5, 21.0, ...
            raw = struct.pack('>f', temp)
            reg0 = (raw[0] << 8) | raw[1]
            reg1 = (raw[2] << 8) | raw[3]
            self.devices[1]['input_registers'][i * 2] = reg0
            self.devices[1]['input_registers'][i * 2 + 1] = reg1
        
        # Pressure sensors (ID=2): 15 датчиков
        for i in range(15):
            pressure = 100.0 + i * 1.5  # 100.0, 101.5, 103.0, ...
            raw = struct.pack('>f', pressure)
            reg0 = (raw[0] << 8) | raw[1]
            reg1 = (raw[2] << 8) | raw[3]
            self.devices[2]['input_registers'][i * 2] = reg0
            self.devices[2]['input_registers'][i * 2 + 1] = reg1
    
    def _update_data_loop(self):
        """Continuously update sensor data (simulate real sensors)"""
        t = 0
        while self.running:
            time.sleep(1)
            t += 1
            
            # Обновляем температуры (имитация шума + дрейфа)
            for i in range(15):
                temp = 20.0 + i * 0.5 + 0.1 * math.sin(t * 0.1 + i)
                raw = struct.pack('>f', temp)
                reg0 = (raw[0] << 8) | raw[1]
                reg1 = (raw[2] << 8) | raw[3]
                self.devices[1]['input_registers'][i * 2] = reg0
                self.devices[1]['input_registers'][i * 2 + 1] = reg1
            
            # Обновляем давления
            for i in range(15):
                pressure = 100.0 + i * 1.5 + 0.5 * math.sin(t * 0.05 + i)
                raw = struct.pack('>f', pressure)
                reg0 = (raw[0] << 8) | raw[1]
                reg1 = (raw[2] << 8) | raw[3]
                self.devices[2]['input_registers'][i * 2] = reg0
                self.devices[2]['input_registers'][i * 2 + 1] = reg1
    
    def start(self):
        """Start the simulator"""
        self.update_thread.start()
        print(f"Simulator started on {self.ser.portstr}")
        print(f"Simulating {len(self.devices)} devices:")
        for dev_id, dev in self.devices.items():
            print(f"  ID {dev_id}: {dev['name']}")
        print("Waiting for Modbus RTU requests...")
    
    def stop(self):
        """Stop the simulator"""
        self.running = False
        self.ser.close()
    
    def run(self):
        """Main loop — listen and respond"""
        self.start()
        
        buf = bytearray()
        
        try:
            while self.running:
                # Читаем данные
                data = self.ser.read(256)
                
                if data:
                    buf.extend(data)
                    
                    # Ищем полный кадр (минимум 4 байта + CRC)
                    while len(buf) >= 4:
                        # Проверяем CRC
                        if verify_crc(bytes(buf)):
                            self._process_frame(bytes(buf))
                            buf.clear()
                            break
                        else:
                            # Некорректный кадр — пропускаем байт
                            buf.pop(0)
                else:
                    # Таймаут — нормально
                    pass
                    
        except KeyboardInterrupt:
            print("\nStopping simulator...")
        finally:
            self.stop()
    
    def _process_frame(self, frame: bytes):
        """Process a Modbus RTU request"""
        slave_id = frame[0]
        function_code = frame[1]
        
        # Проверяем, есть ли такое устройство
        if slave_id not in self.devices:
            return  # Игнорируем запросы к несуществующим устройствам
        
        device = self.devices[slave_id]
        
        # Обрабатываем по коду функции
        response = None
        
        if function_code == 0x01:  # Read Coils
            response = self._handle_read_coils(slave_id, device, frame)
        elif function_code == 0x02:  # Read Discrete Inputs
            response = self._handle_read_discrete_inputs(slave_id, device, frame)
        elif function_code == 0x03:  # Read Holding Registers
            response = self._handle_read_holding_registers(slave_id, device, frame)
        elif function_code == 0x04:  # Read Input Registers
            response = self._handle_read_input_registers(slave_id, device, frame)
        elif function_code == 0x05:  # Write Single Coil
            response = self._handle_write_single_coil(slave_id, device, frame)
        elif function_code == 0x06:  # Write Single Register
            response = self._handle_write_single_register(slave_id, device, frame)
        elif function_code == 0x0F:  # Write Multiple Coils
            response = self._handle_write_multiple_coils(slave_id, device, frame)
        elif function_code == 0x10:  # Write Multiple Registers
            response = self._handle_write_multiple_registers(slave_id, device, frame)
        else:
            # Unsupported function
            response = self._exception_response(slave_id, function_code, 0x01)
        
        if response:
            # Inter-frame gap
            time.sleep(0.005)
            
            # Отправляем ответ
            self.ser.write(response)
            self.ser.flush()
    
    def _handle_read_input_registers(self, slave_id: int, device: dict, frame: bytes):
        """FC04: Read Input Registers"""
        if len(frame) < 8:
            return self._exception_response(slave_id, 0x04, 0x03)
        
        start = (frame[2] << 8) | frame[3]
        count = (frame[4] << 8) | frame[5]
        
        if count == 0 or count > 125:
            return self._exception_response(slave_id, 0x04, 0x03)
        
        registers = device.get('input_registers', {})
        
        response = bytearray([slave_id, 0x04, count * 2])
        for i in range(count):
            addr = start + i
            value = registers.get(addr, 0)
            response.extend(struct.pack('>H', value))
        
        return append_crc(bytes(response))
    
    def _handle_read_holding_registers(self, slave_id: int, device: dict, frame: bytes):
        """FC03: Read Holding Registers"""
        if len(frame) < 8:
            return self._exception_response(slave_id, 0x03, 0x03)
        
        start = (frame[2] << 8) | frame[3]
        count = (frame[4] << 8) | frame[5]
        
        if count == 0 or count > 125:
            return self._exception_response(slave_id, 0x03, 0x03)
        
        registers = device.get('holding_registers', {})
        
        response = bytearray([slave_id, 0x03, count * 2])
        for i in range(count):
            addr = start + i
            value = registers.get(addr, 0)
            response.extend(struct.pack('>H', value))
        
        return append_crc(bytes(response))
    
    def _handle_read_coils(self, slave_id: int, device: dict, frame: bytes):
        """FC01: Read Coils"""
        if len(frame) < 8:
            return self._exception_response(slave_id, 0x01, 0x03)
        
        start = (frame[2] << 8) | frame[3]
        count = (frame[4] << 8) | frame[5]
        
        if count == 0 or count > 2000:
            return self._exception_response(slave_id, 0x01, 0x03)
        
        coils = device.get('coils', {})
        byte_count = (count + 7) // 8
        
        response = bytearray([slave_id, 0x01, byte_count])
        for i in range(byte_count):
            byte_val = 0
            for bit in range(8):
                idx = i * 8 + bit
                if idx < count:
                    addr = start + idx
                    if coils.get(addr, False):
                        byte_val |= 1 << bit
            response.append(byte_val)
        
        return append_crc(bytes(response))
    
    def _handle_read_discrete_inputs(self, slave_id: int, device: dict, frame: bytes):
        """FC02: Read Discrete Inputs (same as Read Coils)"""
        return self._handle_read_coils(slave_id, device, frame)
    
    def _handle_write_single_coil(self, slave_id: int, device: dict, frame: bytes):
        """FC05: Write Single Coil"""
        if len(frame) < 8:
            return self._exception_response(slave_id, 0x05, 0x03)
        
        addr = (frame[2] << 8) | frame[3]
        value = (frame[4] << 8) | frame[5]
        
        if value not in (0x0000, 0xFF00):
            return self._exception_response(slave_id, 0x05, 0x03)
        
        coils = device.setdefault('coils', {})
        coils[addr] = (value == 0xFF00)
        
        print(f"  Device {slave_id}: Coil {addr} = {'ON' if coils[addr] else 'OFF'}")
        
        return frame  # Echo
    
    def _handle_write_single_register(self, slave_id: int, device: dict, frame: bytes):
        """FC06: Write Single Register"""
        if len(frame) < 8:
            return self._exception_response(slave_id, 0x06, 0x03)
        
        addr = (frame[2] << 8) | frame[3]
        value = (frame[4] << 8) | frame[5]
        
        registers = device.setdefault('holding_registers', {})
        registers[addr] = value
        
        print(f"  Device {slave_id}: Register {addr} = {value}")
        
        return frame  # Echo
    
    def _handle_write_multiple_coils(self, slave_id: int, device: dict, frame: bytes):
        """FC0F: Write Multiple Coils"""
        if len(frame) < 9:
            return self._exception_response(slave_id, 0x0F, 0x03)
        
        start = (frame[2] << 8) | frame[3]
        count = (frame[4] << 8) | frame[5]
        byte_count = frame[6]
        
        if len(frame) < 7 + byte_count + 2:
            return self._exception_response(slave_id, 0x0F, 0x03)
        
        coils = device.setdefault('coils', {})
        for i in range(count):
            byte_idx = 7 + i // 8
            bit_idx = i % 8
            value = bool(frame[byte_idx] & (1 << bit_idx))
            coils[start + i] = value
        
        response = bytearray([slave_id, 0x0F])
        response.extend(struct.pack('>HH', start, count))
        return append_crc(bytes(response))
    
    def _handle_write_multiple_registers(self, slave_id: int, device: dict, frame: bytes):
        """FC10: Write Multiple Registers"""
        if len(frame) < 9:
            return self._exception_response(slave_id, 0x10, 0x03)
        
        start = (frame[2] << 8) | frame[3]
        count = (frame[4] << 8) | frame[5]
        byte_count = frame[6]
        
        if len(frame) < 7 + byte_count + 2:
            return self._exception_response(slave_id, 0x10, 0x03)
        
        registers = device.setdefault('holding_registers', {})
        for i in range(count):
            offset = 7 + i * 2
            value = (frame[offset] << 8) | frame[offset + 1]
            registers[start + i] = value
        
        response = bytearray([slave_id, 0x10])
        response.extend(struct.pack('>HH', start, count))
        return append_crc(bytes(response))
    
    def _exception_response(self, slave_id: int, function_code: int, exception_code: int):
        """Build exception response"""
        response = bytearray([slave_id, function_code | 0x80, exception_code])
        return append_crc(bytes(response))

def main():
    port = sys.argv[1] if len(sys.argv) > 1 else '/dev/ttyUSB0'
    baudrate = int(sys.argv[2]) if len(sys.argv) > 2 else 9600
    
    print("=" * 60)
    print("Modbus RTU Device Simulator")
    print("=" * 60)
    
    sim = ModbusSimulator(port, baudrate)
    
    try:
        sim.run()
    except KeyboardInterrupt:
        print("\nStopped.")

if __name__ == '__main__':
    main()
