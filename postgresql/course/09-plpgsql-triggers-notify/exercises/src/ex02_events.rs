//! Упражнение 02: «Тревоги и события в реальном времени» (кейс 6).
//!
//! **Кейс:** 6 (тревоги в реальном времени). **Модуль:** 09. **Время:** 2 ч.
//!
//! ТЗ: триггер `trg_devices_notify` шлёт `pg_notify('device_changed', jsonb)`
//! при изменениях devices (00-setup.sql). Rust-слушатель (пример 06) должен
//! превращать payload в событие. Здесь — разбор payload (чистая функция):
//! приходит JSON `{"id":…, "tag":…, "op":…, "ts":…}`.
//!
//! Проверка: `cargo test ex02_` — парсинг валидного/битого payload
//! + интеграционный тест «listener → INSERT → recv → parse» (нужен стенд).
//!
//! Решение — в `solutions/ex02_events.rs`.

use anyhow::Result;
use serde::Deserialize;

/// Событие об изменении устройства (payload из канала).
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct DeviceEvent {
    pub id: i32,
    pub tag: String,
    pub op: String, // INSERT | UPDATE | DELETE
    pub ts: String,
}

/// Разобрать payload канала `device_changed` в событие.
pub fn parse_payload(_payload: &str) -> Result<DeviceEvent> {
    todo!("serde_json::from_str с понятными ошибками")
}
