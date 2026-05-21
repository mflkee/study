// Типы данных в Rust
// https://doc.rust-lang.org/book/ch03-02-data-types.html
// Rust — статически типизированный язык, компилятор выводит типы где возможно.

fn main() {
    // === Скалярные типы (Scalar Types) ===
    // Представляют одно значение: целые числа, плавающая точка, bool, char

    // Целочисленные типы: i8..i128 (знаковые), u8..u128 (беззнаковые)
    let decimal: i32 = 97_321;       // десятичная запись с разделителями
    let hex: i32 = 0xff;             // шестнадцатеричная
    let octal: i32 = 0o77;           // восьмеричная
    let binary: i32 = 0b1111_0000;   // двоичная
    let byte: u8 = b'A';             // байт (только для u8)

    println!("Целые: dec={decimal}, hex={hex}, oct={octal}, bin={binary}, byte={byte}");

    // Числа с плавающей точкой: f32 (32 бита) и f64 (64 бита, по умолчанию)
    let float_32: f32 = 2.0;  // f32
    let float_64: f64 = 3.0;  // f64 — точнее, но медленнее на некоторых архитектурах

    // Логический тип bool: true / false
    let t: bool = true;
    let f: bool = false;

    // Символьный тип char — 4 байта, Unicode
    let c: char = 'z';
    let heart: char = '❤';
    let japanese: char = 'あ';
    println!("Символы: {c}, {heart}, {japanese}");

    // === Составные типы (Compound Types) ===
    // Группируют несколько значений в один тип

    // Кортеж (tuple) — фиксированная длина, элементы могут быть разных типов
    let tup: (i32, f64, u8) = (500, 6.4, 1);
    let (x, y, z) = tup; // деструктуризация (распаковка)
    println!("Кортеж: x={x}, y={y}, z={z}");

    // Доступ по индексу через точку
    let five_hundred = tup.0;
    let six_point_four = tup.1;

    // Unit-тип () — пустой кортеж, "ничего не возвращает"
    let unit: () = ();

    // Массив (array) — фиксированная длина, все элементы одного типа
    // В отличие от Vec, длина известна на этапе компиляции
    let arr: [i32; 5] = [1, 2, 3, 4, 5];
    let first = arr[0];
    let second = arr[1];

    // Массив с повторяющимся значением: [значение; количество]
    let repeated = [3; 5]; // [3, 3, 3, 3, 3]

    println!("Массив: first={first}, second={second}, repeated={repeated:?}");

    // Выход за границы массива вызывает panic! (программа аварийно завершается)
    // let index = 10;
    // let element = arr[index]; // PANIC! индекс за пределами
}
