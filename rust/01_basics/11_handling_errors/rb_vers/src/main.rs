// Обработка ошибок: версия с match (ручная обработка)
// https://doc.rust-lang.org/book/ch09-00-error-handling.html
// Вместо expect() используем match для обработки разных видов ошибок
// ErrorKind позволяет различать: файл не найден, нет прав, и т.д.

use std::fs::OpenOptions;
use std::io::Write;


fn main() {
    let path: &str = "hello.txt";

    let mut greeting_file = match OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(true)
        .open(path){
        Ok(file) => file,
        Err(e) => panic!("Ошибка открытия файла {e:?}"),
    };

    let text = String::from("Hello World!");
    match greeting_file.write_all(text.as_bytes()) {
        Ok(()) => println!("Текст успешно записан"),
        Err(e) => panic!("Ошибка записи в файл: {e:?}"),
    }

}
