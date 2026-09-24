//! Пример 06: Rust-слушатель LISTEN/NOTIFY — события в реальном времени (кейс 6).
//!
//! Запуск (в двух терминалах):
//!   терминал 1: cargo run --example 06-listener
//!   терминал 2: psql course_m06 → INSERT INTO devices ... (триггер пошлёт NOTIFY)
//!
//! Слушатель получает `device_changed`, разбирает payload и пишет событие
//! в `device_events` (квитирование: status new → обработано). Глазной вывод:
//! NOTIFY доходит ТОЛЬКО после COMMIT (урок 03) — в незакоммиченной
//! транзакции слушатель «молчит».

use anyhow::Result;
use pg_course_module_09::{database_url, new_pool};
use serde::Deserialize;
use sqlx::postgres::PgListener;

#[derive(Debug, Deserialize)]
struct DeviceEvent {
    id: i32,
    tag: String,
    op: String,
    ts: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;
    let mut listener = PgListener::connect(&database_url()).await?;
    listener.listen("device_changed").await?;
    println!("слушаю канал device_changed (Ctrl-C для выхода)…");

    let mut handled = 0u64;
    loop {
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {
                println!("\nстоп: обработано {handled} событий");
                break;
            }
            notif = listener.recv() => {
                let notif = notif?;
                let ev: DeviceEvent = serde_json::from_str(notif.payload())?;
                // Пишем событие в таблицу (квитирование: 'new' → здесь «принято»).
                let res = sqlx::query(
                    "INSERT INTO device_events (channel, payload, status)
                     VALUES ($1, $2::jsonb, 'new')",
                )
                .bind(notif.channel())
                .bind(notif.payload())
                .execute(&pool)
                .await?;
                handled += res.rows_affected();
                println!(
                    "событие #{handled}: op={} tag={} id={} ts={} payload={}",
                    ev.op, ev.tag, ev.id, ev.ts, notif.payload()
                );
            }
        }
    }
    Ok(())
}
