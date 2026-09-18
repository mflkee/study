use std::time::{Duration, Instant};
use std::env;

fn main() {
    let name = env::args().nth(1).unwrap_or("/dev/ttyACM1".into());
    let t0 = Instant::now();
    let mut p = serialport::new(&name, 9600)
        .timeout(Duration::from_millis(60))
        .open()
        .expect("open");

    // Сбрасываем мусор в RX.
    let mut junk = [0u8; 512];
    while p.read(&mut junk).is_ok() {}

    // READ INPUT REGISTERS, addr 0, count 60
    let (hi, lo) = frame_crc(1, 0x04, 0x00, 0x00, 0x00, 0x3C);
    p.write_all(&[1, 0x04, 0x00, 0x00, 0x00, 0x3C, hi, lo]).unwrap();
    eprintln!("[{}ms] request sent", t0.elapsed().as_millis());

    let mut total = Vec::new();
    let mut last = Instant::now();
    let mut buf = [0u8; 256];
    for _ in 0..80 {
        match p.read(&mut buf) {
            Ok(n) => {
                total.extend_from_slice(&buf[..n]);
                last = Instant::now();
            }
            Err(_) => {
                if !total.is_empty() && last.elapsed() > Duration::from_millis(300) {
                    break;
                }
            }
        }
    }
    eprintln!(
        "[{}ms] got {} bytes: {}",
        t0.elapsed().as_millis(),
        total.len(),
        hex(&total)
    );
    if total.len() >= 5 {
        eprintln!("slave={:02X} fc={:02X} bytecount={:02X}", total[0], total[1], total[2]);
    }
}

fn frame_crc(slave: u8, fc: u8, a: u8, b: u8, c: u8, d: u8) -> (u8, u8) {
    let mut crc: u16 = 0xFFFF;
    for b in [slave, fc, a, b, c, d] {
        crc ^= b as u16;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    ((crc & 0xFF) as u8, (crc >> 8) as u8)
}

fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{:02X}", x)).collect::<Vec<_>>().join(" ")
}