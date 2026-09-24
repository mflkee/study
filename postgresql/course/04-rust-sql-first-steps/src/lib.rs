//! Общая библиотека модуля 04 «Первый Rust + PostgreSQL».
//!
//! Здесь живёт то, что используют все примеры и упражнения модуля:
//! подключение к стенду курса, общие типы данных каталога и
//! включение упражнений (`exercises/src/*`).
//!
//! Модуль построен на принципе D2: runtime-типизированные запросы
//! `sqlx::query_as::<_, T>` (без макросов `query!`).

use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

/// Стенд курса: PostgreSQL 18.6 на хосте, порт 15432 (см. infra/docker-compose.yml).
/// Пароль и пользователь — `course`, `sslmode=disable` — локальный стенд без TLS.
pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15432/course?sslmode=disable";

/// URL подключения: значение из переменной окружения `DATABASE_URL`,
/// если задано, иначе — стенд курса по умолчанию.
pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

/// Пул соединений к стенду курса. `max` — максимум одновременных соединений.
pub async fn new_pool(max: u32) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .max_connections(max)
        .connect(&database_url())
        .await?)
}

/// Устройство справочника каталога (таблица `devices`).
#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct Device {
    pub id: i32,
    pub line_id: i32,
    pub device_type: String,
    pub tag: String,
    pub model: Option<String>,
}

/// Измерение телеметрии (таблица `measurements`).
/// Числовое значение — `numeric(20,6)` → `rust_decimal::Decimal` (точность, не float).
#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct Measurement {
    pub id: i64,
    pub device_id: i32,
    pub ts: chrono::DateTime<chrono::Utc>, // timestamptz
    pub value: rust_decimal::Decimal,      // numeric(20,6)
    pub quality: i32,                      // int, 0..3
}

// --- Упражнения: включены в библиотеку, чтобы их можно было тестировать и
// вызывать из CLI-примера. До решения содержат todo!() — тесты падают,
// после решения — проходят (см. exercises/README.md).
#[path = "../exercises/src/ex01_katalog.rs"]
pub mod ex01_katalog;

#[path = "../exercises/src/ex02_massa_netto.rs"]
pub mod ex02_massa_netto;

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::Decimal;
    use std::str::FromStr;

    /// Хелпер: Decimal из строки (без вставки в тест кастов).
    fn de(s: &str) -> Decimal {
        Decimal::from_str(s).expect("валидный decimal")
    }

    // --- чистая логика библиотеки (зелёные всегда) ---

    #[test]
    fn default_url_ukazyvaet_na_stend() {
        assert!(DEFAULT_DATABASE_URL.contains("15432"), "порт стенда курса");
        assert!(DEFAULT_DATABASE_URL.contains("sslmode=disable"));
        assert_eq!(database_url(), DEFAULT_DATABASE_URL);
    }

    // --- упражнение 01: парсинг и форматирование (без БД) ---
    // Падают, пока в заготовке todo!(); проходят после решения.

    #[test]
    fn ex01_parse_device_type_ok() {
        use ex01_katalog::DeviceKind;
        assert_eq!(
            DeviceKind::parse("mass_meter").expect("ok").as_str(),
            "mass_meter"
        );
        assert_eq!(
            DeviceKind::parse("density_meter").expect("ok").as_str(),
            "density_meter"
        );
        assert_eq!(
            DeviceKind::parse("moisture_meter").expect("ok").as_str(),
            "moisture_meter"
        );
    }

    #[test]
    fn ex01_parse_device_type_rejects_unknown() {
        use ex01_katalog::DeviceKind;
        assert!(DeviceKind::parse("massometer").is_err());
        assert!(DeviceKind::parse("").is_err());
    }

    #[test]
    fn ex01_format_line_contains_name() {
        use ex01_katalog::{format_line, Line};
        let l = Line {
            id: 1,
            name: "ЛИНИЯ-1".to_owned(),
            description: Some("линия измерения №1".to_owned()),
        };
        let out = format_line(&l);
        assert!(out.contains("ЛИНИЯ-1"), "вывод должен содержать имя: {out}");
    }

    #[test]
    fn ex01_format_device_row_contains_tag_and_line() {
        use ex01_katalog::{format_device_row, DeviceRow};
        let d = DeviceRow {
            id: 7,
            line_name: "ЛИНИЯ-2".to_owned(),
            tag: "M-02-001".to_owned(),
            model: Some("CMF-200".to_owned()),
        };
        let out = format_device_row(&d);
        assert!(
            out.contains("M-02-001"),
            "вывод должен содержать тег: {out}"
        );
        assert!(
            out.contains("ЛИНИЯ-2"),
            "вывод должен содержать линию: {out}"
        );
    }

    // --- упражнение 02: масса нетто (без БД) ---

    #[test]
    fn ex02_massa_netto_basic() {
        use ex02_massa_netto::massa_netto;
        // 1000 кг/ч брутто, вода 0.50 %, примеси 0.05 %
        // => 1000 × (1 − 0.0055) = 994.5000
        let netto = massa_netto(de("1000.00"), de("0.50"), de("0.05")).expect("ok");
        assert_eq!(netto, de("994.5000"));
    }

    #[test]
    fn ex02_massa_netto_without_impurities() {
        use ex02_massa_netto::massa_netto;
        let netto = massa_netto(de("500.000000"), de("0"), de("0")).expect("ok");
        assert_eq!(netto, de("500.0000"));
    }

    #[test]
    fn ex02_rounding_half_up() {
        use ex02_massa_netto::massa_netto;
        // 1 × (1 − 0.00005) = 0.99995 → ROUND_HALF_UP до 4 знаков => 1.0000
        let netto = massa_netto(de("1"), de("0.004"), de("0.001")).expect("ok");
        assert_eq!(netto, de("1.0000"));
    }

    #[test]
    fn ex02_rejects_water_plus_sediment_over_100() {
        use ex02_massa_netto::massa_netto;
        assert!(massa_netto(de("1000"), de("99.5"), de("0.6")).is_err());
        assert!(massa_netto(de("1000"), de("100"), de("0")).is_err());
    }

    #[test]
    fn ex02_rejects_negative_input() {
        use ex02_massa_netto::massa_netto;
        assert!(massa_netto(de("1000"), de("-0.1"), de("0")).is_err());
        assert!(massa_netto(de("-5"), de("0.1"), de("0")).is_err());
    }
}
