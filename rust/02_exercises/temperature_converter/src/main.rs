// Temperature Converter
// Конвертер температур: Celsius <-> Fahrenheit
// F = C * 9/5 + 32
// C = (F - 32) * 5/9

use std::io;

fn main() {
    println!("Temperature Converter");
    println!("=====================");
    println!("1. Celsius to Fahrenheit");
    println!("2. Fahrenheit to Celsius");

    let mut choice = String::new();
    io::stdin()
        .read_line(&mut choice)
        .expect("Failed to read choice");

    let choice: u32 = match choice.trim().parse() {
        Ok(num) => num,
        Err(_) => {
            println!("Invalid choice!");
            return;
        }
    };

    println!("Enter temperature value:");
    let mut temp_input = String::new();
    io::stdin()
        .read_line(&mut temp_input)
        .expect("Failed to read temperature");

    let temp: f64 = match temp_input.trim().parse() {
        Ok(num) => num,
        Err(_) => {
            println!("Invalid temperature!");
            return;
        }
    };

    match choice {
        1 => {
            let result = celsius_to_fahrenheit(temp);
            println!("{temp}°C = {result}°F");
        }
        2 => {
            let result = fahrenheit_to_celsius(temp);
            println!("{temp}°F = {result}°C");
        }
        _ => println!("Invalid choice!"),
    }
}

fn celsius_to_fahrenheit(celsius: f64) -> f64 {
    celsius * 9.0 / 5.0 + 32.0
}

fn fahrenheit_to_celsius(fahrenheit: f64) -> f64 {
    (fahrenheit - 32.0) * 5.0 / 9.0
}
