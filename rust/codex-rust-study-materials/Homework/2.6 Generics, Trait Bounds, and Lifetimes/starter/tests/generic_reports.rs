use starter::{
    ExamResult, HomeworkResult, Scorable, average_score, best_item, longer_title, top_titles,
};

fn homeworks() -> Vec<HomeworkResult> {
    vec![
        HomeworkResult {
            title: "Ownership".to_string(),
            score: 82,
            attempts: 1,
        },
        HomeworkResult {
            title: "Borrowing".to_string(),
            score: 96,
            attempts: 2,
        },
        HomeworkResult {
            title: "Enums".to_string(),
            score: 88,
            attempts: 1,
        },
    ]
}

#[test]
fn finds_best_homework() {
    let items = homeworks();
    let best = best_item(&items).unwrap();

    assert_eq!(best.title(), "Borrowing");
}

#[test]
fn calculates_average_score_for_homeworks() {
    let items = homeworks();
    let average = average_score(&items).unwrap();

    assert!((average - 88.666664).abs() < 0.001);
}

#[test]
fn filters_titles_by_threshold() {
    let items = homeworks();
    let titles = top_titles(&items, 90);

    assert_eq!(titles, vec!["Borrowing"]);
}

#[test]
fn works_with_another_type_implementing_trait() {
    let exams = vec![
        ExamResult {
            title: "Midterm".to_string(),
            score: 71,
            oral_bonus: 3,
        },
        ExamResult {
            title: "Final".to_string(),
            score: 91,
            oral_bonus: 5,
        },
    ];

    let best = best_item(&exams).unwrap();
    assert_eq!(best.title(), "Final");
}

#[test]
fn longer_title_returns_reference_with_correct_lifetime() {
    let left = "Rust";
    let right = "borrow checker";

    assert_eq!(longer_title(left, right), "borrow checker");
}
