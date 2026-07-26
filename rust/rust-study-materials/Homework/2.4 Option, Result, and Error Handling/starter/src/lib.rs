#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Priority {
    Low,
    Medium,
    High,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StudyTask {
    pub time: String,
    pub title: String,
    pub priority: Priority,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ScheduleError {
    MissingParts,
    InvalidTime,
    InvalidPriority,
    EmptyTitle,
    DuplicateTime(String),
}

pub fn is_valid_time(_time: &str) -> bool {
    todo!("Проверьте формат времени HH:MM")
}

pub fn parse_task(_line: &str) -> Result<StudyTask, ScheduleError> {
    todo!("Разберите строку формата HH:MM|title|priority")
}

pub struct StudySchedule {
    tasks: Vec<StudyTask>,
}

impl StudySchedule {
    pub fn new() -> Self {
        Self { tasks: Vec::new() }
    }

    pub fn add_from_line(&mut self, _line: &str) -> Result<(), ScheduleError> {
        todo!("Добавьте задачу из строки, не допуская дублирования времени")
    }

    pub fn remove_by_time(&mut self, _time: &str) -> Option<StudyTask> {
        todo!("Удалите задачу по времени")
    }

    pub fn tasks_for_priority(&self, _priority: Priority) -> Vec<&StudyTask> {
        todo!("Верните только задачи заданного приоритета")
    }

    pub fn next_after(&self, _time: &str) -> Option<&StudyTask> {
        todo!("Найдите ближайшую задачу после заданного времени")
    }

    pub fn all(&self) -> &[StudyTask] {
        &self.tasks
    }
}
