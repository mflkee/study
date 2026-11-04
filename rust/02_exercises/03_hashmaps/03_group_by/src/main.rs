// Упражнение 10: группировка по ключу
// Тема: HashMap<K, Vec<V>>, entry API (or_default)
// Один ключ -> список значений: складываем людей по отделам.
// Разбор API — 01_basics/09_collections/HASHMAP.md

use std::collections::HashMap;

/// Группирует пары (имя, отдел) в карту отдел -> [имена].
fn group_by<'a>(people: &[(&'a str, &'a str)]) -> HashMap<&'a str, Vec<&'a str>> {
    let mut groups: HashMap<&str, Vec<&str>> = HashMap::new();
    for &(name, dept) in people {
        // or_default() даёт пустой Vec, в который сразу кладём имя
        groups.entry(dept).or_default().push(name);
    }
    groups
}

/// Тот же результат, но отсортированный для стабильного вывода.
fn sorted_groups<'a>(groups: &HashMap<&'a str, Vec<&'a str>>) -> Vec<(&'a str, Vec<&'a str>)> {
    let mut out: Vec<(&'a str, Vec<&'a str>)> = groups
        .iter()
        .map(|(&dept, names)| {
            let mut names = names.clone();
            names.sort();
            (dept, names)
        })
        .collect();
    out.sort_by_key(|(dept, _)| *dept);
    out
}

fn main() {
    println!("Упражнение 10: группировка по отделам");

    let people = [
        ("Аня", "Разработка"),
        ("Борис", "Разработка"),
        ("Вера", "Маркетинг"),
        ("Глеб", "Разработка"),
        ("Дина", "Поддержка"),
        ("Егор", "Маркетинг"),
    ];

    let groups = group_by(&people);
    for (dept, names) in sorted_groups(&groups) {
        println!("{dept}: {}", names.join(", "));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn groups_by_department() {
        let people = [("Аня", "Dev"), ("Борис", "Dev"), ("Вера", "Ops")];
        let groups = group_by(&people);
        assert_eq!(groups.len(), 2);

        let mut dev = groups["Dev"].clone();
        dev.sort();
        assert_eq!(dev, vec!["Аня", "Борис"]);
        assert_eq!(groups["Ops"], vec!["Вера"]);
    }

    #[test]
    fn empty_input() {
        assert!(group_by(&[]).is_empty());
    }

    #[test]
    fn sorted_output_is_deterministic() {
        let people = [("b", "Z"), ("a", "Z"), ("c", "A")];
        let groups = group_by(&people);
        let sorted = sorted_groups(&groups);
        assert_eq!(sorted[0], ("A", vec!["c"]));
        assert_eq!(sorted[1], ("Z", vec!["a", "b"]));
    }
}
