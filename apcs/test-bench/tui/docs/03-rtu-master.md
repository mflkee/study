# 03. Modbus RTU мастер: `master.rs` (219 строк)

Это «рука» TUI, которая ходит по RS-485/USB и опрашивает (polling — поллинг,
периодический опрос) реальные устройства. Организован как классический
**master** (мастер): один запрос за раз, с таймаутом (timeout) и логом каждой
транзакции (transaction — обмена «запрос+ответ»).

## Константы (`master.rs:12-15`)

- `RESPONSE_WAIT = 200 мс` — сколько ждём **первый байт** ответа.
- `TRACE_CAP = 100` — сколько транзакций держим в «логе шины» (trace, кольцевой
  буфер / ring buffer: новые записи вытесняют старые).

## `TraceEntry` — одна строка вкладки Bus (`master.rs:18-34`)

```rust
pub struct TraceEntry {
    pub transport: &'static str, // "RTU" или "TCP"
    pub fc: String,              // "READ INPUT REGISTERS (0x04)"
    pub req: String,             // hex с CRC
    pub resp: String,            // hex с CRC (пусто при ошибке)
    pub ok: bool,
    pub err: Option<String>,
    pub ms: u64,
}
```

Этот тип общий для всех ролей: RTU-мастер (`master.rs`), TCP-мастер
(`tcp_master.rs`) и TCP-сервер (`tcp_server.rs`) кладут транзакции именно так,
поэтому в одной вкладке Bus можно сравнить RTU и TCP на равных.

## `fc_name` — имя функции (`master.rs:37-49`)

`match` по коду → строка. Неизвестный код → `FUNCTION 0x..`. Используется
при формировании каждой записи трейса.

## `SerialMaster` — обёртка над портом (`master.rs:52-57`)

```rust
pub struct SerialMaster {
    port: Box<dyn serialport::SerialPort>,
    slave_id: u8,
    trace: VecDeque<TraceEntry>,
}
```

Стоит обратить внимание на `Box<dyn SerialPort>` — trait object (объект-трейт,
динамическая диспетчеризация), потому что `serialport` возвращает разные
реализации (USB, Bluetooth и т.д.), а нам нужен один конкретный тип в поле.

## `open` — открытие порта 8N1 (`master.rs:61-78`)

8N1 — параметры UART: 8 data bits (бит данных), no parity (без чётности),
1 stop bit (стоповый бит):

```rust
pub fn open(port_name: &str, baud: u32, slave_id: u8) -> Result<Self, ModbusError> {
    let mut port = serialport::new(port_name, baud)
        .timeout(RESPONSE_WAIT)
        .data_bits(serialport::DataBits::Eight)
        .stop_bits(serialport::StopBits::One)
        .parity(serialport::Parity::None)
        .open()...;
    let _ = port.write_request_to_send(false);
    let _ = port.write_data_terminal_ready(false);
    ...
}
```

Два самых важных момента:

1. **`.timeout(RESPONSE_WAIT)`** — таймаут на каждый `read`. Этот механизм и
   даёт «конец кадра»: между байтами одного кадра пауз нет, а между кадрами —
   есть (T1.5/T3.5 по спеке). Таймаут на чтении означает «кадра больше нет».
2. **`write_request_to_send(false)` / `write_data_terminal_ready(false)`** —
   снимаем сигналы DTR (Data Terminal Ready) / RTS (Request To Send). На многих
   ESP-платах DTR/RTS подключаются к EN/GPIO0 и вызывают **auto-reset**
   (автоматический сброс/перезагрузку чипа) при каждом открытии порта. Для
   Modbus по UART0 это не нужно, поэтому быстро гасим.

## `transact` — ядро: один запрос ≈ одна транзакция (`master.rs:111-174`)

Алгоритм, шаг за шагом:

1. `build_request` — собрать `[addr][fc][pdu][crc]` (`master.rs:112`).
2. `t0 = Instant::now()` — засечь время (оно уходит в `TraceEntry.ms`).
3. `flush()` → `write_all` → `flush()`: очистить приёмный буфер (buffer),
   отправить, протолкнуть в порт (`master.rs:115-123`).
