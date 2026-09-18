# 06. Modbus TCP: `tcp_master.rs` + `tcp_server.rs`

Оба модуля — зеркальные роли одной связки: `tcp_master.rs` умеет спрашивать
(клиент-мастер, client/master), `tcp_server.rs` — отвечать (сервер-slave,
server/slave). Общая идея та же, что в RTU (Remote Terminal Unit), но вместо
`[slave][fc][pdu][crc]` — MBAP-заголовок (Modbus Application Protocol)
`[tid][proto=0][len][unit]` + PDU, и **без CRC** (его место занимает TCP/IP —
Transmission Control Protocol / Internet Protocol).

```
TUI (master) ── TCP MBAP ──►  внешнее устройство (AI-32, стенд…)
TUI (server)  ── TCP MBAP ──►  Zynq/PLC читают карту эмулятора
```

## MBAP 7 байт — на пальцах

| Поле | Размер | Смысл |
|------|--------|-------|
| tid (transaction id — идентификатор транзакции) | 2 | номер запроса, мастер сопоставляет ответы |
| proto (protocol id) | 2 | всегда `0` = Modbus IP |
| len (length) | 2 | количество байт **после** len: `unit(1) + fc(1) + данные` |
| unit (unit id) | 1 | «адрес слейва» (в TCP точка-точка это просто число) |

Ответ имеет ту же структуру. Исключение (exception) — PDU
`[fc|0x80][code]`.

## `tcp_master.rs` — клиент (301 строка)

### `TcpMaster` (`tcp_master.rs:30-39`)

```rust
pub struct TcpMaster {
    stream: TcpStream,
    unit: u8,
    tid: u16,
    trace: VecDeque<TraceEntry>,
}
```

Состояние минимальное: открытый стрим (stream — поток данных), адрес слейва,
номер транзакции и лог. `open(host, port, unit)` (`tcp_master.rs:43-53`) —
`TcpStream::connect`, `set_read_timeout(RESPONSE_WAIT=300мс)`,
`set_write_timeout(5с)`.

### `transact` — один MBAP-запрос (`tcp_master.rs:89-157`)

1. `tid = tid.wrapping_add(1).max(1)` — следующий номер (не ниже 1).
2. Собрать кадр: `[tid][proto 0][len=(1+1+pdu.len())][unit][fc][pdu]`
   (`tcp_master.rs:94-100`). Обратите внимание на `len`: единица + FC + данные.
3. `write_all + flush`; ошибка → `Io` + запись в трейс (`tcp_master.rs:103-106`).
4. Прочитать ровно 7 байт заголовка через `read_n_timeout`; после чтения —
   строгие проверки:
   - `tid_resp != tid` → «tid mismatch: sent N, got M» — TCP может
     перемешивать (переупорядочивать), если вдруг несколько запросов в полёте
     (in-flight);
   - `proto != 0 || len < 2 || len > 1+253 || unit_resp != unit` → «bad MBAP
     header» (`tcp_master.rs:118-127`);
5. `len-1` байт тела (unit уже прочитан из заголовка) (`tcp_master.rs:128-133`);
6. exception: `resp_fc == fc | 0x80` → `exception_to_error(body[1])`
   (`tcp_master.rs:141-146`);
7. чужой FC → «unexpected FC»; успех → PDU `body[1..]` + трейс `ok`
   (`tcp_master.rs:155-156`).

`read_n_timeout` (`tcp_master.rs:221-237`) — чтение ровно `buf.len()` байт с
«меньше = конец кадра»: `Ok(0)` (EOF) или `TimedOut`/`WouldBlock` → `Ok(false)`.

Успех: `Ok(body[1..].to_vec())` — это PDU без FC (тот же контракт, что у
`SerialMaster::transact`).

`exception_to_error` (`tcp_master.rs:205-218`) — та же таблица, что в
`frames::parse_response`; дублирование тут осознанное: TCP-путь не ходит через
RTU-парсер.

Высокоуровневый API (`tcp_master.rs:161-201`) полностью повторяет
`SerialMaster` — `read_holding_registers`, `read_input_registers`, `read_coils`,
`write_single_register`, `write_single_coil` (с `0xFF00`/`0x0000`) с теми же
подписями и тем же поведением. Это делает поллеры `worker.rs` почти
идентичными для serial и TCP.

## `tcp_server.rs` — сервер (465 строк)

### Константы и «общий лог» (`tcp_server.rs:26-38`)

- `DEFAULT_PORT = 1502` — 502 требует прав root (администратора); 1502 —
  стандартный dev-порт (development port) Modbus TCP.
- `IDLE_TIMEOUT = 60с` — сколько живёт соединение без запросов (idle —
  простой/бездействие).
- `TRACE_CAP = 100`.
- `SharedTrace = Arc<Mutex<VecDeque<TraceEntry>>>` — TCP-журнал, который UI
  дренит (drain — опорожняет) в общий Bus (`app.rs:436-447`).

