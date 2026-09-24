//! Общая библиотека модуля 05 «Асинхронность».
//!
//! Модуль изучает async-механику, нужную для БД-слоя (модуль 06):
//! задачи tokio, каналы, `select!`, отмена и backpressure. Кода с БД
//! здесь нет — «запись в БД» моделируется асинхронной задержкой.
//!
//! Упражнение включено в библиотеку, чтобы его можно было тестировать
//! (`cargo test ex01_`). До решения содержит `todo!()` — тесты падают,
//! после решения — проходят (см. exercises/README.md).

#[path = "../exercises/src/ex01_pipeline.rs"]
pub mod ex01_pipeline;

#[cfg(test)]
mod tests {
    use super::ex01_pipeline::{produce, run, PipelineReport};
    use std::time::Duration;

    // --- проверки пайплайна ---

    #[tokio::test]
    async fn ex01_limit_produces_exactly_limit() {
        // Производитель с limit=5 шлёт ровно 5 значений.
        let (tx, mut rx) = tokio::sync::mpsc::channel(16);
        tokio::spawn(async move {
            produce(tx, Duration::from_millis(1), 5).await;
        });
        let mut n = 0u64;
        while rx.recv().await.is_some() {
            n += 1;
        }
        assert_eq!(n, 5, "производитель должен отправить ровно лимит");
    }

    #[tokio::test]
    async fn ex01_run_processes_everything() {
        // Короткий пайплайн: ничего не должно теряться (drain в конце).
        let report = run(4, Duration::from_millis(1), Duration::from_millis(2), 20)
            .await
            .expect("пайплайн должен завершиться без ошибок");
        assert_eq!(report.produced, 20);
        assert_eq!(report.processed, 20, "все отправленные значения обработаны");
    }

    #[tokio::test]
    async fn ex01_backpressure_no_loss() {
        // Потребитель медленнее производителя (delay > interval, маленький
        // канал) — bounded-канал заставляет производителя ждать, и всё
        // равно обрабатывается каждый элемент.
        let report = run(2, Duration::from_millis(1), Duration::from_millis(5), 30)
            .await
            .expect("ok");
        assert_eq!(report.produced, 30);
        assert_eq!(report.processed, 30, "backpressure не должен терять данные");
    }

    #[tokio::test]
    async fn ex01_zero_limit_no_work() {
        let report = run(2, Duration::from_millis(1), Duration::from_millis(1), 0)
            .await
            .expect("ok");
        assert_eq!(report, PipelineReport::default(), "пустой пайплайн");
    }
}
