# Лаба 01: Обход триггера / отключённый NOTIFY — сломай и почини

**Кейсы:** 12 (инциденты), 6 (события), 4 (аудит)
**Модуль:** 09-plpgsql-triggers-notify
**Время:** 1.5 ч
**Тип:** диагностируй по симптомам

## Цель

Понять, как «тихо» ломается безопасность/события, когда триггеры отключаются, и как это вовремя увидеть:

1. **Обход триггера аудита** — `DISABLE TRIGGER` → изменения идут, журнал молчит (а цепочка хэшей «честна», но неполна).
2. **Отключённый NOTIFY** — события не приходят никуда: ни триггеру, ни в `device_events`.

## Схема стенда

`course_m06` с 00-setup.sql (триггеры trg_devices_audit, trg_devices_notify) + Rust-слушатель (пример 06).

## Часть A. Обход триггера аудита

1. Зафиксируй число записей аудита:

   ```sql
   SELECT count(*) AS before FROM audit_log;
   ```

2. «Сломал» (грубая ошибка эксплуатации — DDL из-под владельца):

   ```sql
   ALTER TABLE devices DISABLE TRIGGER trg_devices_audit;
   INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'mass_meter', 'M-99-hidden', 'X');
   UPDATE devices SET model = 'X2' WHERE tag = 'M-99-hidden';
   SELECT count(*) AS after FROM audit_log;      -- НЕ изменилось!
   ```

3. Диагностика «почему нет аудита»: посмотри активные триггеры:

   ```sql
   SELECT tgname, tgenabled FROM pg_trigger WHERE tgrelid = 'devices'::regclass;
   -- tgenabled: O = enabled, D = disabled
   ```

   `tgenabled = 'D'` — триггер отключён. Проверь и целостность цепочки (`cargo run --example 05-audit-verify`): она **пройдёт** — потому что обход не «ломает» цепочку, а просто не пишет строки. Дыра — в полноте, не в связности.

4. **Почини**: 

   ```sql
   ALTER TABLE devices ENABLE TRIGGER trg_devices_audit;
   INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'mass_meter', 'M-99-fixed', 'X');
   SELECT count(*) FROM audit_log;               -- выросло
   ```

5. **Профилактика**: владелец/DDL — только деплой-роль; приложение-шлюз — без DDL-прав. Мониторинг отключённых триггеров (`pg_trigger.tgenabled <> 'O'`).

## Часть B. Отключённый NOTIFY

1. Запусти слушателя (пример 06, фон):

   ```bash
   cargo run --example 06-listener
   ```

2. NOTIFY «не дойдёт» в двух случаях:

   ```sql
   -- случай 1: ROLLBACK — NOTIFY уйдёт только на COMMIT, на ROLLBACK — не придёт
   BEGIN;
   INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'moisture_meter', 'W-99-nc', 'X');
   ROLLBACK;                       -- слушатель МОЛЧИТ (правильно!)

   -- случай 2: триггер отключён — событий нет, даже с COMMIT
   ALTER TABLE devices DISABLE TRIGGER trg_devices_notify;
   INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'moisture_meter', 'W-99-t', 'X');
   COMMIT;  -- (в psql автокоммит) — слушатель МОЛЧИТ (неправильно!)
   SELECT count(*) FROM device_events;   -- 0 — событие потеряно
   ```

3. **Почини**:

   ```sql
   ALTER TABLE devices ENABLE TRIGGER trg_devices_notify;
   INSERT INTO devices (line_id, device_type, tag, model) VALUES (1, 'moisture_meter', 'W-99-fixed', 'X');
   -- слушатель получил событие, device_events пополнилась
   ```

## Критерии успеха

- [ ] Часть A: воспроизвёл `INSERT` без аудита после `DISABLE TRIGGER`; нашёл причину в `pg_trigger.tgenabled`; починил и увидел новую запись
- [ ] Объяснил, почему проверка цепочки «прошла», хотя изменения не залогированы (полнота ≠ связность)
- [ ] Часть B: слушатель молчит на ROLLBACK — объяснил транзакционность NOTIFY
- [ ] Воспроизвёл потерю событий при отключённом триггере; починил; события дошли
- [ ] Прибрал за собой (удалил временные устройства/данные, триггеры включены)

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| «аудит есть, но изменений не видно» | DISABLE TRIGGER или забыли триггер на операцию | `pg_trigger.tgenabled`; проф-мониторинг отключённых |
| цепочка «проходит», а записей мало | дыра в полноте (не связана с хэшами) | сверяй count аудита с реальными изменениями (алертинг) |
| слушатель молчит на INSERT в psql | NOTIFY ждёт COMMIT | события приходят «на коммите», в psql автокоммит — ок, но BEGIN+ROLLBACK — нет |
| события потеряны навсегда | триггер отключён/ошибка в нем | включи; для критичных событий — дублирование в таблицу при COMMIT (модуль 10, outbox) |
| `pg_trigger.tgenabled` = 'D' у многих | деплой выключал | включай осознанно; прогоняй проверку «все важные триггеры enabled» |

## Задания «со звёздочкой»

1. **Алерт на отключённые триггеры**: запрос по `pg_trigger` в скрипт/алиасинг (`pg_trigger.tgenabled <> 'O'`), добавь в мониторинг модуля 13.
2. **NOTIFY без триггера**: покажи `pg_notify` из транзакции + задержку до COMMIT (двухсессионный сценарий).
3. **Outbox-паттерн**: вместо «сигнала» — запись в таблицу событий в той же транзакции, чтение воркером (задел модуля 10).

## Что дальше

- Модуль 10: рабочая очередь и outbox поверх этих механик.
- Модуль 13: мониторинг триггеров/событий в проде.