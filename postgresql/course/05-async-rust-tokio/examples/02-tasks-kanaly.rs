//! Пример 02: задачи и каналы `mpsc`, `select!`.
//!
//! Запуск: `cargo run --example 02-tasks-kanaly`
//!
//! mpsc = multi-producer, single-consumer: много «производителей» шлют
//! одному «потребителю». Канал bounded: буфер ограничен (backpressure —
//! пример 04). `select!` ждёт ПЕРВОЕ готовое событие из нескольких.

use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc;

#[tokio::main]
async fn main() -> Result<()> {
    // 1. Обычная передача: продюсер → канал → потребитель.
    let (tx, mut rx) = mpsc::channel(32);
    let producer = tokio::spawn(async move {
        for i in 0..10 {
            tx.send(format!("сообщение {i}")).await.unwrap();
        }
        // tx упадёт здесь — канал закроется, recv вернёт None.
    });

    let mut got = Vec::new();
    while let Some(msg) = rx.recv().await {
        got.push(msg);
    }
    producer.await?;
    println!("получено {} сообщений: {}", got.len(), got.join(", "));

    // 2. select!: первое готовое событие выигрывает.
    //    Сообщение приходит через 100 мс, тик — каждые 300 мс → побеждает канал.
    let (tx, mut rx) = mpsc::channel::<String>(8);
    tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(100)).await;
        let _ = tx.send("данные с датчика".to_owned()).await;
        // после drop(tx) канал закроется
    });

    let mut ticks = 0u32;
    loop {
        tokio::select! {
            maybe = rx.recv() => {
                match maybe {
                    Some(m) => { println!("первым пришло СООБЩЕНИЕ: {m}"); break; }
                    None    => { println!("канал закрыт до сообщений"); break; }
                }
            }
            _ = tokio::time::sleep(Duration::from_millis(300)) => {
                ticks += 1;
                println!("тик {ticks} (сообщение ещё в пути…)");
                if ticks == 3 { println!("3 тика без сообщения — выходим"); break; }
            }
        }
    }

    println!("готово");
    Ok(())
}
