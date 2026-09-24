//! Упражнение 01: «Каталог оборудования» — SQL-слой CRUD на sqlx.
//!
//! **Кейс:** 1 (каталог оборудования). **Модуль:** 04. **Время:** 2 ч.
//!
//! ТЗ: справочник уже есть в БД (применён `examples/00-setup.sql`):
//! таблицы `equipment_lines`, `devices`, `measurements`. Нужно написать
//! SQL-функции, которые использует CLI (пример 06): список линий,
//! список устройств с именем линии, добавление устройства,
//! последнее измерение по тегу, а также форматирование вывода.
//!
//! Заготовка: все `todo!()` заменить на рабочий код по образцу урока 02
//! (`sqlx::query_as::<_, T>` + `bind`). Типы полей уже заданы — они же
//! говорят, какие колонки выбирать.
//!
//! Параметры функций названы с `_` намеренно: в заготовке они ещё не
//! используются (todo!()), после решения префикс убирается.
//!
//! Проверка: `cargo test ex01_` — все тесты с этим префиксом зелёные.
//! Затем CLI: `cargo run --example 06-katalog-cli -- list-devices`.
//!
//! Решение — в `solutions/ex01_katalog.rs` (открывать после своей попытки).

use crate::{Device, Measurement};
use anyhow::Result;
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
    /// Неизвестную строку — вернуть ошибкой.
    pub fn parse(_s: &str) -> Result<DeviceKind> {
        todo!("разбор строки device_type")
    }

    /// Обратное преобразование: значение для INSERT.
    pub fn as_str(&self) -> &'static str {
        todo!("имя строки для БД")
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
    pub line_name: String, // l.name AS line_name
    pub tag: String,
    pub model: Option<String>,
}

/// Все линии, отсортированные по имени.
pub async fn list_lines(_pool: &PgPool) -> Result<Vec<Line>> {
    todo!("SELECT id, name, description ... ORDER BY name")
}

/// Все устройства с именем линии (JOIN), сортировка по line_name, tag.
pub async fn list_devices(_pool: &PgPool) -> Result<Vec<DeviceRow>> {
    todo!("SELECT d.id, l.name AS line_name, d.tag, d.model ... JOIN ...")
}

/// Добавить устройство, вернуть вставленную строку (INSERT ... RETURNING).
pub async fn insert_device(
    _pool: &PgPool,
    _line_id: i32,
    _kind: DeviceKind,
    _tag: &str,
    _model: Option<&str>,
) -> Result<Device> {
    todo!(
        "INSERT INTO devices (line_id, device_type, tag, model) VALUES ($1,$2,$3,$4) RETURNING ..."
    )
}

/// Последнее измерение устройства по тегу; `None`, если измерений нет.
pub async fn last_measurement(_pool: &PgPool, _tag: &str) -> Result<Option<Measurement>> {
    todo!("JOIN devices по d.tag = $1, ORDER BY m.ts DESC LIMIT 1")
}

/// Формат строки списка линий: `#1 ЛИНИЯ-1 — линия измерения №1`.
pub fn format_line(_l: &Line) -> String {
    todo!("см. тест ex01_format_line_contains_name")
}

/// Формат строки списка устройств: `M-02-001 [ЛИНИЯ-2] CMF-200`.
pub fn format_device_row(_d: &DeviceRow) -> String {
    todo!("см. тест ex01_format_device_row_contains_tag_and_line")
}
