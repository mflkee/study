// Exercise 07: Pig Latin
// Topic: chars, slices, String building

fn main() {
    println!("Exercise 07: pig latin");
    println!("Complete TODO blocks in this file.");

    // Rules:
    // - consonant start: "first" -> "irst-fay"
    // - vowel start: "apple" -> "apple-hay"
    //
    // Self-check (uncomment after implementation):
    // println!("{}", to_pig_latin("first")); // irst-fay
    // println!("{}", to_pig_latin("apple")); // apple-hay
    // println!("{}", sentence_to_pig_latin("rust is cool"));
}

/// TODO:
/// Return true for vowels: a, e, i, o, u (any case).
fn is_vowel(ch: char) -> bool {
    matches!(ch.to_ascii_lowercase(), 'a' | 'e' | 'i' | 'o' | 'u')
}

/// TODO:
/// Convert one word to Pig Latin by rules above.
fn to_pig_latin(word: &str) -> String {
    let _ = word;
    // Write your code below this line.
    String::new()
}

/// TODO:
/// Convert all words in sentence to Pig Latin.
/// Join words with one space.
fn sentence_to_pig_latin(text: &str) -> String {
    let _ = text;
    // Write your code below this line.
    String::new()
}
