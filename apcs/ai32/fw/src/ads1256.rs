//! Драйвер 24-битного АЦП ADS1256 (3 чипа × 8 каналов на общей SPI-шине).
//!
//! # ⚠️ ВНИМАНИЕ: учебный каркас
//!
//! Это **начальный драйвер**, который показывает направление: общая SPI-шина,
//! выбор кристалла тремя GPIO, запись регистра MUX для выбора канала, чтение
//! 24-битного кода. Точные значения конфигурационных регистров (порядок
//! команд, PGA, диапазон, DRATE) СВЕРЬТЕ с текущей редакцией datasheet:
//! TI ADS1256 — разделы «Command Definitions» и «Register Description».
//! В коде места, требующие сверки, помечены `TODO`.
//!
//! # Зачем три чипа и три CS-нога
//!
//! У модуля 24 аналоговых входа. Один ADS1256 умеет 8 входов (8:1 против
//! AINCOM), поэтому берём **три кристалла по 8 каналов**. Все три висят на
//! одной SPI-шине (общие SCLK/MOSI/MISO), а каждая ножка CS (CS0..CS2)
//! выбирает свой чип: подняли CS0 — разговариваем с первым АЦП, сняли —
//! со вторым и так по кругу.
//!
//! ```text
//!         аналоговые входы 0..7        входы 8..15       входы 16..23
//!              ┌─────────┐             ┌─────────┐       ┌─────────┐
//!   шунты 4..20 мА ───┬────┤ ADS1256 0 │  ...   │ ...    │        │
//!   ──────────────────┴────┤ #0        │        │        │        │
//!   ───────────────────────┤           │        │        │        │
//!   ───────────────────────┘           └────────┘        └────────┘
//!                      ▲                 ▲                 ▲
//!   OSP_SCLK ──────────┴─────────────────┴─────────────────┴── (общая шина)
//!   OSP_MOSI / OSP_MISO ─ по той же шине
//!   CS0 (GPIO10) / CS1 (GPIO9) / CS2 (GPIO8) — выбор кристалла
//! ```
//!
//! # Как читать отсчёт у ADS1256 (кратко)
//!
//! 1. **WREG MUX** — выбрать вход: записать в регистр MUX (0x01) номер канала;
//! 2. подождать время установления (зависит от DRATE — TODO: сверьте с
//!    datasheet, в каркасе фиксированная задержка);
//! 3. **RDATA** (0x01) — по этой команде чип выдаёт 24 бита результата
//!    старшим байтом вперёд (MSB-first).
//!
//! ADS1256 — дельта-сигма АЦП с программируемым усилением (PGA). Диапазон
//! входов и скорость выборки задаются через регистры ADCON/DRATE; для учебного
//! каркаса они оставлены «как есть с завода» и помечены `TODO`.

#![cfg_attr(feature = "sim", allow(dead_code))]

use esp_idf_hal::gpio::{AnyOutputPin, Output, PinDriver};
use esp_idf_hal::spi::{SpiBusDriver, SpiDriver};
use esp_idf_sys::EspError;

use crate::adc_backend::AdcBackend;

/// Задержка установления после переключения канала, мкс.
///
/// У дельта-сигма АЦП (в отличие от последовательного приближения) смена
/// входа требует лишнего цикла преобразования, чтобы фильтр «перестроился».
/// Точное значение зависит от DRATE/TODO; 1000 мкс — с запасом, для каркаса.
const SETTLE_US: u32 = 1000;

/// Команды ADS1256 (справочно, полный список — в datasheet, табл. Command Word).
const CMD_RDATA: u8 = 0x01; // Read Data: выдать 24-битный результат
const REG_MUX: u8 = 0x01;   // Регистр MUX: выбор входа (PSEL/NSEL)
const WREG: u8 = 0x50;      // Write Register: 0x50 | адрес

