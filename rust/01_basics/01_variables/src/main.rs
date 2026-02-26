// Variables and Mutability
// https://doc.rust-lang.org/book/ch03-01-variables-and-mutability.html

fn main() {
    // Immutable binding (по умолчанию)
    let x = 5;
    println!("The value of x is: {x}");
    // x = 6; // ERROR: cannot assign twice to immutable variable

    // Mutable binding
    let mut y = 10;
    println!("The value of y is: {y}");
    y = 20;
    println!("The value of y after mutation is: {y}");

    // Constants (константы)
    const MAX_POINTS: u32 = 100_000;
    println!("Max points: {MAX_POINTS}");

    // Shadowing (теневое связывание)
    let z = 5;
    let z = z + 1;
    let z = z * 2;
    println!("The value of z after shadowing is: {z}");

    // Shadowing с изменением типа
    let spaces = "   ";
    let spaces = spaces.len();
    println!("Spaces count: {spaces}");
}
