// Упражнение 05: объединение строк
// Тема: String, push_str, format!
// Склеиваем массив слов через запятую с пробелом

fn main() {
    println!("Упражнение 05: объединение строк через запятую");

    let words = ["rust", "is", "fun"];
    let result = join_with_comma(&words);
    println!("Результат: {result}");
    // Ожидаемый вывод: "rust, is, fun"
}

/// Объединяет все слова через разделитель ", ".
/// Пример: ["a", "b", "c"] -> "a, b, c"
fn join_with_comma(words: &[&str]) -> String {
    let mut result = String::new();
    for (i, word) in words.iter().enumerate() {
        if i > 0 {
            result.push_str(", "); // разделитель между словами
        }
        result.push_str(word);
    }
    result
}
