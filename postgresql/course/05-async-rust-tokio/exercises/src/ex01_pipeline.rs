//! Упражнение 01: «Пайплайн опроса датчиков» — producer → bounded-channel → consumer.
//!
//! **Кейс:** 2 (телеметрия) / задел 7 (очередь задач). **Модуль:** 05. **Время:** 2 ч.
//!
//! ТЗ: смоделировать опрос датчиков и запись в БД без самой БД (модуль 06
//! подставит реальную запись). Производитель шлёт значения в канал раз в
//! `poll_interval`; потребитель «обрабатывает» каждое значение за
//! `process_delay` (модель записи). Канал — **bounded**: если потребитель
//! не успевает, производитель ждёт (backpressure), очередь не растёт.
//!
//! Заготовка: три `todo!()` — `produce`, `consume`, `run`. `main` нет —
//! проверка через тесты (`cargo test ex01_`) и пример `examples/05-pipeline`
//! как референс (там добавлен ещё грациозный останов по Ctrl-C).
//!
//! Решение — в `solutions/ex01_pipeline.rs` (после своей попытки).

use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc::{Receiver, Sender};

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
    pub produced: u64,  // сколько значений отправлено производителем
    pub processed: u64, // сколько обработано потребителем
}

/// Производитель: каждые `poll_interval` отправляет одно значение, всего `limit`.
/// `send(...).await` на bounded-канале ждёт, пока потребитель освободит место
/// (это и есть backpressure).
pub async fn produce(_tx: Sender<SensorReading>, _poll_interval: Duration, _limit: u64) {
    todo!("цикл seq в 0..limit: sleep(poll_interval), tx.send(SensorReading::new(seq, ...)).await")
}

/// Потребитель: читает значения, «обрабатывает» за `process_delay`
/// (модель записи в БД) и возвращает число обработанных.
/// Завершается, когда канал закрыт (все senders упали).
pub async fn consume(_rx: Receiver<SensorReading>, _process_delay: Duration) -> u64 {
    todo!("while let Some(r) = rx.recv().await: sleep(process_delay), счётчик += 1")
}

/// Прогон пайплайна: spawn производителя, потребитель — в текущей задаче;
/// после закрытия канала потребитель «дренирует» остаток очереди.
/// Возвращает отчёт: produced == limit, processed == produced (ничего не теряется).
pub async fn run(
    _capacity: usize,
    _poll_interval: Duration,
    _process_delay: Duration,
    _limit: u64,
) -> Result<PipelineReport> {
    todo!("mpsc::channel(capacity), tokio::spawn(produce(tx, ...)), consume(rx).await, отчёт")
}
