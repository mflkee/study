// Угадай число — классическая игра из Rust Book
// https://doc.rust-lang.org/book/ch02-00-guessing-game-tutorial.html
// Компьютер загадывает случайное число от 0 до 100, игрок угадывает

use rand::Rng;
use std::cmp::Ordering;
use std::io;

fn main() {
    println!("Угадай число!");

    // Генерация случайного числа
    let mut rng = rand::thread_rng();
    let random_number: u32 = rng.gen_range(0..=100);

    loop {
        println!("Введите вашу догадку:");

        let mut guess = String::new();
        io::stdin()
            .read_line(&mut guess)
            .expect("Ошибка чтения строки");

        // trim() убирает \n, parse() конвертирует &str в u32
        // Если ввод не число — continue (новая попытка)
        let guess: u32 = match guess.trim().parse() {
            Ok(num) => num,
            Err(_) => continue,
        };

        // Сравнение с загаданным числом
        match guess.cmp(&random_number) {
            Ordering::Less => println!("Слишком мало!"),
            Ordering::Greater => println!("Слишком много!"),
            Ordering::Equal => {
                println!("Вы выиграли!");
                break; // выход из цикла
            }
        }
    }
}
