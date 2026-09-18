//! AI-32 — 24-канальный модуль аналогового ввода 4–20 мА.
//!
//! # Что делает прошивка
//!
//! Когда модуль работает, на входы приходят токовые петли 4–20 мА.
//! Прошивка в цикле:
//!
//! 1. выбирает один из трёх 24-битных АЦП (CS0..CS2) и его канал;
//! 2. снимает с АЦП «сырой» 24-битный код (или берёт его у симулятора);
//! 3. превращает код в миллиамперы через калибровку (`ai32-core`);
//! 4. кладёт результат в общий банк каналов;
//! 5. сервер Modbus RTU по RS-485 отдаёт банк наружу в ИВК.
//!
//! # Потоки
//!
//! ```text
//!   главный  :  выбор АЦП + канала → АЦП → калибровка → bank (цикл)
//!   rtu-slave:  Modbus RTU slave по RS-485 (UART)
//!                банк делят через Arc<RwLock>
//! ```
//!
//! # Сборка (два режима)
//!
//! ```text
//!   cargo +esp build --release            // sim: АЦП имитируется (по умолчанию)
//!   cargo +esp build --release --no-default-features  // real: 3×ADS1256 + SPI
//! ```
//!
//! Режим выбирает фича `sim` (включена по умолчанию). В sim-режиме не нужно
//! железо вообще — можно разрабатывать и отлаживать Modbus-часть на ПК.
//!
//! # Прошивка на устройство
//!
//! ```text
//!   esptool write_flash 0x10000 target/xtensa-esp32s3-espidf/release/ai32-fw
//! ```

#![allow(clippy::single_component_path_imports)]

mod adc_backend;
mod ads1256;
mod config_store;
mod rtu_slave;
mod sim;

use std::sync::{Arc, RwLock};

use ai32_core::channels::CHANNEL_COUNT;

use esp_idf_hal::delay::FreeRtos;
use esp_idf_hal::peripherals::Peripherals;

// Та самая «магия» esp-idf-sys: `use esp_idf_sys as _` подключает биндинги
// C-функций ESP-IDF и линкует их в прошивку. Без этого крейта не собрать.
use esp_idf_sys as _;

use adc_backend::AdcBackend;

// В реальном режиме понадобятся типы GPIO и SPI — импортируем их только там,
// где они используются (иначе в sim-сборке будут «мёртвые» импорты).
#[cfg(not(feature = "sim"))]
use esp_idf_hal::gpio::{AnyOutputPin, PinDriver, Output};
#[cfg(not(feature = "sim"))]
use esp_idf_hal::spi::config::{Config as SpiConfig, DriverConfig as SpiDriverConfig, MODE_1};
#[cfg(not(feature = "sim"))]
use esp_idf_hal::spi::{SpiBusDriver, SpiDriver};
#[cfg(not(feature = "sim"))]
use esp_idf_hal::units::Hertz;

/// Период сканирования всего банка (задержка между проходами), мс.
///
/// 100 мс × 24 канала = полный проход банка каждые ~2.4 c на канал.
/// Дистанционная SCADA обычно опрашивает ИВК 1–5 раз/с — этого достаточно.
const SCAN_PERIOD_MS: u32 = 100;
/// Число выборок АЦП на канал — усредняем для подавления шума и дребезга.
const SAMPLES_PER_CHANNEL: u32 = 8;

/// Создаёт pin-драйвер в режиме Output из любого I/O пина (для CS АЦП).
///
/// Зачем это: `PinDriver::output` требует пины конкретного типа, а у нас —
/// обёртка `Peripherals::take()`. Здесь мы «понижаем» (downgrade) любой пин
/// до `AnyOutputPin` и сразу переводим в режим выхода. Возвращаемый тип
/// `PinDriver<'static, AnyOutputPin, Output>` — «овнер» выхода на всё время
/// работы программы.
#[cfg(not(feature = "sim"))]
fn to_output(
    pin: impl esp_idf_hal::gpio::IOPin,
) -> Result<PinDriver<'static, AnyOutputPin, Output>, esp_idf_sys::EspError> {
    PinDriver::output(esp_idf_hal::gpio::AnyOutputPin::from(pin.downgrade()))
}

