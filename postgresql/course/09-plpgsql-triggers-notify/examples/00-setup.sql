-- 00-setup.sql — модуль 09: журнал аудита с защитой от подмены, триггеры, NOTIFY, RLS.
-- База: course_m06 (таблицы devices/equipment_lines уже есть из модулей 06/08).
-- Идемпотентно; применяется в psql course_m06.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- 1. Журнал аудита: цепочка хэшей (кейс 4).
-- ============================================================
DROP TABLE IF EXISTS audit_log CASCADE;

CREATE TABLE audit_log (
    id        bigserial PRIMARY KEY,
    entity    text NOT NULL,            -- 'devices' и т.п.
    entity_id int NOT NULL,
    action    text NOT NULL,            -- 'INSERT' | 'UPDATE' | 'DELETE'
    data      jsonb NOT NULL,           -- снимок строки
    ts        timestamptz NOT NULL DEFAULT now(),
    prev_hash text NOT NULL,            -- хэш ПРЕДЫДУЩЕЙ строки журнала
    row_hash  text NOT NULL             -- хэш этой строки (см. функцию ниже)
);

-- Хэш строки журнала: содержимое + связь с предыдущей записью.
CREATE OR REPLACE FUNCTION audit_chain_hash(
    prev    text,
    entity  text,
    entity_id int,
    action  text,
    data    jsonb,
    ts      timestamptz
) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(digest(
        prev || '|' || entity || '|' || entity_id::text || '|' || action
             || '|' || data::text || '|' || ts::text,
        'sha256'), 'hex');
$$;

-- «Зерно» цепочки: константа, известная всем проверяющим.
CREATE OR REPLACE FUNCTION audit_seed() RETURNS text
    LANGUAGE sql IMMUTABLE AS $$ SELECT 'seed_СИКН-2026-v1' $$;

-- Аудит изменений справочника devices (кейс 4: журнал с защитой от подмены).
CREATE OR REPLACE FUNCTION devices_audit_trigger() RETURNS trigger AS $$
DECLARE
    prev text;
    rh   text;
BEGIN
    SELECT row_hash INTO prev FROM audit_log ORDER BY id DESC LIMIT 1;
    IF prev IS NULL THEN prev := audit_seed(); END IF;
    rh := audit_chain_hash(prev, 'devices', COALESCE(NEW.id, OLD.id), TG_OP,
                           to_jsonb(COALESCE(NEW, OLD)), now());
    -- ВАЖНО: на DELETE NEW пуст — снимок берём из OLD (иначе data = NULL).
    INSERT INTO audit_log (entity, entity_id, action, data, ts, prev_hash, row_hash)
    VALUES ('devices', COALESCE(NEW.id, OLD.id), TG_OP, to_jsonb(COALESCE(NEW, OLD)), now(), prev, rh);
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_devices_audit ON devices;
CREATE TRIGGER trg_devices_audit
    AFTER INSERT OR UPDATE OR DELETE ON devices
    FOR EACH ROW EXECUTE FUNCTION devices_audit_trigger();

-- ============================================================
-- 2. Защита журнала: UPDATE/DELETE запрещены триггером (не только привилегиями).
-- ============================================================
CREATE OR REPLACE FUNCTION deny_audit_rewrite() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log защищён: изменение/удаление записей запрещено (кейс 4)';
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_deny_audit_rewrite ON audit_log;
CREATE TRIGGER trg_deny_audit_rewrite
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION deny_audit_rewrite();

-- ============================================================
-- 3. NOTIFY: триггер шлёт событие при изменении devices (кейс 6).
-- ============================================================
CREATE OR REPLACE FUNCTION devices_notify_trigger() RETURNS trigger AS $$
DECLARE
    payload jsonb;
    dev     devices%ROWTYPE;
BEGIN
    dev := COALESCE(NEW, OLD);
    payload := jsonb_build_object(
        'id',   dev.id,
        'tag',  dev.tag,
        'op',   TG_OP,
        'ts',   now()
    );
    PERFORM pg_notify('device_changed', payload::text);
    RETURN COALESCE(NEW, OLD);
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_devices_notify ON devices;
CREATE TRIGGER trg_devices_notify
    AFTER INSERT OR UPDATE OR DELETE ON devices
    FOR EACH ROW EXECUTE FUNCTION devices_notify_trigger();

-- ============================================================
-- 4. Таблица событий для Rust-слушателя + квитирование (кейс 6).
-- ============================================================
DROP TABLE IF EXISTS device_events;
CREATE TABLE device_events (
    id      bigserial PRIMARY KEY,
    channel text NOT NULL,
    payload jsonb NOT NULL,
    ts      timestamptz NOT NULL DEFAULT now(),
    status  text NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'done'))
);

-- ============================================================
-- 5. Роли и RLS (урок 04).
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_user';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auditor') THEN
        CREATE ROLE auditor LOGIN PASSWORD 'auditor';
    END IF;
END $$;

-- RLS на devices: аудитор читает всё, app_user — только «свои» строки по tag.
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS devices_select_all ON devices;
CREATE POLICY devices_select_all ON devices
    FOR SELECT TO auditor USING (true);
DROP POLICY IF EXISTS devices_app_user ON devices;
CREATE POLICY devices_app_user ON devices
    FOR SELECT TO app_user USING (device_type <> 'level_meter');   -- пример правила

-- ------------------------------------------------------------
-- Снэпшот: примеры для проверки.
-- ------------------------------------------------------------
SELECT count(*) AS audit_rows FROM audit_log;
SELECT count(*) AS events_rows FROM device_events;