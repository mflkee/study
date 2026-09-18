//! Урок 02: Borrowing — заимствование.
//!
//! Запуск: `cargo run --example 02_borrowing`
//!
//! Реальный код: везде. `crc16(data: &[u8])` (crc.rs:4) — читает по ссылке;
//! `append(frame: &mut Vec<u8>)` (crc.rs:30) — мутабельная ссылка.

/// &T — Immutable borrow: можно иметь МНОГО таких ссылок.
/// В crc.rs:4 `data: &[u8]` — читаем, не забирая владение.
fn sum_bytes(data: &[u8]) -> u32 {
    data.iter().map(|&b| b as u32).sum()
}

/// &mut T — Мутable borrow: можно иметь ТОЛЬКО ОДНУ активную.
/// В crc.rs:30 `frame: &mut Vec<u8>` — дописываем CRC в конец.
fn append_crc(frame: &mut Vec<u8>) {
    let crc: u16 = 0x0A84; // для "01 03 00 00 00 01"
    frame.extend_from_slice(&crc.to_le_bytes());
}

/// Борроу в поля структуры: читаем, не забирая.
struct ModbusFrame {
    bytes: Vec<u8>,
}

impl ModbusFrame {
    /// Функция-член: &self — immutable borrow структуры.
    fn len(&self) -> usize {
        self.bytes.len()
    }

    /// &mut self — можно менять содержимое через метод.
    fn push(&mut self, b: u8) {
        self.bytes.push(b);
    }
}

fn borrow_rules() {
    let mut frame = ModbusFrame { bytes: vec![0x01, 0x03] };

    // Много immutable borrows — норм.
    let a = &frame;
    let b = &frame;
    let n = frame.len(); // тоже immutable
    assert_eq!(a.len() + b.len(), n * 2);

    // Один mutable borrow за раз.
    frame.push(0x00);
    assert_eq!(frame.len(), 3);
}

/// Почему borrow нужен в мастер-цикле TRansact (master.rs:111-174):
/// transact(&mut, fc, pdu) — и буфер ответа, и трасса, и порт требуют
/// mutable доступа, но при этом входные pdu читаются иммutably.
fn why_borrowing() {
    let mut port = ModbusFrame { bytes: Vec::new() };
    let pdu = vec![0x00, 0x01];

    // pdu мы БЕРЁМ по ссылке (immutable) — можно продолжать использовать.
    port.bytes.extend_from_slice(&pdu);
    assert_eq!(port.len(), 2);
}

fn main() {
    let data = [0x01, 0x03, 0x00, 0x00, 0x00, 0x01];
    let sum = sum_bytes(&data);
    assert_eq!(sum, 5);

    let mut frame = vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x01];
    append_crc(&mut frame); // mutable borrow
    assert_eq!(frame.len(), 8); // + 2 байта CRC

    borrow_rules();
    why_borrowing();
    println!("02_borrowing: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn immutable_vs_mutable() {
        let mut v = vec![1, 2, 3];
        let read = sum_bytes(&v);
        v.push(4); // только после того, как read-ссылка больше не используется
        assert_eq!(read, 6);
    }

    #[test]
    fn crc_roundtrip() {
        let mut f = vec![0x01, 0x03, 0x00, 0x00, 0x00, 0x01];
        append_crc(&mut f);
        assert!(f.ends_with(&[0x84, 0x0A]));
    }
}