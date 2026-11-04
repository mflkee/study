// Упражнение 12: топ-N по частоте
// Тема: HashMap -> Vec -> сортировка -> truncate
// Считаем частоты слов и выводим N самых частых.

use std::collections::HashMap;

/// Считает частоты слов в тексте (регистр не важен).
fn frequencies(text: &str) -> HashMap<String, usize> {
    let mut freq = HashMap::new();
    for word in text.split_whitespace() {
        *freq.entry(word.to_lowercase()).or_insert(0) += 1;
    }
    freq
}

/// Возвращает N самых частых слов. При равной частоте — по алфавиту,
/// чтобы результат был детерминированным (порядок обхода HashMap случаен).
fn top_n(freq: &HashMap<String, usize>, n: usize) -> Vec<(String, usize)> {
    let mut pairs: Vec<_> = freq.iter().map(|(w, &c)| (w.clone(), c)).collect();
    pairs.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    pairs.truncate(n);
    pairs
}

fn main() {
    println!("Упражнение 12: топ-N слов");

    let text = "rust map rust vec rust map iter vec rust";
    let freq = frequencies(text);

    for (i, (word, count)) in top_n(&freq, 3).iter().enumerate() {
        println!("{}. {word} — {count}", i + 1);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn counts_words_case_insensitively() {
        let f = frequencies("Rust rust RUST vec");
        assert_eq!(f["rust"], 3);
        assert_eq!(f["vec"], 1);
    }

    #[test]
    fn top_n_orders_by_count_then_name() {
        let f = frequencies("b a b a c");
        let top = top_n(&f, 2);
        // a=2, b=2, c=1 -> при равной частоте a идёт раньше b
        assert_eq!(top, vec![("a".to_string(), 2), ("b".to_string(), 2)]);
    }

    #[test]
    fn top_n_more_than_available() {
        let f = frequencies("one two");
        assert_eq!(top_n(&f, 10).len(), 2);
    }
}
