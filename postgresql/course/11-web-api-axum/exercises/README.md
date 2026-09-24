# Упражнения модуля 11

## Заготовки и решения

- `exercises/src/ex01_trends.rs` — «Эндпоинт трендов» (кейс 9): `trends_handler` (date_bin-агрегаты + DTO + from/to-фильтры).
- `exercises/src/ex02_page.rs` — «Пагинация значений»: `page_params` (клампы/дефолты).
- Решения: `solutions/ex01_trends.rs`, `solutions/ex02_page.rs`.

## Как проверять

```bash
cargo test ex01_trends     # интеграционный: Router → oneshot GET /trends → непустой массив
cargo test ex02_page       # чистые кейсы клампа + интеграция пагинации
cargo test
```

- **До решения** — хендлеры падают (`not yet implemented`).
- **После решения** — 4 зелёных: devices-смоук, пагинация (limit=2 → items 2, total>0), тренды (200 + непустой массив + поля bucket/avg).

Интеграционные тесты требуют стенда + `00-setup.sql`.

## Контроль качества

```bash
cargo check --all-targets
cargo clippy --all-targets    # 0 предупреждений
cargo fmt --check
```

Особенность: решение трендов использует динамический SQL (from/to-фильтры) — отмечено пометкой `AssertSqlSafe` (sqlx 0.9), см. урок 02.