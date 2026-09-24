# Упражнения модуля 10

## Заготовки и решения

- `exercises/src/ex01_queue.rs` — «Очередь задач» (кейс 7): `backoff`, `claim_batch` (SKIP LOCKED), `complete`, `fail_with_retry` (dead-letter).
- `exercises/src/ex02_buffer.rs` — «Буферизация при потере связи» (кейс 8): `to_line`/`parse_lines`, `deliver` (ON CONFLICT DO NOTHING).
- Решения: `solutions/ex01_queue.rs`, `solutions/ex02_buffer.rs` (открыть после попытки).

## Как проверять

```bash
cargo test ex01_        # бэкoff-математика + интеграция «3 задачи → done/pending/dead»
cargo test ex02_        # round-trip строки + «повторная доставка → 0 дублей»
cargo test
```

- **До решения** — тесты падают (`not yet implemented`).
- **После решения** — 4 зелёных: полный жизненный цикл задачи (claim→done, claim→fail→pending, fail×3→dead), идемпотентность приема.

Интеграционные тесты требуют поднятого стенда + `00-setup.sql` в `course_m06`.

## Контроль качества

```bash
cargo check --all-targets
cargo clippy --all-targets    # 0 предупреждений
cargo fmt --check
```