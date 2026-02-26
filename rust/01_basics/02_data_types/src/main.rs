// Data Types
// https://doc.rust-lang.org/book/ch03-02-data-types.html

fn main() {
    // === Scalar Types ===

    // Integer types
    let decimal: i32 = 97_321;
    let hex: i32 = 0xff;
    let octal: i32 = 0o77;
    let binary: i32 = 0b1111_0000;
    let byte: u8 = b'A';

    println!("Integers: decimal={decimal}, hex={hex}, octal={octal}, binary={binary}, byte={byte}");

    // Floating-point
    let float_32: f32 = 2.0;
    let float_64: f64 = 3.0;

    // Boolean
    let t: bool = true;
    let f: bool = false;

    // Character
    let c: char = 'z';
    let heart: char = '❤';
    let japanese: char = 'あ';
    println!("Chars: {c}, {heart}, {japanese}");

    // === Compound Types ===

    // Tuple (кортеж) - фиксированная длина, разные типы
    let tup: (i32, f64, u8) = (500, 6.4, 1);
    let (x, y, z) = tup; // destructuring
    println!("Tuple: x={x}, y={y}, z={z}");

    // Access by index
    let five_hundred = tup.0;
    let six_point_four = tup.1;

    // Unit type (пустой кортеж)
    let unit: () = ();

    // Array (массив) - фиксированная длина, одинаковые типы
    let arr: [i32; 5] = [1, 2, 3, 4, 5];
    let first = arr[0];
    let second = arr[1];

    // Array с повторением
    let repeated = [3; 5]; // [3, 3, 3, 3, 3]

    println!("Array: first={first}, second={second}, repeated={repeated:?}");

    // Array bounds (выход за границы вызывает panic)
    // let index = 10;
    // let element = arr[index]; // PANIC!
}
