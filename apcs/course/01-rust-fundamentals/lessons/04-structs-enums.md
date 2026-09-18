# 04: Structs, Enums, Pattern Matching

> **Цель урока:** научиться хранить данные в структурах, описывать
> варианты состояния через enum и обрабатывать их через `match`.
> Это основа типов данных в TUI (`ModbusError`, `DataType`, `Event`,
> `TraceEntry` — в общем, все наши типы).

## Struct — описание «коробки»

```rust
// Простая структура
struct VirtualSensor {
    name: String,
    base: f32,
    amplitude: f32,
    period_s: f32,
    enabled: bool,
}

// Создание
let s = VirtualSensor {
    name: "boiler_circuit".to_string(),
    base: 20.0,
    amplitude: 5.0,
    period_s: 60.0,
    enabled: true,
};
```

### Методы (impl-блок)

```rust
impl VirtualSensor {
    /// конструктор
    fn new(name: &str, base: f32, amplitude: f32, period_s: f32) -> Self {
        Self {
            name: name.to_string(),
            base,
            amplitude,
            period_s,
            enabled: true,
        }
    }

    /// вычисление текущего значения (как в emulator.rs:54-60)
    fn value(&self, now_secs: f64) -> f32 {
        if !self.enabled {
            return self.base;
        }
        let t = (now_secs / self.period_s) as f32;
        self.base + self.amplitude * t.sin()
    }
}
```

## Enum — «тип как набор вариантов»

```rust
enum DataType {
    Temperature,
    Pressure,
    Flow,
}
```

### Варианты могут нести данные

```rust
enum ReadRequest {
    Registers { start: u16, count: u16 },   // именованные поля
    Coils(u16, bool),                        // кортеж
    Single(),                                // без данных
}
```

Это называется **алгебраическим типом данных** (ADT) — enum «почти как
struct, но выбор один из».

### match — исчерпывающее ветвление

```rust
fn describe(req: &ReadRequest) -> String {
    match req {
        ReadRequest::Registers { start, count } =>
            format!("registers at {start}, {count} items"),
        ReadRequest::Coils(addr, state) =>
            format!("coil #{addr} = {state}"),
        ReadRequest::Single() => "single".to_string(),
    }
}
```

Компилятор **проверит**, что все ветки перечислены. Добавили вариант
в enum — и компилятор заставит обновить все `match`.

## if let / while let

Для «проверить один вариант» — чтобы не писать пустые ветки `_ => {}`:

```rust
if let Some(x) = map.get(&addr) {
    println!("value {x}");
} else {
    println!("address empty");
}
```

## Реальный код в TUI

- `frames.rs:18-33` — `enum ModbusError` с вариантами-причинами ошибок.
- `emulator.rs:11-19` — `enum DataType` для датчиков, плюс метод `.label()`.
- `master.rs:19-34` — `struct TraceEntry` для лога шины.
- `match` в `fc_name` (master.rs:37-49) — сопоставление числа → строки.

## Задание (проект: тип запроса)

См. `exercises/src/ex04_structs_enums.rs`.

1. Создайте `enum ReadRequest` с вариантами с данными.
2. Напишите `describe()` через `match`.
3. Добавьте проверку через `if let`, чтоб забрать только `Coils`.
4. Объясните, что изменится в `match`, если добавить третий вариант.

## Шпаргалка

| Тип      | Когда использовать                                   |
|----------|------------------------------------------------------|
| struct   | «коробка» связанных полей                             |
| enum     | «один из вариантов состояния» — например ошибки       |
| match    | исчерпывающая обработка всех вариантов (без `_`)      |
| if let   | лёгкая проверка одного варианта                       |

## Следующий урок

[05-traits.md](05-traits.md) — общее поведение для разных типов.