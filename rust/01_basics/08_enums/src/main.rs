// Enums and Pattern Matching
// https://doc.rust-lang.org/book/ch06-00-enums.html

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
    State(UsState),
}

// Enum с данными
#[derive(Debug)]
enum Message {
    Quit,                       // no data
    Move { x: i32, y: i32 },   // struct-like
    Write(String),              // single String
    ChangeColor(i32, i32, i32), // tuple-like
}

// Option<T> - enum для nullable значений
// enum Option<T> {
//     None,
//     Some(T),
// }

fn main() {
    // === Enum Values ===
    let coin = Coin::Penny;
    let value = value_in_cents(coin);
    println!("Coin value: {value} cents");

    // === Match with Quarter ===
    let quarter = Quarter::State(UsState::Alaska);
    let cents = quarter_value_in_cents(&quarter);
    println!("Quarter value: {cents} cents");

    // === If Let ===
    let coin = Coin::Nickel;
    if let Coin::Nickel = coin {
        println!("It's a nickel!");
    }

    // === Option<T> ===
    let some_number: Option<i32> = Some(5);
    let absent_number: Option<i32> = None;

    // Pattern matching with Option
    match some_number {
        Some(x) => println!("Number: {x}"),
        None => println!("No number"),
    }

    // unwrap / unwrap_or
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
            println!("State quarter from {state:?}");
            25
        }
    }
}

// Methods on Message enum
impl Message {
    fn call(&self) {
        println!("Calling message: {self:?}");
    }
}
