// Обработка ошибок: версия с match (ручная обработка)
// https://doc.rust-lang.org/book/ch09-00-error-handling.html
// Вместо expect() используем match для обработки разных видов ошибок
// ErrorKind позволяет различать: файл не найден, нет прав, и т.д.

use std::fs::File;
use std::io::ErrorKind;

fn main() {
    let path: &str = "hello.txt";
    let greeting_file_result = File::open(path);

    let greeting_file = match greeting_file_result {
        Ok(fo) => fo, // файл открылся успешно
        Err(e) => match e.kind() {
            // Файл не найден — создаём новый
            ErrorKind::NotFound => match File::create(path) {
                Ok(fc) => fc,
                Err(e) => panic!("Ошибка создания файла {e:?}"),
            },
            // Другая ошибка (нет прав, и т.д.)
            other_error => {
                panic!("Проблема с открытием файла {other_error:?}")
            }
        },
    };
}
