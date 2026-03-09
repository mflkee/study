use std::collections::HashMap;

fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

#[test]
fn vectors_can_grow() {
    let mut modules = vec!["ownership", "traits"];
    modules.push("testing");

    assert_eq!(modules.len(), placeholder::<usize>());
    assert_eq!(modules[2], placeholder::<&str>());
}

#[test]
fn hashmap_stores_key_value_pairs() {
    let mut scores = HashMap::new();
    scores.insert("Mira", 5);
    scores.insert("Oleg", 4);

    assert_eq!(scores.get("Mira"), placeholder::<Option<&i32>>());
}

#[test]
fn map_transforms_every_element() {
    let scores = [2_u8, 3, 4];
    let doubled: Vec<u8> = scores.into_iter().map(|value| value * 2).collect();

    assert_eq!(doubled, placeholder::<Vec<u8>>());
}

#[test]
fn filter_keeps_only_matching_elements() {
    let scores = [2_u8, 5, 4, 5];
    let excellent: Vec<u8> = scores.into_iter().filter(|value| *value == 5).collect();

    assert_eq!(excellent, placeholder::<Vec<u8>>());
}

#[test]
fn fold_accumulates_a_result() {
    let scores = [5_u8, 4, 5];
    let total = scores.into_iter().fold(0_u8, |acc, value| acc + value);

    assert_eq!(total, placeholder::<u8>());
}
