#!/usr/bin/env python3
"""
ИВК-клиент для модуля AI-32 (24 канала 4–20 мА, Modbus RTU via RS-485).

Читает карту регистров модуля и выводит «табло» в консоль:

    FC04 INPUT   0..47   ток по каналу, float32 (2 регистра на канал, мА)
    FC03 HOLDING 0..95   калибровка: gain + offset (float32), read-only
    FC01 COILS   0..23   включён ли канал (1 бит = 1 канал)
    FC02 DISCR   0..23   копия COILS (для совместимости)
    FC05/FC06           запись одиночного coil / регистра (WRITE)

RTU кадр: [slave id][fc][data ...][CRC16 lo][CRC16 hi]
Разбирать CRC умеем сами — сторонняя библиотека Modbus не нужна.

Использование:
    python3 ive_client.py [port]                    # разовый опрос + демо-тесты
    python3 ive_client.py /dev/ttyUSB0 poll         # цикл обновления каждые 2 с
    python3 ive_client.py COM5 --baud 38400         # другой бод (по умолч. 9600)

По умолчанию: /dev/ttyUSB0, 9600 8N1, slave id = 1 (SLAVE_ID в rtu_slave.rs).
Требуется pyserial:  pip install pyserial
"""

import struct
import sys
import time

DEFAULT_PORT = '/dev/ttyUSB0'
DEFAULT_BAUD = 9600
TIMEOUT = 0.5
CHANNELS = 24
SLAVE_ID = 1


def regs_to_f32(reg0: int, reg1: int) -> float:
    """Два big-endian регистра -> float32."""
    return struct.unpack('>f', struct.pack('>I', (reg0 << 16) | reg1))[0]


def crc16(data: bytes) -> int:
    """CRC16/Modbus: полином 0xA001, начальное значение 0xFFFF."""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc


class ModbusRtuClient:
    """Минимальный Modbus RTU мастер поверх pyserial."""

    def __init__(self, port: str, baud: int = DEFAULT_BAUD, unit: int = SLAVE_ID):
        import serial
        self.ser = serial.Serial(port, baud, timeout=TIMEOUT)
        self.unit = unit

    def close(self):
        try:
            self.ser.close()
        except Exception:
            pass

    def _transact(self, fc: int, data: bytes = b'') -> bytes:
        """Полный RTU обмен: addr + pdu + crc, чтение ответа до silent interval."""
        pdu = bytes([fc]) + data
        frame = bytes([self.unit]) + pdu
        frame += struct.pack('<H', crc16(frame))
        self.ser.flushInput()
        self.ser.write(frame)

        # Читаем ответ байтами, пока они идут (RTU отделяет кадр паузой ~3.5
        # символа; после последнего байта читалка «засыпает» и кадр собран).
        resp = b''
        while True:
            chunk = self.ser.read(256)
            if not chunk:
                break
            resp += chunk
            if len(chunk) < 256:  # ждём паузу: новых байт в 0.5 c нет
                time.sleep(0.01)
                if not self.ser.in_waiting:
                    break
        if len(resp) < 4:
            raise ConnectionError('нет ответа (пустой/короткий RTU кадр)')
        if resp[0] != self.unit:
            raise ConnectionError(f'ответ чужого slave id {resp[0]}')
        if crc16(resp[:-2]) != struct.unpack('<H', resp[-2:])[0]:
            raise ConnectionError('CRC ошибка в ответе')
        pdu_rsp = resp[1:-2]
        if pdu_rsp and (pdu_rsp[0] & 0x80):
            raise ConnectionError(f'exception fc=0x{pdu_rsp[0]:02X} code=0x{pdu_rsp[1]:02X}')
        return pdu_rsp

    def read_input_registers(self, start: int, count: int) -> list[int]:
        pdu = self._transact(0x04, struct.pack('>HH', start, count))
        bc = pdu[1]
        return [struct.unpack('>H', pdu[2 + i:4 + i])[0] for i in range(0, bc, 2)]

    def read_holding_registers(self, start: int, count: int) -> list[int]:
        pdu = self._transact(0x03, struct.pack('>HH', start, count))
        bc = pdu[1]
        return [struct.unpack('>H', pdu[2 + i:4 + i])[0] for i in range(0, bc, 2)]

    def read_coils(self, start: int, count: int) -> list[bool]:
        pdu = self._transact(0x01, struct.pack('>HH', start, count))
        bc = pdu[1]
        coils: list[bool] = []
        for byte in pdu[2:2 + bc]:
            for bit in range(8):
                if len(coils) >= count:
                    break
                coils.append(bool(byte & (1 << bit)))
        return coils

    def write_single_coil(self, address: int, value: bool) -> None:
        self._transact(0x05, struct.pack('>HH', address, 0xFF00 if value else 0))

    def write_single_register(self, address: int, value: int) -> None:
        """FC06: ожидаем исключение (калибровка в модуле read-only)."""
        try:
            self._transact(0x06, struct.pack('>HH', address, value))
        except ConnectionError as e:
            return e


