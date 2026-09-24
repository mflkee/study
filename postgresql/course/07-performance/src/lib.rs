//! Общая библиотека модуля 07 «Производительность».
//!
//! Подключение к базе `course_m06`, тип «точка телеметрии» и генератор
//! синтетических показаний для замеров. Упражнения включены через `#[path]`.

use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;

/// База модуля — `course_m06` (создана в модуле 06; таблицы приёма —
/// из `examples/00-setup.sql`).
pub const DEFAULT_DATABASE_URL: &str =
    "postgres://course:course@localhost:15432/course_m06?sslmode=disable";

pub fn database_url() -> String {
    std::env::var("DATABASE_URL").unwrap_or_else(|_| DEFAULT_DATABASE_URL.to_owned())
}

pub async fn new_pool(max: u32) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .max_connections(max)
        .acquire_timeout(Duration::from_secs(5))
        .connect(&database_url())
        .await?)
}

/// Точка телеметрии «как пришла с устройства» (упрощённо: одно число).
#[derive(Debug, Clone, serde::Serialize)]
pub struct TelemetryPoint {
    pub device_id: i32,
    pub seq: u32,
    pub ts: chrono::DateTime<chrono::Utc>,
    pub value: rust_decimal::Decimal,
}

/// Синтетические показания для замеров: N точек, seq 0..N, value «растёт».
pub fn generate_points(
    device_id: i32,
    n: usize,
    start: chrono::DateTime<chrono::Utc>,
) -> Vec<TelemetryPoint> {
    (0..n)
        .map(|i| TelemetryPoint {
            device_id,
            seq: i as u32,
            ts: start + chrono::Duration::milliseconds(i as i64),
            value: rust_decimal::Decimal::from(1000 + (i % 100) as i64),
        })
        .collect()
}

// --- Упражнения ---
#[path = "../exercises/src/ex01_modbus_ingest.rs"]
pub mod ex01_modbus_ingest;

#[path = "../exercises/src/ex02_loader.rs"]
pub mod ex02_loader;

#[cfg(test)]
mod tests {
    use super::generate_points;
    use chrono::Utc;

    #[test]
    fn points_have_monotonic_seq() {
        let pts = generate_points(1, 5, Utc::now());
        assert_eq!(pts.len(), 5);
        let mut seqs: Vec<u32> = pts.iter().map(|p| p.seq).collect();
        seqs.sort_unstable();
        assert_eq!(seqs, vec![0, 1, 2, 3, 4]);
    }

    // --- упражнение 01: приём телеметрии ---

    #[test]
    fn ex01_copy_line_format() {
        use crate::ex01_modbus_ingest::copy_line;
        let pts = generate_points(1, 1, Utc::now());
        let line = copy_line(&pts[0]);
        assert!(line.ends_with('\n'), "перевод строки в конце");
        assert!(line.starts_with("1,0,"), "device_id,seq первыми: {line}");
        assert_eq!(line.trim().split(',').count(), 4, "CSV-строка из 4 колонок");
    }

    #[test]
    fn ex01_summarize_format() {
        use crate::ex01_modbus_ingest::summarize;
        let out = summarize("copy", 2000, std::time::Duration::from_millis(500));
        assert!(out.contains("copy") && out.contains("2000 строк"), "{out}");
        assert!(out.contains("строк/с"), "{out}");
    }

    #[tokio::test]
    async fn ex01_ingest_methods_consistent() {
        // Интеграционный: требует поднятого стенда (course_m06 + 00-setup.sql).
        use crate::ex01_modbus_ingest::{ingest_batch, ingest_copy, ingest_row};
        let pool = super::new_pool(5)
            .await
            .expect("стенд курса должен быть поднят");
        let pts = generate_points(1, 2000, Utc::now());

        sqlx::query("TRUNCATE telemetry_raw")
            .execute(&pool)
            .await
            .expect("cleanup");

        let n1 = ingest_row(&pool, &pts).await.expect("row");
        sqlx::query("TRUNCATE telemetry_raw")
            .execute(&pool)
            .await
            .expect("cleanup");
        let n2 = ingest_batch(&pool, &pts).await.expect("batch");
        sqlx::query("TRUNCATE telemetry_raw")
            .execute(&pool)
            .await
            .expect("cleanup");
        let n3 = ingest_copy(&pool, &pts).await.expect("copy");

        assert_eq!(
            (n1, n2, n3),
            (2000, 2000, 2000),
            "все методы пишут ровно 2000 строк"
        );

        let cnt: i64 = sqlx::query_scalar("SELECT count(*) FROM telemetry_raw")
            .fetch_one(&pool)
            .await
            .expect("count");
        assert_eq!(cnt, 2000, "в таблице ровно 2000 строк");

        // Повторный запуск батча — 0 новых строк (UNIQUE + ON CONFLICT).
        let again = ingest_batch(&pool, &pts).await.expect("batch повторно");
        assert_eq!(again, 0, "дублей нет: идемпотентность приёма");
        sqlx::query("TRUNCATE telemetry_raw")
            .execute(&pool)
            .await
            .expect("cleanup");
    }

    // --- упражнение 02: генератор нагрузки ---

    #[test]
    fn ex02_tps_basic() {
        use crate::ex02_loader::tps;
        assert!((tps(100, 2.0) - 50.0).abs() < 1e-9);
        assert_eq!(tps(100, 0.0), 0.0, "деление на ноль → 0");
        assert_eq!(tps(0, 1.0), 0.0);
    }

    #[test]
    fn ex02_report_line_format() {
        use crate::ex02_loader::report_line;
        let out = report_line("INSERT", 100, 2.0);
        assert!(out.contains("INSERT") && out.contains("50 TPS"), "{out}");
    }
}