/// Точка входа прошивки (это `main`, как в обычной программе на std).
fn main() -> anyhow::Result<()> {
    // Первое, что обязана сделать каждая прошивка на esp-idf-sys — «пришить»
    // патчи времени (ESP-IDF требует свои тайм-функции вместо C stdlib).
    esp_idf_sys::link_patches();

    // Сначала — NVS (флеш-хранилище калибровки), пока не нужен банк.
    config_store::init_nvs_flash();

    // Глобальный уровень логов ESP-IDF (действует на все модули).
unsafe {
            esp_idf_sys::esp_log_level_set(
                c"*".as_ptr(), // вся подсистема логов
                esp_idf_sys::esp_log_level_t_ESP_LOG_INFO,
            );
        }

    log::info!("{}", "=".repeat(64));
    log::info!("AI-32: 24-канальный модуль аналогового ввода 4–20 мА");
    // В каком режиме собрано — видно сразу, чтобы не гадать по железу.
    #[cfg(feature = "sim")]
    log::info!("Режим: СИМУЛЯЦИЯ АЦП (SimAdc) — без железа");
    #[cfg(not(feature = "sim"))]
    log::info!("Режим: реальные 3×ADS1256 (24 бита, общая SPI)");
    log::info!("{}", "=".repeat(64));

    // Peripherals::take() — единственным экземпляром «раздаёт» периферию:
    // SPI, UART, GPIO. Повторный вызов вернёт None — это защита от того,
    // что две части кода схватят одно и то же железо.
    let peripherals = Peripherals::take().unwrap();

    // --- Хранилище калибровки (NVS) и разделяемый банк каналов ---
    //
    // Банк — единое «окно данных» для всех потоков. Обёртки:
    //   Arc<T>   — разделяемый указатель: каждый поток держит свою копию
    //              указателя на ОДИН объект (ячейку кучи);
    //   RwLock<T> — выдача доступа «по одному»: много читателей или один
    //              писатель (читает сервер, пишет сканер).
    let store = config_store::CalibStore::open();
    let bank = Arc::new(RwLock::new(ai32_core::ChannelBank::default()));
    {
        // Грузим сохранённую калибровку по каждому каналу в банк один раз.
        let mut b = bank.write().unwrap();
        for ch in 0..CHANNEL_COUNT as u8 {
            let cal = store.calibration(ch);
            b.set_calibration(ch as usize, cal);
        }
    }

    // --- Источник кодов АЦП ---
    //
    // Box<dyn AdcBackend> — динамическая абстракция «что угодно, умеющее
    // выдавать код АЦП». В sim-сборке — SimAdc (синусоиды), иначе — блок
    // из трёх реальных ADS1256 на общей шине SPI (CS0..CS2).
    #[cfg(feature = "sim")]
    let mut adc: Box<dyn AdcBackend> = Box::new(sim::SimAdc::new());

    // Реальная инициализация SPI-шины и трёх АЦП (только не-sim).
    #[cfg(not(feature = "sim"))]
    let mut adc: Box<dyn AdcBackend> = {
        // Общая SPI-шина SPI2: SCLK/MOSI/MISO без аппаратного CS — выбор
        // кристалла делаем тремя ногами CS0..CS2 (см. ads1256.rs).
        let spi: SpiDriver<'static> = SpiDriver::new(
            peripherals.spi2,
            peripherals.pins.gpio12,             // SCLK
            peripherals.pins.gpio11,             // MOSI
            Some(peripherals.pins.gpio13),       // MISO
            &SpiDriverConfig::new(),
        )?;

        // Драйвер «по шине»: те же такты/режим, что у ADS1256 (Mode 1).
        let bus: SpiBusDriver<'static, SpiDriver<'static>> = SpiBusDriver::new(
            spi,
            &SpiConfig::new()
                .baudrate(Hertz(1_000_000))
                .data_mode(MODE_1),
        )?;

        // Три CS-ноги — по одной на каждый кристалл ADS1256 (активный низкий).
        let cs_pins = [
            to_output(peripherals.pins.gpio10)?, // CS0 — чип #0 (каналы 0..7)
            to_output(peripherals.pins.gpio9)?,  // CS1 — чип #1 (каналы 8..15)
            to_output(peripherals.pins.gpio8)?,  // CS2 — чип #2 (каналы 16..23)
        ];

        Box::new(ads1256::Ads1256::new(bus, cs_pins)?)
    };

    // --- Последовательный интерфейс ---
    //
    // `spawn` поднимает фоновый поток (`rtu-slave`), который сам живёт и умрёт
    // только с устройством. Банк ему отдаётся «клонированием Arc» — тот же
    // объект, то же состояние.
    rtu_slave::spawn(
        bank.clone(),
        peripherals.uart0,
        peripherals.pins.gpio43, // TX → шина RS-485 (через MAX3485)
        peripherals.pins.gpio44, // RX ← шина RS-485
    )?;

    log::info!("Отдаю данные в ИВК по Modbus RTU (RS-485)");

    // --- Главный цикл сканирования ---
    //
    // Единственный «писатель» банка: ходит по всем 24 каналам, снимает код,
    // калибрует и сохраняет в банк. Между циклами спит `SCAN_PERIOD_MS`.
    loop {
        for ch in 0..CHANNEL_COUNT as u8 {
            // Усреднение выборок (подавление шума / дребезга контакта).
            let mut sum = 0u64;
            for _ in 0..SAMPLES_PER_CHANNEL {
                sum += adc.read_code(ch) as u64;
            }
            let raw_avg = sum as f32 / SAMPLES_PER_CHANNEL as f32;

            // Кратковременно захватываем банк «на запись» и пишем ток канала.
            let mut b = bank.write().unwrap();
            let cal = b.calibration(ch as usize).unwrap_or(ai32_core::Calibration::ideal());
            let current_ma = cal.current_ma(raw_avg);
            b.set_current(ch as usize, current_ma);
        }
        FreeRtos::delay_ms(SCAN_PERIOD_MS);
    }
}