### `TcpServerState` — остановка сервера (`tcp_server.rs:41-55`)

Держит `addr` (для UI), `stop: Sender<()>` и `handle`. `stop()` шлёт сигнал и
джойнит поток. Именно так `toggle_tcp_server` (`app.rs:370-392`) включает и
выключает сервер по `[t]`.

### `spawn` — акцептор (acceptor — принимающий соединения) (`tcp_server.rs:58-100`)

- `TcpListener::bind(("127.0.0.1", port))` + `set_nonblocking(true)` (неблокирующий
  режим, non-blocking).
- Цикл: `accept()` → `WouldBlock` → просто сон 100 мс; успех → отдельный поток
  `handle_conn` с `clone` эмулятора и трейса; ошибка → `break`.
- Каждый шаг опрашивает `stop_rx.try_recv()` — так сервер останавливается
  меньше чем за 100 мс.

### `handle_conn` — обслуживание одного клиента (`tcp_server.rs:103-166`)

```
while read_exact_timeout(&hdr[7]) {
   tid/proto/len/unit = распарсить MBAP
   if proto != 0 || len < 2 || len > 1+253 → break
   pdu = read_exact_timeout(len-1)
   resp_pdu = handle_pdu(&emu, unit, fc, data)
              // либо exception [fc|0x80][code]
   собрать ответ [tid][proto 0][len=(1+resp.len())][unit][resp]
   push_trace(...)   // транспорт "TCP"
   write_all + flush
}
```

Таймауты: `set_read_timeout(IDLE_TIMEOUT)` на весь цикл, `set_write_timeout(5с)`.
`read_exact_timeout` (`tcp_server.rs:169-185`) — та же логика «ровно N байт /
конец кадра», что и у мастера.

### `handle_pdu` — диспетчер slave (дёшево, но важно) (`tcp_server.rs:188-321`)

Принимает `emu: &SharedEmulator, unit, fc, data` и отдаёт тело ответа
(после fc). По функциям:

- **FC01/FC02** (coils/discrete): `read_count(data, 2000)` — валидирует
  `len==4`, `count in 1..=2000`, `start+count <= 0x10000`. Ответ: `[byte_count][..биты]`,
  биты упаковываются через `byte |= 1 << (i % 8)` (`tcp_server.rs:216-229`).
- **FC03/FC04** (holding/input): тоже `read_count`, ответ `[byte_count][..регистры BE]`.
- **FC05** (write single coil): `data.len()==4`, значение строго `0xFF00`/`0x0000`
  (иначе ошибка), колл (call — вызов) эмулятору; эхо (echo) PDU `data[..4]`.
- **FC06** (write single reg): `data.len()==4`, колл эмулятору; эхо.
- **FC0F** (write multi coils): `data.len() >= 6`, `qty in 1..=1968`,
  `byte_count == qty/8 ceil`, распаковка (unpacking) с
  `data[5 + i/8] >> (i % 8) & 1`, ответ `[start][qty]`.
- **FC10** (write multi regs): `qty in 1..=123`, `byte_count == qty*2`,
  `as_chunks::<2>()` → ответ `[start][qty]`.
- прочее → `Err(0x01)` illegal function.

`read_count` (`tcp_server.rs:190-203`) — локальная функция-помощник (helper)
с лимитами из спеки: count == 0 или > max → `0x03` (illegal value —
недопустимое значение); переполнение адреса → `0x02` (illegal address).

### `push_trace` (`tcp_server.rs:324-346`)

Пишет в `SharedTrace` с `transport: "TCP"` и `fc_name(fc)` — так в Bus рядом
с RTU-кадрами видны TCP-транзакции.

## Тесты: «TUI опрашивает сам себя» (`tcp_server.rs:348-464`, `tcp_master.rs:239-301`)

- `tcp_server_roundtrip_and_exceptions` — сервер: FC04 с float (22±6°C), FC06
  эхо, FC03 того же регистра (0x04D2), FC00-неизвестная → exception 0x01,
  slave 9 → exception 0x02.
- `tcp_master_polls_own_tcp_server` — клиент против собственного сервера:
  read inputs, write single register, read holding (тот же 0x04D2), read coils,
  slave 9 → IllegalAddress, FC 0x7F → IllegalFunction, все транзакции в трейсе
  помечены `TCP`. Это «полный круг» обоих MBAP-путей.

---

**Вопросы для самопроверки**

1. Что значит `len` в MBAP (сколько байт после len? и включая unit?). См. `tcp_master.rs:93-97`.
2. Почему `proto` обязан быть 0? (это идентификатор протокола Modbus IP).
3. Зачем мастеру `tid`? (сопоставление ответов; защита от перемешивания).
4. Чем отличается «конец кадра» в TCP от RTU? (в TCP — по len из заголовка; таймаут только ждёт первый байт).
5. Какие лимиты у `read_count` для регистров? (count 1..=2000, no-переполнение `start+count`).
6. Зачем `TcpServerState::stop()` джойнит поток? (гарантированная остановка до возврата).