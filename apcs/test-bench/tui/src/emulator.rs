//! Эмулятор field-устройств: несколько Modbus slave в памяти.
//!
//! Это «сервер генерации данных» для TUI: позволяет создавать устройства
//! и датчики без железа, редактировать регистры и наблюдать поток данных.

use std::collections::BTreeMap;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

/// Тип данных, который регистрирует виртуальный датчик.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DataType {
    TemperatureC,
    PressureKPa,
    Flow,
    Test,
    LevelM,
    HumidityPct,
}

impl DataType {
    pub fn label(&self) -> &'static str {
        match self {
            DataType::TemperatureC => "Temperature (°C)",
            DataType::PressureKPa => "Pressure (kPa)",
            DataType::Flow => "Flow",
            DataType::Test => "Test value",
            DataType::LevelM => "Level (m)",
            DataType::HumidityPct => "Humidity (%)",
        }
    }
}

/// Виртуальный датчик внутри эмулятора.
#[derive(Debug, Clone)]
pub struct VirtualSensor {
    pub name: String,
    pub data_type: DataType,
    /// Базовый уровень (20°C, 100 kPa ...)
    pub base: f32,
    /// Амплитуда синусоиды.
    pub amplitude: f32,
    /// Период в секундах.
    pub period_s: f32,
    /// Адрес в Input Registers (float32 = 2 регистра).
    pub input_reg: u16,
    /// Включён ли датчик.
    pub enabled: bool,
    /// Последнее вычисленное смещение (для отображения).
    pub last_value: f32,
}

impl VirtualSensor {
    fn value(&self, now_secs: f64) -> f32 {
        if !self.enabled {
            return self.base;
        }
        let t = (now_secs / self.period_s as f64) as f32;
        self.base + self.amplitude * t.sin()
    }
}

/// Устройство-эмулятор со своей картой регистров.
#[derive(Debug, Clone)]
pub struct EmuDevice {
    pub slave_id: u8,
    pub name: String,
    pub description: String,
    pub input_regs: BTreeMap<u16, u16>,
    pub holding_regs: BTreeMap<u16, u16>,
    pub coils: BTreeMap<u16, bool>,
    pub discrete: BTreeMap<u16, bool>,
    pub sensors: Vec<VirtualSensor>,
}

impl EmuDevice {
    pub fn new(slave_id: u8, name: &str, description: &str) -> Self {
        Self {
            slave_id,
            name: name.to_string(),
            description: description.to_string(),
            input_regs: BTreeMap::new(),
            holding_regs: BTreeMap::new(),
            coils: BTreeMap::new(),
            discrete: BTreeMap::new(),
            sensors: Vec::new(),
        }
    }

    /// Обновляет значения датчиков в input registers.
    fn tick(&mut self, now_secs: f64) {
        let mut to_update = Vec::new();
        for s in &mut self.sensors {
            let v = s.value(now_secs);
            s.last_value = v;
            to_update.push((s.input_reg, v));
        }
        for (reg, v) in to_update {
            let bits = v.to_bits();
            self.input_regs.insert(reg, (bits >> 16) as u16);
            self.input_regs.insert(reg + 1, (bits & 0xFFFF) as u16);
        }
    }
}

/// Эмулятор в целом: набор устройств.
pub struct Emulator {
    devices: Vec<EmuDevice>,
    last_tick: f64,
}

impl Default for Emulator {
    fn default() -> Self {
        Self::new()
    }
}

impl Emulator {
    pub fn new() -> Self {
        Self {
            devices: Vec::new(),
            last_tick: 0.0,
        }
    }

    /// Добавляет пустое устройство и возвращает его id.
    pub fn add_device(&mut self, slave_id: u8, name: &str, desc: &str) -> usize {
        self.devices.push(EmuDevice::new(slave_id, name, desc));
        self.devices.len() - 1
    }

    /// Удаляет устройство по индексу.
    pub fn remove_device(&mut self, idx: usize) {
        if idx < self.devices.len() {
            self.devices.remove(idx);
        }
    }

    pub fn devices(&self) -> &[EmuDevice] {
        &self.devices
    }

    pub fn device(&self, idx: usize) -> Option<&EmuDevice> {
        self.devices.get(idx)
    }

    pub fn device_mut(&mut self, idx: usize) -> Option<&mut EmuDevice> {
        self.devices.get_mut(idx)
    }

    /// Добавляет виртуальный датчик в устройство.
    pub fn add_sensor(
        &mut self,
        dev_idx: usize,
        name: &str,
        data_type: DataType,
        base: f32,
        amplitude: f32,
        period_s: f32,
    ) -> Result<(), String> {
        // Находим свободную пару input-регистров (по всем устройствам).
        let mut reg = 0u16;
        while self.any_input_reg_in_use(dev_idx, reg) || self.any_input_reg_in_use(dev_idx, reg + 1) {
            reg += 2;
        }
        let sensor = VirtualSensor {
            name: name.to_string(),
            data_type,
            base,
            amplitude,
            period_s: period_s.max(0.1),
            input_reg: reg,
            enabled: true,
            last_value: base,
        };
        let dev = self
            .devices
            .get_mut(dev_idx)
            .ok_or_else(|| "device not found".to_string())?;
        dev.sensors.push(sensor);
        Ok(())
    }

