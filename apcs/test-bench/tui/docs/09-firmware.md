# 09. Прошивка: `firmware.rs` (357 строк)

Весь модуль работает **только через внешние программы** `espflash`/`esptool`
(subprocess — дочерний процесс): никакой прямой работы с чипом — намеренно.
Так интерфейс остаётся простым (одна функция на операцию), а риск и сложность
скрываются в инструментах, которые уже проверены сообществом.

## `FwResult` — унифицированный результат (`firmware.rs:13-35`)

```rust
pub struct FwResult {
    pub ok: bool,
    pub output: String,
    pub error: String,
}
```

`success(output)`/`failure(error)` — конструкторы. Один тип для всех операций —
и для синхронных (`run`), и для стриминговых (`run_progress`; streaming —
потоковый вывод по мере поступления). Текстовый вывод инструментов аккуратно
складывается в `output`.

## `find_tool` / `is_esptool` / `command_exists` (`firmware.rs:38-61`)

- `find_tool()`: перебирает `espflash`, `esptool`, `esptool.py` через
  `command_exists` (тот вызывает `--help` — понятен обоим и быстро).
- Возвращается первый найденный.
- `is_esptool(tool)`: `esptool`/`esptool.py` → синтаксис команд (CLI syntax) у
  них другой (`--port` глобально перед подкомандой; нет
  `board-info`/`--save-image`).

## `run` — синхронный запуск (`firmware.rs:64-82`)

`Command::new(tool).args(..)` (опционально `current_dir` — рабочая папка),
`cmd.output()`, сшивает stdout+stderr (стандартный вывод ошибок) в один текст.
Успех по `status.success()`.

## `run_progress` — стриминг живого прогресса (`firmware.rs:108-190`)

esptool печатает «Reading… 45.0 %», «Writing… 100.0 %» в stdout **или** stderr
(зависит от версии/потока). Поэтому:

1. `cmd.stdout(Stdio::piped()).stderr(Stdio::piped())`, `spawn`.
2. Для каждого потока — отдельный поток-«помп» (pump — перекачка): режет буфер
   по `\n`/`\r`, шлёт непустые строки в `mpsc` (канал; `firmware.rs:131-156`).
3. Основной цикл ждёт строки из канала; строку с процентом распознаёт
   `parse_pct` и при **смене** процента вызывает прогресс-колбэк (callback —
   функция обратного вызова; `firmware.rs:171-177`). Обычные строки копятся в
   `out_buf` (лимит 16 KiB, чтобы не раздуть память).
4. В конце `child.wait()` → `FwResult`.

`pump` — локальная функция внутри `run_progress` с собственным upper-mini
телом потока; она держит `carry` (хвост строки между чтениями), чтобы не
порезать строку пополам.

## `parse_pct` — вытащить процентный счётчик (`firmware.rs:85-102`)

Разные версии esptool пишут по-разному: `99.5%` и `45.0 %` (пробел перед %).
Логика: найти `%`, отойти назад через пробелы, взять подряд идущие цифры и
`'.'`, распарсить как f64, округлить, срезать `min(100)`. Не-числовые строки
→ `None`.

## Высокоуровневые операции

| Функция | Команда (esptool v5) | (espflash) |
|---------|----------------------|------------|
| `board_info(port)` (`firmware.rs:193-203`) | `--port P chip-id` | `board-info --port P` |
| `flash(port, bin, progress)` (`firmware.rs:206-228`) | `-b 921600 --port P write-flash 0x0 bin` | `write-flash --port P 0x0 bin` |
| `backup(port, out, mb, progress)` (`firmware.rs:250-299`) | `-b 921600 --port P read-flash 0x0 size out` | `read-flash --port P --save-image 0x0 size out` |
| `restore` | = `flash` (прошивка дампа) | = `flash` |

Детали:

- `board_info` на esptool — команда `chip-id` (не `board-info`, которой нет).
- **Бонус точности про планы**: достичь 4 МБ как `0x100000 * 4 = 0x400000`
  (в старой версии считалось `0x1000*1024*4` = 16 МБ! — тест
  `backup_size_math_is_megabytes` это и ловит, `firmware.rs:340-345`).
- `backup` старается уточнить размер автоматически через `detect_flash_size_mb`
  (`flash-id` + разбор строки «Detected flash size: 8MB»), иначе fallback
  (запасной вариант) — параметр `flash_size_mb` (в TUI — 16).
- Нет инструмента — `FwResult::failure("No esptool available. Install: …")`.

## Вспомогательные пути

- `backup_dir()` (`firmware.rs:307-312`): `~/esp32-backups` (создаёт при
  необходимости).
- `test_firmware_bin()` (`firmware.rs:315-318`): ищет
  `test-bench/firmware/esp32-modbus-fw.bin` (бинарник для прошивки).
- `project_root()` (`firmware.rs:321-326`): `CARGO_MANIFEST_DIR` → parent
  (TUI живёт в `test-bench/tui`, значит корень — `test-bench/`).

## Тесты (`firmware.rs:329-357`)

`parse_flash_size_handles_esptool_output`, `backup_size_math_is_megabytes`,
`parse_pct_reads_esptool_progress` — все чисто строковые, без железа.

---

**Вопросы для самопроверки**

1. Почему всё идёт через subprocess? (простота + проверенные инструменты; прямой протокол ESP32 сложен).
2. В чём разница синтаксиса esptool и espflash? (`--port` глобально vs подкоманда; `chip-id` vs `board-info`).
3. Зачем читать и stdout, и stderr прогресса? (esptool пишет то туда, то сюда — зависит от версии).
4. Как `parse_pct` справляется с пробелом перед `%`? (шаг назад через пробелы перед «%»).
5. Сколько байт в 4 МБ и почему было 16 МБ? (`0x100000*4=0x400000`; старая формула `0x1000*1024*4` была в 4 раза больше).
6. Что `restore` — новая команда или переиспользование? (переиспользует `flash`).