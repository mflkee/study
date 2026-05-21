// Упражнение 07: Pig Latin (поросячья латынь)
// Тема: chars, срезы, построение строк
// Правила:
// - слово начинается с согласной: "first" -> "irst-fay"
// - слово начинается с гласной:  "apple" -> "apple-hay"

fn main() {
    println!("Упражнение 07: Pig Latin");

    println!("{}", to_pig_latin("first")); // irst-fay
    println!("{}", to_pig_latin("apple")); // apple-hay
    println!("{}", sentence_to_pig_latin("rust is cool"));
}

/// Возвращает true для гласных: a, e, i, o, u (любой регистр)
fn is_vowel(ch: char) -> bool {
    matches!(ch.to_ascii_lowercase(), 'a' | 'e' | 'i' | 'o' | 'u')
}

/// Преобразует одно слово в Pig Latin по правилам выше
fn to_pig_latin(word: &str) -> String {
    match word.chars().next() {
        Some(first_char) if is_vowel(first_char) => {
            // Гласная: слово + "-hay"
            format!("{word}-hay")
        }
        Some(first_char) => {
            // Согласная: переносим первую букву в конец + "ay"
            let rest: String = word.chars().skip(1).collect();
            format!("{rest}-{first_char}ay")
        }
        None => String::new(), // пустая строка
    }
}

/// Преобразует все слова в предложении в Pig Latin.
/// Разделяет по пробелам, преобразует каждое слово, собирает обратно.
fn sentence_to_pig_latin(text: &str) -> String {
    text.split_whitespace()
        .map(to_pig_latin)
        .collect::<Vec<_>>()
        .join(" ")
}