    /// Удаляет виртуальный датчик из устройства по индексу.
    pub fn remove_sensor(&mut self, dev_idx: usize, sensor_idx: usize) -> Result<(), String> {
        let dev = self
            .devices
            .get_mut(dev_idx)
            .ok_or_else(|| "device not found".to_string())?;
        if sensor_idx >= dev.sensors.len() {
            return Err(format!(
                "sensor index {} out of range ({} sensors)",
                sensor_idx,
                dev.sensors.len()
            ));
        }
        dev.sensors.remove(sensor_idx);
        Ok(())
    }

    fn any_input_reg_in_use(&self, dev_idx: usize, reg: u16) -> bool {
        let Some(dev) = self.devices.get(dev_idx) else {
            return false;
        };
        if dev
            .sensors
            .iter()
            .any(|s| s.input_reg == reg || s.input_reg + 1 == reg)
        {
            return true;
        }
        // Регистр занят, если в нём уже есть данные и он кратко 2 (float-сегмент).
        dev.input_regs.contains_key(&reg)
    }

    /// Обновление всех датчиков.
    pub fn tick(&mut self) {
        let now = now_secs();
        if now - self.last_tick < 0.05 {
            return;
        }
        self.last_tick = now;
        for dev in &mut self.devices {
            dev.tick(now);
        }
    }

    // --- обработка Modbus-запросов (как slave) ---

    pub fn read_input_regs(&mut self, slave: u8, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let dev = self
            .devices
            .iter()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        let mut out = Vec::with_capacity(count as usize);
        for i in 0..count {
            let v = dev.input_regs.get(&(start + i)).copied().unwrap_or(0);
            out.push(v);
        }
        Ok(out)
    }

    pub fn read_holding_regs(&mut self, slave: u8, start: u16, count: u16) -> Result<Vec<u16>, u8> {
        let dev = self
            .devices
            .iter()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        let mut out = Vec::with_capacity(count as usize);
        for i in 0..count {
            let v = dev.holding_regs.get(&(start + i)).copied().unwrap_or(0);
            out.push(v);
        }
        Ok(out)
    }

    pub fn write_holding_reg(&mut self, slave: u8, addr: u16, value: u16) -> Result<(), u8> {
        let dev = self
            .devices
            .iter_mut()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        dev.holding_regs.insert(addr, value);
        Ok(())
    }

    pub fn write_holding_regs(
        &mut self,
        slave: u8,
        start: u16,
        values: &[u16],
    ) -> Result<(), u8> {
        let dev = self
            .devices
            .iter_mut()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        for (i, v) in values.iter().enumerate() {
            dev.holding_regs.insert(start + i as u16, *v);
        }
        Ok(())
    }

    pub fn read_coils(&mut self, slave: u8, start: u16, count: u16) -> Result<Vec<bool>, u8> {
        let dev = self
            .devices
            .iter()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        let mut out = Vec::with_capacity(count as usize);
        for i in 0..count {
            out.push(dev.coils.get(&(start + i)).copied().unwrap_or(false));
        }
        Ok(out)
    }

    pub fn write_coil(&mut self, slave: u8, addr: u16, value: bool) -> Result<(), u8> {
        let dev = self
            .devices
            .iter_mut()
            .find(|d| d.slave_id == slave)
            .ok_or(0x02)?;
        dev.coils.insert(addr, value);
        Ok(())
    }
}

pub fn now_secs() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs_f64())
        .unwrap_or(0.0)
}

/// Shared-эмулятор для передачи между потоками.
pub type SharedEmulator = Arc<Mutex<Emulator>>;

