# Модуль 08: Временные ряды — партиционирование и downsampling (timeseries-partitioning)

**Цели модуля:**
- Нативное партиционирование по времени: схема, pruning, управление партициями, ретеншн без bloat.
- Downsampling и отчёты: `date_bin`-бакеты, сменные/суточные агрегаты, материализованные представления.
- TimescaleDB как вариант: hypertable, continuous aggregates, политики — и **честное сравнение** с нативным подходом (design D3).
- Кейсы: «сменные и суточные отчёты» (кейс 5), «телеметрия» (кейс 2).

**Пререквизиты:** модули 01–03 (SQL, EXPLAIN, транзакции), стенд курса. Это SQL-модуль без cargo-проекта (отклонение 4 в PLAN.md).

**Подготовка:**

```bash
# нативная схема (course_m06, 15432) — база модуля, 1 М строк
cd infra && docker compose exec -T postgres psql -U course -d course_m06 -f - < ../course/08-timeseries-partitioning/examples/00-setup-partitions.sql

# TimescaleDB (15433, база course) — профиль timescale, уже включён в compose
docker compose --profile timescale up -d
```

**План (по `course/PLAN.md`, ~8 ч):**
1. Урок 01: партиционирование, ретеншн, BRIN (~2.5 ч)
2. Урок 02: downsampling — бакеты, matview (~2 ч)
3. Урок 03: TimescaleDB и честное сравнение (~2.5 ч)
4. Упражнение 01: «Сменный отчёт» (~30 мин)
5. Упражнение 02: «Разверни TimescaleDB и сравни» (~45 мин)
6. Лаба 01: «Партиция без индекса / Ретеншн удалил нужное» (~1.5 ч)

**Чеклист готовности:**
- [ ] `00-setup-partitions.sql` применён (видишь 3 партиции, ~1 М строк)
- [ ] `examples/01…03` прогнаны; планы pruning/индекс/BRIN объяснены
- [ ] `examples/04-timescale.sql` прогнан на 15433 (hypertable, cagg, политика)
- [ ] Упражнение 01: `infra/check-sql.sh … ex01 …` → `ok`
- [ ] Упражнение 02: `infra/check-sql.sh … ex02 … timescale course` → `ok`
- [ ] Лаба: «no partition found for row» воспроизведён и починен ретро-партицией
- [ ] `PROGRESS.md` обновлён

**Следующий модуль:** `09-plpgsql-triggers-notify` (аудит, триггеры, LISTEN/NOTIFY).