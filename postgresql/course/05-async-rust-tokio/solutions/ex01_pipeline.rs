//! Решение упражнения 01 «Пайплайн опроса датчиков» (tokio).
//! Скопируйте содержимое в `exercises/src/ex01_pipeline.rs` после попытки.

use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc::{channel, Receiver, Sender};

/// Одно «сырое» значение датчика (в СИКН — регистр Modbus, модуль 07).
#[derive(Debug, Clone, Copy)]
pub struct SensorReading {
    pub seq: u64,
    pub value: f64,
}

impl SensorReading {
    pub fn new(seq: u64, value: f64) -> Self {
        Self { seq, value }
    }
}

/// Итог прогона пайплайна.
#[derive(Debug, Default, PartialEq, Eq)]
pub struct PipelineReport {
    pub produced: u64,
    pub processed: u64,
}

/// Производитель: каждые `poll_interval` отправляет одно значение, всего `limit`.
/// `send(...).await` на bounded-канале ждёт, пока потребитель освободит место.
pub async fn produce(tx: Sender<SensorReading>, poll_interval: Duration, limit: u64) {
    for seq in 0..limit {
        // Пауза «опроса»: в реальном шлюзе здесь чтение регистров Modbus.
        tokio::time::sleep(poll_interval).await;
        let value = 1000.0 + seq as f64; // какой-то сигнал
                                         // await! — если канал полон, ждём, пока потребитель заберёт элемент.
        if tx.send(SensorReading::new(seq, value)).await.is_err() {
            // Канал закрыт (потребитель уже завершился) — пора выходить.
            return;
        }
    }
}

/// Потребитель: читает значения, «обрабатывает» за `process_delay`
/// (модель записи в БД) и возвращает число обработанных.
pub async fn consume(mut rx: Receiver<SensorReading>, process_delay: Duration) -> u64 {
    let mut processed = 0u64;
    while let Some(r) = rx.recv().await {
        // Модель записи в БД: асинхронная операция, не блокирующая поток.
        tokio::time::sleep(process_delay).await;
        processed += 1;
        eprintln!("обработано seq={} value={:.2}", r.seq, r.value);
    }
    processed
}

/// Прогон пайплайна: spawn производителя, потребитель — в текущей задаче;
/// после закрытия канала потребитель «дренирует» остаток очереди.
pub async fn run(
    capacity: usize,
    poll_interval: Duration,
    process_delay: Duration,
    limit: u64,
) -> Result<PipelineReport> {
    let (tx, rx) = channel(capacity);

    // Производитель живёт в отдельной задаче; после цикла tx упадёт —
    // канал закроется, consumer дочитает остаток и завершится.
    tokio::spawn(async move {
        produce(tx, poll_interval, limit).await;
    });

    let processed = consume(rx, process_delay).await;
    Ok(PipelineReport {
        produced: limit,
        processed,
    })
}
