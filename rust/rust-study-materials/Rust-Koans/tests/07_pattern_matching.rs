fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

#[derive(Debug, PartialEq)]
enum StudyEvent {
    Lesson { title: &'static str, minutes: u16 },
    Break(u16),
    Submit { passed: bool },
}

fn event_points(event: StudyEvent) -> u16 {
    match event {
        StudyEvent::Lesson { minutes, .. } => minutes / 10,
        StudyEvent::Break(_) => 0,
        StudyEvent::Submit { passed: true } => 5,
        StudyEvent::Submit { passed: false } => 1,
    }
}

fn score_label(score: u8) -> &'static str {
    match score {
        5 => "excellent",
        3..=4 => "pass",
        _ => "fail",
    }
}

#[test]
fn match_can_destructure_enum_fields() {
    let event = StudyEvent::Lesson {
        title: "ownership",
        minutes: 40,
    };

    let title = match event {
        StudyEvent::Lesson { title, .. } => title,
        _ => "other",
    };
    let expected_title: &str = todo();

    assert_eq!(title, expected_title);
}

#[test]
fn match_can_return_different_values_for_variants() {
    let result = event_points(StudyEvent::Submit { passed: true });
    let expected_result: u16 = todo();

    assert_eq!(result, expected_result);
}

#[test]
fn tuple_like_variants_can_be_unpacked() {
    let break_minutes = StudyEvent::Break(15);

    let extracted = match break_minutes {
        StudyEvent::Break(minutes) => minutes,
        _ => 0,
    };
    let expected_minutes: u16 = todo();

    assert_eq!(extracted, expected_minutes);
}

#[test]
fn ranges_work_in_match_arms() {
    let expected_label: &str = todo();

    assert_eq!(score_label(4), expected_label);
}
