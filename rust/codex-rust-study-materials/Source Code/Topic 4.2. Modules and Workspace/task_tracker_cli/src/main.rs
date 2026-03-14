fn main() {
    let mut tasks = vec![
        task_tracker_core::Task::new("Разобрать workspace"),
        task_tracker_core::Task::new("Понять границы модулей"),
    ];
    tasks[1].mark_done();

    println!("{}", task_tracker_core::render_tasks(&tasks));
}
