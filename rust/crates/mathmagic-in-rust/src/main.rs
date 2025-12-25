use rand::seq::SliceRandom;
use rand::thread_rng;
use std::io;

fn print_matrix(matrix: &[[u32; 4]]) {
    for row in matrix {
        for &x in row {
            print!("{:2} ", x);
        }
        println!();
    }
    println!();
}

fn main() {
    // 1) Генерация начальной матрицы
    let mut numbers: Vec<u32> = (1..=16).collect();
    numbers.shuffle(&mut thread_rng());

    let mut first_matrix = [[0; 4]; 4];
    for (i, chunk) in numbers.chunks(4).enumerate() {
        first_matrix[i].copy_from_slice(&chunk[0..4]);
    }

    println!("Первая раскладка:");
    print_matrix(&first_matrix);

    // 2) Первый выбор
    let col1: usize = loop {
        println!("В каком столбце загаданная карта? (1-4):");
        let mut input = String::new();
        io::stdin().read_line(&mut input).expect("Ошибка ввода");

        match input.trim().parse() {
            Ok(num) if (1..=4).contains(&num) => break num,
            _ => println!("Только от 1 до 4!"),
        }
    };

    // 3) Транспонируем и переупорядочиваем
    // Создаем транспонированную матрицу (столбцы становятся строками)
    let mut transposed = [[0; 4]; 4];
    for i in 0..4 {
        for j in 0..4 {
            transposed[i][j] = first_matrix[j][i];
        }
    }

    // Переставляем строки: выбранный столбец становится первой строкой
    let order: Vec<usize> = (col1 - 1..4).chain(0..col1 - 1).collect();
    let mut reordered = [[0; 4]; 4];
    for (new_idx, &old_idx) in order.iter().enumerate() {
        reordered[new_idx] = transposed[old_idx];
    }

    // Транспонируем обратно
    let mut second_matrix = [[0; 4]; 4];
    for i in 0..4 {
        for j in 0..4 {
            second_matrix[i][j] = reordered[j][i];
        }
    }

    println!("\nВторая раскладка:");
    print_matrix(&second_matrix);

    // 4) Второй выбор
    let col2: usize = loop {
        println!("В каком столбце карта теперь? (1-4):");
        let mut input = String::new();
        io::stdin().read_line(&mut input).expect("Ошибка ввода");

        match input.trim().parse() {
            Ok(num) if (1..=4).contains(&num) => break num,
            _ => println!("Только от 1 до 4!"),
        }
    };

    // 5) "Угадываем" карту
    // В фокусе карта всегда оказывается в позиции [col2-1][col1-1]
    let guess = second_matrix[col2 - 1][0];
    println!("\nВаша загаданная карта: {}", guess);

    // Демонстрация как работает фокус
    println!("\nКак работает фокус:");
    println!("1. Карта была в столбце {}", col1);
    println!("2. При второй раскладке она попала в строку {}", col2);
    println!("3. Поэтому она всегда оказывается на пересечении");
}
