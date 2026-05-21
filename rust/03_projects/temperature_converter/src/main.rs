// Конвертер температур (CLI-версия)
// Преобразует: Celsius <-> Fahrenheit
// Формулы: F = C * 9/5 + 32, C = (F - 32) * 5/9
// Демонстрирует: ввод/вывод, парсинг строк, match по выбору

use std::io;

fn main() {
    println!("Конвертер температур");
    println!("=====================");
    println!("1. Цельсий -> Фаренгейт");
    println!("2. Фаренгейт -> Цельсий");

    // Читаем выбор пользователя
    let mut choice = String::new();
    io::stdin()
        .read_line(&mut choice)
        .expect("Ошибка чтения");

    let choice: u32 = match choice.trim().parse() {
        Ok(num) => num,
        Err(_) => {
            println!("Неверный выбор!");
            return;
        }
    };

    // Читаем значение температуры
    println!("Введите температуру:");
    let mut temp_input = String::new();
    io::stdin()
        .read_line(&mut temp_input)
        .expect("Ошибка чтения");

    let temp: f64 = match temp_input.trim().parse() {
        Ok(num) => num,
        Err(_) => {
            println!("Неверная температура!");
            return;
        }
    };

    // Выбираем формулу по номеру
    match choice {
        1 => {
            let result = celsius_to_fahrenheit(temp);
            println!("{temp}°C = {result}°F");
        }
        2 => {
            let result = fahrenheit_to_celsius(temp);
            println!("{temp}°F = {result}°C");
        }
        _ => println!("Неверный выбор!"),
    }
}

fn celsius_to_fahrenheit(celsius: f64) -> f64 {
    celsius * 9.0 / 5.0 + 32.0
}

fn fahrenheit_to_celsius(fahrenheit: f64) -> f64 {
    (fahrenheit - 32.0) * 5.0 / 9.0
}
