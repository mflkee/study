pub fn main() {
    // let s = String::from("hello world");
    let s = String::from("hello!! world!!");
    println!("{s}");

    let word = first_word(&s);
    let word = first_word(s)

    let second_word = second_word(&s);
    println!("{word}");
    println!("{second_word}");
}

fn first_word(s: &str) -> &str {
    let bytes = s.as_bytes();

    for (i, &item) in bytes.iter().enumerate() {
        if item == b' ' {
            return &s[..i];
        }
    }
    s
}

fn second_word(s: &str) -> &str {
    let bytes = s.as_bytes();

    for (i, &item) in bytes.iter().enumerate() {
        if item == b' ' {
            return &s[i + 1..];
        }
    }
    s
}

#[cfg(test)]
mod tests {
    use super::{first_word, second_word};

    #[test]
    fn splits_two_words() {
        let input = "hello world";
        assert_eq!(first_word(input), "hello");
        assert_eq!(second_word(input), "world");
    }

    #[test]
    fn handles_strings_without_spaces() {
        let input = "rustacean";
        assert_eq!(first_word(input), "rustacean");
        assert_eq!(second_word(input), "rustacean");
    }

    #[test]
    fn handles_leading_space() {
        let input = " leading";
        assert_eq!(first_word(input), "");
        assert_eq!(second_word(input), "leading");
    }
}
