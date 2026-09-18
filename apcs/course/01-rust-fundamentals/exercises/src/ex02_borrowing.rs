//! Упражнение 02: Borrowing.
//!
//! Задание: реализуйте функцию, которая считает CRC-подобную сумму,
//! подсоединяя результат к переданному по &mut Vec (как `crc::append`).
//!
//! Кроме того — добейтесь, чтобы компилятор принял чтение и мутацию
//! в разных последовательных фазах (borrow checker — не враг).

/// # Задание
/// Добавить в конец переданного вектора два контрольных байта:
/// low = сумма байт mod 256, high = low ^ 0xFF.
pub fn append_crc_like(frame: &mut Vec<u8>) {
    let sum: u8 = frame.iter().fold(0u8, |acc, &b| acc.wrapping_add(b));
    frame.push(sum);
    frame.push(sum ^ 0xFF);
}

/// # Идиома
/// Функция, которая "читает и не трогает" вход (borrow по &[u8]),
/// а потом отдельно мутирует буфер. Так работает master: сначала
/// прочитали pdu, потом записали в порт.
pub fn compute_and_copy(src: &[u8], dst: &mut Vec<u8>) {
    dst.extend_from_slice(src);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn append_creates_two_bytes() {
        let mut f = vec![0x01, 0x03];
        append_crc_like(&mut f);
        assert_eq!(f.len(), 4);
        assert_eq!(f[2], 0x04); // 01+03
        assert_eq!(f[3], 0xFB); // 0x04 ^ 0xFF
    }

    #[test]
    fn copy_borrows_cleanly() {
        let src = vec![1, 2, 3];
        let mut dst = Vec::new();
        compute_and_copy(&src, &mut dst);
        assert_eq!(dst, vec![1, 2, 3]);
        assert_eq!(src.len(), 3); // владельца не трогали
    }
}