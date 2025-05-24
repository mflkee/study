use rand::Rng;
use core::num;
use std::cmp::Ordering;
use std::io;

fn main() {
    println!("Guess the number!");
    let mut rng = rand::thread_rng(); // get generator
    let random_number: u32 = rng.gen_range(0..=100); // gen random number

    loop {
        println!("Pleas input your guess.");

        let mut guess = String::new();
        io::stdin()
            .read_line(&mut guess)
            .expect("error");

        let guess: u32 = match guess.trim().parse() {
            Ok(num) => num,
            Err(_) => continue,
        };

        match guess.cmp(&random_number) {
            Ordering::Less => println!("Too small!"),
            Ordering::Greater => println!("Too big!"),
            Ordering::Equal => {
                println!("You win!");
                break;
            }
        }
    }
}