4. Ожидание первого байта с дедлайном `RESPONSE_WAIT` (deadline — крайний
   срок ожидания):
   ```rust
   let deadline = Instant::now() + RESPONSE_WAIT;
   if len == 0 && Instant::now() >= deadline {
       // Timeout
   }
   ```
   Тонкость (`master.rs:129-155`): после первого байта кадр читается, пока
   порт не вернёт `TimedOut`/`WouldBlock` — это сигнал «пауза между кадрами,
   кадр завершён». Если первый байт так и не пришёл — `ModbusError::Timeout`
   с записью в трейс.
5. `crc::verify(f)` — если CRC не сошёлся, аппаратно это могло быть из-за
   «прилипших хвостов» предыдущего кадра; пробуем повторно разобрать —
   в текущей версии это просто ошибка `BadCrc` + запись в трейс
   (`master.rs:163-168`).
6. `frames::parse_response(f, self.slave_id, fc)` — отдаёт PDU, либо
   `ModbusError` (в т.ч. исключения, exceptions!). Результат тоже идёт в трейс
   (`master.rs:170-173`).

Весь путь одной транзакции: `transact` → `build_request` → (физика) →
`verify` → `parse_response`. Тривиальная причина ошибок на реальном железе —
записанная в трейс невыполненная транзакция: там видно, до какого шага
дошло (нет ответа — `Timeout`/`BadCrc`; есть `01 84 02` — устройство вернуло
exception 0x02).

## Высокоуровневые функции (`master.rs:178-218`)

Знакомый API, который уже не думает про CRC и адреса:

- `read_holding_registers(start, count)` (`master.rs:178-186`) /
  `read_input_registers(start, count)` (`master.rs:188-192`) —
  собирают `read_pdu`, зовут `transact(FC_.., pdu)`, разбирают ответ через
  `parse_read_registers`.
- `read_coils` — то же для катушек, но через `parse_read_bits`
  (`master.rs:194-198`).
- `write_single_register(addr, value)` — `write_single_pdu`, транзакция,
  проверка длины эха (echo — ответ-подтверждение, повторяющий запрос;
  `resp.len() < 4` → «short write echo»).
- `write_single_coil(addr, bool)` — главный трюк: катушка (coil) **не** пишется
  как `true`/`false`. По спеке значение только `0xFF00` (ON) или `0x0000` (OFF)
  (`master.rs:211`). Именно поэтому в `frames::write_single_pdu` кладётся
  `0xFF00`, а сетевое значение `bool`-уже.

Обратите внимание, «write single» — это FC05 (coil) и FC06 (register).
Проигнорированные в интерфейсе multi-write (FC0F/0x10) живут в
`frames.rs` как PDU-билдеры для будущего.

## Зачем `set_slave_id` / `get_slave_id`

`probe_modbus` (`discover.rs:93-104`) перебирает slave'ов 1..15, каждый раз
`set_slave_id` и один `read_holding_registers(0, 1)`. Именно благодаря
отдельному сеттеру тот же `SerialMaster` щупает много адресов, не пересоздавая
порт.

## Тесты

В `master.rs` тестов нет (живой порт в CI недоступен) — протокольная логика
покрыта в `frames`/**`crc`**; интеграционные TCP-тесты покрывают оба MBAP-пути,
RTU-мастер проверяется на реальном железе/эмуляторе руками через Bus.

---

**Вопросы для самопроверки**

1. Почему таймаут порта = конец кадра? (пауза T1.5/T3.5 между кадрами длиннее паузы внутри кадра, на 9600 это ~4 мс).
2. Зачем гасить DTR/RTS при открытии? (безопасный сброс чипа — см. `master.rs:69-72`).
3. Что случится, если устройство не ответит? (`Timeout` + запись в трейс, `master.rs:131-133`).
4. Какое значение уходит в кадр при записи катушки ON? (`0xFF00`).
5. Какой тип поля `port` у `SerialMaster` и почему не конкретный? (`Box<dyn SerialPort>` — trait object для разных реализаций).
6. Сколько страховочных проверок проходит ответ до возврата PDU? (длина ≥5, CRC, slave addr, exception/fc).