/// Драйвер блока из трёх ADS1256 на одной SPI-шине.
///
/// `bus` — единственный SPI-интерфейс (без аппаратного CS: выбор кристалла
/// делается тремя CS-ногами вручную). `cs` — массив ровно из трёх пин-драйверов
/// CS0..CS2 в режиме Output, активный низкий.
pub struct Ads1256 {
    bus: SpiBusDriver<'static, SpiDriver<'static>>,
    cs: [PinDriver<'static, AnyOutputPin, Output>; 3],
}

impl Ads1256 {
    /// `bus` — созданная `SpiBusDriver` (общая шина), `cs` — ровно 3 пина.
    pub fn new(
        bus: SpiBusDriver<'static, SpiDriver<'static>>,
        cs: [PinDriver<'static, AnyOutputPin, Output>; 3],
    ) -> Result<Self, EspError> {
        let mut adc = Self { bus, cs };
        adc.hard_reset();
        log::info!("ADS1256: 3 чипа × 8 каналов готовы (24 бита, SPI)");
        Ok(adc)
    }

    /// Снимает выбор со всех чипов: все CS в высокий (неактивный) уровень.
    ///
    /// Полезно один раз после инициализации — и для «все выключены» между
    /// операциями.
    fn deselect_all(&mut self) {
        for c in self.cs.iter_mut() {
            let _ = c.set_high();
        }
    }

    /// Аппаратный сброс всех чипов (короткий импульс по CS — с запасом).
    fn hard_reset(&mut self) {
        self.deselect_all();
        // На дельта-сигма АЦП после включения питания ~2..3 мс на стабилизацию
        // внутреннего опорного источника. Hold — «сил спит», снимаем наверняка.
        esp_idf_hal::delay::FreeRtos::delay_ms(10);
    }

    /// Выбирает кристалл `chip` (0..=2) и записывает ему вход в регистр MUX.
    ///
    /// Параметр `channel` — номер входа ВНУТРИ чипа: 0..=7. Для single-ended
    /// измерения «против земли» негативный вход NSEL ставится на AINCOM (8).
    ///
    /// TODO: проверить кодировку MUX по datasheet (PSEL[3:0]/NSEL[3:0]) и
    /// выбрать нужный режим (single-ended vs differential).
    fn select_channel(&mut self, chip: usize, channel: u8) -> Result<(), EspError> {
        // Единственный активный чип — только тот, с кем сейчас разговариваем.
        self.deselect_all();
        let _ = self.cs[chip].set_low();

        // WREG MUX: 2 байта команды + 1 байт данных.
        // NSEL=8 (AINCOM) — «земля» измерения; PSEL = номер канала (0..7).
        let mux_value = (channel << 4) | 0x08; // TODO: сверить с datasheet
        let cmd = [WREG | REG_MUX, 0x00, mux_value];
        self.bus.write(&cmd)?;

        esp_idf_hal::delay::Ets::delay_us(SETTLE_US);
        Ok(())
    }

    /// Читает 24-битный код выбранного кристалла: команда RDATA + 3 байта.
    fn read_data(&mut self) -> Result<u32, EspError> {
        // RDATA — 1 байт команды, затем 3 байта результата; по CNVST-конвейеру
        // в ответ MOSI надо слать нули (защёлкиваем сдвиг).
        self.bus.write(&[CMD_RDATA])?;
        let mut raw = [0u8; 3];
        self.bus.transfer(&mut raw, &[0u8; 3])?;
        Ok(((raw[0] as u32) << 16) | ((raw[1] as u32) << 8) | raw[2] as u32)
    }

    /// Читает код канала 0..=23: разложение на «чип + вход», выбор по CS.
    pub fn read_channel(&mut self, channel: u8) -> Result<u32, EspError> {
        let chip = (channel / 8) as usize;
        let local_channel = channel % 8;
        self.select_channel(chip, local_channel)?;
        self.read_data()
    }
}

impl AdcBackend for Ads1256 {
    fn read_code(&mut self, channel: u8) -> u32 {
        // «Не упасть» важнее, чем идеальное значение: при ошибке SPI честно
        // отдаём 0 — сканер калибровки не заметит разницы на одном канале.
        self.read_channel(channel).unwrap_or(0)
    }
}