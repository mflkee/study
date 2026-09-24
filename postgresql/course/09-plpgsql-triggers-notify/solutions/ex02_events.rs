//! Решение упражнения 02: «Тревоги и события в реальном времени».
//! Скопируйте содержимое в `exercises/src/ex02_events.rs` после попытки.

use anyhow::{anyhow, Result};
use serde::Deserialize;

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct DeviceEvent {
    pub id: i32,
    pub tag: String,
    pub op: String,
    pub ts: String,
}

/// Разобрать payload канала `device_changed` в событие.
pub fn parse_payload(payload: &str) -> Result<DeviceEvent> {
    serde_json::from_str::<DeviceEvent>(payload)
        .map_err(|e| anyhow!("неверный payload события: {e}: {payload:?}"))
}
