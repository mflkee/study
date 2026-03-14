#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum LogLevel {
    Info,
    Warning,
    Error,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LogEntry {
    pub level: LogLevel,
    pub timestamp: String,
    pub module: String,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LogParseError {
    MissingParts,
    InvalidLevel,
    EmptyModule,
    EmptyMessage,
}

pub fn parse_entry(_line: &str) -> Result<LogEntry, LogParseError> {
    todo!("Разберите строку лога в типизированную структуру")
}

pub fn count_by_level(_entries: &[LogEntry], _level: LogLevel) -> usize {
    todo!("Посчитайте число записей заданного уровня")
}

pub fn modules_with_errors(_entries: &[LogEntry]) -> Vec<String> {
    todo!("Верните список модулей, в которых есть записи уровня Error")
}

pub fn latest_message_for<'a>(_entries: &'a [LogEntry], _module: &str) -> Option<&'a str> {
    todo!("Найдите последнее сообщение для выбранного модуля")
}
