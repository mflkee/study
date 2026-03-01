// Exercise 06: UTF-8 Trap
// Topic: bytes vs chars in Rust strings

fn main() {
    println!("Exercise 06: utf8 trap");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let s = "Hello";
    // println!("{:?}", first_char(s)); // Some('H')
    // println!("{:?}", last_char(s));  // Some('o')
    // println!("chars: {}", char_count(s)); // 5
    // println!("bytes: {}", byte_count(s)); // 5
    //
    // let ru = "Привет";
    // println!("chars: {}", char_count(ru));
    // println!("bytes: {}", byte_count(ru));
}

/// TODO:
/// Return first Unicode character.
fn first_char(s: &str) -> Option<char> {
    let _ = s;
    // Write your code below this line.
    None
}

/// TODO:
/// Return last Unicode character.
fn last_char(s: &str) -> Option<char> {
    let _ = s;
    // Write your code below this line.
    None
}

/// TODO:
/// Count Unicode characters (not bytes).
fn char_count(s: &str) -> usize {
    let _ = s;
    // Write your code below this line.
    0
}

/// TODO:
/// Count bytes in UTF-8 string.
fn byte_count(s: &str) -> usize {
    let _ = s;
    // Write your code below this line.
    0
}
