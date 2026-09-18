//! Мини-полигон: кнопка + светодиод по Modbus RTU.
//!
//! # Как это работает
//!
//! ```text
//!   кнопка ─┐  Debounce ─┐                    ┌── RTU slave (UART0)
//!           ▼            ▼                   ▼
//! GPIO0 ──► Button ──► state.button ◄──┐  state (Arc<RwLock>)
//! GPIO1 ◄─ Led      state.led  ────────┼── RTU slave пишет LED
//!                                      │   (FC05) и читает кнопку (FC01)
//! счётчик нажатий: state.presses ──────┘   (FC03)
//! ```
//!
//! Единственный «писатель» состояния — главный цикл (он же и рулит LED,
//! синхронизируя его с битом `state.led`, которым управляет Modbus).
//! RTU-поток только читает/пишет `state` под `RwLock`.
//!
//! # Как собрать и прошить
//!
//! ```text
//! cd fw
//! cargo +esp build --release
//! esptool write_flash 0x10000 target/xtensa-esp32s3-espidf/release/mini-fw
//! ```
//!
//! Затем на ПК: `python3 scripts/mini_client.py /dev/ttyUSB0`.

#![allow(clippy::single_component_path_imports)]

mod hw;
mod rtu;

use std::sync::{Arc, RwLock};

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;

// Биндинги C-функций ESP-IDF: без `use esp_idf_sys as _` прошивка не соберётся.
use esp_idf_sys as _;

use mini_core::debounce::Debounce;
use mini_core::MiniState;

/// Период опроса кнопки (мс). 20 мс — разумный компромисс между
/// отзывчивостью и временем, которое тратится на GPIO-чтения.
const POLL_MS: u32 = 20;

/// Точка входа прошивки (обычный `main`, как в std-программе).
fn main() -> anyhow::Result<()> {
    // Обязательная «пришивка» патчей времени ESP-IDF.
    esp_idf_sys::link_patches();

    // Глобальный уровень логов.
    unsafe {
        esp_idf_sys::esp_log_level_set(
            c"*".as_ptr(),
            esp_idf_sys::esp_log_level_t_ESP_LOG_INFO,
        );
    }

    log::info!("{}", "=".repeat(60));
    log::info!("Мини-полигон: кнопка + светодиод по Modbus RTU");
    log::info!("{}", "=".repeat(60));

    // Peripherals::take() — единственный вход к периферии. Повторный вызов — None.
    let peripherals = Peripherals::take().unwrap();

    // --- Общее состояние (читает Modbus-поток, пишет главный цикл) ---
    let state = Arc::new(RwLock::new(MiniState::default()));

    // --- Светодиод (GPIO1) и кнопка (GPIO0, BOOT) ---
    // Здесь ставим пинпоинты под плату-кандидата; если LED светит на другой
    // ноге — поменяйте gpio1 на свой номер. GPIO0 у DevKitC-1 — это BOOT.
    let mut led = hw::Led::new(peripherals.pins.gpio1)?;
    let button = hw::Button::new(peripherals.pins.gpio0)?;

    // Стартовый «проблеск»: 3 коротких вспышки = живой признак загрузки.
    for _ in 0..3 {
        led.toggle();
        FreeRtos::delay_ms(120);
        led.toggle();
        FreeRtos::delay_ms(120);
    }

    // --- RTU slave по UART0 (USB-UART мост платы): GPIO43 TX, GPIO44 RX ---
    rtu::spawn(
        state.clone(),
        peripherals.uart0,
        peripherals.pins.gpio43, // TX → мост USB-UART
        peripherals.pins.gpio44, // RX ← мост USB-UART
    )?;

    log::info!("RTU slave на UART0 (9600 8N1), slave id 1");

    // --- Главный цикл: опрос кнопки + синхронизация светодиода ---
    let mut debounce = Debounce::new(button.is_pressed());
    let mut prev_pressed = false;

    loop {
        // 1) Читаем кнопку и фильтруем дребезг.
        let pressed = debounce.feed(button.is_pressed());

        // 2) Логируем фронт «нажали» и инкрементируем счётчик.
        if pressed && !prev_pressed {
            log::info!("кнопка нажата!");
            state.write().unwrap().presses += 1;
        }
        prev_pressed = pressed;

        // 3) Пишем состояние кнопки и применяем бит светодиода из Modbus.
        {
            let mut s = state.write().unwrap();
            s.button = pressed;
            if s.led {
                led.on();
            } else {
                led.off();
            }
        }

        FreeRtos::delay_ms(POLL_MS);
    }
}