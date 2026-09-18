//! Обвязка железа: светодиод и кнопка.
//!
//! # Зачем отдельный модуль
//!
//! Весь GPIO-код собран здесь, чтобы `main.rs` читался как «сценарий»:
//! инициализация → цикл опроса. Драйвер света не решает бизнес-логики —
//! только выполняет `on/off`.

use esp_idf_hal::gpio::{Input, InputPin, Output, OutputPin, PinDriver};
use esp_idf_hal::peripheral::Peripheral;
use esp_idf_sys::EspError;

/// Светодиод (активный высокий уровень: на пин выставляем единицу — горит).
pub struct Led<'d, P: OutputPin> {
    pin: PinDriver<'d, P, Output>,
    on: bool,
}

impl<'d, P: OutputPin> Led<'d, P> {
    /// Забирает пин во владение. Двойное использование пина дальше —
    /// ошибка компиляции, а не молчаливый конфликт периферии.
    pub fn new(pin: impl Peripheral<P = P> + 'd) -> Result<Self, EspError> {
        Ok(Self {
            pin: PinDriver::output(pin)?,
            on: false,
        })
    }

    pub fn on(&mut self) {
        if !self.on {
            let _ = self.pin.set_high();
            self.on = true;
        }
    }

    pub fn off(&mut self) {
        if self.on {
            let _ = self.pin.set_low();
            self.on = false;
        }
    }

    /// Переключает состояние. Возвращает новое.
    pub fn toggle(&mut self) -> bool {
        if self.on {
            self.off();
            false
        } else {
            self.on();
            true
        }
    }
}

/// Кнопка (активный низкий: нажата = `0` на пине, подтяжка к питанию).
pub struct Button<'d, P: InputPin> {
    pin: PinDriver<'d, P, Input>,
}

impl<'d, P: InputPin + OutputPin> Button<'d, P> {
    /// Включает внутреннюю подтяжку `Pull::Up` и считает «нажато» при `low`.
    pub fn new(pin: impl Peripheral<P = P> + 'd) -> Result<Self, EspError> {
        let mut driver = PinDriver::input(pin)?;
        driver.set_pull(esp_idf_hal::gpio::Pull::Up)?;
        Ok(Self { pin: driver })
    }

    /// Сырое значение кнопки (без антидребезга — его делает `debounce::Debounce`).
    pub fn is_pressed(&self) -> bool {
        self.pin.is_low()
    }
}