# Лабораторная: пошаговый разбор кода

Пошаговый обход мини-проекта. Каждый шаг — один файл,
одна идея. Цель: **понять, как ядро и прошивка связаны**.

---

## Шаг 1: Ядро — CRC-16

**Файл:** `apcs/small_project_for_study/core/src/crc.rs`

```rust
pub fn append_crc(data: &[u8]) -> Vec<u8>      // «обернуть» кадр
pub fn verify_crc(frame: &[u8]) -> bool         // «проверить» кадр
```

CRC-16 Modbus: полином `0xA001`, инициал `0xFFFF`.
Алгоритм — битовый сдвиг вправо с XOR.

### Упражнение

Запустите `cargo test` в core/ и посмотрите тест `crc_known_vector`:
известный запрос `[01 03 00 00 00 0A]` → CRC `[C5 CD]`.
Проверьте руками: возьмите hex-калькулятор и посчитайте
побайтно — должно совпасть.

---

## Шаг 2: Ядро — dispatch по коду функции

**Файл:** `apcs/small_project_for_study/core/src/registers.rs`

```rust
pub fn dispatch(frame: &[u8]) -> Option<Vec<u8>>
```

Это «мозг» протокола: парсит запрос, выбирает ответ.

| Путь в dispatch | Что происходит |
|-----------------|----------------|
| `FC01 (Read Coils)` | Читает биты из `MiniState` → пакует в байты |
| `FC03 (Read Holding)` | Читает регистр 0 (счётчик) → 2 байта big-endian |
| `FC05 (Write Coil)` | Меняет `state.led`, отвечает «эхо» |
| Неверный FC | → `exception_pdu(0x01)` |
| Неверный адрес | → `exception_pdu(0x02)` |

### Упражнение

Посмотрите тест `full_frame_roundtrip`: он собирает запрос,
передаёт в dispatch, и проверяет ответ. Это та же цепочка,
что работает в реальной прошивке.

**Вопрос для размышления:** почему dispatch принимает `&[u8]`,
а не `MiniFrame` (как в ai32/core)?

> Ответ: мини-проект intentionally проще — никаких структур-обёрток,
> прямая работа с байтами. Это тот же стиль, что в клиенте.

---

## Шаг 3: Ядро — дебаунс

**Файл:** `apcs/small_project_for_study/core/src/debounce.rs`

```rust
pub struct Debounce {
    stable: bool,   // текущее стабильное состояние
    count: usize,   // сколько подряд одинаковых чтений
}
```

`feed(raw)` возвращает `stable`:
если `raw == stable` — сбрасывает счётчик;
если `raw != stable` — считает подряд идущие.
При `count >= SAMPLES` — переключает.

### Упражнение

Тест `single_glitch_is_ignored`: один «0» среди единиц
не меняет результат. Представьте, что контакт дребезжит
10 раз за 5 мс — `SAMPLES=5` при `POLL_MS=20` означает,
что нужно 5 подряд одинаковых (100 мс стабильности).

**TODO (расширение):** добавьте `hysteresis_ms` —
переключение только через задержку после фронта.

---

## Шаг 4: Прошивка — GPIO-обёртки

**Файл:** `apcs/small_project_for_study/fw/src/hw.rs`

```rust
pub struct Led<'d, P: OutputPin>  { ... }
pub struct Button<'d, P: InputPin> { ... }
```

- `Led` — забирает пин во владение (`PinDriver::output`)
- `Button` — забирает пин, ставит `Pull::Up`
- `toggle()` — «проблеск» стартовой загрузки

### Упражнение

Посмотрите `main.rs`: стартовый цикл `toggle()` 3 раза
с задержкой 120 мс. Это «приветствие» —
если мигает при загрузке, значит, прошивка живая.

**TODO:** замените 3 вспышки на SOS (три короткие,
три длинные, три короткие).

---

## Шаг 5: Прошивка — RTU slave loop

**Файл:** `apcs/small_project_for_study/fw/src/rtu.rs`

```rust
pub fn slave_loop(mut uart, state: MiniState) -> !
pub fn handle_frame(state: &MiniState, frame: &[u8]) -> Option<Vec<u8>>
```

`slave_loop` — бесконечный цикл:
1. Читает 1 байт из UART (ждёт `slave_id`)
2. Если не наш `slave_id` — пропускает
3. Читает остальной кадр (PDU + CRC)
4. Проверяет CRC; если неверный — пропускает
5. `handle_frame` → `registers::dispatch` → ответ

### Упражнение

Заметьте: `slave_loop` **никогда не возвращает** (-> `!`).
Это значит, что весь цикл опроса работает в фоновом потоке.

**TODO:** добавьте `log::debug!` при каждом пропущенном
кадре (неверный slave_id или неверный CRC).

---

## Шаг 6: Мейнлуп

**Файл:** `apcs/small_project_for_study/fw/src/main.rs`

```rust
loop {
    let pressed = debounce.feed(button.is_pressed());
    if pressed && !prev_pressed {
        state.write().unwrap().presses += 1;
    }
    {
        let mut s = state.write().unwrap();
        s.button = pressed;
        if s.led { led.on() } else { led.off() }
    }
    FreeRtos::delay_ms(POLL_MS);
}
```

Каждые 20 мс:
1. Читаем кнопку, фильтруем дребезг
2. На фронте «нажали» — инкрементируем счётчик
3. Синхронизируем LED с состоянием из Modbus
4. Засыпаем на 20 мс

### Упражнение

Синхронизация LED происходит **внутри блока `{ let mut s = ... }`**:
setState + apply + drop. Это важно —
если сделать `led.on()` вне блока, состояние может «моргнуть».

**TODO:** добавьте `log::info!("p={}", presses)` после инкремента —
увидите в консоли при нажатии кнопки.

---

## Шаг 7: Python-клиент

**Файл:** `apcs/small_project_for_study/scripts/mini_client.py`

```bash
python3 mini_client.py /dev/ttyUSB0 read       # опрос
python3 mini_client.py /dev/ttyUSB0 led on     # включить LED
python3 mini_client.py /dev/ttyUSB0 poll       # цикл
```

Клиент строит Modbus RTU-фрейм вручную (без библиотеки):
`build_request(pdu)` → `transact(port, pdu)` → `read_exact`.

### Упражнение

Запустите `mini_client.py /dev/ttyUSB0 read` и одновременно
нажмите кнопку на плате. Счётчик нажатий должен вырасти.

**TODO:** добавьте команду `python3 mini_client.py ... write_holding 0 42`
для записи значения в HOLDING-регистр (ожидаем exception 0x04).

---

## Итого: что мы прошли

| Шаг | Где | Что |
|-----|-----|-----|
| 1 | `core/src/crc.rs` | CRC-16 Modbus — как считается и проверяется |
| 2 | `core/src/registers.rs` | Dispatch по FC — превращаем запрос в ответ |
| 3 | `core/src/debounce.rs` | Дебаунс — стабилизация механической кнопки |
| 4 | `fw/src/hw.rs` | GPIO — светодиод (output) и кнопка (input + pull-up) |
| 5 | `fw/src/rtu.rs` | RTU slave loop — чтение/проверка/ответ |
| 6 | `fw/src/main.rs` | Мейнлуп — опрос + синхронизация + логика |
| 7 | `scripts/mini_client.py` | ИВК-клиент — ручное построение Modbus-фреймов |

---

## Следующий шаг

Если прошли все 7 шагов — мини-проект пройден.
Дальше можно:

- Вернуться в [модуль 09](../../09-ai32-module/) — масштабирование до 24 каналов
- Или в [модуль 07](../../07-gateway-project/) — TCP-шлюз
