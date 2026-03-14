fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

#[test]
fn utf8_string_has_different_bytes_and_chars_counts() {
    let word = "Привет";
    let expected_bytes: usize = todo();
    let expected_chars: usize = todo();

    assert_eq!(word.len(), expected_bytes);
    assert_eq!(word.chars().count(), expected_chars);
}

#[test]
fn split_whitespace_helps_parse_human_text() {
    let text = "  Rust   makes   bugs  louder ";
    let parts: Vec<&str> = text.split_whitespace().collect();
    let expected_parts: Vec<&str> = todo();

    assert_eq!(parts, expected_parts);
}

#[test]
fn format_builds_a_new_string() {
    let topic = "borrowing";
    let lesson = 3;
    let line = format!("Lesson {lesson}: {topic}");
    let expected_line: String = todo();

    assert_eq!(line, expected_line);
}

#[test]
fn bytes_expose_raw_utf8_representation() {
    let crab = "🦀";
    let expected_chars: usize = todo();
    let expected_bytes: usize = todo();

    assert_eq!(crab.chars().count(), expected_chars);
    assert_eq!(crab.len(), expected_bytes);
}
