// Управляющие конструкции в Rust
// https://doc.rust-lang.org/book/ch03-05-control-flow.html
// if/else, loop, while, for — без круглых скобок вокруг условия

fn main() {
    // === if/else ===
    let number = 7;

    if number < 5 {
        println!("условие истинно");
    } else {
        println!("условие ложно");
    }

    // Несколько условий: else if
    if number % 4 == 0 {
        println!("число делится на 4");
    } else if number % 3 == 0 {
        println!("число делится на 3");
    } else if number % 2 == 0 {
        println!("число делится на 2");
    } else {
        println!("число не делится на 4, 3 или 2");
    }

    // if в let — тернарный оператор по-растовски
    let condition = true;
    let number = if condition { 5 } else { 6 };
    // Оба варианта должны быть одного типа!
    println!("Значение number: {number}");

    // === Циклы ===

    // loop — бесконечный цикл, пока не break
    let mut counter = 0;
    let result = loop {
        counter += 1;
        if counter == 10 {
            break counter * 2; // break может возвращать значение
        }
    };
    println!("Результат loop: {result}");

    // Метки циклов для вложенных циклов
    let mut count = 0;
    'counting_up: loop {  // метка 'counting_up
        println!("count = {count}");
        let mut remaining = 10;
        loop {
            println!("remaining = {remaining}");
            if remaining == 9 {
                break; // выход из внутреннего цикла
            }
            if count == 2 {
                break 'counting_up; // выход из внешнего цикла по метке
            }
            remaining -= 1;
        }
        count += 1;
    }

    // while — цикл с предусловием
    let mut n = 3;
    while n != 0 {
        println!("{n}!");
        n -= 1;
    }
    println!("ПУСК!");

    // for — наиболее идиоматичный цикл в Rust
    let arr = [10, 20, 30, 40, 50];
    for element in arr {
        println!("значение: {element}");
    }

    // Range (диапазоны) — 1..4 это от 1 до 3 (не включая 4)
    for number in 1..4 {
        println!("{number}");
    }

    // rev() — обратный порядок
    for number in (1..4).rev() {
        println!("обратный отсчёт: {number}");
    }
}
