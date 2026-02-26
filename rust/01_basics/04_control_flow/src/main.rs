// Control Flow
// https://doc.rust-lang.org/book/ch03-05-control-flow.html

fn main() {
    // === if/else ===
    let number = 7;

    if number < 5 {
        println!("condition was true");
    } else {
        println!("condition was false");
    }

    // Multiple conditions
    if number % 4 == 0 {
        println!("number is divisible by 4");
    } else if number % 3 == 0 {
        println!("number is divisible by 3");
    } else if number % 2 == 0 {
        println!("number is divisible by 2");
    } else {
        println!("number is not divisible by 4, 3, or 2");
    }

    // if в let statement
    let condition = true;
    let number = if condition { 5 } else { 6 };
    println!("The value of number is: {number}");

    // === Loops ===

    // loop (бесконечный цикл)
    let mut counter = 0;
    let result = loop {
        counter += 1;
        if counter == 10 {
            break counter * 2; // return value from loop
        }
    };
    println!("Loop result: {result}");

    // Loop labels (метки для вложенных циклов)
    let mut count = 0;
    'counting_up: loop {
        println!("count = {count}");
        let mut remaining = 10;
        loop {
            println!("remaining = {remaining}");
            if remaining == 9 {
                break;
            }
            if count == 2 {
                break 'counting_up;
            }
            remaining -= 1;
        }
        count += 1;
    }

    // while
    let mut n = 3;
    while n != 0 {
        println!("{n}!");
        n -= 1;
    }
    println!("LIFTOFF!");

    // for (наиболее идиоматичный цикл)
    let arr = [10, 20, 30, 40, 50];
    for element in arr {
        println!("the value is: {element}");
    }

    // Range (диапазоны)
    for number in 1..4 {
        println!("{number}");
    }

    for number in (1..4).rev() {
        println!("countdown: {number}");
    }
}
