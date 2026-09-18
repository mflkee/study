//! Состояние 24 каналов модуля.
//!
//! # Физика против логики
//!
//! **Физически**: каждый канал — это клемма, на которую заходит токовая
//! петля 4–20 мА. Через измерительный шунт сигнал попадает на один из трёх
//! 8-канальных АЦП, а «сырой» код прошивка превращает в миллиамперы с
//! помощью калибровки ([`crate::calib`]).
//!
//! **Логически**: модуль (а вслед за ним и ИВК) видит 24 «канала» с двумя
//! свойствами у каждого: измеренный ток и флаг включённости. Флаг — это
//! тот же `COIL` из Modbus: ИВК может выключить канал, не трогая прошивку.
//!
//! Этот модуль — **просто структуры данных**: хранит значения и отвечает на
//! вопросы «что по каналу 5?». Он не знает ни про АЦП, ни про сеть.

use crate::calib::Calibration;

/// Сколько физических входов у модуля (24 токовые петли: 3 АЦП × 8 каналов).
pub const CHANNEL_COUNT: usize = 24;

/// Один канал: текущее измерение + флаг включённости.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ChannelState {
    /// Включён ли канал. `false` — канал «отключён» (coil = 0):
    /// прошивка продолжает мерить, но ИВК такого канала «не видит»
    /// (FC04 возвращает 0.0).
    pub enabled: bool,
    /// Последний усреднённый ток (мА) после калибровки.
    pub current_ma: f32,
}

impl Default for ChannelState {
    fn default() -> Self {
        Self {
            enabled: true,   // по умолчанию все каналы включены
            current_ma: 0.0, // ещё не измеряли
        }
    }
}

/// Банк из 24 каналов: измерения + калибровки под каждый канал.
///
/// Это общая «витрина», которой пользуются все потоки прошивки:
/// сканер **пишет** сюда токи, a RTU-сервер **читает** их для ответов
/// ИВК (см. `ai32/fw/src/main.rs` — там банк обёрнут в `Arc<RwLock<_>>`,
/// чтобы несколько потоков могли безопасно делить его).
#[derive(Debug, Clone)]
pub struct ChannelBank {
    pub channels: [ChannelState; CHANNEL_COUNT],
    pub calib: [Calibration; CHANNEL_COUNT],
}

impl Default for ChannelBank {
    fn default() -> Self {
        Self {
            channels: [ChannelState::default(); CHANNEL_COUNT],
            // Первая калибровка для всех каналов — идеальная «с завода».
            calib: [Calibration::ideal(); CHANNEL_COUNT],
        }
    }
}

impl ChannelBank {
    /// Записывает результат одного измерения (усреднённый ток) в канал.
    pub fn set_current(&mut self, ch: usize, current_ma: f32) {
        // get_mut + if let — «борьба с паникой через Option»: индекс за
        // пределами не роняет программу, а просто ничего не делает.
        if let Some(c) = self.channels.get_mut(ch) {
            c.current_ma = current_ma;
        }
    }

    /// Ток канала для ИВК: `Some(мА)`, если канал включён; `None` —
    /// если выключен (или канала нет вообще).
    ///
    /// Вызвающий (карта регистров) решает, что делать с `None` — например,
    /// отдать `0.0` в ответ на FC04 (см. `register_map::read_input`).
    pub fn current_of(&self, ch: usize) -> Option<f32> {
        let c = self.channels.get(ch)?; // ? извлечёт из Option → None вернётся сразу
        if c.enabled {
            Some(c.current_ma)
        } else {
            None
        }
    }

    /// Включает/выключает канал. Возвращает `false`, если канал не найден.
    /// Это логика Coil-записи FC05: адрес пришёл по сети, проверили границы.
    pub fn set_enabled(&mut self, ch: usize, enabled: bool) -> bool {
        match self.channels.get_mut(ch) {
            Some(c) => {
                c.enabled = enabled;
                true
            }
            None => false,
        }
    }

    /// Прямой доступ к коэффициентам калибровки канала.
    pub fn calibration(&self, ch: usize) -> Option<Calibration> {
        self.calib.get(ch).copied()
    }

    /// Заменяет калибровку канала (используется при загрузке из NVS).
    pub fn set_calibration(&mut self, ch: usize, cal: Calibration) -> bool {
        match self.calib.get_mut(ch) {
            Some(c) => {
                *c = cal;
                true
            }
            None => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_channels_exist_and_default_enabled() {
        // Главный инвариант: каналов ровно 24 и они по умолчанию включены.
        let bank = ChannelBank::default();
        assert_eq!(bank.channels.len(), 24);
        assert!(bank.channels.iter().all(|c| c.enabled));
    }

    #[test]
    fn disabled_channel_hides_current() {
        // Выключили канал — ИВК больше не увидит его ток через current_of.
        let mut bank = ChannelBank::default();
        bank.set_current(5, 12.5);
        assert_eq!(bank.current_of(5), Some(12.5));

        assert!(bank.set_enabled(5, false));
        assert_eq!(bank.current_of(5), None);

        // Включили обратно — старое измерение «вернулось» (оно же не стёрлось).
        assert!(bank.set_enabled(5, true));
        assert_eq!(bank.current_of(5), Some(12.5));
    }

    #[test]
    fn bounds_are_checked() {
        // Канала 24 не существует (индексы 0..23) — и это не паника, а false.
        let mut bank = ChannelBank::default();
        assert!(!bank.set_enabled(24, false));
        assert!(!bank.set_enabled(usize::MAX, false));
        assert_eq!(bank.current_of(24), None);
    }

    #[test]
    fn calibration_per_channel_is_independent() {
        // Калибровка у каждого канала своя: поменяли один — другие целы.
        let mut bank = ChannelBank::default();
        let special = Calibration::from_points(1_000.0, 2_000.0).unwrap();
        assert!(bank.set_calibration(3, special));
        assert_eq!(bank.calibration(3).unwrap().gain, special.gain);
        assert_eq!(bank.calibration(4).unwrap().gain, Calibration::ideal().gain);
    }
}