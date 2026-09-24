//! Упражнение 01: «Эволюция схемы — новый тип датчика» (кейс 10).
//!
//! **Кейс:** 10 (эволюция схемы). **Модуль:** 06. **Время:** 2.5 ч.
//!
//! ТЗ: SCADA требует новый тип устройства — «расчётчик» (`flow_calc`)
//! (считает расход по соседним датчикам). Два слоя изменения:
//!
//! 1. **Схема.** Напиши миграцию `migrations/0003_add_flow_calc.sql`:
//!    - расширить CHECK-ограничение `devices_device_type_check` значением
//!      `'flow_calc'` (образец — миграция 0002, там уже добавлен `level_meter`);
//!    - добавить устройство `FC-01-001` (тип `flow_calc`, модель `FCC-900`)
//!      на `ЛИНИЯ-1` (id линии бери подзапросом по имени — как в 0002).
//! 2. **Rust.** В `DeviceKind` добавь вариант `FlowCalc` и допиши
//!    `parse`/`as_str`; реализуй `add_flow_calc_device`.
//!
//! Проверка: `cargo test ex01_` — тесты сами применят миграции к базе
//! `course_m06` и проверят: constraint знает `flow_calc`, вставка проходит,
//! строка на месте.
//!
//! Решение — в `solutions/ex01_evolution.rs` и `solutions/0003_add_flow_calc.sql`
//! (открыть после своей попытки).

use anyhow::Result;
use sqlx::PgPool;

/// Тип устройства в справочнике (столбец `devices.device_type`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceKind {
    MassMeter,
    DensityMeter,
    MoistureMeter,
    LevelMeter, // добавлен миграцией 0002
    FlowCalc,   // добавить в этом упражнении
}

impl DeviceKind {
    /// Разобрать строковое значение из БД.
    pub fn parse(_s: &str) -> Result<DeviceKind> {
        todo!("массомер/плотномер/влагомер/уровнемер/расчётчик + ошибка на неизвестном")
    }

    /// Обратное преобразование: значение для INSERT.
    pub fn as_str(&self) -> &'static str {
        todo!("имена строк для БД (включая 'flow_calc')")
    }
}

/// Добавить «расчётчик» на линию по её имени.
/// Подсказка (урок 02): INSERT INTO devices (line_id, device_type, tag, model)
/// SELECT id, 'flow_calc', $2, $3 FROM equipment_lines WHERE name = $1;
pub async fn add_flow_calc_device(
    _pool: &PgPool,
    _line_name: &str,
    _tag: &str,
    _model: &str,
) -> Result<()> {
    todo!("вставка устройства flow_calc (после написания миграции 0003)")
}
