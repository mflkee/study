//! Урок 00: типы, переменные, функции, Cargo.
//!
//! Запуск: `cargo run --example 00_types`
//!
//! Реальный код: `test-bench/tui/src/crc.rs` — функции над `&[u8]`,
//! `test-bench/tui/src/frames.rs:58` — `Vec::with_capacity`.

/// unsigned: u8 (байт), u16 (регистр Modbus), u32, u64, usize — размер-тип.
/// signed:   i8, i16, i32 ...
/// float:    f32 (у ESP32-DEVKITC — с одинарной точностью дешевле), f64.
fn integer_math() {
    // Целочисленное деление ОТРЕЗАЕТ дробную часть.
    let half = 5 / 2; // = 2, НЕ 2.5
    assert_eq!(half, 2);

    // mod — остаток, в Modbus-адресации встречается на каждом шагу.
    let addr: u16 = 7;
    assert_eq!(addr % 4, 3);

    // Младший/старший байт 16-битного регистра (как в emulator.rs:100-101).
    let reg: u16 = 0xABCD;
    assert_eq!(reg >> 8, 0xAB); // старший байт
    assert_eq!(reg & 0xFF, 0xCD); // младший байт
}

/// String (собственная, move) vs &str (заимствованная, только чтение).
/// В emulator.rs:35-51 struct VirtualSensor держит `name: String`.
fn strings_slices() {
    let name: String = String::from("pump_station");
    let bytes: &[u8] = name.as_bytes(); // &str == &[u8] + валидный utf-8
    let first: u8 = bytes[0];
    assert_eq!(first, b'p');

    // Срез без аллокаций (borrowing!). В master.rs:127 — чтение в `&mut buf[..]`.
    let s: &str = &name[0..4];
    assert_eq!(s, "pump");
}

/// Функции: параметры и возврат. Ниже — аналог `crc::to_hex` (crc.rs:36-45),
/// упрощённый до идеи "собрать строку из байтов".
fn to_hex_ish(data: &[u8]) -> String {
    let mut s = String::with_capacity(data.len() * 3);
    for &b in data {
        if !s.is_empty() {
            s.push(' ');
        }
        s.push_str(&format!("{:02X}", b));
    }
    s
}

fn main() {
    integer_math();
    strings_slices();

    let frame: [u8; 4] = [0x01, 0x03, 0x00, 0x01];
    println!("frame = {}", to_hex_ish(&frame));

    // Всё, что выше — документальные assert'ы. Это и есть минимальный код,
    // который вам понадобится для работы с пакетами на Modbus.
    println!("00_types: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hex_format() {
        assert_eq!(to_hex_ish(&[0x01, 0x0A]), "01 0A");
        assert_eq!(to_hex_ish(&[]), "");
    }
}