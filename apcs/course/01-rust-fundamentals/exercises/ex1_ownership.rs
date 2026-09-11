// Упражнение 1.1: Memory Safety
//
// Задача: Напишите функцию, которая принимает строку и возвращает
// сумму всех ASCII-байтов. Решите проблему ownership без clone().
//
// Подсказки:
// - Используйте &str вместо String (borrowing)
// - Или используйте .into_owned() если нужнаownership
// - Или принимайте owned String и возвращайте его вместе с результатом

/// Способ 1: Принимаем &str (borrowing) - нет ownership проблем
fn sum_ascii_borrow(s: &str) -> u32 {
    s.bytes().map(|b| b as u32).sum()
}

/// Способ 2: Принимаем String, возвращаем её вместе с результатом
fn sum_ascii_owned(s: String) -> (String, u32) {
    let sum = s.bytes().map(|b| b as u32).sum();
    (s, sum)
}

/// Способ 3: Using Into<String> для гибкости
fn sum_ascii_generic(s: impl Into<String>) -> u32 {
    let s = s.into();
    s.bytes().map(|b| b as u32).sum()
}

fn main() {
    // Тесты
    let text = "Hello, World!";

    // Способ 1: borrowing
    let sum1 = sum_ascii_borrow(text);
    println!("Sum (borrow): {} = {}", text, sum1);

    // Способ 2: owned
    let owned = String::from(text);
    let (returned, sum2) = sum_ascii_owned(owned);
    println!("Sum (owned): {} = {}", returned, sum2);

    // Способ 3: generic
    let sum3 = sum_ascii_generic("Rust");
    println!("Sum (generic): Rust = {}", sum3);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_borrow() {
        assert_eq!(sum_ascii_borrow("ABC"), 65 + 66 + 67);
        assert_eq!(sum_ascii_borrow(""), 0);
        assert_eq!(sum_ascii_borrow("a"), 97);
    }

    #[test]
    fn test_owned() {
        let s = String::from("Hi");
        let (returned, sum) = sum_ascii_owned(s);
        assert_eq!(returned, "Hi");
        assert_eq!(sum, 72 + 105);
    }
}
