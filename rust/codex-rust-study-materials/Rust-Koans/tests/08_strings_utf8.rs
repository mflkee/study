fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

#[test]
fn utf8_string_has_different_bytes_and_chars_counts() {
    let word = "Привет";

    assert_eq!(word.len(), placeholder::<usize>());
    assert_eq!(word.chars().count(), placeholder::<usize>());
}

#[test]
fn split_whitespace_helps_parse_human_text() {
    let text = "  Rust   makes   bugs  louder ";
    let parts: Vec<&str> = text.split_whitespace().collect();

    assert_eq!(parts, placeholder::<Vec<&str>>());
}

#[test]
fn format_builds_a_new_string() {
    let topic = "borrowing";
    let lesson = 3;
    let line = format!("Lesson {lesson}: {topic}");

    assert_eq!(line, placeholder::<String>());
}

#[test]
fn bytes_expose_raw_utf8_representation() {
    let crab = "🦀";

    assert_eq!(crab.chars().count(), placeholder::<usize>());
    assert_eq!(crab.len(), placeholder::<usize>());
}