/// Создаёт эмулятор «из коробки» с несколькими устройствами.
pub fn default_scenario() -> SharedEmulator {
    let mut emu = Emulator::new();

    // Устройство 1 — насосная станция.
    emu.add_device(
        1,
        "Pump Station",
        "15 thermocouples + 15 pressures + 2 pumps (like ESP32 firmware)", 
    );
    emu.add_sensor(0, "T-101 Inlet", DataType::TemperatureC, 22.0, 4.0, 8.0)
        .unwrap();
    emu.add_sensor(0, "T-202 Outlet", DataType::TemperatureC, 45.0, 2.0, 5.0)
        .unwrap();
    emu.add_sensor(0, "P-101 Discharge", DataType::PressureKPa, 101.325, 3.0, 6.0)
        .unwrap();
    {
        let dev = emu.device_mut(0).unwrap();
        let base = dev.sensors.len() as u16;
        dev.input_regs.insert(base, 0);
        dev.holding_regs.insert(0, 30); // setpoint temp
        dev.holding_regs.insert(1, 10); // timebase
        dev.coils.insert(0, true);
        dev.coils.insert(1, false);
        dev.description.push_str(" | sim");
    }

    // Устройство 2 — Test device.
    emu.add_device(2, "Test Device", "pure test values");
    emu.add_sensor(1, "Sine A", DataType::Test, 50.0, 25.0, 4.0).unwrap();
    emu.add_sensor(1, "Sine B", DataType::Test, 30.0, 10.0, 2.0).unwrap();
    emu.add_sensor(1, "Flow C", DataType::Flow, 100.0, 30.0, 7.0).unwrap();
    {
        let dev = emu.device_mut(1).unwrap();
        dev.input_regs.insert(60, 0);
    }

    // Устройство 3 — электрический котёл (пример другой карты регистров).
    emu.add_device(3, "Electric Boiler", "heater + temp/level sensors");
    emu.add_sensor(2, "B-1 Water temp", DataType::TemperatureC, 60.0, 3.0, 12.0)
        .unwrap();
    emu.add_sensor(2, "B-2 Exhaust temp", DataType::TemperatureC, 180.0, 5.0, 20.0)
        .unwrap();
    emu.add_sensor(2, "L-1 Water level", DataType::LevelM, 1.2, 0.05, 30.0)
        .unwrap();
    {
        let dev = emu.device_mut(2).unwrap();
        dev.holding_regs.insert(0, 70); // setpoint °C
        dev.holding_regs.insert(1, 0); // heater mode
        dev.coils.insert(0, false); // heater
        dev.coils.insert(1, false); // circulation pump
    }

    // Устройство 4 — метеостанция.
    emu.add_device(4, "Weather Station", "wind / humidity / outside temp");
    emu.add_sensor(3, "W wind speed", DataType::Test, 3.0, 2.0, 5.0).unwrap();
    emu.add_sensor(3, "H humidity", DataType::HumidityPct, 60.0, 20.0, 8.0)
        .unwrap();
    emu.add_sensor(3, "T outside", DataType::TemperatureC, 12.0, 8.0, 15.0)
        .unwrap();
    {
        let dev = emu.device_mut(3).unwrap();
        dev.input_regs.insert(80, 0);
        dev.holding_regs.insert(0, 15); // alarm threshold °C
    }

    Arc::new(Mutex::new(emu))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scenario_creates_devices() {
        let emu = default_scenario();
        let e = emu.lock().unwrap();
        assert_eq!(e.devices().len(), 4);
        assert_eq!(e.devices()[0].sensors.len(), 3);
        assert_eq!(e.devices()[1].sensors.len(), 3);
        assert_eq!(e.devices()[2].sensors.len(), 3);
        assert_eq!(e.devices()[3].sensors.len(), 3);
        // Разные slave-адреса.
        let ids: Vec<u8> = e.devices().iter().map(|d| d.slave_id).collect();
        assert_eq!(ids, vec![1, 2, 3, 4]);
    }

    #[test]
    fn remove_sensor_frees_device_list() {
        let mut emu = Emulator::new();
        emu.add_device(1, "D", "");
        emu.add_sensor(0, "A", DataType::Test, 1.0, 0.0, 1.0).unwrap();
        emu.add_sensor(0, "B", DataType::Test, 1.0, 0.0, 1.0).unwrap();
        assert_eq!(emu.devices()[0].sensors.len(), 2);

        emu.remove_sensor(0, 0).unwrap();
        assert_eq!(emu.devices()[0].sensors.len(), 1);
        assert_eq!(emu.devices()[0].sensors[0].name, "B");

        assert!(emu.remove_sensor(0, 5).is_err());
        assert!(emu.remove_sensor(9, 0).is_err());
    }

    #[test]
    fn sensors_write_input_regs() {
        let mut emu = Emulator::new();
        emu.add_device(1, "D", "");
        emu.add_sensor(0, "T1", DataType::TemperatureC, 20.0, 1.0, 1.0).unwrap();
        emu.tick();
        emu.tick();
        let regs = emu.read_input_regs(1, 0, 2).unwrap();
        assert_ne!(regs[0], 0);
        assert!(regs[0] != 0 || regs[1] != 0);
    }

    #[test]
    fn regs_read_write() {
        let mut emu = Emulator::new();
        emu.add_device(3, "D", "");
        emu.write_holding_reg(3, 10, 0xABCD).unwrap();
        assert_eq!(emu.read_holding_regs(3, 10, 1).unwrap(), vec![0xABCD]);
        emu.write_coil(3, 2, true).unwrap();
        assert_eq!(emu.read_coils(3, 2, 1).unwrap(), vec![true]);
    }

    #[test]
    fn missing_device_returns_illegal_address() {
        let mut emu = Emulator::new();
        assert_eq!(emu.read_input_regs(9, 0, 1), Err(0x02));
    }
}