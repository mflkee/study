#!/usr/bin/env python3
"""
Modbus TCP Test Client — тестирование TCP-сервера TUI (ModbusTCP через MBAP).

TCP-сервер встроен в TUI (вкладка Ports → [t], порт 1502) и обслуживает
ту же карту регистров, что и эмулятор / ESP32 по RS-485. Это удобно для
изучения Modbus TCP и для будущей интеграции с Zynq по Ethernet.

Использование:
    python3 modbus_tcp_client.py [host] [port]

Примеры:
    python3 modbus_tcp_client.py 127.0.0.1 1502
"""

import socket
import struct
import sys
import time

DEFAULT_HOST = '127.0.0.1'
DEFAULT_PORT = 1502
TIMEOUT = 2.0


class ModbusTcpClient:
    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port
        self.sock = socket.create_connection((host, port), timeout=TIMEOUT)
        self.tid = 0
        print(f"Connected to {host}:{port}")

    def close(self):
        self.sock.close()

    def _transact(self, unit: int, function_code: int, data: bytes = b'') -> bytes:
        """Отправляет MBAP-кадр и читает весь ответ PDU (начиная с fc)."""
        self.tid += 1
        # MBAP: transaction id, protocol id=0, length = unit + fc + data, unit.
        pdu = bytes([function_code]) + data
        frame = struct.pack('>HHHB', self.tid, 0, 1 + len(pdu), unit) + pdu
        self.sock.sendall(frame)

        hdr = b''
        while len(hdr) < 7:
            chunk = self.sock.recv(7 - len(hdr))
            if not chunk:
                raise ConnectionError('connection closed by server')
            hdr += chunk
        tid, proto, length, unit_rsp = struct.unpack('>HHHB', hdr)
        length = length - 1  # length считает и unit; после заголовка остаётся PDU.
        pdu_rsp = b''
        while len(pdu_rsp) < length:
            chunk = self.sock.recv(length - len(pdu_rsp))
            if not chunk:
                raise ConnectionError('connection closed by server')
            pdu_rsp += chunk
        return pdu_rsp

    def read_input_registers(self, unit: int, start: int, count: int):
        """FC04: Read Input Registers."""
        pdu = self._transact(unit, 0x04, struct.pack('>HH', start, count))
        if pdu[0] & 0x80:
            print(f"  Exception: 0x{pdu[1]:02X}")
            return None
        byte_count = pdu[1]
        regs = [struct.unpack('>H', pdu[2 + i:4 + i])[0] for i in range(0, byte_count, 2)]
        return regs

    def read_holding_registers(self, unit: int, start: int, count: int):
        """FC03: Read Holding Registers."""
        pdu = self._transact(unit, 0x03, struct.pack('>HH', start, count))
        if pdu[0] & 0x80:
            print(f"  Exception: 0x{pdu[1]:02X}")
            return None
        byte_count = pdu[1]
        regs = [struct.unpack('>H', pdu[2 + i:4 + i])[0] for i in range(0, byte_count, 2)]
        return regs

    def read_coils(self, unit: int, start: int, count: int):
        """FC01: Read Coils."""
        pdu = self._transact(unit, 0x01, struct.pack('>HH', start, count))
        if pdu[0] & 0x80:
            print(f"  Exception: 0x{pdu[1]:02X}")
            return None
        byte_count = pdu[1]
        coils = []
        for byte in pdu[2:2 + byte_count]:
            for bit in range(8):
                if len(coils) >= count:
                    break
                coils.append(bool(byte & (1 << bit)))
        return coils

    def write_single_register(self, unit: int, address: int, value: int):
        """FC06: Write Single Register."""
        pdu = self._transact(unit, 0x06, struct.pack('>HH', address, value))
        if pdu[0] & 0x80:
            print(f"  Exception: 0x{pdu[1]:02X}")
            return False
        return True


def registers_to_float(reg0: int, reg1: int) -> float:
    raw = (reg0 << 16) | reg1
    return struct.unpack('>f', struct.pack('>I', raw))[0]


def main():
    host = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_HOST
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT

    print("=" * 60)
    print("Modbus TCP Test Client (MBAP)")
    print("=" * 60)

    client = ModbusTcpClient(host, port)

    try:
        unit = 1
        print(f"\n[Test 1] Read Input Registers (FC04) — температуры")
        regs = client.read_input_registers(unit, 0, 30)
        if regs:
            for i in range(0, min(len(regs), 30), 2):
                if i + 1 < len(regs):
                    print(f"  Sensor {i // 2 + 1}: {registers_to_float(regs[i], regs[i+1]):.2f}")

        print(f"\n[Test 2] Read Holding Registers (FC03) — давление")
        regs = client.read_holding_registers(unit, 0, 10)
        if regs:
            for i in range(0, len(regs), 2):
                if i + 1 < len(regs):
                    p = struct.unpack('>f', struct.pack('>HH', regs[i], regs[i+1]))[0]
                    print(f"  Сенсор {i//2 + 1} давление: {p:.2f}")
            print(f"  Raw: {regs}")

        print(f"\n[Test 3] Read Coils (FC01) — насосы")
        coils = client.read_coils(unit, 0, 2)
        if coils:
            print(f"  Pump 1: {'ON' if coils[0] else 'OFF'}")
            print(f"  Pump 2: {'ON' if coils[1] else 'OFF'}")

        print(f"\n[Test 4] Write Single Register (FC06)")
        ok = client.write_single_register(unit, 0, 12345)
        print(f"  Write 12345 to reg 0: {'OK' if ok else 'FAILED'}")
        regs = client.read_holding_registers(unit, 0, 1)
        if regs:
            print(f"  Read back: {regs[0]} (expected: 12345)")

        print(f"\n[Test 5] Exception — несуществующий регистр + 'живые' данные")
        regs = client.read_holding_registers(unit, 9999, 1)
        print(f"  Result: {regs}")

        print("\n[Test 6] Два опроса за 2 с — значения T1 должны дрожать")
        for probe in range(2):
            regs = client.read_input_registers(unit, 0, 2)
            t1 = registers_to_float(regs[0], regs[1]) if regs else float('nan')
            print(f"  probe {probe}: T1 = {t1:.2f} °C")
            time.sleep(2.0)

        print("\n" + "=" * 60)
        print("All tests completed (см. вкладку Bus в TUI — кадры TCP)")
        print("=" * 60)
    finally:
        client.close()


if __name__ == '__main__':
    main()