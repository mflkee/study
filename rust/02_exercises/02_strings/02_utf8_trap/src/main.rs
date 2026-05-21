// Упражнение 06: UTF-8 ловушка
// Тема: bytes vs chars в строках Rust
// В Rust строки — это UTF-8, поэтому:
// - s.len() возвращает количество БАЙТ, а не символов
// - s.chars() — правильный способ работать с Unicode

fn main() {
    println!("Упражнение 06: UTF-8 байты vs символы");

    let s = "Hello";
    println!("{:?}", first_char(s)); // Some('H')
    println!("{:?}", last_char(s));  // Some('o')
    println!("chars: {}", char_count(s)); // 5
    println!("bytes: {}", byte_count(s)); // 5

    let ru = "Привет";
    println!("Символов в 'Привет': {}", char_count(ru)); // 6
    println!("Байтов в 'Привет': {}", byte_count(ru));   // 12 (каждый символ по 2 байта)
}

/// Возвращает первый Unicode-символ строки (или None если строка пуста)
fn first_char(s: &str) -> Option<char> {
    s.chars().next()
}

/// Возвращает последний Unicode-символ строки (или None если строка пуста)
fn last_char(s: &str) -> Option<char> {
    s.chars().last()
}

/// Считает количество Unicode-символов (не байтов!)
fn char_count(s: &str) -> usize {
    s.chars().count()
}

/// Считает количество байт в UTF-8 строке
fn byte_count(s: &str) -> usize {
    s.len()
}
