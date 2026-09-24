# Модуль 11: REST API на axum (web-api-axum)

**Цели модуля:**
- Базовый REST на axum + sqlx: маршруты, состояние, слои (кейс 9 — дашборд).
- DTO, валидация, пагинация, единый формат ошибок JSON.
- Конфигурация из env, tracing-логирование, метрики.

**Пререквизиты:** модули 04–08 (sqlx, транзакции, бакетирование), модуль 10 (паттерны). Собственный cargo-проект; данные в `course_m06`.

**Подготовка (один раз):**

```bash
cd infra && docker compose exec -T postgres psql -U course -d course_m06 -v ON_ERROR_STOP=1 -f - < ../course/11-web-api-axum/examples/00-setup.sql
```

**План (по `course/PLAN.md`, ~8 ч):**
1. Урок 01: маршруты, состояние, слои (~2.5 ч)
2. Урок 02: DTO, пагинация, ошибки (~2.5 ч)
3. Урок 03: конфигурация, tracing, метрики (~2 ч)
4. Упражнение 01: «Эндпоинт трендов» (~1 ч)
5. Упражнение 02: «Пагинация значений» (~45 мин)
6. Лаба 01: «Токены и кэш» (~1.5 ч)

**Чеклист готовности:**
- [ ] `00-setup.sql` применён (свежие серии M-01-001/D-01-001)
- [ ] `cargo run --example 01-hello` отвечает; `02-measurements` — все эндпоинты curl'ом
- [ ] Упражнения: `cargo test ex01_trends`/`ex02_page` зелёные
- [ ] Лаба: 401 без токена, кэш current работает (calls в pg_stat_statements не растёт)
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний; `PROGRESS.md` обновлён

**Следующий модуль:** `12-testing` (testcontainers, sqlx::test, property-тесты).