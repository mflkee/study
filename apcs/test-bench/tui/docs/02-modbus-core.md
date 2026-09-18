# 02. Ядро Modbus: `crc.rs` и `frames.rs`

Эти два модуля — самый низкий слой. Они ничего не знают ни про порты, ни про
TUI: только байты. Всё, что выше, опирается на них.

## `crc.rs` — CRC-16 Modbus (Cyclic Redundancy Check) (69 строк)

Modbus RTU (Remote Terminal Unit) защищает каждый кадр (frame) двумя байтами
CRC-16. Алгоритм использует полином (polynomial) `0x8005`, но «отражённый»
(LSB-first) вариант, поэтому в коде константа выглядит как `0xA001`.

### `crc16` — главный алгоритм (`crc.rs:4-17`)

```rust
pub fn crc16(data: &[u8]) -> u16 {
    let mut crc: u16 = 0xFFFF;
    for &byte in data {
        crc ^= byte as u16;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    crc
}
```

Разбор по шагам:

1. Стартовое значение `0xFFFF` (это табличная хитрость Modbus-варианта).
2. Для каждого байта: XOR (exclusive OR — «исключающее ИЛИ») байта в младшие
   8 бит регистра.
3. Потом 8 раз (по одному на бит): если младший бит `1` — сдвиг вправо (right
   shift) и XOR с `0xA001`; иначе просто сдвиг вправо.
4. Порядок байтов (byte order) на шине — **младший первым**: заранее
   посчитанный CRC `0x0A84` кладётся в кадр как `84 0A` (см. тест
   `crc_known_values`, `crc.rs:52-57`, и `append` ниже).

«Отражённость» означает, что полином повёрнут зеркально: прямой `0x8005`
(биты 1_0000_0000_0000_0101) превращается в `0xA001` (побитовый реверс
16-битного значения). Это стандартная особенность алгоритма CRC-16/Modbus,
а не ошибка: так делают почти все реальные устройства.

### `verify` — проверка кадра (`crc.rs:20-27`)

```rust
pub fn verify(frame: &[u8]) -> bool {
    if frame.len() < 4 { return false; }
    let data = &frame[..frame.len() - 2];
    let rcvd = u16::from_le_bytes([frame[frame.len() - 2], frame[frame.len() - 1]]);
    rcvd == crc16(data)
}
```

- `frame.len() < 4` — минимальный валидный кадр `[addr][fc][..pdu][crc2]`
  не может быть короче 4 байт.
- `&frame[..len-2]` — всё, кроме последних двух байт (это данные).
- `from_le_bytes` — читает CRC как little-endian (LE — «младший байт первым»),
  как его и клал `append`.

### `append` — добавить CRC в кадр (`crc.rs:30-33`)

```rust
pub fn append(frame: &mut Vec<u8>) {
    let crc = crc16(frame);
    frame.extend_from_slice(&crc.to_le_bytes());
}
```

Мутирует `Vec` на месте, что позволяет строить кадр по шагам.

### `to_hex` — человекочитаемые байты (`crc.rs:36-45`)

`01 03 00 00 00 01 84 0A` — так показываются кадры во вкладке Bus. Резервирует
`len * 3` байт строки заранее (каждый байт = 2 hex + пробел), чтобы не
аллоцировать по чуть-чуть.

## `frames.rs` — кадры, PDU, ошибки (245 строк)

### Константы функций и размер (`frames.rs:5-15`)

`FC_READ_COILS 0x01`… `FC_WRITE_MULTI_REGS 0x10` — это коды функций (Function
Code, FC), `MAX_FRAME = 256` (максимум RTU-кадра по спеке с CRC). У многих
CTF/задач фигурирует чтение inputs — это `FC_READ_INPUT 0x04`, не путайте с
holding `0x03`.

### `ModbusError` — типизация ошибок (`frames.rs:18-55`)

```rust
pub enum ModbusError {
    Timeout, IllegalFunction, IllegalAddress, IllegalValue,
    ServerFailure, Ack, Busy, Nack,
    GatewayNoRoute, GatewayTargetFailed,
    Unknown(u8), Io(String),
}
```

Два интересных момента:

- **Исключения Modbus** (exceptions; код ответа `[fc|0x80][code]`)
  превращаются в варианты с говорящими именами. `parse_response` мапит (map —
  сопоставляет) код 0x01..0x0B, а всё остальное — в `Unknown(u8)` (код
  сохраняется).
- `Io(String)` — транспортные/I/O ошибки (не открылся порт, порвался поток),
  которые не являются «ответом устройства».

`Display` выводит человекопонятный текст, который попадает в лог и статус;
`impl Error` (`frames.rs:55`) позволяет использовать `?` в `FnOnce -> Result<..>`
задачах.

### `build_request` — кадр запроса (`frames.rs:58-65`)

```rust
pub fn build_request(slave_id: u8, fc: u8, pdu: &[u8]) -> Vec<u8> {
    let mut frame = Vec::with_capacity(pdu.len() + 4);
    frame.push(slave_id);
    frame.push(fc);
    frame.extend_from_slice(pdu);
    crc::append(&mut frame);
    frame
}
```

