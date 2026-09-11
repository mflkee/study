#!/usr/bin/env python3
"""
Упражнение 4.3: Modbus Client Test
Задача: Используя pymodbus, протестируйте Modbus TCP сервер.

Установка:
    pip install pymodbus

Запуск:
    python3 ex4_modbus_client.py
"""

from pymodbus.client import ModbusTcpClient
from pymodbus.exceptions import ModbusException
import time
import sys

# Конфигурация
SERVER_HOST = "127.0.0.1"
SERVER_PORT = 502
SERVER_UNIT = 1

def test_read_holding_registers(client):
    """Тест чтения Holding Registers"""
    print("\n=== Тест 1: Read Holding Registers ===")
    
    try:
        # Читаем 10 регистров с адреса 0
        result = client.read_holding_registers(address=0, count=10, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Ошибка: {result}")
            return False
        
        print(f"Успешно прочитано {len(result.registers)} регистров:")
        for i, val in enumerate(result.registers):
            print(f"  Register {i}: {val}")
        
        return True
        
    except ModbusException as e:
        print(f"Modbus ошибка: {e}")
        return False

def test_write_single_register(client):
    """Тест записи одного регистра"""
    print("\n=== Тест 2: Write Single Register ===")
    
    try:
        # Записываем значение 42 в регистр 0
        address = 0
        value = 42
        
        result = client.write_register(address=address, value=value, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Ошибка записи: {result}")
            return False
        
        print(f"Записано {value} в регистр {address}")
        
        # Читаем обратно для проверки
        read_result = client.read_holding_registers(address=address, count=1, slave=SERVER_UNIT)
        if read_result.isError():
            print(f"Ошибка чтения: {read_result}")
            return False
        
        if read_result.registers[0] == value:
            print(f"Проверка: значение {value} прочитано корректно")
            return True
        else:
            print(f"Ошибка: записано {value}, прочитано {read_result.registers[0]}")
            return False
            
    except ModbusException as e:
        print(f"Modbus ошибка: {e}")
        return False

def test_write_multiple_registers(client):
    """Тест записи нескольких регистров"""
    print("\n=== Тест 3: Write Multiple Registers ===")
    
    try:
        address = 10
        values = [100, 200, 300, 400, 500]
        
        result = client.write_registers(address=address, values=values, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Ошибка записи: {result}")
            return False
        
        print(f"Записано {len(values)} регистров начиная с адреса {address}")
        
        # Читаем обратно
        read_result = client.read_holding_registers(address=address, count=len(values), slave=SERVER_UNIT)
        if read_result.isError():
            print(f"Ошибка чтения: {read_result}")
            return False
        
        if read_result.registers == values:
            print(f"Проверка: все значения прочитаны корректно")
            return True
        else:
            print(f"Ошибка: записано {values}, прочитано {read_result.registers}")
            return False
            
    except ModbusException as e:
        print(f"Modbus ошибка: {e}")
        return False

def test_exception_illegal_address(client):
    """Тест исключения: обращение к несуществующему адресу"""
    print("\n=== Тест 4: Illegal Data Address (0x02) ===")
    
    try:
        # Пытаемся прочитать регистр по несуществующему адресу
        result = client.read_holding_registers(address=9999, count=10, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Получено исключение (ожидаемо): {result}")
            return True
        else:
            print("Ошибка: исключение не было возвращено")
            return False
            
    except ModbusException as e:
        print(f"Получено исключение: {e}")
        return True

def test_exception_illegal_quantity(client):
    """Тест исключения: слишком большое количество регистров"""
    print("\n=== Тест 5: Illegal Data Value (0x03) ===")
    
    try:
        # Запрашиваем 200 регистров (лимит 125)
        result = client.read_holding_registers(address=0, count=200, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Получено исключение (ожидаемо): {result}")
            return True
        else:
            print("Ошибка: исключение не было возвращено")
            return False
            
    except ModbusException as e:
        print(f"Получено исключение: {e}")
        return True

def test_bulk_read(client):
    """Тест массового чтения"""
    print("\n=== Тест 6: Bulk Read (100 регистров) ===")
    
    try:
        # Сначала заполним регистры значениями
        values = list(range(100))
        client.write_registers(address=0, values=values, slave=SERVER_UNIT)
        
        # Теперь читаем
        result = client.read_holding_registers(address=0, count=100, slave=SERVER_UNIT)
        
        if result.isError():
            print(f"Ошибка: {result}")
            return False
        
        if result.registers == values:
            print(f"Успешно прочитано 100 регистров, все значения корректны")
            return True
        else:
            print(f"Ошибка: не все значения совпадают")
            return False
            
    except ModbusException as e:
        print(f"Modbus ошибка: {e}")
        return False

def main():
    print(f"Подключение к Modbus TCP серверу {SERVER_HOST}:{SERVER_PORT}")
    
    # Создаем клиента
    client = ModbusTcpClient(
        host=SERVER_HOST,
        port=SERVER_PORT,
        timeout=3.0
    )
    
    # Подключаемся
    if not client.connect():
        print("Ошибка подключения к серверу!")
        print("Убедитесь, что сервер запущен на", f"{SERVER_HOST}:{SERVER_PORT}")
        sys.exit(1)
    
    print("Подключено успешно!")
    
    # Запускаем тесты
    tests = [
        ("Read Holding Registers", test_read_holding_registers),
        ("Write Single Register", test_write_single_register),
        ("Write Multiple Registers", test_write_multiple_registers),
        ("Illegal Address", test_exception_illegal_address),
        ("Illegal Quantity", test_exception_illegal_quantity),
        ("Bulk Read", test_bulk_read),
    ]
    
    results = []
    for name, test_func in tests:
        try:
            passed = test_func(client)
            results.append((name, passed))
        except Exception as e:
            print(f"Неожиданная ошибка в тесте '{name}': {e}")
            results.append((name, False))
    
    # Итоги
    print("\n" + "="*50)
    print("ИТОГИ ТЕСТИРОВАНИЯ")
    print("="*50)
    
    passed_count = sum(1 for _, passed in results if passed)
    total_count = len(results)
    
    for name, passed in results:
        status = "✓ ПРОЙДЕН" if passed else "✗ ПРОВАЛЕН"
        print(f"  {name}: {status}")
    
    print(f"\nРезультат: {passed_count}/{total_count} тестов пройдено")
    
    # Закрываем соединение
    client.close()
    
    sys.exit(0 if passed_count == total_count else 1)

if __name__ == "__main__":
    main()
