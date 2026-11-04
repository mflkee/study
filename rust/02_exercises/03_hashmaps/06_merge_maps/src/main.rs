// Упражнение 13: объединение карт
// Тема: entry API для слияния + сравнение с extend
// Сливаем две карты частот: значения СКЛАДЫВАЮТСЯ (в отличие от extend,
// который бы просто перезаписал совпадающие ключи).

use std::collections::HashMap;

/// Сливает две карты частот, складывая значения по общим ключам.
fn merge_sum(a: &HashMap<String, u32>, b: &HashMap<String, u32>) -> HashMap<String, u32> {
    let mut out = a.clone();
    for (k, v) in b {
        *out.entry(k.clone()).or_insert(0) += v;
    }
    out
}

fn main() {
    println!("Упражнение 13: объединение карт");

    let chapter1 = HashMap::from([("rust".to_string(), 3), ("borrow".to_string(), 2)]);
    let chapter2 = HashMap::from([("rust".to_string(), 4), ("trait".to_string(), 1)]);

    // 1) слияние со сложением значений
    let total = merge_sum(&chapter1, &chapter2);
    let mut rows: Vec<_> = total.iter().collect();
    rows.sort_by_key(|(w, _)| w.as_str());
    println!("Сумма по главам:");
    for (word, count) in rows {
        println!("  {word}: {count}");
    }

    // 2) extend перезаписывает — сравним поведение
    let mut overwritten = chapter1.clone();
    overwritten.extend(chapter2.clone());
    println!("\nextend (перезапись): rust = {}", overwritten["rust"]); // 4, а не 7

    // 3) инверсия: частота -> список слов (обратный индекс)
    let mut inverted: HashMap<u32, Vec<String>> = HashMap::new();
    for (word, count) in &total {
        inverted.entry(*count).or_default().push(word.clone());
    }
    let mut inv: Vec<_> = inverted.iter().collect();
    inv.sort_by_key(|(count, _)| **count);
    println!("\nОбратный индекс (частота -> слова):");
    for (count, words) in inv {
        let mut words = words.clone();
        words.sort();
        println!("  {count}: {}", words.join(", "));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn map(pairs: &[(&str, u32)]) -> HashMap<String, u32> {
        pairs.iter().map(|(k, v)| (k.to_string(), *v)).collect()
    }

    #[test]
    fn merge_sums_values() {
        let a = map(&[("rust", 3), ("borrow", 2)]);
        let b = map(&[("rust", 4), ("trait", 1)]);
        let merged = merge_sum(&a, &b);
        assert_eq!(merged["rust"], 7);
        assert_eq!(merged["borrow"], 2);
        assert_eq!(merged["trait"], 1);
    }

    #[test]
    fn extend_overwrites_instead_of_summing() {
        let a = map(&[("rust", 3)]);
        let b = map(&[("rust", 4)]);
        let mut m = a;
        m.extend(b);
        assert_eq!(m["rust"], 4);
    }

    #[test]
    fn original_not_modified() {
        let a = map(&[("rust", 3)]);
        let b = map(&[("rust", 4)]);
        let _ = merge_sum(&a, &b);
        assert_eq!(a["rust"], 3);
    }
}
