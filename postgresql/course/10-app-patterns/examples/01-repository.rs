//! Пример 01: repository-паттерн и границы транзакций.
//!
//! Запуск: `cargo run --example 01-repository`
//!
//! Урок 01: данные прячутся за repository (SQL в одном месте), транзакция =
//! бизнес-операция («линия + её устройства» атомарно), ошибка в середине
//! откатывает всё (всё-или-ничего — модуль 03/06).

use anyhow::Result;
use pg_course_module_10::new_pool;
use sqlx::{PgPool, Row};

#[derive(Debug)]
struct Device {
    id: i32,
    line_id: i32,
    device_type: String,
    tag: String,
    model: Option<String>,
}

struct DeviceRepository {
    pool: PgPool,
}

impl DeviceRepository {
    fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    async fn find_by_tag(&self, tag: &str) -> Result<Option<Device>> {
        let row =
            sqlx::query("SELECT id, line_id, device_type, tag, model FROM devices WHERE tag = $1")
                .bind(tag)
                .fetch_optional(&self.pool)
                .await?;
        Ok(row.map(|r| Device {
            id: r.get(0),
            line_id: r.get(1),
            device_type: r.get(2),
            tag: r.get(3),
            model: r.get(4),
        }))
    }

    /// Граница транзакции: создание линии с устройствами — атомарно.
    async fn create_line_with_devices(
        &self,
        name: &str,
        description: &str,
        devices: &[(&str, &str)],
    ) -> Result<()> {
        let mut tx = self.pool.begin().await?;
        let line_id: i32 = sqlx::query_scalar(
            "INSERT INTO equipment_lines (name, description) VALUES ($1, $2) RETURNING id",
        )
        .bind(name)
        .bind(description)
        .fetch_one(&mut *tx)
        .await?;
        for (device_type, tag) in devices {
            sqlx::query("INSERT INTO devices (line_id, device_type, tag) VALUES ($1, $2, $3)")
                .bind(line_id)
                .bind(device_type)
                .bind(tag)
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        Ok(())
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let repo = DeviceRepository::new(new_pool(5).await?);

    // Идемпотентность: убираем возможные остатки прошлых прогонов.
    sqlx::query("DELETE FROM equipment_lines WHERE name LIKE 'ЛИНИЯ-R%'")
        .execute(&repo.pool)
        .await?;

    // 1. Чтение через repository.
    if let Some(d) = repo.find_by_tag("M-01-001").await? {
        println!(
            "найдено: id={} line={} {} ({}) model={:?}",
            d.id, d.line_id, d.tag, d.device_type, d.model
        );
    }

    // 2. Успешная транзакция.
    repo.create_line_with_devices("ЛИНИЯ-R1", "демо repository", &[("mass_meter", "M-R1-001")])
        .await?;
    println!("ЛИНИЯ-R1: создана вместе с устройством");

    // 3. Ошибка в середине транзакции — ВСЁ откатывается (линии нет!).
    let res = repo
        .create_line_with_devices("ЛИНИЯ-R2", "должна откатиться", &[("warp_drive", "X-R2")])
        .await;
    match res {
        Err(e) => println!("ЛИНИЯ-R2: ошибка ({e}) — строка откатилась целиком"),
        Ok(()) => println!("неожиданно: создалась (а CHECK должен был упасть)"),
    }
    let exists: bool =
        sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM equipment_lines WHERE name = 'ЛИНИЯ-R2')")
            .fetch_one(&repo.pool)
            .await?;
    println!("проверка: ЛИНИЯ-R2 существует = {exists} (ждём false)");

    // Уборка демо.
    sqlx::query("DELETE FROM equipment_lines WHERE name LIKE 'ЛИНИЯ-R%' OR name = 'ЛИНИЯ-R1'")
        .execute(&repo.pool)
        .await?;
    println!("демо-строки удалены");
    Ok(())
}
