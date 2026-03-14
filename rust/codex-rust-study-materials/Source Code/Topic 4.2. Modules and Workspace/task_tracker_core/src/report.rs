use crate::{Status, Task};

pub fn render_tasks(tasks: &[Task]) -> String {
    tasks
        .iter()
        .enumerate()
        .map(|(index, task)| {
            let marker = match task.status() {
                Status::Todo => "[ ]",
                Status::Done => "[x]",
            };

            format!("{marker} {index}. {}", task.title())
        })
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod tests {
    use crate::{Task, render_tasks};

    #[test]
    fn renders_task_list() {
        let tasks = vec![Task::new("Разнести проект по crates")];
        let report = render_tasks(&tasks);

        assert!(report.contains("[ ] 0. Разнести проект по crates"));
    }
}
