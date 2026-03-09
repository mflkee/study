fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

fn text_len(text: &str) -> usize {
    text.len()
}

fn append_exclamation(text: &mut String) {
    text.push('!');
}

#[test]
fn string_can_be_borrowed_without_moving() {
    let title = String::from("Rust");
    let length = text_len(&title);

    assert_eq!(length, placeholder::<usize>());
    assert_eq!(title, placeholder::<String>());
}

#[test]
fn clone_creates_an_independent_owner() {
    let first = String::from("notes");
    let second = first.clone();

    assert_eq!(first, placeholder::<String>());
    assert_eq!(second, placeholder::<String>());
}

#[test]
fn mutable_reference_allows_change() {
    let mut topic = String::from("borrow");
    append_exclamation(&mut topic);

    assert_eq!(topic, placeholder::<String>());
}

#[test]
fn string_slice_points_to_part_of_string() {
    let phrase = String::from("cargo test");
    let first_word = &phrase[..5];

    assert_eq!(first_word, placeholder::<&str>());
}

#[test]
fn vec_slice_borrows_part_of_vector() {
    let scores = vec![5, 4, 5, 3];
    let best = &scores[..2];

    assert_eq!(best, placeholder::<&[i32]>());
}
