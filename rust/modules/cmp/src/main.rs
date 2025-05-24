use std::cmp::Ordering;
fn main() {
    let a = 11;
    let b = 12;

    // 1. Cамый ржавый и идиоматичный, match + cmp::Ordering
    match a.cmp(&b) {
        Ordering::Less => println!("Too small!"),
        Ordering::Greater => println!("Too big!"),
        Ordering::Equal => println!("Equal!"),
    };

    // 2. Самый минималистичный и похожий на другие языки, if - else
    if a < b {
        println!("Too small!");
    } else if a > b {
        println!("Too big!");
    } else {
        println!("Equal!");
    }

    // 3. Через match и конструкцию if-else
    match (a, b) {
        (a, b) if a > b => println!("Too big!"),
        (a, b) if a < b => println!("Too small!"),
        _ => println!("eq!"),
    }
    
    // 4. Через match и true
    match true {
        _ if a < b => println!("Too small!"),
        _ if a > b => println!("Too big!"),
        _ => println!("Equal!"),
    }
}