`[slave][fc][pdu][crc_lo][crc_hi]`. `+4` = addr + fc + 2 байта CRC. Именно так
выглядит любой исходящий RTU-запрос. Проверка известным кадром — тест
`request_frame_matches_known` (`frames.rs:211-215`): для `01 03 00 00 00 01`
ожидаем `84 0A` (это CRC 0x0A84).

### `parse_response` — разбор ответа (`frames.rs:68-101`)

Порядок проверок важен, это хороший образец защитного кода:

1. `frame.len() < 5` → `Timeout` (слишком коротко даже для exception-ответа).
2. `!crc::verify(frame)` → `BadCrc`.
3. `frame[0] != slave_id` → «unexpected slave 0x..» (чужой ответ — шина общая!).
4. `frame[1] == fc | 0x80` → exception: `frame[2]` превращается в конкретный
   вариант `ModbusError` (таблица `0x01..0x0B`, остальное — `Unknown`).
   Замечание: `fc | 0x80` — установка старшего бита; так slave показывает
   «это ошибка на вашу функцию N».
5. `frame[1] != fc` → «unexpected FC» (устройство ответило другой функцией).
6. Иначе возвращается PDU: `frame[2..len-2]` — отбрасываем addr, fc и CRC.

Проверочный тест `parse_exception` (`frames.rs:228-231`) разбирает кадр
`01 83 02 C0 F1` на `IllegalAddress`.

### PDU-билдеры (builders — функции-сборщики)

- `read_pdu(start, count)` (`frames.rs:104-109`): `[start_hi][start_lo][count_hi][count_lo]` — big-endian (BE — «старший байт первым»).
- `write_single_pdu(addr, value)` (`frames.rs:154-159`): `[addr_hi][addr_lo][val_hi][val_lo]`.
- `write_multi_coils_pdu(start, values)` (`frames.rs:166-183`):
  `[start_hi][start_lo][qty_hi][qty_lo][byte_count][..биты]`. Биты упаковываются
  (packing) в байты, младший бит — первая катушка (coil). Помечен
  `#[allow(dead_code)]`, потому что интерфейс TUI оперирует одной катушкой
  напрямую.
- `write_multi_regs_pdu(start, values)` (`frames.rs:189-198`): тот же паттерн
  для регистров. Тоже справочный.

Эти два «многозаписных» билдера вызывают вопросы у новичков: «зачем мёртвый
код?» — они остаются как справочник функций и целевые точки для лабораторных
и будущих фич («записать сразу 10 катушек»).

### Разбор ответов-данных

- `parse_read_registers(pdu, expected)` (`frames.rs:112-130`):
  `[byte_count][..данные]`. Проверяет `byte_count == expected * 2`, затем через
  `as_chunks::<2>()` нарезает данные на пары и читает каждую как BE `u16`
  (big-endian, старший байт первым).
  `as_chunks` появился в стабильном Rust (1.80+) и избавляет от ручного
  счётчика — это современный идиоматичный способ.
- `parse_read_bits(pdu, expected)` (`frames.rs:133-151`):
  `[byte_count][..биты]`. Проходит по байтам, из каждого берёт 8 бит (младший
  первым), останавливается, как только набрал `expected` значений. Защита от
  `expected` > 8×byte_count — срез (slice) попадает в `Vec<bool>` нужной длины.

### `float_from_regs` — IEEE-754 из пары регистров (`frames.rs:201-204`)

```rust
pub fn float_from_regs(hi: u16, lo: u16) -> f32 {
    let raw = ((hi as u32) << 16) | (lo as u32);
    f32::from_bits(raw)
}
```

Прошивка ESP32 кладёт float32 (число с плавающей точкой одинарной точности,
single precision) как два BE-регистра (старшее слово первым) —
то же и в эмуляторе (`emulator.rs:99-101`). Наш мастер читает 60 input-регистров
(Input Registers) и склеивает их в 30 float: `input.as_chunks::<2>()` +
`float_from_regs` (`worker.rs:374-376`). Тест `float64_from_regs`
(`frames.rs:234-238`) проверяет, что `0x42CA A666` == 101.325 kPa.

## Тесты модуля (`frames.rs:206-245`)

`request_frame_matches_known`, `parse_valid_response`, `parse_exception`,
`float64_from_regs`, `multi_regs_pdu`. Плюс в `crc.rs:47-69` — `crc_known_values`
и `append_and_verify_roundtrip`.

Имя функции на человекоязыке живёт НЕ здесь, а в `master::fc_name`
(`master.rs:37-49`) — этот модуль про байты, «READ INPUT REGISTERS (0x04)» —
слой выше.

---

**Вопросы для самопроверки**

1. Почему в `crc16` константа `0xA001`, а не `0x8005`? (полином отражён).
2. В каком порядке байтов CRC лежит в кадре? (`to_le_bytes` → младший первым).
3. Сколько минимум байт в кадре, чтобы `verify` вернул true? (4: addr, fc, ... , crc2).
4. Что означает бит `0x80` в первом байте ответа? (exception на запрошенную FC).
5. Почему `parse_response` отдельно проверяет адрес slave? (шина общая, ответ мог прийти от другого устройства).
6. Какова максимальная длина кадра и где она задана? (`MAX_FRAME=256`, `frames.rs:15`).
7. Что произойдёт, если `byte_count` в ответе ≠ `expected*2`? (`Io("bad read response: byte_count=…, data=…")`, `frames.rs:117-122`).