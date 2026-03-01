// Exercise 01: Vec as Stack
// Topic: Vec::push, Vec::pop, while let

fn main() {
    println!("Exercise 01: vec stack");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let stack = build_stack(vec![10, 20, 30, 40]);
    // let popped = pop_all(stack);
    // println!("{popped:?}");
    // Expected: [40, 30, 20, 10]
}

/// TODO:
/// 1) Add all values from `input` to stack.
/// 2) Return resulting stack.
fn build_stack(input: Vec<i32>) -> Vec<i32> {
    let _ = input;
    // Write your code below this line.
    let stack: Vec<i32> = Vec::new();
    stack
}

/// TODO:
/// 1) Pop all values from stack.
/// 2) Save popped values in order.
/// 3) Return them as Vec<i32>.
fn pop_all(mut stack: Vec<i32>) -> Vec<i32> {
    let _ = &mut stack;
    // Write your code below this line.
    let popped: Vec<i32> = Vec::new();
    popped
}
