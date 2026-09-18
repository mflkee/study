//! Упражнение 03: Lifetimes.
//!
//! Задание: напишите функцию, которая возвращает первый из двух срезов,
//! у которого значение больше (аналог `longer`), — неявно связав
//! время жизни входных ссылок с выходной.

/// # Задание
/// Вернуть ссылку на больший по значению первый байт из двух срезов.
/// Нужна явная аннотация 'a.
pub fn max_first<'a>(a: &'a [u8], b: &'a [u8]) -> &'a u8 {
    if a[0] > b[0] { &a[0] } else { &b[0] }
}

/// # Идиома из сети
/// Структура, которая хранит заимствованный срез (как командный буфер,
/// содержащий &[u8] на временном окне) — с параметром lifetime.
pub struct BorrowedFrame<'a> {
    pub name: &'a str,
}

impl<'a> BorrowedFrame<'a> {
    pub fn new(name: &'a str) -> Self {
        Self { name }
    }

    pub fn name(&self) -> &'a str {
        self.name
    }

    pub fn len(&self) -> usize {
        self.name.len()
    }

    pub fn is_empty(&self) -> bool {
        self.name.is_empty()
    }
}

/// Функция, вызывающая `max_first`, — аннотация обязательна, так как две
/// входные ссылки: компилятор не знает, куда привязать возвращаемую.
pub fn pick<'a>(a: &'a [u8], b: &'a [u8]) -> &'a u8 {
    max_first(a, b)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn max_first_picks_bigger() {
        let lo = [0x10];
        let hi = [0x80];
        let chosen = max_first(&lo, &hi);
        assert_eq!(*chosen, 0x80);
        assert!(std::ptr::eq(chosen, &hi[0])); // вернули именно ссылку на hi
    }

    #[test]
    fn borrowed_frame_lives_with_data() {
        let name = String::from("slave-1");
        let f = BorrowedFrame::new(&name);
        assert_eq!(f.len(), 7);
        assert_eq!(f.name, "slave-1");
    }

    #[test]
    fn elision_keeps_relation() {
        let a = [0x01];
        let b = [0xFF];
        assert_eq!(*pick(&a, &b), 0xFF);
    }
}