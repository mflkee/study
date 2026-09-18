//! Урок 05: Traits и Generics.
//!
//! Запуск: `cargo run --example 05_traits`
//!
//! Реальный код: `frames.rs:35-55` — ModbusError impl Display + Error.
//! Плюс обобщённые функции над байтами (crc16, to_hex) работают с &[u8]
//! независимо от того, кто передал срез.

use std::fmt;

/// Trait — описание поведения. Аналог интерфейса в C/С++.
/// В Real::Into — trait Into<String> используется в cov.
trait Readable {
    /// Читаем блок данных. Возвращаем число прочитанных байт.
    fn read(&mut self, buf: &mut [u8]) -> usize;
}

/// Реализация trait для конкретной структуры.
struct Uart {
    fake_bytes: Vec<u8>,
    pos: usize,
}

impl Readable for Uart {
    fn read(&mut self, buf: &mut [u8]) -> usize {
        let n = self.fake_bytes.len().saturating_sub(self.pos);
        let n = n.min(buf.len());
        buf[..n].copy_from_slice(&self.fake_bytes[self.pos..self.pos + n]);
        self.pos += n;
        n
    }
}

#[allow(dead_code)]
struct FileLike {
    data: Vec<u8>,
}

#[allow(dead_code)]
impl FileLike {
    fn new(data: Vec<u8>) -> Self {
        Self { data }
    }
}

/// Реализация для другого типа — trait можно реализовать длЯ многих.
impl Readable for FileLike {
    fn read(&mut self, buf: &mut [u8]) -> usize {
        // упрощённо, для демонстрации:
        buf.copy_from_slice(&self.data[..buf.len().min(self.data.len())]);
        self.data.len().min(buf.len())
    }
}

/// Generic функция поверх trait. `T: Readable` — trait bound.
/// В реальном коде: `fn crc16(data: &[u8]) -> u16` — здесь было бы:
/// `fn compute<T: Readable>(r: &mut T) -> u16`
fn read_all<T: Readable>(r: &mut T, cap: usize) -> Vec<u8> {
    let mut buf = vec![0u8; cap];
    let n = r.read(&mut buf);
    buf[..n].to_vec()
}

/// default method в trait — реализация "из коробки".
trait Named {
    fn name(&self) -> String;
    /// default impl
    fn describe(&self) -> String {
        format!("object named {}", self.name())
    }
}

struct Slave {
    id: u8,
}

impl Named for Slave {
    fn name(&self) -> String {
        format!("slave-{}", self.id)
    }
}

/// fmt::Display для типа — реальный пример frames.rs:35-55.
#[allow(dead_code)]
#[derive(Clone, Copy)]
enum ChipState {
    Ok,
    Busy,
    Error(u8),
}

impl fmt::Display for ChipState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ChipState::Ok => write!(f, "OK"),
            ChipState::Busy => write!(f, "busy"),
            ChipState::Error(c) => write!(f, "error 0x{:02X}", c),
        }
    }
}

fn main() {
    let mut uart = Uart { fake_bytes: vec![0x01, 0x03, 0x00, 0x01], pos: 0 };
    let data = read_all(&mut uart, 8);
    assert_eq!(data, vec![0x01, 0x03, 0x00, 0x01]);

    let s = Slave { id: 1 };
    assert_eq!(s.describe(), "object named slave-1");

    let cs = ChipState::Error(0x02);
    assert_eq!(format!("{cs}"), "error 0x02");

    println!("05_traits: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generic_read_works_for_uart() {
        let mut uart = Uart { fake_bytes: vec![9, 8, 7], pos: 0 };
        assert_eq!(read_all(&mut uart, 10), vec![9, 8, 7]);
    }

    #[test]
    fn default_method_impl() {
        let c = ChipState::Busy;
        assert_eq!(format!("{c}"), "busy");
    }
}