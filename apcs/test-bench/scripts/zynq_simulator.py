#!/usr/bin/env python3
"""
Zynq Simulator — Modbus RTU Master

Эмулирует поведение Zynq SoC: опрашивает ESP32 (Slave) по RS-485,
отображает данные датчиков в реальном времени.

Использование:
    python3 zynq_simulator.py [port] [baudrate] [interval_ms]

Примеры:
    python3 zynq_simulator.py /dev/ttyUSB0 9600 500
    python3 zynq_simulator.py COM3 115200 1000
"""

import sys
import struct
import serial
import time

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

def registers_to_float(reg0: int, reg1: int) -> float:
    raw = (reg0 << 16) | reg1
    return struct.unpack('>f', struct.pack('>I', raw))[0]

class ZynqMaster:
    def __init__(self, port: str, baudrate: int = 9600, interval_ms: int = 500):
        self.ser = serial.Serial(
            port=port,
            baudrate=baudrate,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=0.3
        )
        self.interval = interval_ms / 1000.0
        print(f"Zynq Master connected: {port} @ {baudrate} baud")
        print(f"Polling interval: {interval_ms} ms")
    
    def close(self):
        self.ser.close()
    
    def _send(self, slave_id: int, function_code: int, data: bytes = b'') -> bytes:
        frame = bytes([slave_id, function_code]) + data
        frame = append_crc(frame)
        
        self.ser.reset_input_buffer()
        self.ser.write(frame)
        self.ser.flush()
        
        time.sleep(0.03)
        response = self.ser.read(256)
        
        if not response:
            return b''
        
        if not verify_crc(response):
            print("  CRC error!")
            return b''
        
        return response
    
    def read_input_registers(self, slave_id: int, start: int, count: int):
        """FC04: Read Input Registers"""
        data = struct.pack('>HH', start, count)
        response = self._send(slave_id, 0x04, data)
        
        if len(response) < 5:
            return None
        
        if response[1] & 0x80:
            print(f"  Exception: 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        return [struct.unpack('>H', response[3+i:5+i])[0] for i in range(0, byte_count, 2)]
    
    def read_holding_registers(self, slave_id: int, start: int, count: int):
        """FC03: Read Holding Registers"""
        data = struct.pack('>HH', start, count)
        response = self._send(slave_id, 0x03, data)
        
        if len(response) < 5:
            return None
        
        if response[1] & 0x80:
            print(f"  Exception: 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        return [struct.unpack('>H', response[3+i:5+i])[0] for i in range(0, byte_count, 2)]
    
    def read_coils(self, slave_id: int, start: int, count: int):
        """FC01: Read Coils"""
        data = struct.pack('>HH', start, count)
        response = self._send(slave_id, 0x01, data)
        
        if len(response) < 4:
            return None
        
        if response[1] & 0x80:
            print(f"  Exception: 0x{response[2]:02X}")
            return None
        
        byte_count = response[2]
        coils = []
        for i in range(byte_count):
            for bit in range(8):
                if len(coils) >= count:
                    break
                coils.append(bool(response[3 + i] & (1 << bit)))
        return coils
    
    def write_holding_register(self, slave_id: int, address: int, value: int):
        """FC06: Write Single Register"""
        data = struct.pack('>HH', address, value)
        response = self._send(slave_id, 0x06, data)
        return len(response) >= 6 and not (response[1] & 0x80)
    
    def write_coil(self, slave_id: int, address: int, value: bool):
        """FC05: Write Single Coil"""
        coil_value = 0xFF00 if value else 0x0000
        data = struct.pack('>HH', address, coil_value)
        response = self._send(slave_id, 0x05, data)
        return len(response) >= 6 and not (response[1] & 0x80)
    
    def poll_once(self, slave_id: int = 1):
        """Poll all sensors once and return the data"""
        # Читаем температуры (15 шт)
        temp_regs = self.read_input_registers(slave_id, 0, 30)
        temps = []
        if temp_regs:
            for i in range(0, len(temp_regs), 2):
                if i + 1 < len(temp_regs):
                    temps.append(registers_to_float(temp_regs[i], temp_regs[i+1]))
        
        # Читаем давления (15 шт)
        pressure_regs = self.read_input_registers(slave_id, 30, 30)
        pressures = []
        if pressure_regs:
            for i in range(0, len(pressure_regs), 2):
                if i + 1 < len(pressure_regs):
                    pressures.append(registers_to_float(pressure_regs[i], pressure_regs[i+1]))
        
        # Читаем статусы насосов
        pumps = self.read_coils(slave_id, 0, 2)
        
        return temps, pressures, pumps

def main():
    port = sys.argv[1] if len(sys.argv) > 1 else '/dev/ttyUSB0'
    baudrate = int(sys.argv[2]) if len(sys.argv) > 2 else 9600
    interval_ms = int(sys.argv[3]) if len(sys.argv) > 3 else 500
    
    zynq = ZynqMaster(port, baudrate, interval_ms)
    
    slave_id = 1
    
    print("=" * 70)
    print("        ZYNQ SIMULATOR — Modbus RTU Master")
    print("=" * 70)
    print("  Опрашивает ESP32 (Slave ID=1) по RS-485")
    print("  Нажмите Ctrl+C для остановки")
    print("=" * 70)
    
    try:
        cycle = 0
        while True:
            cycle += 1
            
            print(f"\n--- Poll cycle {cycle} ({time.strftime('%H:%M:%S')}) ---")
            
            temps, pressures, pumps = zynq.poll_once(slave_id)
            
            if temps:
                avg_temp = sum(temps) / len(temps)
                max_temp = max(temps)
                min_temp = min(temps)
                print(f"  TEMPERATURES ({len(temps)} sensors):")
                print(f"    avg: {avg_temp:.2f} °C  min: {min_temp:.2f}  max: {max_temp:.2f}")
                for i in range(0, len(temps), 3):
                    chunk = temps[i:i+3]
                    print(f"    " + "  ".join(f"S{i+j+1}: {v:.2f}°C" for j, v in enumerate(chunk)))
            else:
                print("  [NO RESPONSE from temperature sensors]")
            
            if pressures:
                avg_p = sum(pressures) / len(pressures)
                print(f"  PRESSURES ({len(pressures)} sensors):")
                print(f"    avg: {avg_p:.2f} kPa")
                for i in range(0, len(pressures), 3):
                    chunk = pressures[i:i+3]
                    print(f"    " + "  ".join(f"P{i+j+1}: {v:.2f} kPa" for j, v in enumerate(chunk)))
            else:
                print("  [NO RESPONSE from pressure sensors]")
            
            if pumps is not None:
                pump_status = "ON" if pumps[0] else "OFF"
                pump2_status = "ON" if pumps[1] else "OFF"
                print(f"  PUMPS:")
                print(f"    Pump 1: {pump_status}")
                print(f"    Pump 2: {pump2_status}")
            
            # Записываем тестовую команду е每次 5 циклов
            if cycle % 5 == 0:
                success = zynq.write_holding_register(slave_id, 0, cycle * 100)
                print(f"  [CMD] Write register 0 = {cycle * 100}: {'OK' if success else 'FAILED'}")
            
            time.sleep(zynq.interval)
    
    except KeyboardInterrupt:
        print("\n\nZynq simulator stopped.")
    finally:
        zynq.close()

if __name__ == '__main__':
    main()