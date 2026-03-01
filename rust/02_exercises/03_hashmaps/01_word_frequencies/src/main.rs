// Exercise 08: Word Frequencies
// Topic: HashMap, entry API

use std::collections::HashMap;

fn main() {
    println!("Exercise 08: word frequencies");
    println!("Complete TODO blocks in this file.");

    // Self-check (uncomment after implementation):
    // let text = "hello world hello rust rust rust";
    // let freq = word_frequencies(text);
    // println!("{freq:?}");
    // println!("{:?}", most_frequent(&freq)); // Some(("rust", 3))
}

/// TODO:
/// Build frequency table from text.
/// Start simple: split by whitespace, lowercase each word.
fn word_frequencies(text: &str) -> HashMap<String, usize> {
    let _ = text;
    // Write your code below this line.
    HashMap::new()
}

/// TODO:
/// Return most frequent word and its count.
fn most_frequent(freq: &HashMap<String, usize>) -> Option<(String, usize)> {
    let _ = freq;
    // Write your code below this line.
    None
}
