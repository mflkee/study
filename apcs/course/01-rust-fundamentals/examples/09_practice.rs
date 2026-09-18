//! Урок 09: Сборка финального — мини-проект.
//!
//! Запуск: `cargo run --example 09_practice`
//!
//! Здесь мы собираем вместе всё: struct + enum + VecDeque ring buffer +
//! Result + итераторы. Это "ядро" мого, что делает SerialMaster (master.rs):
//! очередь последних транзакций.

use std::collections::VecDeque;

/// Запись лога шины (упрощённый TraceEntry, master.rs:19-34).
#[derive(Debug, Clone, PartialEq)]
struct TraceEntry {
    fc: u8,
    req: String,     // hex-строка
    resp: String,
    ok: bool,
    ms: u64,
}

impl TraceEntry {
    fn ok(fc: u8, req: &str, resp: &str, ms: u64) -> Self {
        Self { fc, req: req.into(), resp: resp.into(), ok: true, ms }
    }
    fn fail(fc: u8, req: &str, ms: u64) -> Self {
        Self { fc, req: req.into(), resp: String::new(), ok: false, ms }
    }
}

/// Кольцо последних N транзакций (как trace: VecDeque<TraceEntry> в master.rs:56).
struct BusLog {
    trace: VecDeque<TraceEntry>,
    cap: usize,
}

impl BusLog {
    fn with_cap(cap: usize) -> Self {
        Self { trace: VecDeque::new(), cap }
    }

    fn push(&mut self, e: TraceEntry) {
        if self.trace.len() >= self.cap {
            self.trace.pop_front();
        }
        self.trace.push_back(e);
    }

    fn last(&self) -> Option<&TraceEntry> {
        self.trace.back()
    }

    fn history(&self) -> Vec<TraceEntry> {
        self.trace.iter().cloned().collect()
    }

    fn success_rate(&self) -> f32 {
        if self.trace.is_empty() {
            return 0.0;
        }
        let ok = self.trace.iter().filter(|e| e.ok).count();
        ok as f32 / self.trace.len() as f32
    }
}

/// Простая "транзакция": собрать кадр, посчитать CRC (упрощённо: XOR-сумма),
/// вернуть Result. ? здесь имитируется матчем.
fn simulate_request(slave: u8, fc: u8, payload: &[u8]) -> Result<String, String> {
    if slave == 0 || slave > 247 {
        return Err(format!("invalid slave id {slave}"));
    }
    let crc: u8 = payload.iter().fold(0u8, |acc, &b| acc.wrapping_add(b));
    let mut s = format!("{slave:02X} {fc:02X}");
    for &b in payload {
        s.push_str(&format!(" {:02X}", b));
    }
    s.push_str(&format!(" {:02X}", crc));
    Ok(s)
}

fn main() {
    let mut log = BusLog::with_cap(3);

    for i in 0..4 {
        let req = simulate_request(0x01, 0x03, &[0x00, i as u8]).unwrap();
        log.push(TraceEntry::ok(0x03, &req, "01 03 02 00 00", i * 5));
    }

    assert_eq!(log.last().unwrap().ms, 15);
    assert_eq!(log.history().len(), 3); // cap 3 — вытеснили первый

    log.push(TraceEntry::fail(0x03, "01 03 00 00 01", 0));
    assert_eq!(log.history().len(), 3);
    assert!(log.success_rate() < 1.0);

    match simulate_request(0x01, 0x03, &[0x00, 0x01]) {
        Ok(frame) => println!("frame: {frame}"),
        Err(e) => println!("error: {e}"),
    }

    println!("09_practice: OK");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ring_buffer_drops_oldest() {
        let mut log = BusLog::with_cap(2);
        log.push(TraceEntry::fail(1, "a", 0));
        log.push(TraceEntry::fail(1, "b", 0));
        log.push(TraceEntry::fail(1, "c", 0));
        assert_eq!(log.history().len(), 2);
        assert_eq!(log.history()[0].req, "b");
    }

    #[test]
    fn slave_id_range() {
        assert!(simulate_request(0, 0x03, &[]).is_err());
        assert!(simulate_request(1, 0x03, &[0x00]).is_ok());
        assert!(simulate_request(248, 0x03, &[]).is_err());
    }
}