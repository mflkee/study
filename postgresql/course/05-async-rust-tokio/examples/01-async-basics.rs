//! Пример 01: async/await, конкурентность и блокирующие операции.
//!
//! Запуск: `cargo run --example 01-async-basics`
//!
//! Рантайм здесь — `current_thread`: один поток. Это намеренно, чтобы было
//! видно (а) что конкурентность не требует потоков (кооперативная), и
//! (б) как ОДИН блокирующий вызов замораживает ВСЁ.

use anyhow::Result;
use std::time::{Duration, Instant};

/// «Работа»: асинхронная задержка + сообщение.
async fn work(name: &str, delay: Duration) -> String {
    tokio::time::sleep(delay).await;
    format!("{name} готово за {delay:?}")
}

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    // 1. Последовательно: .await ждёт завершения; время = сумма.
    let t = Instant::now();
    let a = work("a", Duration::from_millis(200)).await;
    let b = work("b", Duration::from_millis(200)).await;
    println!("последовательно: {a}; {b}; всего {:?}", t.elapsed());

    // 2. Конкурентно (всё ещё ОДИН поток!): tokio::join! — задачи чередуются.
    let t = Instant::now();
    let (a, b) = tokio::join!(
        work("a", Duration::from_millis(200)),
        work("b", Duration::from_millis(200)),
    );
    println!("join!:            {a}; {b}; всего {:?}", t.elapsed());

    // 3. Блокирующий вызов в async-задаче — беда.
    //    std::thread::sleep останавливает сам поток рантайма: тики замирают.
    let t = Instant::now();
    let ticker = tokio::spawn(async move {
        for i in 1..=6 {
            tokio::time::sleep(Duration::from_millis(100)).await;
            println!("  тик {i} на {:.0} мс", t.elapsed().as_millis());
        }
    });
    std::thread::sleep(Duration::from_millis(350)); // БЛОКИРУЕТ единственный поток
    println!("после блокирующего сна: {:?}, тики пропали", t.elapsed());
    ticker.await?;

    // 4. Правильно: тяжёлую работу — в spawn_blocking (отдельный поток).
    let t = Instant::now();
    let ticker = tokio::spawn(async move {
        for i in 1..=6 {
            tokio::time::sleep(Duration::from_millis(100)).await;
            println!("  тик {i} на {:.0} мс", t.elapsed().as_millis());
        }
    });
    let heavy = tokio::task::spawn_blocking(|| {
        // Симуляция тяжёлой CPU/IO работы: живёт в своём потоке.
        std::thread::sleep(Duration::from_millis(350));
        "тяжёлая работа сделана"
    });
    println!(
        "spawn_blocking: {} ({:?}) — тики идут",
        heavy.await?,
        t.elapsed()
    );
    ticker.await?;

    println!("готово");
    Ok(())
}
