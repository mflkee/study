// Exercise 02: Safe Access to Vec
// Topic: Option, get(), match

fn main() {
    println!("Exercise 02: safe get");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let data = vec![5, 10, 15];
    // println!("{:?}", safe_get(&data, 1)); // Some(10)
    // println!("{:?}", safe_get(&data, 10)); // None
}

/// TODO:
/// Return element by index without panic.
/// Use `get()` and convert `Option<&i32>` to `Option<i32>`.
fn safe_get(data: &[i32], index: usize) -> Option<i32> {
    let _ = data;
    let _ = index;
    // Write your code below this line.
    None
}
