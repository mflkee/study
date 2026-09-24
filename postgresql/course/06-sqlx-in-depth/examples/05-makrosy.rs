//! Пример 05: compile-time макросы sqlx — `query_as!`/`query!` и offline-кэш.
//!
//! Запуск: `cargo run --example 05-makrosy`
//!
//! В отличие от runtime-запросов (урок 02), макросы проверяют SQL на этапе
//! КОМПИЛЯЦИИ: имена колонок, типы, типы параметров — всё сверяется с БД.
//! Для этого макросу нужен живой `DATABASE_URL` при сборке ИЛИ offline-кэш:
//! каталог `.sqlx/` (сгенерирован `cargo sqlx prepare`), закоммиченный в репозиторий.
//!
//! Если сборка идёт без живого стенда — макросы берут метаданные из `.sqlx`
//! (в этом модуле кэш подготовлен и закоммичен, поэтому «скопировал — собрал»
//! работает без БД: D2).
//!
//! Минусы: макросы не умеют динамический SQL (построение WHERE по фильтрам),
//! а подготовка кэша требует живого стенда у автора. Поэтому основной стиль
//! курса — runtime-запросы, макросы — для стабильных запросов ядра.

use anyhow::Result;
use pg_course_module_06::new_pool;
use sqlx::FromRow;

/// Строка справочника (все поля как в таблице).
#[derive(Debug, FromRow)]
struct Device {
    id: i32,
    line_id: i32,
    device_type: String,
    tag: String,
    model: Option<String>,
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;

    // query_as! — компилятор проверил SQL и типы; результаты в struct.
    let devices: Vec<Device> = sqlx::query_as!(
        Device,
        "SELECT id, line_id, device_type, tag, model
           FROM devices
          WHERE line_id = $1
          ORDER BY id",
        1
    )
    .fetch_all(&pool)
    .await?;
    println!("устройств на ЛИНИИ-1: {}", devices.len());
    for d in &devices {
        println!(
            "  id={} line={} {}: {} ({})",
            d.id,
            d.line_id,
            d.tag,
            d.device_type,
            d.model.as_deref().unwrap_or("-")
        );
    }

    // query! — без структуры: колонку называем алиасом.
    // Суффикс "!" в алиасе говорит макросу: колонка не может быть NULL —
    // иначе count(*) мапился бы в Option<i64>.
    let row = sqlx::query!("SELECT count(*) AS \"n!\" FROM measurements")
        .fetch_one(&pool)
        .await?;
    println!("измерений в базе: {}", row.n);

    // Ошибки компиляции макроса видны сразу: поменяй `line_id` на `line_id_wrong`
    // или тип `model` на `i32` — cargo check упадёт с причиной.

    Ok(())
}
