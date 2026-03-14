use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use starter::{
    Command, Task, TodoError, add_task, format_tasks, load_tasks, mark_done, parse_command,
    remove_task, save_tasks,
};

fn temp_file_path() -> PathBuf {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("time should move forward")
        .as_nanos();

    std::env::temp_dir().join(format!("rust_homework_{nanos}.txt"))
}

#[test]
fn parses_add_command() {
    let args = vec!["add".to_string(), "Изучить".to_string(), "Rust".to_string()];

    assert_eq!(
        parse_command(&args),
        Ok(Command::Add("Изучить Rust".to_string()))
    );
}

#[test]
fn rejects_empty_add_command() {
    let args = vec!["add".to_string()];

    assert_eq!(parse_command(&args), Err(TodoError::EmptyTitle));
}

#[test]
fn adds_and_marks_task_done() {
    let mut tasks = Vec::new();
    add_task(&mut tasks, "Прочитать главу").unwrap();
    mark_done(&mut tasks, 0).unwrap();

    assert_eq!(
        tasks,
        vec![Task {
            title: "Прочитать главу".to_string(),
            done: true
        }]
    );
}

#[test]
fn removes_task_by_index() {
    let mut tasks = vec![Task::new("A"), Task::new("B")];

    let removed = remove_task(&mut tasks, 0).unwrap();

    assert_eq!(removed.title, "A");
    assert_eq!(tasks.len(), 1);
    assert_eq!(tasks[0].title, "B");
}

#[test]
fn saves_and_loads_tasks() {
    let path = temp_file_path();
    let tasks = vec![
        Task::new("Подготовить конспект"),
        Task {
            title: "Написать тесты".to_string(),
            done: true,
        },
    ];

    save_tasks(&path, &tasks).unwrap();
    let loaded = load_tasks(&path).unwrap();
    let _ = std::fs::remove_file(&path);

    assert_eq!(loaded, tasks);
}

#[test]
fn formats_tasks_for_output() {
    let tasks = vec![
        Task::new("Первое"),
        Task {
            title: "Второе".to_string(),
            done: true,
        },
    ];

    let output = format_tasks(&tasks);

    assert!(output.contains("[ ] 0. Первое"));
    assert!(output.contains("[x] 1. Второе"));
}
