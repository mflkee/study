//! Пример 02: транзакции из Rust — COMMIT, ROLLBACK, savepoint, границы.
//!
//! Запуск: `cargo run --example 02-tranzakcii` (и ТЕРМИНАЛ psql рядом:
//!  `docker compose exec -T postgres psql -U course -d course_m06` — видно,
//!  что видно другим сессиям до COMMIT).
//!
//! Правило границ: транзакция = «бизнес-операция» (записать строку +
//! привязанные записи + валидация), а не «один INSERT» и не «вся ночь».

use anyhow::Result;
use pg_course_module_06::new_pool;
use sqlx::PgPool;

async fn line_exists(pool: &PgPool, name: &str) -> bool {
    sqlx::query_scalar::<_, bool>("SELECT EXISTS(SELECT 1 FROM equipment_lines WHERE name = $1)")
        .bind(name)
        .fetch_one(pool)
        .await
        .expect("exists")
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // 0. Зачистка демо-строк (повторные запуски безопасны).
    sqlx::query("DELETE FROM equipment_lines WHERE name LIKE 'ЛИНИЯ-T%'")
        .execute(&pool)
        .await?;

    // 1. COMMIT: строка сохранена.
    {
        let mut tx = pool.begin().await?;
        sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T1', 'демо COMMIT')",
        )
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
    }
    println!(
        "после COMMIT: ЛИНИЯ-T1 существует = {}",
        line_exists(&pool, "ЛИНИЯ-T1").await
    );

    // 2. ROLLBACK: строка откатилась.
    {
        let mut tx = pool.begin().await?;
        sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T2', 'демо ROLLBACK')",
        )
        .execute(&mut *tx)
        .await?;
        tx.rollback().await?;
    }
    println!(
        "после ROLLBACK: ЛИНИЯ-T2 существует = {}",
        line_exists(&pool, "ЛИНИЯ-T2").await
    );

    // 3. Savepoint — явным SQL (`SAVEPOINT sp` / `ROLLBACK TO sp`):
    //    откатываем только «хвост» внутри транзакции (модуль 03 — то же самое
    //    в psql; в sqlx 0.9 вложенный API скрыт, явный SQL — портативный способ).
    {
        let mut tx = pool.begin().await?;
        sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T3', 'до точки')",
        )
        .execute(&mut *tx)
        .await?;

        sqlx::query("SAVEPOINT sp").execute(&mut *tx).await?;
        sqlx::query("INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T3-спойлер', 'в точке')")
            .execute(&mut *tx)
            .await?;
        sqlx::query("ROLLBACK TO sp").execute(&mut *tx).await?; // откат только точки
        sqlx::query("RELEASE sp").execute(&mut *tx).await?;

        sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T3-2', 'после точки')",
        )
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
    }
    println!(
        "savepoint: T3 = {}, спойлер = {}, T3-2 = {}",
        line_exists(&pool, "ЛИНИЯ-T3").await,
        line_exists(&pool, "ЛИНИЯ-T3-спойлер").await,
        line_exists(&pool, "ЛИНИЯ-T3-2").await
    );

    // 4. Границы транзакции: ошибка в середине НЕ портит предыдущее —
    //    вся операция откатывается целиком (всё или ничего).
    {
        let mut tx = pool.begin().await?;
        sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T4-1', 'первая')",
        )
        .execute(&mut *tx)
        .await?;
        // «вторая вставка» нарушает UNIQUE — вся транзакция падает.
        let res = sqlx::query(
            "INSERT INTO equipment_lines (name, description) VALUES ('ЛИНИЯ-T4-1', 'дубль имени')",
        )
        .execute(&mut *tx)
        .await;
        // В реальном коде обрабатываем ошибку и решаем: rollback или savepoint.
        assert!(res.is_err(), "дубль должен упасть на UNIQUE");
        tx.rollback().await?;
    }
    println!(
        "всё-или-ничего: T4-1 существует = {}",
        line_exists(&pool, "ЛИНИЯ-T4-1").await
    );

    // 5. Уберём демо-строки.
    sqlx::query("DELETE FROM equipment_lines WHERE name LIKE 'ЛИНИЯ-T%'")
        .execute(&pool)
        .await?;
    println!("демо-строки удалены, база чистая");
    Ok(())
}
