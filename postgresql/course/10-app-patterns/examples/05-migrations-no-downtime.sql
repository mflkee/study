-- 05-migrations-no-downtime.sql — миграции без даунтайма (урок 04).
-- Модуль: 10. База: course_m06. Запускать ВНЕ транзакции (psql),
-- т.к. CREATE INDEX CONCURRENTLY и DROP INDEX CONCURRENTLY нельзя в транзакции.

-- 1. Новая колонка со значением по умолчанию — мгновенно (метаданные),
--    без переписывания таблицы (PG11+):
ALTER TABLE devices ADD COLUMN IF NOT EXISTS installed_since date DEFAULT CURRENT_DATE;

-- 2. NOT NULL: в PG11+ это тоже «метаданные» (без ре-записи), НО требует
--    отсутствия NULL во всех строках. Правильный порядок в проде:
--    ADD COLUMN с DEFAULT → backfill пачками → ALTER COLUMN SET NOT NULL.
ALTER TABLE devices ALTER COLUMN installed_since SET NOT NULL;

-- 3. Индекс БЕЗ блокировки записей — CONCURRENTLY (не в транзакции!):
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_devices_line_type
    ON devices (line_id, device_type);
SELECT indexrelid::regclass, indisready, indisvalid FROM pg_index
 WHERE indexrelid = 'idx_devices_line_type'::regclass;
-- indisvalid = t → индекс готов и используется.

-- 4. Backfill большими пачками (не одним UPDATE: не держим снимок сутками):
--    UPDATE devices SET installed_since = ... WHERE id BETWEEN $1 AND $2 (пачкой 10_000);
--    (демонстрация шаблона; тут backfill не нужен — колонка пустая с DEFAULT).

-- 5. Двойная запись (выкатка) — тема урока: новую колонку заполняют и старый код,
--    и новый, потом переключают чтение. Модель проверки просто показать:

-- Убираем демо-колонку (в проде — новая миграция, не правка истории):
ALTER TABLE devices DROP COLUMN IF EXISTS installed_since;
DROP INDEX CONCURRENTLY IF EXISTS idx_devices_line_type;