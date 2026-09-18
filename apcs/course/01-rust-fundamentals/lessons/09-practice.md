# 09: Практика — соберём «мини-проект» из всех тем модуля

> **Цель урока:** свести воедино всё, что наработано: структуры,
> enums, VecDeque, Result, итераторы. У вас будет «кольцо последних
> транзакций» — почти как лог шины в master.rs.

## Что мы строим: BusLog

Этот модуль — основа для понимания master.rs (`trace: VecDeque<TraceEntry>`).

Состояние:
- `VecDeque<TraceEntry>` — последние N записей.
- `cap: usize` — максимум хранимых записей.

Операции:
- `push()` — добавить запись, выкинув старую при переполнении.
- `last()` — последняя запись.
- `history()` — все записи (копия).
- `success_rate()` — доля успешных (ок / всего).

```rust
use std::collections::VecDeque;

#[derive(Debug, Clone, PartialEq)]
struct TraceEntry {
    fc: u8,
    req: String,
    resp: String,
    ok: bool,
    ms: u64,
}

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
            self.trace.pop_front();       // вытесняем самую старую
        }
        self.trace.push_back(e);          // новую — в конец
    }

    fn success_rate(&self) -> f32 {
        if self.trace.is_empty() {
            return 0.0;
        }
        let ok = self.trace.iter().filter(|e| e.ok).count();
        ok as f32 / self.trace.len() as f32
    }
}
```

## Check-точки для вашего кода

**Проверка 1**: после `push` 4 записей в `cap=3`:
```rust
assert_eq!(log.history().len(), 3);
```

**Проверка 2**: `success_rate` при двух ок и одной ошибке ≈ 2/3.

**Проверка 3**: `last_ok` — последняя успешная, или `Err(BusError::Empty)`.

## Какие темы из прошлых уроков задействованы

| Тема                    | Где встречается в примере          |
|-------------------------|-------------------------------------|
| struct + impl           | `TraceEntry`, `BusLog`              |
| VecDeque                | кольцо trace                        |
| move/Copy               | `push(e)` забирает владение         |
| Borrowing               | методы `&self`, `&mut self`         |
| Result + Display        | `last_ok` → `Result`, собственный `BusError` |
| Итераторы               | `.iter().filter().count()` в success_rate |

## Задание (полный стек)

См. `exercises/src/ex09_practice.rs` и `examples/09_practice.rs`.

1. Реализуйте `BusLog` и все методы.
2. Соберите `BusLog` в кольцо и проверьте через тесты.
3. Реализуйте `last_ok()` через `Result`.
4. Сравните своё решение с файлом в `examples/` — должны совпадать.

## Дальше

После урока 09 вы готовы читать `src/workshop.rs` или любой модуль TUI:
темы ownership, borrowing, коллекции, ошибки и потоки покрыты.
Можно приступать к модулю 2: [Embedded Rust](../02-embedded-rust/).