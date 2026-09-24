# Модуль 07: Производительность (performance)

**Цели модуля:**
- Читать `EXPLAIN (ANALYZE, BUFFERS)` и находить узкие места (Seq Scan, неверный индекс, плохие оценки).
- Искать медленные запросы в проде через `pg_stat_statements`.
- Писать телеметрию быстро: построчно vs batch (`UNNEST`) vs `COPY` — и выбирать разумно.
- Тюнить пул соединений под нагрузку; честно сравнивать свой генератор и `pgbench`.

**Пререквизиты:** модули 04–06 (sqlx, транзакции, миграции), стенд + база `course_m06`. Расширение `pg_stat_statements` включено в стенде курса (см. `infra/docker-compose.yml`), для модуля создано в `course_m06`.

**Подготовка:**

```bash
# таблицы приёма телеметрии (идемпотентно)
cd infra && docker compose exec -T postgres psql -U course -d course_m06 -f - < ../course/07-performance/examples/00-setup.sql

# расширение статистики (один раз)
docker compose exec -T postgres psql -U course -d course_m06 -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

**План (по `course/PLAN.md`, ~12 ч):**
1. Урок 01: EXPLAIN (ANALYZE, BUFFERS) (~2.5 ч)
2. Урок 02: pg_stat_statements (~2 ч)
3. Урок 03: массовая запись — row / batch / COPY (~3 ч)
4. Урок 04: тюнинг пула (~2.5 ч)
5. Упражнение 01: «Приём телеметрии Modbus» (~2.5 ч)
6. Упражнение 02: «Генератор нагрузки» (~1.5 ч)
7. Лаба 01: «Нагрузочный стенд: найти узкое место» (~2 ч)

**Чеклист готовности:**
- [ ] `00-setup.sql` применён к `course_m06`; `pg_stat_statements` создан
- [ ] `cargo run --example 03-insert-methods -- --n 50000`: таблица row/batch/copy объяснена на словах
- [ ] `cargo run --example 04-pgstat` показывает реальную статистику (не пусто)
- [ ] Эмулятор + шлюз (примеры 01–02) работают в двух терминалах
- [ ] `cargo test ex01_` и `cargo test ex02_` зелёные
- [ ] Лаба 01: найденное узкое место подтверждено замером (TPS/EXPLAIN), не «на глаз»
- [ ] `cargo clippy` и `cargo fmt --check` без замечаний; `PROGRESS.md` обновлён

**Следующий модуль:** `08-timeseries-partitioning` (партиции, BRIN, downsampling).