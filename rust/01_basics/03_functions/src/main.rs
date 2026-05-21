// Функции и выражения
// https://doc.rust-lang.org/book/ch03-03-how-functions-work.html
// Функции объявляются через fn, параметры с типами, возвращаемое значение после ->

fn main() {
    // Вызов функции с параметрами
    print_labeled_value(5, 'h');

    // Выражение (expression) vs инструкция (statement)
    // Инструкция — действие (let x = ...), не возвращает значение
    // Выражение — вычисляет значение, не имеет точки с запятой
    let y = {
        let x = 3;
        x + 1 // выражение — это значение блока
    };
    println!("Значение y: {y}");

    // Функция с возвращаемым значением
    let result = plus_one(5);
    println!("plus_one(5) = {result}");

    // Несколько возвращаемых значений через кортеж
    let (len, sum) = analyze_array(&[1, 2, 3, 4, 5]);
    println!("Массив: len={len}, sum={sum}");
}

// Функция с двумя параметрами, без возвращаемого значения
fn print_labeled_value(value: i32, unit_label: char) {
    println!("Измерение: {value}{unit_label}");
}

// Функция с возвращаемым значением (-> i32)
// Последнее выражение в теле — это возвращаемое значение
fn plus_one(x: i32) -> i32 {
    x + 1
    // return x + 1; // явный return (не нужен в конце функции)
}

// Функция возвращает кортеж (два значения)
fn analyze_array(arr: &[i32]) -> (usize, i32) {
    let len = arr.len();
    let sum: i32 = arr.iter().sum();
    (len, sum) // возвращаем кортеж
}
