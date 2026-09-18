//! Упражнение 09: Сборка — мини-проект "BusLog w/ Result".
//!
//! Задание: объедините struct + enum + VecDeque + Result в кольцо
//! последних транзакций с функциями, как в master.rs (BusLog-логика),
//! но компактно.

use std::collections::VecDeque;
use std::fmt;

/// Запись лога шины.
#[derive(Debug, Clone, PartialEq)]
pub struct Tx {
    pub fc: u8,
    pub ok: bool,
}

/// Кольцо последних N записей.
pub struct BusLog {
    trace: VecDeque<Tx>,
    cap: usize,
}

impl BusLog {
    pub fn with_cap(cap: usize) -> Self {
        Self { trace: VecDeque::new(), cap }
    }

    /// Добавить запись, вытеснив самую старую при переполнении.
    pub fn push(&mut self, tx: Tx) {
        if self.trace.len() >= self.cap {
            self.trace.pop_front();
        }
        self.trace.push_back(tx);
    }

    pub fn len(&self) -> usize {
        self.trace.len()
    }

    pub fn is_empty(&self) -> bool {
        self.trace.is_empty()
    }

    /// Доля успешных транзакций (0.0..=1.0).
    pub fn success_rate(&self) -> f32 {
        if self.trace.is_empty() {
            return 0.0;
        }
        let ok = self.trace.iter().filter(|t| t.ok).count();
        ok as f32 / self.trace.len() as f32
    }
}

/// Кастомная ошибка с реализацией Display.
#[derive(Debug, Clone, PartialEq)]
pub enum BusError {
    Empty,
}

impl fmt::Display for BusError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BusError::Empty => write!(f, "log is empty"),
        }
    }
}

impl std::error::Error for BusError {}

/// Последняя успешная транзакция или BusError::Empty.
pub fn last_ok(log: &BusLog) -> Result<Tx, BusError> {
    log.trace.iter().rev().find(|t| t.ok).cloned().ok_or(BusError::Empty)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn busy_log() -> BusLog {
        let mut log = BusLog::with_cap(3);
        log.push(Tx { fc: 0x03, ok: true });
        log.push(Tx { fc: 0x04, ok: false });
        log.push(Tx { fc: 0x03, ok: true });
        log
    }

    #[test]
    fn ring_buffer_capacity() {
        let mut log = BusLog::with_cap(2);
        for fc in [0x01u8, 0x02, 0x03] {
            log.push(Tx { fc, ok: true });
        }
        assert_eq!(log.len(), 2);
        assert_eq!(log.trace.front().unwrap().fc, 0x02);
    }

    #[test]
    fn success_rate() {
        let log = busy_log();
        assert_eq!(log.success_rate(), 2.0 / 3.0);
    }

    #[test]
    fn last_ok_found() {
        let log = busy_log();
        assert_eq!(last_ok(&log).unwrap(), Tx { fc: 0x03, ok: true });
    }

    #[test]
    fn last_ok_empty() {
        let log = BusLog::with_cap(2);
        assert_eq!(last_ok(&log), Err(BusError::Empty));
    }
}