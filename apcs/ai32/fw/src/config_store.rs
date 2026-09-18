//! Хранение калибровочных коэффициентов в NVS (flash ESP32).
//!
//! # Что такое NVS
//!
//! NVS (Non-Volatile Storage) — встроенная в ESP-IDF «флеш-база» пар
//! «ключ → значение» внутри seed по типу `namespace/key`. Она переживает
//! перезагрузки и служит для хранения настроек: у нас — коэффициенты
//! калибровки каналов. Имена namespace и ключа ограничены **15 символами**.
//!
//! # Формат хранения
//!
//! На каждый канал — пара `(gain, offset)` типа `f32` (та самая
//! `ai32-core::Calibration`). Храним одним blob-блоком:
//! `24 канала × (4+4) байта = 192 байта`:
//!
//! ```text
//! [ch0: gain(4)[offset(4)] [ch1: gain(4)[offset(4)] ... [ch23: ...]
//! ```
//!
//!
//! # Почему напрямую C-функциями
//!
//! Реализация — прямыми C-функциями esp-idf `nvs_*` **без** крейта
//! esp-idf-svc: это наглядный учебный пример работы с FFI-биндингами
//! (`unafe`-блоки, типы `nvs_handle_t`, регистровые коды ошибок), который
//! интереснее, чем «спрятанный» вызов готовой обёртки.

use std::ffi::CString;

use ai32_core::calib::Calibration;

use esp_idf_sys::{
    nvs_close, nvs_commit, nvs_get_blob, nvs_open, nvs_set_blob, nvs_handle_t,
    nvs_open_mode_t_NVS_READWRITE, ESP_ERR_NVS_NOT_FOUND,
};

/// Имя секции в NVS (Max 15 символов).
const NVS_NAMESPACE: &str = "ai32cal";
/// Имя ключа внутри секции.
const NVS_KEY: &str = "calib";

/// Размер blob: 24 канала × (gain f32 + offset f32) = 192 байта.
pub const CALIB_BLOB_LEN: usize = 24 * 8;

/// Инициализация flash-подсистемы NVS (вызывать один раз при старте).
///
/// `nvs_flash_init()` возвращает `ESP_ERR_NVS_NO_FREE_PAGES`, если таблица
/// страниц NVS слишком «усеяна» удалёнными записями — тогда надо стереть
/// флеш-секцию NVS и инициализировать заново. Логика «попробуй, а если
/// грязно — пересоздай» — штатный паттерн из примеров Espressif.
pub fn init_nvs_flash() {
    if unsafe { esp_idf_sys::nvs_flash_init() } == esp_idf_sys::ESP_ERR_NVS_NO_FREE_PAGES {
        log::warn!("NVS: таблица грязная — пересоздаю");
        unsafe { esp_idf_sys::nvs_flash_erase() };
        unsafe { esp_idf_sys::nvs_flash_init() };
    }
}

/// Недолговечная сессия работы с NVS: открыли handle → читаем/пишем → закрыли.
///
/// Реализует Drop: когда сессия выходит из области видимости, handle
/// закрывается сам (RAII), и его невозможно забыть открытым.
struct NvsSession {
    handle: nvs_handle_t,
}

impl NvsSession {
    /// Открывает секцию NVS в режиме «чтение+запись».
    /// Возвращает код ошибки esp_err (0 = ESP_OK), если не открылась.
    fn open() -> Result<Self, esp_idf_sys::esp_err_t> {
        // Строки для C API — нуль-терминированные (поэтому CString).
        let ns = CString::new(NVS_NAMESPACE).expect("namespace");
        let mut handle: nvs_handle_t = 0;
        // вызов C-функции — всегда unsafe: компилятор не может проверить
        // контракты Си-библиотеки.
        let rc =
            unsafe { nvs_open(ns.as_ptr().cast(), nvs_open_mode_t_NVS_READWRITE, &mut handle) };
        if rc != 0 {
            return Err(rc);
        }
        Ok(NvsSession { handle })
    }

    /// Читает blob по ключу. `len` до вызова — максимально возможная длина;
    /// после — фактическая. Возвращает ESP_ERR_NVS_NOT_FOUND, если ключа нет.
    fn get_blob(&self, key: &str, buf: &mut [u8]) -> Result<usize, esp_idf_sys::esp_err_t> {
        let key = CString::new(key).expect("key");
        let mut len = buf.len();
        let rc =
            unsafe { nvs_get_blob(self.handle, key.as_ptr().cast(), buf.as_mut_ptr().cast(), &mut len) };
        if rc != 0 {
            return Err(rc);
        }
        Ok(len)
    }

