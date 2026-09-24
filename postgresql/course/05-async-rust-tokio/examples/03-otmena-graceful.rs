//! Пример 03: отмена и грациозный останов воркера (Ctrl-C / SIGINT).
//!
//! Запуск:
//!   cargo run --example 03-otmena-graceful -- --limit 8     # остановится сам
//!   cargo run --example 03-otmena-graceful                  # ждёт Ctrl-C
//!
//! Проверка «без пальца»: `timeout -s INT 2 cargo run --example 03-otmena-graceful`
//! (timeout пошлёт SIGINT через 2 секунды — увидишь грациозный дренаж).
//!
//! Как устроен грациозный останов:
//! 1) потребитель получил SIGINT → отправляет СТОП-сигнал производителю
//!    (watch-канал) и выходит из select!;
//! 2) производитель прекращает слать новые значения (tx падает → канал закроется);
//! 3) потребитель ДОЧИТЫВАЕТ остаток очереди (дренаж) — ничего не теряется;
//! 4) присоединяемся к задаче производителя и выводим итог.

use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc;
use tokio::sync::watch;

#[tokio::main]
async fn main() -> Result<()> {
    // Режим лимита для автономных демонстраций и тестов.
    let limit: Option<u64> = match std::env::args().nth(1).as_deref() {
        Some("--limit") => std::env::args()
            .nth(2)
            .map(|s| s.parse().expect("--limit <число>")),
        _ => None,
    };

    let (tx, mut rx) = mpsc::channel::<u64>(16);
    // Стоп-сигнал: watch=false → true означает «прекрати слать».
    let (stop_tx, mut stop_rx) = watch::channel(false);

    let producer = tokio::spawn(async move {
        let mut seq = 0u64;
        loop {
            // biased: стоп-ветка проверяется ПЕРВОЙ — сигнал не «затеряется»
            // среди постоянно готовой ветки сна (грабли урока 03).

            tokio::select! {
                biased;
                _ = stop_rx.changed() => {
                    eprintln!("  производитель остановлен (стоп-сигнал)");
                    break;
                }
                _ = tokio::time::sleep(Duration::from_millis(50)) => {}
            }
            if tx.send(seq).await.is_err() {
                break; // канал закрыт — потребитель уже ушёл
            }
            seq += 1;
            if Some(seq) == limit {
                break; // режим демо/теста
            }
        }
    });

    let mut processed = 0u64;

    // ВАЖНО (грабли урока 03): future сигнала создаётся ОДИН раз и живёт
    // всю программу (tokio::pin!). Нельзя пересоздавать ctrl_c() в цикле:
    // между drop старого и регистрацией нового future сигнал теряется.
    let sigint = tokio::signal::ctrl_c();
    tokio::pin!(sigint);

    loop {
        tokio::select! {
            biased;
            _ = &mut sigint => {
                println!(
                    "SIGINT пойман: останавливаем производителя и дренируем очередь…"
                );
                let _ = stop_tx.send(true); // 1) стоп-сигнал
                break; // 2) выход из select!-цикла
            }
            maybe = rx.recv() => match maybe {
                Some(seq) => {
                    tokio::time::sleep(Duration::from_millis(20)).await; // «запись»
                    processed += 1;
                    eprintln!("  обработано {seq}");
                }
                None => { println!("канал закрыт — данных больше нет"); break; }
            },
            _ = tokio::signal::ctrl_c() => {
                println!(
                    "SIGINT пойман: останавливаем производителя и дренируем очередь…"
                );
                let _ = stop_tx.send(true); // 1) стоп-сигнал
                break; // 2) выход из select!-цикла
            }
        }
    }

    // 3) Дренаж: всё, что уже отправлено производителем, обрабатываем.
    while let Some(seq) = rx.recv().await {
        tokio::time::sleep(Duration::from_millis(20)).await;
        processed += 1;
        eprintln!("  дренаж: обработано {seq}");
    }

    producer.await?; // 4) производитель точно завершился
    println!("итог: обработано {processed} значений; очередь пуста, выходим с кодом 0");
    Ok(())
}
