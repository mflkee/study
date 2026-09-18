#!/usr/bin/env python3
"""Мини-клиент «ИВК» для мини-полигона (кнопка + светодиод по Modbus RTU).

Общается с прошивкой по UART (USB-UART мост платы) — так же, как настоящий
верхний уровень опрашивал бы устройство:

    python3 mini_client.py /dev/ttyUSB0                 # один опрос
    python3 mini_client.py /dev/ttyUSB0 poll            # цикл каждые 1 с
    python3 mini_client.py /dev/ttyUSB0 led on          # включить светодиод
    python3 mini_client.py /dev/ttyUSB0 led off         # выключить

Карта адресов мини-полигона (нумерация 0-based):
    COIL    0   светодиод  (FC01 чтение / FC05 запись)
    COIL    1   кнопка     (FC01, только чтение)
    HOLDING 0   счётчик    (FC03, только чтение)
"""

import argparse
import sys

try:
    import serial
except ImportError:
    sys.exit("нужен pyserial: pip install pyserial")

SLAVE_ID = 1
BAUD = 9600
TIMEOUT = 1.0

# Modbus function codes.
FC_READ_COILS = 0x01
FC_READ_HOLDING = 0x03
FC_WRITE_COIL = 0x05


def crc16(frame: bytes) -> bytes:
    """CRC-16 Modbus: возвращает [lo, hi] для дописывания в кадр."""
    crc = 0xFFFF
    for byte in frame:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])


def build_request(pdu: bytes) -> bytes:
    frame = bytes([SLAVE_ID]) + pdu
    return frame + crc16(frame)


def read_exact(port, n: int) -> bytes:
    data = port.read(n)
    if len(data) != n:
        raise RuntimeError(f"короткий ответ: {len(data)} байт, ждали {n}")
    return data


def transact(port, pdu: bytes) -> bytes:
    """Посылает PDU запроса, возвращает PDU ответа."""
    port.reset_input_buffer()
    port.write(build_request(pdu))
    head = read_exact(port, 2)  # [slave][fc]
    if head[0] != SLAVE_ID:
        raise RuntimeError(f"ответ от чужого адреса {head[0]:02X}")
    if head[1] & 0x80:
        code = read_exact(port, 2)
        raise RuntimeError(
            f"исключение {code[1]:02X} (0x{code[1]:02X}) на функцию 0x{head[1] & 0x7F:02X}"
        )
    length = read_exact(port, 1)[0]  # byte count
    body = read_exact(port, length)
    return body


def read_coils(port, addr: int, count: int) -> list[bool]:
    pdu = bytes([FC_READ_COILS]) + addr.to_bytes(2, "big") + count.to_bytes(2, "big")
    body = transact(port, pdu)
    bits = body[0]
    return [bool(bits & (1 << i)) for i in range(count)]


def read_holding(port, addr: int, count: int) -> list[int]:
    pdu = bytes([FC_READ_HOLDING]) + addr.to_bytes(2, "big") + count.to_bytes(2, "big")
    body = transact(port, pdu)
    regs = []
    for i in range(0, len(body), 2):
        regs.append(int.from_bytes(body[i : i + 2], "big"))
    return regs


def write_coil(port, addr: int, value: bool) -> None:
    pdu = (
        bytes([FC_WRITE_COIL])
        + addr.to_bytes(2, "big")
        + (0xFF00 if value else 0x0000).to_bytes(2, "big")
    )
    transact(port, pdu)


def one_poll(port):
    led, button = read_coils(port, 0, 2)
    presses = read_holding(port, 0, 1)[0]
    print(
        f"LED={'ON ' if led else 'OFF'}  "
        f"кнопка={'НАЖАТА' if button else 'отпущена'}  "
        f"нажатий={presses}"
    )


def cmd_read(port, args):
    one_poll(port)


def cmd_led(port, args):
    write_coil(port, 0, args.action == "on")
    print(f"LED -> {'ON' if args.action == 'on' else 'OFF'}")
    one_poll(port)


def cmd_poll(port, args):
    import time

    for _ in range(args.limit):
        one_poll(port)
        time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description="мини-клиент ИВК (Modbus RTU)")
    parser.add_argument("port", help="порт, например /dev/ttyUSB0")
    sub = parser.add_subparsers(dest="cmd")

    p_read = sub.add_parser("read", help="один опрос")
    p_read.set_defaults(fn=cmd_read)

    p_led = sub.add_parser("led", help="вкл/выкл светодиод")
    p_led.add_argument("action", choices=["on", "off"])
    p_led.set_defaults(fn=cmd_led)

    p_poll = sub.add_parser("poll", help="цикл опроса каждые 1 с")
    p_poll.add_argument("--limit", type=int, default=10)
    p_poll.set_defaults(fn=cmd_poll)

    args = parser.parse_args()
    if not args.cmd:
        parser.print_help()
        sys.exit(1)

    with serial.Serial(args.port, BAUD, timeout=TIMEOUT) as port:
        args.fn(port, args)


if __name__ == "__main__":
    main()