use starter::{Priority, ScheduleError, StudySchedule, is_valid_time, parse_task};

#[test]
fn validates_time_format() {
    assert!(is_valid_time("09:30"));
    assert!(!is_valid_time("9:30"));
    assert!(!is_valid_time("24:10"));
}

#[test]
fn parses_valid_task() {
    let task = parse_task("09:30|Прочитать главу|high").unwrap();

    assert_eq!(task.time, "09:30");
    assert_eq!(task.title, "Прочитать главу");
    assert_eq!(task.priority, Priority::High);
}

#[test]
fn rejects_invalid_priority() {
    let error = parse_task("09:30|Прочитать главу|urgent").unwrap_err();

    assert_eq!(error, ScheduleError::InvalidPriority);
}

#[test]
fn rejects_empty_title() {
    let error = parse_task("09:30|   |low").unwrap_err();

    assert_eq!(error, ScheduleError::EmptyTitle);
}

#[test]
fn schedule_rejects_duplicate_time() {
    let mut schedule = StudySchedule::new();
    schedule
        .add_from_line("09:30|Прочитать главу|high")
        .unwrap();

    let error = schedule
        .add_from_line("09:30|Написать конспект|medium")
        .unwrap_err();

    assert_eq!(error, ScheduleError::DuplicateTime("09:30".to_string()));
}

#[test]
fn filters_tasks_by_priority() {
    let mut schedule = StudySchedule::new();
    schedule.add_from_line("09:30|Rust book|high").unwrap();
    schedule.add_from_line("11:00|Break|low").unwrap();
    schedule.add_from_line("12:00|Solve tasks|high").unwrap();

    let titles: Vec<&str> = schedule
        .tasks_for_priority(Priority::High)
        .iter()
        .map(|task| task.title.as_str())
        .collect();

    assert_eq!(titles, vec!["Rust book", "Solve tasks"]);
}

#[test]
fn finds_next_task_after_time() {
    let mut schedule = StudySchedule::new();
    schedule.add_from_line("09:30|Rust book|high").unwrap();
    schedule.add_from_line("11:00|Break|low").unwrap();
    schedule.add_from_line("12:00|Solve tasks|high").unwrap();

    let next = schedule.next_after("10:00").unwrap();
    assert_eq!(next.time, "11:00");
}
