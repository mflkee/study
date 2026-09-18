//! Упражнение 05: Traits + Generics.
//!
//! Задание: определите trait и реализуйте его для нескольких типов,
//! напишите generic-функцию с trait bound (как `SerialPort` и `Readable`
//! в проекте).

use std::fmt;

/// # Задание
/// Trait «может отдать имя» — как helper, который нужен UI-коду
/// для подписи устройств.
pub trait Named {
    fn name(&self) -> String;
}

pub struct Slave {
    pub id: u8,
}

impl Named for Slave {
    fn name(&self) -> String {
        format!("slave-{}", self.id)
    }
}

pub struct Sensor {
    pub kind: &'static str,
    pub address: u16,
}

impl Named for Sensor {
    fn name(&self) -> String {
        format!("sensor-{}@{}", self.kind, self.address)
    }
}

/// Generic-функция с bound `T: Named`.
pub fn describe<T: Named>(v: &T) -> String {
    format!("device: {}", v.name())
}

/// Реализация Display через match (как фреймерные ошибки в frames.rs).
pub enum BusStatus {
    Idle,
    Busy,
}

impl std::fmt::Display for BusStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BusStatus::Idle => write!(f, "idle"),
            BusStatus::Busy => write!(f, "busy"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn slave_named() {
        let s = Slave { id: 4 };
        assert_eq!(s.name(), "slave-4");
    }

    #[test]
    fn generic_describe_works() {
        let s = Sensor { kind: "temp", address: 0x000A };
        assert_eq!(describe(&s), "device: sensor-temp@10");
    }

    #[test]
    fn display_impl() {
        assert_eq!(format!("{}", BusStatus::Busy), "busy");
    }
}