//! Урок 01: Ownership — система владения.
//!
//! Запуск: `cargo run --example 01_ownership`
//!
//! Реальный код: `master.rs:53-57` — `SerialMaster` ВЛАДЕЕТ портом и кольцом:
//! ```rust
//! pub struct SerialMaster {
//!     port: Box<dyn serialport::SerialPort>, // владение портом
//!     trace: VecDeque<TraceEntry>,
//! }
//! ```

/// Правило 1: у значения один владелец. При присваивании в обычном случае —
/// move: владелец меняется, старое имя "умирает".
// Скомментировано: строка ниже НЕ компилируется (E0382), поэтому мы её
// не включаем, а объясняем в тексте урока.
#[allow(dead_code)]
fn move_semantics() {
    let a: String = String::from("request");
    let b: String = a; // a ПОПАДАЕТ ВНУТРЬ b (move)
    assert_eq!(b, "request");
    // println!("{a}"); // ❌ E0382: use of moved value: `a`
}

/// Правило 2: для маленьких типов типа Copy владение не переносится — копия.
fn copy_semantics() {
    let x: u16 = 0x1234;
    let y: u16 = x; // x СКОПИРОВАН в y — оба живы
    assert_eq!(x, y);
}

/// Move на входе функции: функция забирает значение.
fn consume(s: String) -> usize {
    s.len()
}

/// Функция может ВЕРНУТЬ владение (т.е. "одолжить наружу").
fn return_ownership(s: String) -> (String, usize) {
    let len = s.len();
    (s, len) // возвращаем и строку, и длину
}

#[derive(Debug, Clone, PartialEq)]
pub struct Pdu {
    pub fc: u8,
    pub bytes: Vec<u8>,
}

impl Pdu {
    pub fn new(fc: u8, bytes: Vec<u8>) -> Self {
        Self { fc, bytes }
    }
}

/// Как работает PDU в frames.rs:58-65 (build_request):
/// `Vec` — это владелец буфера. `frame` на выходе — новый владелец.
fn build_request(slave_id: u8, fc: u8, pdu: &[u8]) -> Vec<u8> {
    let mut frame = Vec::with_capacity(pdu.len() + 4);
    frame.push(slave_id);
    frame.push(fc);
    frame.extend_from_slice(pdu); // копируем БАЙТЫ из pdu (borrow)
    frame // владение доверяется вызывающему
}

fn main() {
    copy_semantics();

    let s = String::from("abc");
    let (s_back, len) = return_ownership(s);
    assert_eq!((s_back.as_str(), len), ("abc", 3));

    // Потребить функцию (move внутрь) — но тогда строки больше нет.
    let consumed = consume(String::from("xyz"));
    assert_eq!(consumed, 3);

    let mut frame = build_request(0x01, 0x03, &[0x00, 0x00]);
    frame.push(0x00); // всё ещё ВЛАДЕЕМ frame и можем менять
    assert_eq!(frame, vec![0x01, 0x03, 0x00, 0x00, 0x00]);

    println!("01_ownership: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pdu_parts() {
        let p = Pdu::new(0x04, vec![0x00, 0x0A]);
        let q = p.clone(); // p-ции не перемещаем
        assert_eq!(q.fc, 0x04);
    }

    #[test]
    fn move_into_struct() {
        let pdu = Pdu::new(0x03, vec![0x00, 0x01]);
        // Владеющего члена нельзя скопировать без clone, но поля доступны.
        assert_eq!(pdu.bytes.len(), 2); // p = field borrow, не move
    }
}