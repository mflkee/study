//! Пример 05: пайплайн опроса датчиков — полный референс.
//!
//! Запуск:
//!   cargo run --example 05-pipeline -- --limit 30   # автономно (тест/демо)
//!   cargo run --example 05-pipeline                 # ждёт Ctrl-C (SIGINT)
//!
//! Это «полная версия» упражнения `ex01_pipeline`: producer (опрос датчиков)
//! → bounded-канал → consumer (модель записи в БД), плюс:
//! - `select!` на Ctrl-C и грациозный дренаж очереди (урок 03);
//! - статистика по значениям (min/max/avg) — задел отчётных модулей.

use anyhow::Result;
use std::time::Duration;
use tokio::sync::{mpsc, watch};

/// Одно «сырое» значение датчика.
#[derive(Debug, Clone, Copy)]
struct SensorReading {
    seq: u64,
    value: f64,
}

const CAPACITY: usize = 8;
const POLL: Duration = Duration::from_millis(10); // «опрос» датчика
const WRITE: Duration = Duration::from_millis(30); // «запись в БД» (условно)

#[tokio::main]
async fn main() -> Result<()> {
    let limit: Option<u64> = match std::env::args().nth(1).as_deref() {
        Some("--limit") => std::env::args().nth(2).map(|s| s.parse().unwrap()),
        _ => None,
    };

    let (tx, mut rx) = mpsc::channel::<SensorReading>(CAPACITY);
    let (stop_tx, mut stop_rx) = watch::channel(false);

    let producer = tokio::spawn(async move {
        let mut seq = 0u64;
        loop {
            // biased: стоп-ветка первой — сигнал не «затеряется» среди
            // постоянно готовой ветки сна (грабли урока 03).
            tokio::select! {
                biased;
                _ = stop_rx.changed() => break,
                _ = tokio::time::sleep(POLL) => {}
            }
            // Симуляция чтения регистров Modbus: 1000 + гармоника.
            let value = 1000.0 + (seq as f64 * 0.5).sin() * 10.0;
            if tx.send(SensorReading { seq, value }).await.is_err() {
                break;
            }
            seq += 1;
            if Some(seq) == limit {
                break;
            }
        }
    });

    // Потребитель: обработка + статистика.
    let (mut min, mut max, mut sum) = (f64::INFINITY, f64::NEG_INFINITY, 0.0);
    let mut processed = 0u64;

    // Future сигнала — одна на всю программу (см. грабли урока 03:
    // пересоздание ctrl_c() в цикле теряет сигнал в окне перерегистрации).
    let sigint = tokio::signal::ctrl_c();
    tokio::pin!(sigint);

    loop {
        tokio::select! {
            biased;
            _ = &mut sigint => {
                println!("SIGINT: останов производителя + дренаж очереди…");
                let _ = stop_tx.send(true);
                break;
            }
            maybe = rx.recv() => match maybe {
                Some(r) => {
                    tokio::time::sleep(WRITE).await; // модель записи
                    min = min.min(r.value);
                    max = max.max(r.value);
                    sum += r.value;
                    processed += 1;
                    eprintln!("  обработано seq={:>3} value={:.3}", r.seq, r.value);
                }
                None => { println!("канал закрыт — данных больше нет"); break; }
            },
            _ = tokio::signal::ctrl_c() => {
                println!("SIGINT: останов производителя + дренаж очереди…");
                let _ = stop_tx.send(true);
                break;
            }
        }
    }
    while let Some(r) = rx.recv().await {
        tokio::time::sleep(WRITE).await;
        min = min.min(r.value);
        max = max.max(r.value);
        sum += r.value;
        processed += 1;
        eprintln!("  дренаж: seq={:>3} value={:.3}", r.seq, r.value);
    }
    producer.await?;

    let avg = if processed > 0 {
        sum / processed as f64
    } else {
        0.0
    };
    println!("итог: обработано {processed} значений; min={min:.3} max={max:.3} avg={avg:.3}");
    Ok(())
}
