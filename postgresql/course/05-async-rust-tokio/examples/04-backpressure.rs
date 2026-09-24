//! Пример 04: backpressure — почему канал обязан быть bounded.
//!
//! Запуск: `cargo run --example 04-backpressure`
//!
//! Потребитель «пишет в БД» по 50 мс на элемент, канал вмещает 4 элемента,
//! производитель шлёт каждые ~мгновенно. Если бы канал был безграничным —
//! очередь росла бы до бесконечности (память/задержки). Bounded-канал
//! заставляет производителя ЖДАТЬ: очередь не растёт, ничего не теряется.
//! В выводе — суммарное время ожидания производителя.

use anyhow::Result;
use std::time::{Duration, Instant};
use tokio::sync::mpsc;

#[tokio::main]
async fn main() -> Result<()> {
    const CAPACITY: usize = 4;
    const TOTAL: u64 = 20;

    let (tx, mut rx) = mpsc::channel::<u64>(CAPACITY);

    let producer = tokio::spawn(async move {
        let mut wait_total = Duration::ZERO;
        for seq in 0..TOTAL {
            let wait_start = Instant::now();
            // Ждём, пока место в канале освободится (это и есть backpressure).
            let _ = tx.send(seq).await;
            wait_total += wait_start.elapsed();
        }
        wait_total
    });

    let mut processed = 0u64;
    while let Some(seq) = rx.recv().await {
        // Медленная «запись в БД»: потребитель — узкое место.
        tokio::time::sleep(Duration::from_millis(50)).await;
        processed += 1;
        eprintln!("  обработано {seq}");
    }
    let waits = producer.await?;

    println!(
        "готово: обработано {processed}/{TOTAL}; \
         производитель ждал место в канале суммарно {waits:?} \
         (емкость канала {CAPACITY}, обработка 50 мс/элемент)"
    );
    Ok(())
}
