use std::io;

fn main() {
    println!("Введите ваше имя: ");
    let mut name = String::new();
    
    io::stdin()
        .read_line(&mut name)
        .expect("Ошибка чтения ввода!");

    println!("Hello, {}!!!", name.trim())
}
