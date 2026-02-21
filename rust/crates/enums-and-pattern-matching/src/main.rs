enum Coin {  // Определение на уровне модуля
    Penny,
    Nickel,
    Dime,
    Quarter,
}

fn value_in_cents(coin: Coin) -> u8 {
    match coin {
        Coin::Penny => 1,
        Coin::Nickel => 5,
        Coin::Dime => 10,
        Coin::Quarter => 25,
    }
}

fn main() {
    let coins = [Coin::Penny, Coin::Nickel, Coin::Dime, Coin::Quarter];
    
    for coin in coins {
        println!("Монета стоит {} центов", value_in_cents(coin));
    }
}
