//! Урок 04: Structs, Enums, Pattern Matching.
//!
//! Запуск: `cargo run --example 04_structs_enums`
//!
//! Реальный код: `emulator.rs:11-31` (DataType), `emulator.rs:35-51`
//! (VirtualSensor), `frames.rs:18-33` (ModbusError — enum с вариантами).

/// Структура с полями — аналог VirtualSensor (emulator.rs:35-51):
/// для хранения виртуального датчика.
#[derive(Debug, Clone, PartialEq)]
pub struct VirtualSensor {
    pub name: String,
    pub base: f32,
    pub amplitude: f32,
    pub enabled: bool,
}

impl VirtualSensor {
    pub fn new(name: &str, base: f32, amplitude: f32) -> Self {
        Self {
            name: name.to_string(),
            base,
            amplitude,
            enabled: true,
        }
    }

    /// Вычисляем текущее значение (как emulator.rs:54-60).
    fn value(&self, t: f64) -> f32 {
        if !self.enabled {
            return self.base;
        }
        self.base + self.amplitude * t.sin() as f32
    }
}

/// Enum с данными в вариантах — аналог DataType (emulator.rs:11-19).
/// Здесь варианты не несут данных, но могут: `Write(String)`,
/// `Move { x: i32, y: i32 }` — алгебраический тип данных.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SensorKind {
    Temperature,
    Pressure,
    Flow,
}

impl SensorKind {
    pub fn label(self) -> &'static str {
        match self {
            SensorKind::Temperature => "Temperature (°C)",
            SensorKind::Pressure => "Pressure (kPa)",
            SensorKind::Flow => "Flow",
        }
    }
}

/// Pattern matching: извлекаем значения из варианта. Как в
/// frames.rs:37-48 (fc_name) и emulator.rs:22-31 (label).
fn describe(kind: SensorKind) -> &'static str {
    match kind {
        SensorKind::Temperature => "measures °C",
        SensorKind::Pressure => "measures kPa",
        SensorKind::Flow => "measures flow",
    }
}

/// if let / while let: удобно для быстрого доступа к одному варианту.
fn if_let_demo() {
    let sensor_opt: Option<&VirtualSensor> = None;
    // Не матчим все варианты — а берём только нужный.
    if let Some(s) = sensor_opt {
        println!("value = {}", s.value(0.0));
    } else {
        println!("no sensor");
    }
}

fn main() {
    let s = VirtualSensor::new("temp", 20.0, 5.0);
    assert_eq!(s.value(0.0), 20.0); // sin(0) = 0

    let kinds = [SensorKind::Temperature, SensorKind::Pressure];
    for k in kinds {
        match k {
            SensorKind::Temperature => assert_eq!(k.label(), "Temperature (°C)"),
            SensorKind::Pressure => assert_eq!(k.label(), "Pressure (kPa)"),
            SensorKind::Flow => assert_eq!(k.label(), "Flow"),
        }
        let _ = describe(k);
    }

    if_let_demo();
    println!("04_structs_enums: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sensor_value_line() {
        let s = VirtualSensor::new("x", 10.0, 2.0);
        assert_eq!(s.value(0.0), 10.0);
        assert!(s.value(1.57) > 11.99); // sin(~1.57)≈1 → 12
    }

    #[test]
    fn disabled_sensor_constant() {
        let mut s = VirtualSensor::new("x", 10.0, 2.0);
        s.enabled = false;
        assert_eq!(s.value(123.0), 10.0);
    }
}