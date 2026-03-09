fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

#[derive(Debug, PartialEq)]
struct Student {
    name: String,
    score: u8,
}

impl Student {
    fn is_excellent(&self) -> bool {
        self.score >= 90
    }
}

#[allow(dead_code)]
#[derive(Debug, PartialEq)]
enum SubmissionStatus {
    Draft,
    Submitted,
    Reviewed,
}

fn status_label(status: SubmissionStatus) -> &'static str {
    match status {
        SubmissionStatus::Draft => "draft",
        SubmissionStatus::Submitted => "submitted",
        SubmissionStatus::Reviewed => "reviewed",
    }
}

#[test]
fn structs_group_related_data() {
    let student = Student {
        name: String::from("Nika"),
        score: 92,
    };

    assert_eq!(student.name, placeholder::<String>());
    assert_eq!(student.is_excellent(), placeholder::<bool>());
}

#[test]
fn enums_model_finite_states() {
    let status = SubmissionStatus::Submitted;

    assert_eq!(status_label(status), placeholder::<&str>());
}

#[test]
fn option_represents_possible_absence_of_value() {
    let next_attempt: Option<u8> = Some(5);

    assert_eq!(next_attempt, placeholder::<Option<u8>>());
}

#[test]
fn if_let_helps_extract_values() {
    let bonus = Some(3);
    let mut result = 10;

    if let Some(value) = bonus {
        result += value;
    }

    assert_eq!(result, placeholder::<i32>());
}
