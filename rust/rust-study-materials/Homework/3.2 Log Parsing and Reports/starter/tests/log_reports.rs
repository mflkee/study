use starter::{
    LogLevel, LogParseError, count_by_level, latest_message_for, modules_with_errors, parse_entry,
};

fn sample_entries() -> Vec<starter::LogEntry> {
    vec![
        parse_entry("INFO|2026-03-10T10:00|auth|login ok").unwrap(),
        parse_entry("ERROR|2026-03-10T10:15|auth|invalid token").unwrap(),
        parse_entry("WARNING|2026-03-10T10:20|billing|slow request").unwrap(),
        parse_entry("ERROR|2026-03-10T10:30|storage|disk almost full").unwrap(),
        parse_entry("INFO|2026-03-10T10:35|auth|logout").unwrap(),
    ]
}

#[test]
fn parses_valid_log_entry() {
    let entry = parse_entry("ERROR|2026-03-10T10:15|auth|invalid token").unwrap();

    assert_eq!(entry.level, LogLevel::Error);
    assert_eq!(entry.module, "auth");
}

#[test]
fn rejects_invalid_level() {
    let error = parse_entry("TRACE|2026-03-10T10:15|auth|invalid token").unwrap_err();

    assert_eq!(error, LogParseError::InvalidLevel);
}

#[test]
fn counts_entries_by_level() {
    let entries = sample_entries();

    assert_eq!(count_by_level(&entries, LogLevel::Error), 2);
}

#[test]
fn returns_modules_with_errors() {
    let entries = sample_entries();
    let modules = modules_with_errors(&entries);

    assert_eq!(modules, vec!["auth".to_string(), "storage".to_string()]);
}

#[test]
fn finds_latest_message_for_module() {
    let entries = sample_entries();

    assert_eq!(latest_message_for(&entries, "auth"), Some("logout"));
    assert_eq!(latest_message_for(&entries, "payments"), None);
}
