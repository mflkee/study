#!/usr/bin/env python3
"""
Тестовый клиент для Modbus TCP Gateway
Используйте для проверки работоспособности ESP32

Установка:
    pip install pymodbus

Запуск:
    python3 test_client.py --host 192.168.1.100 --port 502
"""

from pymodbus.client import ModbusTcpClient
from pymodbus.exceptions import ModbusException
import argparse
import sys
import time

def test_read_holding_registers(client, unit_id=1):
    """Тест чтения Holding Registers"""
    print("\n=== Read Holding Registers ===")
    
    try:
        result = client.read_holding_registers(address=0, count=20, slave=unit_id)
        
        if result.isError():
            print(f"Error: {result}")
            return False
        
        print(f"Read {len(result.registers)} registers:")
        for i, val in enumerate(result.registers):
            print(f"  [{i:3d}] = {val}")
        
        return True
        
    except ModbusException as e:
        print(f"Modbus error: {e}")
        return False

def test_read_input_registers(client, unit_id=1):
    """Тест чтения Input Registers (температуры, давления)"""
    print("\n=== Read Input Registers ===")
    
    try:
        result = client.read_input_registers(address=0, count=20, slave=unit_id)
        
        if result.isError():
            print(f"Error: {result}")
            return False
        
        print(f"Read {len(result.registers)} registers:")
        for i, val in enumerate(result.registers):
            print(f"  [{i:3d}] = {val}")
        
        return True
        
    except ModbusException as e:
        print(f"Modbus error: {e}")
        return False

def test_write_single_register(client, unit_id=1):
    """Тест записи одного регистра"""
    print("\n=== Write Single Register ===")
    
    try:
        address = 0
        value = 42
        
        result = client.write_register(address=address, value=value, slave=unit_id)
        
        if result.isError():
            print(f"Error: {result}")
            return False
        
        print(f"Written {value} to register {address}")
        
        # Read back
        read_result = client.read_holding_registers(address=address, count=1, slave=unit_id)
        if read_result.registers[0] == value:
            print(f"Verified: register {address} = {value}")
            return True
        else:
            print(f"Mismatch: written {value}, read {read_result.registers[0]}")
            return False
            
    except ModbusException as e:
        print(f"Modbus error: {e}")
        return False

def test_write_multiple_registers(client, unit_id=1):
    """Тест записи нескольких регистров"""
    print("\n=== Write Multiple Registers ===")
    
    try:
        address = 10
        values = [100, 200, 300, 400, 500]
        
        result = client.write_registers(address=address, values=values, slave=unit_id)
        
        if result.isError():
            print(f"Error: {result}")
            return False
        
        print(f"Written {len(values)} registers starting at {address}")
        
        # Read back
        read_result = client.read_holding_registers(address=address, count=len(values), slave=unit_id)
        if read_result.registers == values:
            print(f"All values verified")
            return True
        else:
            print(f"Mismatch")
            return False
            
    except ModbusException as e:
        print(f"Modbus error: {e}")
        return False

def test_read_coils(client, unit_id=1):
    """Тест чтения Coils"""
    print("\n=== Read Coils ===")
    
    try:
        result = client.read_coils(address=0, count=10, slave=unit_id)
        
        if result.isError():
            print(f"Error: {result}")
            return False
        
        print(f"Read {len(result.bits)} coils:")
        for i, val in enumerate(result.bits[:10]):
            print(f"  Coil {i}: {'ON' if val else 'OFF'}")
        
        return True
        
    except ModbusException as e:
        print(f"Modbus error: {e}")
        return False

def test_exception_handling(client, unit_id=1):
    """Тест обработки исключений"""
    print("\n=== Exception Handling ===")
    
    # Test 1: Illegal Data Address
    print("\nTest 1: Illegal Data Address (address 9999)")
    result = client.read_holding_registers(address=9999, count=10, slave=unit_id)
    if result.isError():
        print(f"  Got exception (expected): {result}")
    else:
        print(f"  ERROR: No exception returned")
        return False
    
    # Test 2: Illegal Data Value
    print("\nTest 2: Illegal Data Value (200 registers)")
    result = client.read_holding_registers(address=0, count=200, slave=unit_id)
    if result.isError():
        print(f"  Got exception (expected): {result}")
    else:
        print(f"  ERROR: No exception returned")
        return False
    
    print("\nAll exception tests passed!")
    return True

def main():
    parser = argparse.ArgumentParser(description='Modbus TCP Gateway Test Client')
    parser.add_argument('--host', default='192.168.1.100', help='Server host')
    parser.add_argument('--port', type=int, default=502, help='Server port')
    parser.add_argument('--unit', type=int, default=1, help='Unit ID')
    
    args = parser.parse_args()
    
    print(f"Connecting to {args.host}:{args.port}")
    
    client = ModbusTcpClient(
        host=args.host,
        port=args.port,
        timeout=3.0
    )
    
    if not client.connect():
        print("Connection failed!")
        sys.exit(1)
    
    print("Connected!")
    
    tests = [
        ("Read Holding Registers", test_read_holding_registers),
        ("Read Input Registers", test_read_input_registers),
        ("Write Single Register", test_write_single_register),
        ("Write Multiple Registers", test_write_multiple_registers),
        ("Read Coils", test_read_coils),
        ("Exception Handling", test_exception_handling),
    ]
    
    results = []
    for name, test_func in tests:
        try:
            passed = test_func(client, args.unit)
            results.append((name, passed))
        except Exception as e:
            print(f"Unexpected error in '{name}': {e}")
            results.append((name, False))
    
    print("\n" + "="*50)
    print("RESULTS")
    print("="*50)
    
    passed_count = sum(1 for _, passed in results if passed)
    total_count = len(results)
    
    for name, passed in results:
        status = "PASS" if passed else "FAIL"
        print(f"  {name}: {status}")
    
    print(f"\n{passed_count}/{total_count} tests passed")
    
    client.close()
    sys.exit(0 if passed_count == total_count else 1)

if __name__ == "__main__":
    main()
