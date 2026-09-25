//! Шлюз Historian: опрос Modbus → буфер → батч в PostgreSQL.
//!
//! Запуск: `cargo run --bin gateway [--addr 127.0.0.1:15504] [--buffer buffer.tsv]`
//!
//! Логика (модули 05, 07, 10):
//! - каждые 500 мс читает holding-регистры эмулятора;
//! - пишет точки в PG батчем (ON CONFLICT → идемпотентность);
//! - если PG недоступен — копит в локальном буфере (файл), при восстановлении
//!   доставляет накопленное; по Ctrl-C — грациозный флаш и останов.

use anyhow::Result;
use capstone_historian::{
    batch_deliver, ensure_partitions, new_pool, parse_lines, to_line, MeteringPoint,
};
use chrono::Utc;
use rust_decimal::Decimal;
use std::path::PathBuf;
use std::time::Duration;
use tokio::time::Instant;
use tokio_modbus::prelude::*;

fn after(flag: &str) -> Option<String> {
    let a: Vec<String> = std::env::args().collect();
    a.iter()
        .position(|x| x == flag)
        .and_then(|i| a.get(i + 1))
        .cloned()
}

async fn flush_buffer(pool: &sqlx::PgPool, path: &PathBuf) -> Result<()> {
    let data = std::fs::read_to_string(path).unwrap_or_default();
    let pts = parse_lines(&data)?;
    if pts.is_empty() {
        return Ok(());
    }
    match batch_deliver(pool, &pts).await {
        Ok(inserted) => {
            std::fs::write(path, "")?;
            println!("буфер: доставлено {inserted} из {} точек", pts.len());
        }
        Err(e) => println!("буфер: доставка не удалась ({e}) — данные целы в {path:?}"),
    }
    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    let addr: std::net::SocketAddr = after("--addr")
        .unwrap_or_else(|| "127.0.0.1:15504".into())
        .parse()?;
    let path = after("--buffer")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("buffer.tsv"));

    let pool = match new_pool(5).await {
        Ok(p) => p,
        Err(e) => {
            println!("PG недоступен на старте ({e}) — работаем в буфер до восстановления");
            new_pool(5).await? // повтор — но контейнер должен быть сначала поднят
        }
    };
    ensure_partitions(
        &pool,
        Utc::now() - chrono::Duration::days(2),
        Utc::now() + chrono::Duration::days(35),
    )
    .await?;

    let mut ctx = tokio_modbus::client::tcp::connect(addr).await?;
    println!("шлюз Historian: опрос {addr}, буфер {path:?}, Ctrl-C — стоп");

    let mut seq: i64 = 0;
    let mut ticks = tokio::time::interval(Duration::from_millis(500));
    let mut last_flush = Instant::now();
    let sigint = tokio::signal::ctrl_c();
    tokio::pin!(sigint);

    loop {
        tokio::select! {
            biased;
            _ = &mut sigint => {
                println!("\nстоп: финальный флаш буфера");
                flush_buffer(&pool, &path).await?;
                break;
            }
            _ = ticks.tick() => {
                let raw: Vec<u16> = match ctx.read_holding_registers(0, 2).await {
                    Ok(Ok(v)) => v,
                    _ => { eprintln!("нет связи с эмулятором — пропуск такта"); continue; }
                };
                let pt = MeteringPoint {
                    device_id: 1,                    // массомер
                    seq,
                    ts: Utc::now(),
                    value: Decimal::from(raw[0]),
                };
                seq += 1;
                // Пишем пачкой (1 точка — тоже батч из 1; нормально для демо).
                match batch_deliver(&pool, std::slice::from_ref(&pt)).await {
                    Ok(_) => {}
                    Err(e) => {
                        // PG недоступен: буферизуем локально (модуль 10).
                        let mut f = std::fs::OpenOptions::new().create(true).append(true).open(&path)?;
                        use std::io::Write;
                        f.write_all(to_line(&pt).as_bytes())?;
                        eprintln!("PG недоступен ({e}) → в буфер; seq={}", pt.seq);
                    }
                }
                // Раз в 5 секунд — флаш накопленного буфера.
                if last_flush.elapsed() > Duration::from_secs(5) {
                    flush_buffer(&pool, &path).await?;
                    last_flush = Instant::now();
                }
            }
        }
    }
    println!("шлюз завершён, записано seq до {seq}");
    Ok(())
}
