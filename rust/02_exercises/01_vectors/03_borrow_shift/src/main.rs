// Exercise 03: Borrow + Mutate
// Topic: mutable borrow, immutable borrow, slices

fn main() {
    println!("Exercise 03: borrow shift");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let mut values = vec![1, 2, 3, 4];
    // shift_all(&mut values, 10);
    // println!("{values:?}"); // [11, 12, 13, 14]
    // println!("{:?}", first_even(&values)); // Some(12)
}

/// TODO:
/// Add `shift` to every element in `data`.
fn shift_all(data: &mut [i32], shift: i32) {
    let _ = data;
    let _ = shift;
    // Write your code below this line.
}

/// TODO:
/// Return first even number from slice.
fn first_even(data: &[i32]) -> Option<i32> {
    let _ = data;
    // Write your code below this line.
    None
}