def print_tape(client: ModbusRtuClient):
    """Один «срез» ИВК: таблица 24 каналов."""
    regs = client.read_input_registers(0, CHANNELS * 2)
    coils = client.read_coils(0, CHANNELS)

    print('-' * 58)
    print(f"{'N':>2} {'тМк':>6} {'I, мА':>8}   {'N':>2} {'тМк':>6} {'I, мА':>8}")
    print('-' * 58)
    for i in range(0, CHANNELS, 2):
        line = ''
        for ch in (i, i + 1):
            ma = regs_to_f32(regs[ch * 2], regs[ch * 2 + 1])
            mark = 'on ' if coils[ch] else 'off'
            line += f"{ch + 1:>2} {mark:>3} {ma:>7.3f}   "
        print(line)
    print('-' * 58)


def demo_tests(client: ModbusRtuClient):
    print('\n[Обработчик ИВК] демо-тесты запросов')
    print(f"  slave id=1, каналов {CHANNELS} (3×8)")

    print('  FC03 HOLDING (калибровка) канала 1:')
    regs = client.read_holding_registers(0, 4)
    gain = regs_to_f32(regs[0], regs[1])
    off = regs_to_f32(regs[2], regs[3])
    print(f"    gain={gain:.9f} offset={off:.3f}  (код АЦП -> мА = code*{gain:.9f}{off:+.3f})")

    print('  FC06 WRITE register (должно вернуть исключение 0x04, read-only):')
    err = client.write_single_register(0, 123)
    print(f"    -> {err or 'OK (неожиданно)'}")

    print('  FC05 WRITE/READ coil: выключаем канал 1, читаем, включаем обратно')
    before = client.read_coils(0, 1)[0]
    client.write_single_coil(0, not before)
    after = client.read_coils(0, 1)[0]
    client.write_single_coil(0, before)
    print(f"    канал 1: было {'вкл' if before else 'выкл'}, стало {'вкл' if after else 'выкл'}")


def main():
    args = sys.argv[1:]
    port = args[0] if len(args) > 0 else DEFAULT_PORT
    mode = args[1] if len(args) > 1 else 'once'
    baud = DEFAULT_BAUD
    if '--baud' in args:
        baud = int(args[args.index('--baud') + 1])

    client = ModbusRtuClient(port, baud)
    try:
        print(f"ИВК <- AI-32 {port} {baud} 8N1, slave id={SLAVE_ID} (Ctrl+C — выход)")
        if mode == 'poll':
            while True:
                print_tape(client)
                time.sleep(2.0)
        else:
            print_tape(client)
            demo_tests(client)
            print('\nДля непрерывного опроса: python3 ive_client.py {} poll'.format(port))
    except KeyboardInterrupt:
        print('\nостановлено')
    except (ConnectionError, OSError, ImportError) as e:
        print(f'ошибка: {e}')
        sys.exit(1)
    finally:
        client.close()


if __name__ == '__main__':
    main()