    /// Пишет blob по ключу и **коммитит** изменения (flush во flash).
    ///
    /// nvs_set_blob копит изменения в RAM, а nvs_commit гарантирует запись
    /// во флеш — без commit данные могут потеряться при отключении питания.
    /// Отмечено dead_code: в каркасе калibrация только читается, набор будет
    /// в лабораторной работе «Процедура калибровки».
    #[allow(dead_code)]
    fn set_blob(&mut self, key: &str, data: &[u8]) -> Result<(), esp_idf_sys::esp_err_t> {
        let key = CString::new(key).expect("key");
        let rc = unsafe { nvs_set_blob(self.handle, key.as_ptr().cast(), data.as_ptr().cast(), data.len()) };
        if rc != 0 {
            return Err(rc);
        }
        let rc = unsafe { nvs_commit(self.handle) };
        if rc != 0 {
            return Err(rc);
        }
        Ok(())
    }
}

impl Drop for NvsSession {
    fn drop(&mut self) {
        unsafe { nvs_close(self.handle) };
    }
}

/// Хранилище калибровки: blob из NVS + кэш в RAM.
///
/// Кэш в оперативной памяти нужен, чтобы **чтение** не ударялось в флеш
/// каждый раз при запросе ИВК: `calibration(ch)` — это просто индекс по
/// массиву. Обновление коэффициентов делается редко (каллибровка), поэтому
/// снять данные в RAM по старту — дешевле, чем держать флеш постоянно.
pub struct CalibStore {
    /// Кэш [gain, offset] по каналам (24 пары f32).
    cache: [(f32, f32); 24],
}

impl CalibStore {
    /// Пытается открыть NVS и прочитать blob; при любой неудаче —
    /// идеальные (заводские) значения.
    pub fn open() -> Self {
        let ideal = Calibration::ideal();
        let mut store = Self { cache: [(ideal.gain, ideal.offset); 24] };

        match Self::load_blob() {
            // Blob ровно 192 байта — распаковываем по 8 байт на канал.
            Ok(Some(bytes)) if bytes.len() == CALIB_BLOB_LEN => {
                for (ch, chunk) in bytes.chunks_exact(8).enumerate() {
                    // Порядок байт — big-endian (совпадает с Modbus в core).
                    let gain = f32::from_be_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
                    let offset = f32::from_be_bytes([chunk[4], chunk[5], chunk[6], chunk[7]]);
                    store.cache[ch] = (gain, offset);
                }
                log::info!("NVS: калибровка загружена (24 канала)");
            }
            Ok(_) => log::info!("NVS: blob другой длины — идеальная калибровка"),
            // Калибровки ещё ни разу не сохраняли — это нормальная ситуация
            // первой загрузки, не ошибка.
            Err(e) => log::warn!("NVS: калибровка не найдена (err={e}) — идеальная"),
        }
        store
    }

    /// Читает blob из NVS. `Ok(Some)` — данные есть; `Ok(None)` — ключ ещё
    /// не записывали; `Err` — реальная ошибка флеша.
    fn load_blob() -> Result<Option<Vec<u8>>, esp_idf_sys::esp_err_t> {
        let session = NvsSession::open()?;
        let mut buf = vec![0u8; CALIB_BLOB_LEN];
        match session.get_blob(NVS_KEY, &mut buf) {
            Ok(len) => Ok(Some(buf[..len].to_vec())),
            // «Не найдено» — это не ошибка, а ответ «нет калибровки».
            Err(ESP_ERR_NVS_NOT_FOUND) => Ok(None),
            Err(e) => Err(e),
        }
    }

    /// Упаковывает кэш в blob и пишет его в NVS.
    /// Отмечено dead_code — вызов появится в лабораторной «записать калибровку».
    #[allow(dead_code)]
    fn save_blob(&self) -> Result<(), esp_idf_sys::esp_err_t> {
        let mut bytes = vec![0u8; CALIB_BLOB_LEN];
        for (ch, (gain, offset)) in self.cache.iter().enumerate() {
            let base = ch * 8;
            bytes[base..base + 4].copy_from_slice(&gain.to_be_bytes());
            bytes[base + 4..base + 8].copy_from_slice(&offset.to_be_bytes());
        }
        let mut session = NvsSession::open()?;
        session.set_blob(NVS_KEY, &bytes)
    }

    /// Калибровка канала (или идеальная, если пользователь ничего не менял).
    pub fn calibration(&self, channel: u8) -> Calibration {
        let (gain, offset) = self.cache[channel as usize];
        Calibration { gain, offset }
    }

    /// Обновить калибровку канала и записать blob в NVS.
    ///
    /// Это будет ключевым шагом процедуры калибровки на стенде: измерили
    /// двумя эталонными токами → `Calibration::from_points` → сюда.
    #[allow(dead_code)]
    pub fn set_calibration(&mut self, channel: u8, cal: Calibration) -> Result<(), ()> {
        self.cache[channel as usize] = (cal.gain, cal.offset);
        match self.save_blob() {
            Ok(()) => {
                log::info!("NVS: калибровка канала {} сохранена", channel);
                Ok(())
            }
            Err(e) => {
                log::error!("NVS: не удалось сохранить: {e}");
                Err(())
            }
        }
    }
}