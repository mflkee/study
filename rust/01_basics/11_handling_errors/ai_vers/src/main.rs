// Обработка ошибок: версия с expect (AI-generated)
// https://doc.rust-lang.org/book/ch09-00-error-handling.html
// Демонстрирует создание и запись файла с использованием expect()
// Expect() — сокращённый вариант match для Result: паникует при ошибке

use std::fs::{File, OpenOptions};
use std::io::Write;

fn main() {
    let path = "cache.txt";
    let mut file = create_file(path);

    write_file(&mut file, "Hello from Rust!");
    println!("Файл {path} создан и записан");
}

/// Создаёт файл (или перезаписывает существующий) через OpenOptions
fn create_file(path: &str) -> File {
    OpenOptions::new()
        .create(true)    // создать если не существует
        .truncate(true)  // очистить если существует
        .write(true)     // открыть для записи
        .open(path)
        .expect("Не удалось создать файл") // panic при ошибке
}

/// Записывает строку в файл
fn write_file(file: &mut File, content: &str) {
    file.write_all(content.as_bytes())
        .expect("Не удалось записать в файл");
}
