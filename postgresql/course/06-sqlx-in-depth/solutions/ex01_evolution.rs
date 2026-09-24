//! Решение упражнения 01 «Эволюция схемы — новый тип датчика».
//! Скопируйте содержимое в `exercises/src/ex01_evolution.rs`.

use anyhow::{anyhow, Result};
use sqlx::PgPool;

/// Тип устройства в справочнике (столбец `devices.device_type`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceKind {
    MassMeter,
    DensityMeter,
    MoistureMeter,
    LevelMeter, // добавлен миграцией 0002
    FlowCalc,   // добавлен в этом упражнении
}

impl DeviceKind {
    /// Разобрать строковое значение из БД.
    pub fn parse(s: &str) -> Result<DeviceKind> {
        match s {
            "mass_meter" => Ok(DeviceKind::MassMeter),
            "density_meter" => Ok(DeviceKind::DensityMeter),
            "moisture_meter" => Ok(DeviceKind::MoistureMeter),
            "level_meter" => Ok(DeviceKind::LevelMeter),
            "flow_calc" => Ok(DeviceKind::FlowCalc),
            other => Err(anyhow!("неизвестный тип устройства: {other:?}")),
        }
    }

    /// Обратное преобразование: значение для INSERT.
    pub fn as_str(&self) -> &'static str {
        match self {
            DeviceKind::MassMeter => "mass_meter",
            DeviceKind::DensityMeter => "density_meter",
            DeviceKind::MoistureMeter => "moisture_meter",
            DeviceKind::LevelMeter => "level_meter",
            DeviceKind::FlowCalc => "flow_calc",
        }
    }
}

/// Добавить «расчётчик» на линию по её имени.
pub async fn add_flow_calc_device(
    pool: &PgPool,
    line_name: &str,
    tag: &str,
    model: &str,
) -> Result<()> {
    let n = sqlx::query(
        "INSERT INTO devices (line_id, device_type, tag, model)
         SELECT id, 'flow_calc', $2, $3 FROM equipment_lines WHERE name = $1",
    )
    .bind(line_name)
    .bind(tag)
    .bind(model)
    .execute(pool)
    .await?;
    if n.rows_affected() == 0 {
        return Err(anyhow!("линия не найдена: {line_name}"));
    }
    Ok(())
}
