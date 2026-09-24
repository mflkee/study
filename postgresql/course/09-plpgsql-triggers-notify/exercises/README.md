# Упражнения модуля 09

## Заготовки и решения

- `exercises/src/ex01_audit.rs` — «Журнал аудита: проверка цепочки» (кейс 4): `row_hash`, `verify_chain`, `audit_chain_from_db`.
- `exercises/src/ex02_events.rs` — «Тревоги в реальном времени» (кейс 6): `parse_payload`.
- Решения: `solutions/ex01_audit.rs`, `solutions/ex02_events.rs` (открыть после попытки).

## Как проверять

```bash
cargo test ex01_        # цепочка хэшей (чистые + интеграционный с course_m06)
cargo test ex02_        # разбор payload + интеграционный «listener → INSERT → recv»
cargo test
```

- **До решения** — тесты падают (`not yet implemented`).
- **После решения** — 6 зелёных: детерминизм хэша, «взлом строки ловится», «вырезанная строка ловится», интеграция цепочки; парсинг валидного/битого payload и живой NOTIFY-тест.

Интеграционные тесты требуют поднятого стенда + `00-setup.sql` в `course_m06` (применяется один раз, идемпотентно).

## Контроль качества

```bash
cargo check --all-targets
cargo clippy --all-targets    # 0 предупреждений
cargo fmt --check
```