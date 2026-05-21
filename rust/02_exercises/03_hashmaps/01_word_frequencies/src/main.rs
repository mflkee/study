// Упражнение 08: частотность слов
// Тема: HashMap, entry API (or_insert)
// Строим таблицу частотности слов из текста

use std::collections::HashMap;

fn main() {
    println!("Упражнение 08: частотность слов");

    let text = "hello world hello rust rust rust";
    let freq = word_frequencies(text);
    println!("Частотность: {:?}", freq);

    match most_frequent(&freq) {
        Some((word, count)) => println!("Самое частое слово: '{word}' — {count} раз"),
        None => println!("Текст пуст"),
    }
}

/// Строит таблицу частотности из текста.
/// Разбивает по пробелам, приводит к нижнему регистру, считает вхождения.
fn word_frequencies(text: &str) -> HashMap<String, usize> {
    let mut freq = HashMap::new();
    for word in text.split_whitespace() {
        let lower = word.to_lowercase();
        // entry API: or_insert(0) вставляет 0 если ключа нет,
        // после чего *count += 1 увеличивает счётчик
        *freq.entry(lower).or_insert(0) += 1;
    }
    freq
}

/// Возвращает самое частое слово и его количество (или None).
/// Проходим по HashMap в поисках максимального значения.
fn most_frequent(freq: &HashMap<String, usize>) -> Option<(String, usize)> {
    freq.iter()
        .max_by_key(|(_, &count)| count)
        .map(|(word, &count)| (word.clone(), count))
}
