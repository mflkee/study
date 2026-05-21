// Срезы (Slices) — ссылка на непрерывную последовательность элементов
// https://doc.rust-lang.org/book/ch04-03-slices.html
// &str — срез строки (string slice)
// &[T] — срез массива

fn main() {
    // === Срезы строк ===
    let s = String::from("hello world");

    // &str — неизменяемая ссылка на часть строки
    let hello = &s[0..5];  // первые 5 символов
    let world = &s[6..11]; // с 6 по 10
    println!("{hello} {world}");

    // Сокращённый синтаксис срезов
    let hello = &s[..5];    // от начала до 5
    let world = &s[6..];    // от 6 до конца
    let whole = &s[..];     // вся строка
    println!("{hello} {world} {whole}");

    // Срез строки и функция
    let word = first_word(&s);
    println!("Первое слово: {word}");

    // Строковые литералы — это уже срезы &str
    let literal: &str = "hello";

    // === Срезы массивов ===
    let arr = [1, 2, 3, 4, 5];
    let slice = &arr[1..3]; // [2, 3]
    println!("Срез массива: {slice:?}");

    // Передаём срез в функцию
    print_slice(&arr);
}

// Принимает &str, а не &String — это более гибко (работает с обоими типами)
fn first_word(s: &str) -> &str {
    let bytes = s.as_bytes();
    for (i, &item) in bytes.iter().enumerate() {
        if item == b' ' {
            return &s[0..i]; // нашли пробел — возвращаем слово до него
        }
    }
    &s[..] // пробела нет — вся строка это одно слово
}

fn print_slice(slice: &[i32]) {
    println!("Срез: {slice:?}");
}
