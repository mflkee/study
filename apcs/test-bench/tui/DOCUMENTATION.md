# ESP32 Modbus Test-Bench — Extended Documentation

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [File Map](#2-file-map)
3. [How to Build and Run](#3-how-to-build-and-run)
4. [UI Guide — What Each Tab Shows](#4-ui-guide--what-each-tab-shows)
5. [Modbus Fundamentals](#5-modbus-fundamentals)
6. [Emulator — How It Works Internally](#6-emulator--how-it-works-internally)
7. [The Register Editor (Registers Tab)](#7-the-register-editor-registers-tab)
8. [The Sensor Factory (Sensors Tab)](#8-the-sensor-factory-sensors-tab)
9. [Architecture Diagrams](#9-architecture-diagrams)
10. [Pedagogical Exercises](#10-pedagogical-exercises)
11. [Debugging Tips](#11-debugging-tips)
12. [Pitfalls and Common Mistakes](#12-pitfalls-and-common-mistakes)
13. [Glossary](#13-glossary)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    esp32-tui (this binary)               │
│                                                         │
│  main.rs ─── keyboard events ──► app.rs (state)          │
│                                       │                 │
│                         ┌─────────────┤                 │
│                         ▼             ▼                 │
│                   ui.rs (draw)   worker.rs (threads)     │
│                                    │                    │
│                        ┌───────────┼───────────┐        │
│                        ▼           ▼           ▼        │
│                   discover.rs  master.rs  emulator.rs    │
│                   (hotplug)   (Modbus)   (virtual)      │
│                                                         │
│                         firmware.rs (espflash CLI)       │
└─────────────────────────────────────────────────────────┘
```

**Data flow (every frame, ~16ms):**

1. `worker.rs` polls the backend every 500ms: if emulator is ON → `emulator.tick()` → `snapshot()` → sends `Event::Snapshot` via channel; if real device connected → `serial_master.read_holding_regs(0,10)` → sends snapshot.
2. `app.rs` receives the event and updates `app.snapshot: Vec<DeviceInfo>`.
3. `ui.rs` reads `app.snapshot` and renders every draw cycle.

**Threading model:** There are 4 thread types:

| Thread | Purpose | Lifetime |
|--------|---------|----------|
| **Main thread** (tokio) | Draws TUI, handles keyboard | Entire run |
| **Poll thread** (spawned by worker) | Reads registers/coils, sends snapshots | Runs while connected |
| **Scanner thread** (spawned by worker) | Watches `/dev/serial/by-id` every 2s | While enabled (default on) |
| **Firmware thread** (spawned on [i]/[f]/[b]/[o]) | Runs espflash/esptool.py | One-shot |

---

## 2. File Map

| File | Lines | What it does |
|------|-------|--------------|
| `src/main.rs` | 224 | Terminal setup (alternate screen, raw mode), tick loop (60fps), keyboard dispatch |
| `src/app.rs` | 1175 | **Core state machine.** All mutations to `App` go through methods here. Forms, log, snapshot, emulator connection, sensor & device management. |
| `src/ui.rs` | 837 | Pure render logic. 7 functions, one per tab. No mutation (except `app.scroll_*`). |
| `src/worker.rs` | 180 | Backend multiplexer. Manages scanner/poll threads, dispatches `Event::PortFound/Connected/Disconnected/Snapshot` |
| `src/master.rs` | 200 | Serial Modbus client (wraps `serialport` + `crc16` crate). Read/write coils, input regs, holding regs. |
| `src/discover.rs` | 90 | Reads `/dev/serial/by-id` symlinks, matches against ESP32 keywords |
| `src/emulator.rs` | 450 | In-process Modbus emulator. 4 devices, 12 virtual sensors, `VirtualMachine` trait, add/remove sensor |
| `src/firmware.rs` | 140 | Best-effort espflash backup/restore/board-info |
| `src/crc.rs` | 45 | CRC-16 Modbus (for unit tests) |
| `src/frames.rs` | 55 | PDU frame builder/parser (for unit tests) |
| `tests/integration.rs` | 30 | 6 integration tests against emulator |

---

## 3. How to Build and Run

```bash
cd test-bench/tui

# Debug build (fast compile, slower runtime)
cargo run

# Release build (slower compile, faster runtime)
cargo run --release

# Run tests
cargo test

# Run on a different terminal size
stty cols 120 rows 40
cargo run --release
```

**First launch behavior:**
- The emulator starts in OFF state (nothing to show).
- Press `e` to enable the emulator — 2 devices with 10 virtual sensors appear.
- The scanner immediately detects `/dev/ttyS*` ports (on this machine).
- Press `c` to connect to a port, or just stay with the emulator for learning.

**Requirements:** Rust 1.75+, no ESP32 needed for emulator mode.

---

## 4. UI Guide — What Each Tab Shows

### Tab Bar (top line)

```
 Dashboard    Ports    Registers    Sensors    Firmware    Log    Help
             ───────
             active tab (black on cyan)
```

The active tab is highlighted. Use `[Tab]` to cycle right, `[Shift+Tab]` (not implemented — use `[Tab]` multiple times) to cycle through.

### Dashboard (default)

Shows live sensor values from all connected devices:

```
 ▸ Slave 1 · Pump Station                     ← active device
     T-101 Inlet   Temperature (°C)   25.12°C        ← input register @0
     T-202 Outlet  Temperature (°C)   46.27°C        ← input register @2
     P-101 Disch.  Pressure (kPa)     98.33 kPa      ← input register @4

   Slave 2 · Test Device
     Sine A    25.59
     Sine B    34.21
     Flow C    125.46 %
```

- `▸` marks the **active device** (selected with `↑/↓`).
- `[Space]` toggles pump 0 coil of the active device.
- Values update every 500ms.

### Ports

Lists `/dev/serial/by-id/*` symlinks with descriptions:

```
 ┌ Ports ───────────────────────┐  ┌ Port detail ──────────────┐
 │ 1. /dev/ttyS4   USB Modem    │  │ Path:      /dev/ttyS4     │
 │ 2. /dev/ttyUSB0  FT232       │  │ Desc:      USB Modem       │
 │         [Connected]          │  │ Baud:      9600            │
 └──────────────────────────────┘  │ Status:    Connected       │
                                   └────────────────────────────┘
```

`[p] probe` sends a probe request to the selected port and shows latency.

### Registers

Split into two panels:

**Left panel — Register Editor:**
```
 Reg type : HOLDING   [t] change   [↑↓] focus  [enter] edit
       Slave ID : 1
    Start addr : 0
         Count : 10
    Write addr : 0          ← write target address
   Write value : 0          ← write target value

 [r] read  [w] write value
```

**Right panel — Live snapshot:** Shows current values from emulator or real device.

**How to use:**
1. Press `↑/↓` to focus a field (highlighted yellow).
2. Press `[Enter]` to start editing — field turns cyan with `■` cursor.
3. Type numbers. Press `[Backspace]` to delete.
4. Press `[Enter]` to finish, `[Esc]` to cancel.
5. Press `[r]` to read, `[w]` to write.

**Result feedback:** the outcome of every read/write is shown in
"Current result" on the panel (including the error code). If nothing is
connected, the panel shows a red hint: `! no target: press [e] for
emulator, or connect in Ports` — that's why `[w]`/`[r]` may seem to do
nothing when you don't have a target.

### Sensors

**Left panel — Sensor factory:**
```
 Device idx : 0    ← index of device to add to
        Name : New Sensor
   Type      : Temperature (°C)   [1..6] change
 Base value  : 25.0
  Amplitude  : 3.0
  Period (s) : 8.0

 [a] add   [↑↓] focus  [enter] edit  [m] manage
```

**Right panel — Existing devices and sensors (by device):**
```
 ◆ Slave 1 — Pump Station
   ●  T-101 Inlet   Temperature (°C)  base=22.0 amp=4.0 T=8s ⇒ ...
   ●  T-202 Outlet  Temperature (°C)  base=45.0 amp=2.0 T=5s ⇒ ...
 ◆ Slave 2 — Test Device
   ●  Sine A ...
```

**Managing devices and sensors (manage mode):**
1. Press `[m]` to enter **manage mode** — the list becomes active and the
   title changes to "Manage: [↑↓] move [d] delete [esc] done".
2. Press `↑/↓` (or `k/j`) to move the grey highlight. The cursor walks **all**
   rows — both device headers (`◆ Slave N`) and sensors (`●`), wrapping over
   the edge (one step below the last sensor lands on the next device header,
   and vice versa).
3. Press `[d]` (or `Delete`) to delete the row under the cursor:
   - on a sensor → deletes the sensor;
   - on a device header → deletes the **whole slave** together with its sensors
     (the last device is protected — the emulator must serve something).
4. Press `[esc]` or `[m]` to leave manage mode.

**Creating a new slave:** press `[n]` on the Sensors tab — a new empty
device `Device N` (unique slave id) is added; its sensors start appearing
from the first input register. Create as many as you need.

**Types:** 1=T°C, 2=Pressure, 3=Flow, 4=Test, 5=Level (m), 6=Humidity (%).

**Built-in devices:** 4 slaves by default — Pump Station (1),
Test Device (2), Electric Boiler (3), Weather Station (4). Switch the
"Device idx" field to add sensors to a different slave (0..=N).

### Firmware

Calls `espflash` (or `esptool.py`) CLI. **Requires physical ESP32** — not functional in emulator mode. See `test-bench/README.md` for hardware setup.

### Log

Scrollable event log. Shows probe results, Modbus read/write confirmations, firmware messages. Press `[Enter]` to clear.

### Help

Shows full key reference. Scroll with `↑/↓`.

---

## 5. Modbus Fundamentals

This section explains the concepts the Register Editor teaches.

### What is Modbus?

Modbus is a serial communication protocol (1979). A **master** (your software) sends requests to **slaves** (devices). Each slave has a numeric address (1–247).

### Register Types

| Type | Name | Range | Typical Use |
|------|------|-------|-------------|
| 0 | **Input** (read-only) | 0–65535 | Sensor readings (temperature, pressure) |
| 1 | **Holding** (read/write) | 0–65535 | Configuration, setpoints, control |
| 2 | **Coil** | 0 or 1 | Digital on/off (pump, valve, LED) |

### PDU Structure

**Request frame:**
```
[slave_id] [function_code] [start_hi] [start_lo] [count_hi] [count_lo] [crc_lo] [crc_hi]
```

**Response frame:**
```
[slave_id] [function_code] [byte_count] [data...] [crc_lo] [crc_hi]
```

Examples from the emulator:

```
Read 10 input registers from slave 1:
  TX: 01 03 00 00 00 0A C5 CD    ← function 0x03, count=10
  RX: 01 03 14 00 FA 01 F4 00 ... 20 bytes data + CRC

Write holding register 0 = 42 on slave 1:
  TX: 01 06 00 00 00 2A 88 0B    ← function 0x06, value=42 (0x002A)
  RX: 01 06 00 00 00 2A 88 0B    ← echo = success

Toggle coil 0 on slave 1:
  TX: 01 05 00 00 FF 00 8C 3A    ← function 0x05, value=0xFF00=ON
  RX: 01 05 00 00 FF 00 8C 3A    ← echo
```

### CRC-16 Modbus

Every frame ends with a CRC-16 (polynomial 0xA001, init 0xFFFF). The `crc.rs` module implements this; `frames.rs` builds and parses frames.

Exercise: use `cargo test -- crc` to see the CRC tests, then modify the test input and predict the output before running.

### Function Codes Used

| Code | Name | Modbus Name |
|------|------|-------------|
| 0x01 | Read coils | FC01 |
| 0x03 | Read holding/input registers | FC03 |
| 0x05 | Write single coil | FC05 |
| 0x06 | Write single register | FC06 |
| 0x0F | Write multiple coils | FC15 |
| 0x10 | Write multiple registers | FC16 |

---

## 6. Emulator — How It Works Internally

The emulator (`emulator.rs`) implements a `VirtualMachine` trait that mirrors the serial master API:

```rust
pub trait VirtualMachine {
    fn read_coils(&mut self, slave: u16, addr: u16, count: u16) -> Result<Vec<bool>, u8>;
    fn write_coil(&mut self, slave: u16, addr: u16, value: bool) -> Result<(), u8>;
    fn read_input_regs(&mut self, slave: u16, start: u16, count: u16) -> Result<Vec<u16>, u8>;
    fn read_holding_regs(&mut self, slave: u16, start: u16, count: u16) -> Result<Vec<u16>, u8>;
    fn write_holding_reg(&mut self, slave: u16, addr: u16, value: u16) -> Result<(), u8>;
}
```

Both `Emulator` (virtual) and `SerialMaster` (real) implement this trait. This is why the same `handle_key` code works for both modes.

### Default Scenario

```rust
pub fn default_scenario() -> Emulator {
    // Slave 1 = "Pump Station":      10 input regs + 8 coils + 10 holding regs
    //   T-101 Inlet  °C  base=22  amp=4  T=8s
    //   T-202 Outlet °C  base=45  amp=2  T=5s
    //   P-101 Disch. kPa base=101.3 amp=3 T=6s
    // Slave 2 = "Test Device":       6 input regs + 2 coils + 10 holding regs
    //   Sine A (Test)  base=50  amp=25  T=4s
    //   Sine B (Test)  base=30  amp=10  T=2s
    //   Flow C         base=100 amp=30  T=7s
    // Slave 3 = "Electric Boiler":   setpoints + coils for heater & pump
    //   B-1 Water temp °C  base=60 amp=3 T=12s
    //   B-2 Exhaust temp °C base=180 amp=5 T=20s
    //   L-1 Water level m base=1.2 amp=0.05 T=30s
    // Slave 4 = "Weather Station":
    //   W wind speed (Test) base=3 amp=2 T=5s
    //   H humidity % base=60 amp=20 T=8s
    //   T outside °C base=12 amp=8 T=15s
}
```

### Time Model

- `Emulator::new()` sets `last_tick = 0.0` (not `now_secs()`).
- First `tick()` call: elapsed = `now_secs() - 0.0` = large value → sine clamps at exactly `base` (phase wraps).
- Subsequent ticks: ~500ms apart → smooth sine progression.
- `now_secs()` returns monotonic seconds from process start.

### Key Point: Register Addresses

In the emulator, input register addresses map directly:
- Address 0 → `input_regs[0]`
- Address 1 → `input_regs[1]`

The Register Editor's **Start addr** field uses these addresses directly. For example, `Start addr = 4, Count = 3` reads `input_regs[4..7]`.

### How Add Sensor Works

1. Finds the first unused register pair (even+odd) across all devices.
2. Creates a `VirtualSensor` with that address.
3. Pushes to `devices[dev_idx].sensors`.
4. On next `tick()`, sine values are written to those addresses.

---

## 7. The Register Editor (Registers Tab)

### Fields

| Field | Description | Editable |
|-------|-------------|----------|
| Reg type | 0=input, 1=holding, 2=coil | Cycle with `[t]` or `[1]/[2]/[3]` |
| Slave ID | Target slave address | `[↑/↓]` focus + `[Enter]` edit |
| Start addr | First register address | (same) |
| Count | Number of registers to read | (same) |
| Write addr | Target address for `[w]` | (same) |
| Write value | Value to write for `[w]` | (same) |

### Editing Workflow

```
Focus field:     [↑] or [↓]
Start editing:   [Enter]
Type:            any digit, ., -, letter
Delete:          [Backspace]
Commit:          [Enter]
Cancel:          [Esc]
```

While editing, the field shows a blinking `■` cursor (cyan background).

### Read Operation

1. Select type (input/holding).
2. Set Slave ID, Start addr, Count.
3. Press `[r]`.
4. Result appears in "Current result" on the left panel and in the Log.

### Write Operation

1. Select type (holding or coil).
2. Set Write addr and Write value.
3. Press `[w]`.
4. For holding: writes a single 16-bit value.
5. For coil: writes ON (value≠0) or OFF (value=0).

---

## 8. The Sensor Factory (Sensors Tab)

### Fields

| Field | Default | Description |
|-------|---------|-------------|
| Device idx | 0 | Zero-based index of target device |
| Name | New Sensor | Human-readable name |
| Type | Temperature (°C) | 1=T°C, 2=Pressure, 3=Flow, 4=Test, 5=Level, 6=Humidity |
| Base value | 25.0 | Sine wave center |
| Amplitude | 3.0 | Sine wave peak deviation |
| Period (s) | 8.0 | Full cycle time |

### Adding a Sensor

1. Set Device idx (0=Pump Station, 1=Test Device, 2=Electric Boiler, 3=Weather Station, 4..=N — added devices).
2. Set Name and parameters.
3. Choose type with `[1..6]`.
4. Press `[a]`.
5. Wait 500ms for next snapshot — the new sensor appears in the list.

The sensor uses the first free register pair on the target device.

### Managing Devices and Sensors (manage mode)

| Клавиша | Действие |
|---------|----------|
| `[m]` | войти/выйти из manage (список становится активным) |
| `↑/↓` или `k/j` | курсор по всем строкам: заголовки `◆` и датчики `●`, с заворотом |
| `[d]` / `Delete` | удалить объект под курсором: датчик или всё устройство |
| `[n]` | создать новое пустое устройство (slave id = max+1) |
| `[esc]` | выйти из manage |

Последнее оставшееся устройство удалить нельзя — эмулятору нужно кого-то
обслуживать. Пример: удалить Test Device целиком → приборная панель
показывает 3 устройства, слэвы 1, 3, 4.

---

## 9. Architecture Diagrams

### Event Flow

```
User presses key
       │
       ▼
main.rs handle_key()
       │
       ├──► app.read_registers()
       │          │
       │          ▼
       │    emu.read_holding_regs() or master.read_holding_regs()
       │          │
       │          ▼
       │    app.status_line = "[42, 10, ...]"
       │
       ├──► toggle_coil() / write_register() / add_sensor()
       │          │
       │          ▼
       │    emu modifies internal state
       │
       ▼
app.draw()
       │
       ▼
ui.rs reads app.snapshot, draws to frame
```

### Poll Thread Loop

```
loop {
    snapshot = read_all_devices();   // ~5-10ms for emulator
    tx.send(Event::Snapshot { snapshot });
    sleep(500ms);
}
```

### Serial Communication Chain

```
app.write_single_coil()
    → master.write_single_coil(coil, value)
        → frame = build_write_coil_frame(slave, coil, value)
            → frame.push(crc_lo, crc_hi)
        → port.write_all(&frame)
        → port.read_exact(&mut buf)
        → parse_response(buf)
```

---

## 10. Pedagogical Exercises

### Exercise 1: Modbus Register Addressing

**Goal:** Understand the difference between holding and input registers.

1. Open the Registers tab (press `e` then `Tab` twice).
2. Set type to INPUT, Start addr = 0, Count = 5. Press `[r]`.
3. Note the values (they change every 500ms — sine wave).
4. Switch to HOLDING (press `[t]`). Press `[r]`.
5. Compare: holding registers are initially fixed (set by `default_scenario`).
6. Write `42` to holding register 0 (set Write addr = 0, Write value = 42, press `[w]`).
7. Read holding register 0 again — it should now be 42.
8. **Question:** Why don't input register values change when you write to them?

**Answer:** Input registers are read-only in Modbus. The emulator enforces this: `write_input_reg()` returns `Err(0x02)`.

### Exercise 2: Coils and the Dashboard

**Goal:** See how coil writes affect the live view.

1. On the Dashboard, press `Space` to toggle pump 0 of Slave 1.
2. Look at the log — it should say "Coil 0=true (slave 1)".
3. Press `Space` again — "Coil 0=false".
4. Now open the Registers tab, set type to COIL, Start addr = 0, Count = 8.
5. Press `[r]` — you'll see the coil states as `true`/`false`.
6. **Question:** What happens if you press `[w]` with a coil and value = 5?

**Answer:** In the emulator, `write_coil(slave, addr, value != false)` — any non-zero value turns it ON.

### Exercise 3: CRC-16 Verification

**Goal:** Understand how CRC protects Modbus frames.

1. Run `cargo test -- crc`.
2. Look at the test for `[0x01, 0x03, 0x00, 0x00, 0x00, 0x0A]`.
3. The CRC is `0x0A84` (little-endian: `0x84, 0x0A`).
4. Try adding a byte to the input (e.g., `0x01` at the end) and predict the new CRC.
5. Run the test — did your prediction match?
6. **Study:** The CRC polynomial is `0xA001` (bit-reversed `0x8005`). Each bit is XOR'd with the LSB of the accumulator.

### Exercise 4: Adding Virtual Sensors

**Goal:** See how the emulator generates data.

1. Open the Sensors tab (`Tab` to Sensors).
2. Add a new sensor: Name = "My Sine", Type = Test, Base = 50, Amplitude = 20, Period = 5.
3. Press `[a]` — it appears in the sensor list immediately.
4. Switch to the Dashboard — the new sensor appears on Slave 1 (device 0) with fluctuating values.
5. Change Device idx to 1 (Test Device) and add another sensor.
6. **Question:** What register address did the emulator assign? (Hint: watch the Dashboard — channel addresses are shown there.)

### Exercise 7: Managing Devices and Sensors

**Goal:** Learn manage mode — navigation over all rows, deleting both.

1. Open the Sensors tab.
2. Add 2–3 sensors (e.g. `[a]` a few times with different Device idx).
3. Press `[m]` — the list becomes active (title changes to "Manage: …").
4. Move with `↑/↓`: the highlight walks **all** rows — device headers `◆`
   and sensors `●` — and wraps over the edge. Confirm you can reach the
   `◆ Slave 4 — Weather Station` header from the top.
5. Position the cursor on one of the new sensors and press `[d]` — the sensor
   is deleted and the cursor jumps to a valid neighbor.
6. Now move the cursor onto the `◆ Slave …` header of a device you added and
   press `[d]` — the **whole slave** disappears (with its sensors).
7. Press `[esc]` to leave manage mode. Press `[n]` to create a new slave and
   `[a]` to add a sensor into it (set Device idx to the new index).
8. Switch to the Dashboard — deleted devices/sensors are gone from the poll snapshot.

**Question:** Why can't you delete the last remaining device?

### Exercise 8: Understanding Why Write "Does Nothing"

**Goal:** Learn to read the status feedback.

1. Do NOT press `[e]` and do not connect any port.
2. Go to the Registers tab — the panel shows the red hint
   `! no target: press [e] for emulator, or connect in Ports`.
3. Press `[w]` — "Current result" now shows the same hint (it was previously
   only in the Log tab, which is why it seemed like the button was broken).
4. Press `[e]` to enable the emulator. The red hint disappears.
5. Press `[w]` again — "Current result" shows `Wrote 42 -> 0 holding reg`.
6. Read back with `[r]` — the value persists.

**Lesson:** the Registers editor targets whichever source is active
(emulator on → emulator; serial connected → ESP32). Without a source it
reports it plainly in the panel instead of silently dying.

### Exercise 5: Register Editor — Writing and Reading Back

**Goal:** Verify that write-then-read gives consistent results.

1. Open the Registers tab.
2. Set type = HOLDING, Slave ID = 1, Start addr = 0, Count = 10.
3. Read: `[r]` → note the values.
4. Set Write addr = 0, Write value = 999.
5. Write: `[w]`.
6. Read again: `[r]`.
7. The first value should be 999, the rest unchanged.
8. **Challenge:** Write different values to addresses 0, 1, 2, then read all three. Do they all persist?

### Exercise 6: Editing Form Fields

**Goal:** Practice the field editing workflow.

1. Go to the Registers tab.
2. Press `[↓]` to focus Slave ID (yellow highlight).
3. Press `[Enter]` → cyan background with `■` cursor.
4. Type `5` → field shows `15` (appended to default `1`).
5. Press `[Backspace]` → `5` (deletes last char).
6. Press `[Esc]` → edit cancelled, field reverts to `1`.
7. Press `[Enter]` again, type `3`, press `[Enter]` → field is now `3`.
8. Read with `[r]` — notice slave 3 doesn't exist in emulator → error in log.
9. **Lesson:** Form fields are strings; parsing happens at the moment of use.

---

## 11. Debugging Tips

### TUI doesn't render correctly

- Ensure your terminal supports Unicode and at least 80×24 characters.
- `script` + `stty cols ROWS` can simulate specific sizes.
- Box-drawing characters require UTF-8 locale.

### "Port not found" or no ports listed

- Check `/dev/serial/by-id/` exists: `ls -la /dev/serial/by-id/`.
- If no ports listed, there may be no ESP32 connected, or udev rules block access.
- The scanner runs every 2 seconds. Press `[R]` to force rescan.

### Emulator shows 0 devices after pressing [e]

- This shouldn't happen. If it does, the emulator was likely not initialized. Check stderr output.

### Read returns empty array or error code

- **Error 0x01 (ILLEGAL FUNCTION):** Wrong register type for the function code.
- **Error 0x02 (ILLEGAL DATA ADDRESS):** Register address out of range for that device.
- **Error 0x03 (ILLEGAL DATA VALUE):** Value out of range.
- In the emulator, these are logged: "Read failed (0xNN)".

### Build fails with "cannot find value `AppTab`"

- This was a known bug (Tab name collision with crossterm's `Tab`). It's fixed in the current code via alias: `use app::Tab as AppTab`.

### Console output corrupts the terminal

- If the TUI crashes, run `reset` to restore terminal state.
- The `Drop` impl for `App` should clean up `disable_raw_mode()` and `LeaveAlternateScreen`, but force-killing (SIGKILL) skips destructors.

---

## 12. Pitfalls and Common Mistakes

### 1. Space key is not `KeyCode::Space`

In crossterm, `KeyCode::Space` doesn't exist. The space bar generates `KeyCode::Char(' ')`. This is the most common mistake when handling keyboard input.

```rust
// WRONG:
KeyCode::Space => { ... }

// CORRECT:
KeyCode::Char(' ') => { ... }
```

### 2. Borrow checker fights with emulator lock

The emulator is behind `Arc<Mutex<Emulator>>`. You **cannot** hold the lock while calling `self.log()` because `self.log()` borrows `self` mutably:

```rust
// WRONG — E0502:
let emu = self.emu.lock().unwrap();
let result = emu.read_holding_regs(0, 0, 5);
self.log(1, format!("..."));  // ← self is already borrowed

// CORRECT — scope the lock:
let result = {
    let mut emu = self.emu.lock().unwrap();
    emu.read_holding_regs(0, 0, 5)
};
self.log(1, format!("..."));  // ← lock is dropped, self is free
```

### 3. `use Tab as AppTab` alias

`crossterm::event::KeyCode::Tab` shadows `app::Tab`. Use an alias:

```rust
use app::Tab as AppTab;
// Now KeyCode::Tab (key) and AppTab::Registers (enum) coexist
```

### 4. Emulator `last_tick = 0.0`

Setting `last_tick = now_secs()` in the constructor means the first `tick()` computes a large elapsed time, clamping sine to `base`. Setting it to `0.0` makes the first tick also compute a large value but from a known anchor, so subsequent ticks are smooth. Both work; `0.0` is simpler.

### 5. Register vs. sensor address confusion

The "Start addr" in the Register Editor is the **Modbus address**, not the sensor index. The emulator maps addresses directly to `input_regs[addr]`. If you add a sensor and it gets assigned address 6, then `Start addr = 6` will read that sensor's value.

---

## 13. Glossary

| Term | Definition |
|------|-----------|
| **Slave** | A Modbus device (addressed 1–247) |
| **Master** | The device that initiates requests (this software) |
| **Register** | A 16-bit (0–65535) value stored in a device |
| **Coil** | A single-bit (0/1) value stored in a device |
| **Holding register** | Read/write register (function codes 0x03/0x06) |
| **Input register** | Read-only register (function code 0x03) |
| **PDU** | Protocol Data Unit — the raw Modbus frame |
| **CRC** | Cyclic Redundancy Check — error-detection checksum |
| **CRC-16 Modbus** | CRC algorithm with polynomial 0xA001, init 0xFFFF |
| **Polling** | Periodically reading registers (every 500ms in this tool) |
| **Hotplug** | Automatic detection of USB devices appearing/disappearing |
| **Raw mode** | Terminal mode where each keystroke is delivered individually |
| **Alternate screen** | A secondary terminal buffer (like `less`/`vim` use) |
| **Virtual sensor** | A sine-wave generator in the emulator, assigned to register addresses |
| **Pump station** | A real-world device type; in the emulator, it's a slave with coils controlling pump motors |
