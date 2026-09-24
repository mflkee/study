# Упражнения модуля 12

## Заготовки и решения

- `exercises/src/ex01_massa.rs` — «Property-функции массы» (кейс 3): `netto_decimal`, `netto_f64`, `diff_on`.
- `exercises/src/ex02_fixtures.rs` — «Фикстуры и SQL-функция»: `insert_fixtures`, `call_order_total`.
- Решения: `solutions/ex01_massa.rs`, `solutions/ex02_fixtures.rs`.

## Как проверять

```bash
cargo test --lib              # чистые юнит-тесты (эталон decimal)
cargo test --test property    # proptest (до решения ex01 — падает; после — 4 зелёных)
cargo test --test pg_integration  # контейнер + миграции + функция (после решения ex02 — 2 зелёных)
```

- **До решения** — ex01: proptest падает с `not yet implemented`; ex02: интеграционный тест функции падает.
- **После решения** — все три набора зелёные (юнит-тест миграций работает и на заготовке — он не зависит от упражнений).

Интеграционные тесты требуют Docker (поднимают свой контейнер PostgreSQL).

## Контроль качества

```bash
cargo check --all-targets
cargo clippy --all-targets    # 0 предупреждений
cargo fmt --check
```