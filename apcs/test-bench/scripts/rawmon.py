#!/usr/bin/env python3
"""
Serial raw monitor (stdlib only, no pyserial) — быстрая проверка линии.

Использование:
    python3 rawmon.py /dev/ttyACM1 [baud]                     # дамп входящих байт
    python3 rawmon.py /dev/ttyACM1 9600 --tx 010400000020f1d2 # отправить HEX-кадр
    python3 rawmon.py /dev/ttyACM1 9600 --modbus 1 4 0 30     # Modbus: slave 1, fc 4, addr 0, count 30

Пример — проверить, отвечает ли ESP32 как Modbus slave:
    python3 rawmon.py /dev/ttyACM1 9600 --modbus 1 3 0 10
"""

import os
import sys
import time
import select
import termios
import fcntl

BAUDS = {
    9600: termios.B9600,
    19200: termios.B19200,
    38400: termios.B38400,
    57600: termios.B57600,
    115200: termios.B115200,
}


def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc


def open_raw(path: str, baud: int):
    if baud not in BAUDS:
        sys.exit(f"unsupported baud {baud}, use one of {sorted(BAUDS)}")
    fd = os.open(path, os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
    attrs = termios.tcgetattr(fd)
    attrs[0] = 0
    attrs[1] = 0
    attrs[3] = 0
    attrs[4] = BAUDS[baud]
    attrs[5] = BAUDS[baud]
    attrs[2] &= ~(termios.CSIZE | termios.PARENB | termios.CSTOPB)
    attrs[2] |= termios.CS8 | termios.CREAD | termios.CLOCAL
    attrs[6][termios.VMIN] = 1
    attrs[6][termios.VTIME] = 0
    termios.tcsetattr(fd, termios.TCSANOW, attrs)
    fcntl.fcntl(fd, fcntl.F_SETFL, os.O_NONBLOCK)
    return fd


def hexdump(data: bytes) -> str:
    return " ".join(f"{b:02X}" for b in data)


def read_some(fd, timeout_ms):
    while True:
        r, _, _ = select.select([fd], [], [], timeout_ms / 1000)
        if not r:
            return b""
        chunk = os.read(fd, 4096)
        if not chunk:
            return b""
        return chunk


def monitor(fd, listen_s=5):
    buf = bytearray()
    start = time.time()
    last = time.time()
    while time.time() - start < listen_s:
        chunk = read_some(fd, 200)
        if chunk:
            last = time.time()
            buf += chunk
            t = time.strftime("%H:%M:%S")
            print(f"[{t}] RX {len(chunk)}: {hexdump(chunk)}")
        elif buf and time.time() - last > 0.3:
            print(f"  -> {len(buf)} bytes: {hexdump(buf)}")
            print(f"  -> CRC OK: {crc16(buf) & 0xFFFF == int.from_bytes(buf[-2:], 'little')}")
            buf.clear()
            last = time.time()
    os.close(fd)


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)
    path = args[0]
    baud = int(args[1]) if len(args) > 1 and args[1].isdigit() else 9600
    fd = open_raw(path, baud)
    print(f"opened {path} @ {baud} raw")

    if "--modbus" in args:
        p = args.index("--modbus")
        slave = int(args[p + 1])
        fc = int(args[p + 2])
        addr = int(args[p + 3], 0)
        count = int(args[p + 4], 0)
        body = bytes([slave, fc, addr >> 8, addr & 0xFF, count >> 8, count & 0xFF])
        frame = body + crc16(body).to_bytes(2, "little")
        print(f"TX: {slave},{fc}@{addr} x{count} -> {hexdump(frame)}")
        os.write(fd, frame)
        monitor(fd)
        sys.exit(0)

    if "--tx" in args:
        p = args.index("--tx")
        frame = bytes.fromhex(args[p + 1].replace(" ", ""))
        print(f"TX: {hexdump(frame)}")
        os.write(fd, frame)
        monitor(fd)
        sys.exit(0)

    monitor(fd, listen_s=10)


if __name__ == "__main__":
    main()