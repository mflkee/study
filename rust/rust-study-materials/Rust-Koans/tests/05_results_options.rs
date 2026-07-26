fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

fn parse_score(input: &str) -> Result<u8, std::num::ParseIntError> {
    input.parse::<u8>()
}

fn bonus_point(input: Option<u8>) -> u8 {
    input.map(|value| value + 1).unwrap_or(0)
}

#[test]
fn result_can_be_ok() {
    let parsed = parse_score("42");
    let expected: Result<u8, std::num::ParseIntError> = todo();

    assert_eq!(parsed, expected);
}

#[test]
fn result_can_be_mapped() {
    let parsed = parse_score("7").map(|value| value * 2);
    let expected: Result<u8, std::num::ParseIntError> = todo();

    assert_eq!(parsed, expected);
}

#[test]
fn option_map_changes_inner_value() {
    let value = bonus_point(Some(4));
    let expected: u8 = todo();

    assert_eq!(value, expected);
}

#[test]
fn unwrap_or_provides_a_default() {
    let value = bonus_point(None);
    let expected: u8 = todo();

    assert_eq!(value, expected);
}

#[test]
fn ok_or_converts_option_to_result() {
    let input = Some("rust");
    let converted: Result<&str, &str> = input.ok_or("empty");
    let expected: Result<&str, &str> = todo();

    assert_eq!(converted, expected);
}
