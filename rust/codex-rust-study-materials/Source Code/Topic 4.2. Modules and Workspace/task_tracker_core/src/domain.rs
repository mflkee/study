#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Status {
    Todo,
    Done,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Task {
    title: String,
    status: Status,
}

impl Task {
    pub fn new(title: &str) -> Self {
        Self {
            title: title.to_string(),
            status: Status::Todo,
        }
    }

    pub fn mark_done(&mut self) {
        self.status = Status::Done;
    }

    pub fn title(&self) -> &str {
        &self.title
    }

    pub fn status(&self) -> &Status {
        &self.status
    }
}

#[cfg(test)]
mod tests {
    use super::{Status, Task};

    #[test]
    fn marks_task_done() {
        let mut task = Task::new("Изучить workspace");
        task.mark_done();

        assert_eq!(task.status(), &Status::Done);
    }
}
