fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
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

    assert_eq!(title, placeholder::<&str>());
}

#[test]
fn match_can_return_different_values_for_variants() {
    let result = event_points(StudyEvent::Submit { passed: true });

    assert_eq!(result, placeholder::<u16>());
}

#[test]
fn tuple_like_variants_can_be_unpacked() {
    let break_minutes = StudyEvent::Break(15);

    let extracted = match break_minutes {
        StudyEvent::Break(minutes) => minutes,
        _ => 0,
    };

    assert_eq!(extracted, placeholder::<u16>());
}

#[test]
fn ranges_work_in_match_arms() {
    assert_eq!(score_label(4), placeholder::<&str>());
}
