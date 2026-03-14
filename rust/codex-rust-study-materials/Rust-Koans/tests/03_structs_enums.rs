fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
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
    let expected_name: String = todo();
    let expected_excellent: bool = todo();

    assert_eq!(student.name, expected_name);
    assert_eq!(student.is_excellent(), expected_excellent);
}

#[test]
fn enums_model_finite_states() {
    let status = SubmissionStatus::Submitted;
    let expected_label: &str = todo();

    assert_eq!(status_label(status), expected_label);
}

#[test]
fn option_represents_possible_absence_of_value() {
    let next_attempt: Option<u8> = Some(5);
    let expected_attempt: Option<u8> = todo();

    assert_eq!(next_attempt, expected_attempt);
}

#[test]
fn if_let_helps_extract_values() {
    let bonus = Some(3);
    let mut result = 10;

    if let Some(value) = bonus {
        result += value;
    }
    let expected_result: i32 = todo();

    assert_eq!(result, expected_result);
}
