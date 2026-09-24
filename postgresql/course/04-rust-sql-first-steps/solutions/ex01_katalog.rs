//! Решение упражнения 01 «Каталог оборудования» (sqlx).
//! Чтобы применить: скопируйте содержимое в `exercises/src/ex01_katalog.rs`
//! (или вставьте после своей попытки — так полезнее).

use crate::{Device, Measurement};
use anyhow::{anyhow, Result};
use sqlx::PgPool;

/// Тип устройства в справочнике (столбец `devices.device_type`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceKind {
    MassMeter,
    DensityMeter,
    MoistureMeter,
}

impl DeviceKind {
    /// Разобрать строковое значение из БД: "mass_meter" → MassMeter и т.д.
    pub fn parse(s: &str) -> Result<DeviceKind> {
        match s {
            "mass_meter" => Ok(DeviceKind::MassMeter),
            "density_meter" => Ok(DeviceKind::DensityMeter),
            "moisture_meter" => Ok(DeviceKind::MoistureMeter),
            other => Err(anyhow!("неизвестный тип устройства: {other:?}")),
        }
    }

    /// Обратное преобразование: значение для INSERT.
    pub fn as_str(&self) -> &'static str {
        match self {
            DeviceKind::MassMeter => "mass_meter",
            DeviceKind::DensityMeter => "density_meter",
            DeviceKind::MoistureMeter => "moisture_meter",
        }
    }
}

/// Строка справочника линий (`equipment_lines`).
#[derive(Debug, sqlx::FromRow)]
pub struct Line {
    pub id: i32,
    pub name: String,
    pub description: Option<String>,
}

/// Устройство вместе с именем линии (JOIN `equipment_lines`).
#[derive(Debug, sqlx::FromRow)]
pub struct DeviceRow {
    pub id: i32,
    pub line_name: String,
    pub tag: String,
    pub model: Option<String>,
}

/// Все линии, отсортированные по имени.
pub async fn list_lines(pool: &PgPool) -> Result<Vec<Line>> {
    let rows = sqlx::query_as::<_, Line>(
        "SELECT id, name, description FROM equipment_lines ORDER BY name",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Все устройства с именем линии (JOIN), сортировка по линии, затем тегу.
pub async fn list_devices(pool: &PgPool) -> Result<Vec<DeviceRow>> {
    let rows = sqlx::query_as::<_, DeviceRow>(
        "SELECT d.id, l.name AS line_name, d.tag, d.model
           FROM devices d
           JOIN equipment_lines l ON l.id = d.line_id
          ORDER BY l.name, d.tag",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

/// Добавить устройство, вернуть вставленную строку (INSERT ... RETURNING).
pub async fn insert_device(
    pool: &PgPool,
    line_id: i32,
    kind: DeviceKind,
    tag: &str,
    model: Option<&str>,
) -> Result<Device> {
    let dev = sqlx::query_as::<_, Device>(
        "INSERT INTO devices (line_id, device_type, tag, model)
         VALUES ($1, $2, $3, $4)
         RETURNING id, line_id, device_type, tag, model",
    )
    .bind(line_id)
    .bind(kind.as_str())
    .bind(tag)
    .bind(model)
    .fetch_one(pool)
    .await?;
    Ok(dev)
}

/// Последнее измерение устройства по тегу; `None`, если измерений нет.
pub async fn last_measurement(pool: &PgPool, tag: &str) -> Result<Option<Measurement>> {
    let m = sqlx::query_as::<_, Measurement>(
        "SELECT m.id, m.device_id, m.ts, m.value, m.quality
           FROM measurements m
           JOIN devices d ON d.id = m.device_id
          WHERE d.tag = $1
          ORDER BY m.ts DESC
          LIMIT 1",
    )
    .bind(tag)
    .fetch_optional(pool)
    .await?;
    Ok(m)
}

/// Формат строки списка линий: `#1 ЛИНИЯ-1 — линия измерения №1`.
pub fn format_line(l: &Line) -> String {
    match &l.description {
        Some(d) => format!("#{} {} — {d}", l.id, l.name),
        None => format!("#{} {}", l.id, l.name),
    }
}

/// Формат строки списка устройств: `M-02-001 [ЛИНИЯ-2] CMF-200`.
pub fn format_device_row(d: &DeviceRow) -> String {
    match &d.model {
        Some(m) => format!("{} [{}] {m}", d.tag, d.line_name),
        None => format!("{} [{}]", d.tag, d.line_name),
    }
}
