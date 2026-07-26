use starter::{
    Student, adjust_scores, best_score_per_student, flatten_attempts, select_for_final_project,
    total_completed_modules,
};

fn sample_students() -> Vec<Student> {
    vec![
        Student::new("Mira", 6, 89.5, true),
        Student::new("Oleg", 4, 91.0, true),
        Student::new("Timur", 7, 94.0, true),
        Student::new("Lena", 6, 72.0, false),
    ]
}

#[test]
fn selects_students_for_final_project() {
    let students = sample_students();
    let selected = select_for_final_project(&students, 85.0);
    let names: Vec<&str> = selected
        .iter()
        .map(|student| student.name.as_str())
        .collect();

    assert_eq!(names, vec!["Mira", "Timur"]);
}

#[test]
fn counts_total_completed_modules() {
    let students = sample_students();

    assert_eq!(total_completed_modules(&students), 23);
}

#[test]
fn adjusts_scores_with_closure() {
    let adjusted = adjust_scores(&[2, 3, 4], |score| (score + 1).min(5));

    assert_eq!(adjusted, vec![3, 4, 5]);
}

#[test]
fn flattens_nested_attempts() {
    let attempts = vec![vec![3, 4], vec![5], vec![], vec![2, 5]];

    assert_eq!(flatten_attempts(&attempts), vec![3, 4, 5, 2, 5]);
}

#[test]
fn finds_best_score_per_student() {
    let attempts = vec![vec![3, 4], vec![], vec![5, 4, 5], vec![2]];

    assert_eq!(
        best_score_per_student(&attempts),
        vec![Some(4), None, Some(5), Some(2)]
    );
}
