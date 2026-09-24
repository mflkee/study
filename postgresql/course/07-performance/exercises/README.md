# Упражнения модуля 07

## Заготовки и решения

- `exercises/src/ex01_modbus_ingest.rs` — «Приём телеметрии Modbus»: три способа записи (`ingest_row` / `ingest_batch` / `ingest_copy`) + `copy_line` + `summarize`.
- `exercises/src/ex02_loader.rs` — «Генератор нагрузки»: чистые функции `tps` / `report_line`.
- Решения: `solutions/ex01_modbus_ingest.rs`, `solutions/ex02_loader.rs` (открыть после попытки).
- Заготовки подключены через `src/lib.rs`, тесты в `src/lib.rs`.

## Как проверять

```bash
cargo test ex01_        # упражнение 01
cargo test ex02_        # упражнение 02
cargo test              # всё
```

- **До решения** — тесты падают с `not yet implemented`.
- **После решения** — 6 зелёных: `ex01_ingest_methods_consistent` (интеграционный, нужен стенд: три метода пишут ровно 2000 строк, повторный прогон без дублей), формат CSV, TPS/отчёт.

Сквозная проверка «вживую» — эмулятор + шлюз (примеры 01–02) в двух терминалах, и `cargo run --example 03-insert-methods` для замеров.

## Контроль качества

```bash
cargo check --all-targets
cargo clippy --all-targets    # 0 предупреждений в заготовках и решениях
cargo fmt --check
```