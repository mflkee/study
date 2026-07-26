use std::collections::HashMap;

fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

#[test]
fn vectors_can_grow() {
    let mut modules = vec!["ownership", "traits"];
    modules.push("testing");
    let expected_len: usize = todo();
    let expected_module: &str = todo();

    assert_eq!(modules.len(), expected_len);
    assert_eq!(modules[2], expected_module);
}

#[test]
fn hashmap_stores_key_value_pairs() {
    let mut scores = HashMap::new();
    scores.insert("Mira", 5);
    scores.insert("Oleg", 4);
    let expected_score: Option<&i32> = todo();

    assert_eq!(scores.get("Mira"), expected_score);
}

#[test]
fn map_transforms_every_element() {
    let scores = [2_u8, 3, 4];
    let doubled: Vec<u8> = scores.into_iter().map(|value| value * 2).collect();
    let expected_doubled: Vec<u8> = todo();

    assert_eq!(doubled, expected_doubled);
}

#[test]
fn filter_keeps_only_matching_elements() {
    let scores = [2_u8, 5, 4, 5];
    let excellent: Vec<u8> = scores.into_iter().filter(|value| *value == 5).collect();
    let expected_excellent: Vec<u8> = todo();

    assert_eq!(excellent, expected_excellent);
}

#[test]
fn fold_accumulates_a_result() {
    let scores = [5_u8, 4, 5];
    let total = scores.into_iter().fold(0_u8, |acc, value| acc + value);
    let expected_total: u8 = todo();

    assert_eq!(total, expected_total);
}
