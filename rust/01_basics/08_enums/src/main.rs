// Перечисления (Enums) и сопоставление с образцом (Pattern Matching)
// https://doc.rust-lang.org/book/ch06-00-enums.html
// Rust enums — мощный инструмент: могут содержать данные разных типов

#[derive(Debug)]
enum Coin {
    Penny,
    Nickel,
    Dime,
    Quarter,
}

#[derive(Debug)]
enum UsState {
    Alabama,
    Alaska,
    // ...
}

#[derive(Debug)]
enum Quarter {
    Regular,
    State(UsState), // вариант с вложенными данными
}

// Enum с данными — каждый вариант может иметь свою структуру
#[derive(Debug)]
enum Message {
    Quit,                       // без данных
    Move { x: i32, y: i32 },   // именованные поля (как struct)
    Write(String),              // одно значение
    ChangeColor(i32, i32, i32), // кортеж
}

// Option<T> — стандартный enum для nullable значений
// enum Option<T> {
//     None,     // нет значения
//     Some(T),  // есть значение
// }
// Option не нужно путать с null — это типобезопасный способ
// обработки отсутствия значения

fn main() {
    // === Enum Values ===
    let coin = Coin::Penny;
    let value = value_in_cents(coin);
    println!("Монета: {value} центов");

    // === Match с Quarter ===
    let quarter = Quarter::State(UsState::Alaska);
    let cents = quarter_value_in_cents(&quarter);
    println!("Стоимость quarter: {cents} центов");

    // === If Let — сокращённый синтаксис для одного варианта ===
    let coin = Coin::Nickel;
    if let Coin::Nickel = coin {
        println!("Это пятицентовик!");
    }

    // === Option<T> ===
    let some_number: Option<i32> = Some(5);
    let absent_number: Option<i32> = None;

    // Безопасная работа с Option через match
    match some_number {
        Some(x) => println!("Число: {x}"),
        None => println!("Нет числа"),
    }

    // unwrap — получить значение (panic! если None)
    // unwrap_or — значение по умолчанию при None
    let x = some_number.unwrap_or(0);
    println!("unwrap_or: {x}");
}

fn value_in_cents(coin: Coin) -> u8 {
    match coin {
        Coin::Penny => 1,
        Coin::Nickel => 5,
        Coin::Dime => 10,
        Coin::Quarter => 25,
    }
}

fn quarter_value_in_cents(quarter: &Quarter) -> u8 {
    match quarter {
        Quarter::Regular => 25,
        Quarter::State(state) => {
            println!("Квартал штата {state:?}");
            25
        }
    }
}

// Методы можно определять и на enum
impl Message {
    fn call(&self) {
        println!("Вызов message: {self:?}");
    }
}
