// Slices (Срезы)
// https://doc.rust-lang.org/book/ch04-03-slices.html

fn main() {
    // === String Slices ===
    let s = String::from("hello world");

    // &str - срез строки
    let hello = &s[0..5];
    let world = &s[6..11];
    println!("{hello} {world}");

    // Синтаксис срезов
    let hello = &s[..5];    // от начала до 5
    let world = &s[6..];    // от 6 до конца
    let whole = &s[..];     // вся строка
    println!("{hello} {world} {whole}");

    // String slices и функции
    let word = first_word(&s);
    println!("First word: {word}");

    // String literals are slices
    let literal: &str = "hello";

    // === Array Slices ===
    let arr = [1, 2, 3, 4, 5];
    let slice = &arr[1..3];
    println!("Array slice: {slice:?}");

    // Slice в функции
    print_slice(&arr);
}

fn first_word(s: &str) -> &str {
    let bytes = s.as_bytes();
    for (i, &item) in bytes.iter().enumerate() {
        if item == b' ' {
            return &s[0..i];
        }
    }
    &s[..]
}

fn print_slice(slice: &[i32]) {
    println!("Slice: {slice:?}");
}
