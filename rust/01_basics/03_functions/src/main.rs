// Functions and Modules
// https://doc.rust-lang.org/book/ch03-03-how-functions-work.html

fn main() {
    // Function call
    print_labeled_value(5, 'h');

    // Expression vs Statement
    let y = {
        let x = 3;
        x + 1 // expression (без точки с запятой)
    };
    println!("The value of y is: {y}");

    // Function with return value
    let result = plus_one(5);
    println!("plus_one(5) = {result}");

    // Multiple return values via tuple
    let (len, sum) = analyze_array(&[1, 2, 3, 4, 5]);
    println!("Array: len={len}, sum={sum}");
}

fn print_labeled_value(value: i32, unit_label: char) {
    println!("The measurement is: {value}{unit_label}");
}

// Function with return value (-> type)
fn plus_one(x: i32) -> i32 {
    x + 1
    // return x + 1; // explicit return (не нужен в конце)
}

// Function returning multiple values
fn analyze_array(arr: &[i32]) -> (usize, i32) {
    let len = arr.len();
    let sum: i32 = arr.iter().sum();
    (len, sum)
}
