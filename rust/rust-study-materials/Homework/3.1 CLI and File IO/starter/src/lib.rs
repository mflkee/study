use std::fs;
use std::path::Path;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Task {
    pub title: String,
    pub done: bool,
}

impl Task {
    pub fn new(title: &str) -> Self {
        Self {
            title: title.to_string(),
            done: false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    Add(String),
    Done(usize),
    Remove(usize),
    List,
    Help,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TodoError {
    EmptyCommand,
    InvalidCommand,
    EmptyTitle,
    InvalidIndex,
    Io(String),
    Parse(String),
}

pub fn parse_command(_args: &[String]) -> Result<Command, TodoError> {
    todo!("Разберите аргументы командной строки в enum Command")
}

pub fn load_tasks(path: &Path) -> Result<Vec<Task>, TodoError> {
    if !path.exists() {
        return Ok(Vec::new());
    }

    let content = fs::read_to_string(path).map_err(|error| TodoError::Io(error.to_string()))?;
    let _ = content;
    todo!("Прочитайте файл и восстановите список задач")
}

pub fn save_tasks(path: &Path, tasks: &[Task]) -> Result<(), TodoError> {
    let _ = (path, tasks);
    todo!("Сохраните задачи в текстовый файл")
}

pub fn add_task(tasks: &mut Vec<Task>, title: &str) -> Result<(), TodoError> {
    let _ = (tasks, title);
    todo!("Добавьте новую задачу, если заголовок не пустой")
}

pub fn mark_done(tasks: &mut [Task], index: usize) -> Result<(), TodoError> {
    let _ = (tasks, index);
    todo!("Отметьте задачу выполненной по индексу")
}

pub fn remove_task(tasks: &mut Vec<Task>, index: usize) -> Result<Task, TodoError> {
    let _ = (tasks, index);
    todo!("Удалите задачу по индексу")
}

pub fn format_tasks(tasks: &[Task]) -> String {
    let _ = tasks;
    todo!("Сформируйте строку для печати списка задач")
}
