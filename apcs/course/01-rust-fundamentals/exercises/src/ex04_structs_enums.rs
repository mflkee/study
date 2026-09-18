//! Упражнение 04: Structs, Enums, Pattern Matching.
//!
//! Задание: реализуйте enum с данными и функцию-диспетчер над ним
//! (как `DataType` в эмуляторе и `fc_name` в master.rs).

/// # Задание
/// Тип «запрос Modbus» с вариантами, несущими разные данные.
#[derive(Debug, Clone, PartialEq)]
pub enum ReadRequest {
    /// Вариант с полем.
    Registers { start: u16, count: u16 },
    /// Вариант с кортежем.
    Coils(u16, bool),
}

impl ReadRequest {
    /// Распечатать запрос в человекочитаемом виде.
    pub fn describe(&self) -> String {
        match self {
            ReadRequest::Registers { start, count } => {
                format!("registers start={start} count={count}")
            }
            ReadRequest::Coils(addr, state) => {
                format!("coil #{addr} = {state}")
            }
        }
    }
}

/// Структура-аналог VirtualSensor без лишнего.
pub struct Sensor {
    pub name: String,
    pub enabled: bool,
}

impl Sensor {
    pub fn toggle(&mut self) {
        self.enabled = !self.enabled;
    }
}

/// if let: проверить один вариант.
pub fn only_coil(req: &ReadRequest) -> Option<(u16, bool)> {
    if let ReadRequest::Coils(addr, state) = req {
        Some((*addr, *state))
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn describe_registers() {
        let r = ReadRequest::Registers { start: 0x0000, count: 2 };
        assert_eq!(r.describe(), "registers start=0 count=2");
    }

    #[test]
    fn describe_coils() {
        let r = ReadRequest::Coils(5, true);
        assert_eq!(r.describe(), "coil #5 = true");
    }

    #[test]
    fn if_let_pattern() {
        let r = ReadRequest::Coils(1, false);
        assert_eq!(only_coil(&r), Some((1, false)));
        let r2 = ReadRequest::Registers { start: 0, count: 1 };
        assert_eq!(only_coil(&r2), None);
    }

    #[test]
    fn sensor_toggle() {
        let mut s = Sensor { name: "boiler".into(), enabled: true };
        s.toggle();
        assert!(!s.enabled);
    }
}