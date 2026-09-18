//! Урок 03: Lifetimes — время жизни ссылок.
//!
//! Запуск: `cargo run --example 03_lifetimes`
//!
//! Реальный код: почти не требуется в TUI (проект нигде не пишет явных
//! аннотаций) — но Borrow checker использует lifetimes автоматически.

/// Явные аннотации: возвращаемый результат живёт столько же, сколько s1.
/// В эмуляторе (emulator.rs:35-51) VirtualSensor по сути "хранит" ссылку
/// на данные — там это не видно, потому что данные внутри String (owned).
fn longer<'a>(s1: &'a str, s2: &'a str) -> &'a str {
    if s1.len() > s2.len() { s1 } else { s2 }
}

/// Борроу из данных, которыми владеет структура. Здесь default для
/// "одалживания" slice из Vec — совсем как мастер берёт &[u8].
struct FrameBuf {
    bytes: Vec<u8>,
}

impl FrameBuf {
    /// Выдаём &[u8] на весь буфер — владение остаётся у self, а время жизни
    /// привязывается к self (elision rule 3).
    fn as_slice(&self) -> &[u8] {
        &self.bytes
    }

    /// Ссылка на один байт с таким же lifetime.
    fn byte(&self, i: usize) -> &u8 {
        &self.bytes[i]
    }
}

/// 'static — всё время работы программы. Строковые литералы и константы.
const FC_NAME: &str = "READ INPUT REGISTERS";

fn lifetime_scope() {
    let s1 = String::from("hello");
    let result: &str;
    {
        let s2 = String::from("rust fondamentals!");
        result = longer(s1.as_str(), s2.as_str());
        println!("longer: {result}");
    }
    // ❌ result здесь уже невалиден (s2 удалена) — компилятор не даст:
    // println!("{result}");
    // Но если s2 жила дольше — было бы ок.
    let _ = s1;
}

fn main() {
    let fb = FrameBuf { bytes: vec![0x01, 0x03, 0x00, 0x01] };
    let slice: &[u8] = fb.as_slice(); // loan из fb
    let b0: &u8 = fb.byte(0);
    assert_eq!(slice[0], 0x01);
    assert_eq!(*b0, 0x01);

// 'static: литералы компилируются в константу; &'static str.
    let name: &'static str = FC_NAME;
    assert_eq!(name, "READ INPUT REGISTERS");

    lifetime_scope();

    println!("03_lifetimes: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn borrow_lives_as_long_as_owner() {
        let fb = FrameBuf { bytes: vec![1, 2, 3] };
        let s = fb.as_slice();
        assert_eq!(s.len(), 3);
        assert_eq!(fb.len(), 3); // owner ещё жив
    }

    #[test]
    fn longer_picks_loger() {
        let a = String::from("abc");
        let b = String::from("defghij");
        assert_eq!(longer(a.as_str(), b.as_str()), "defghij");
    }
}