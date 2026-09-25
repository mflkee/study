-- 0001_capstone.sql — схема капстоуна: каталог с аудитом + партиционированные измерения.
-- База: capstone (порт 15442, поднимается docker-compose капстоуна).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Каталог устройств (минимальный) + Справочник «линий» нет — достаточно devices.
CREATE TABLE IF NOT EXISTS devices (
    id          serial PRIMARY KEY,
    device_type text NOT NULL CHECK (device_type IN ('mass_meter', 'density_meter')),
    tag         text NOT NULL UNIQUE,
    model       text
);

-- 2. Архив измерений: партицирование РО ДОКУМЕНТИРОВАННОЕ по времени (модуль 08).
CREATE TABLE IF NOT EXISTS measurements_hist (
    id        bigserial NOT NULL,
    device_id int NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    seq       bigint NOT NULL,
    ts        timestamptz NOT NULL,
    value     numeric(12,4) NOT NULL,
    UNIQUE (device_id, seq, ts)      -- идемпотентность приёма (модуль 10);
                                     -- партиционированные таблицы требуют ключ партиции
                                     -- в UNIQUE (ADR-02), поэтому ts включён
) PARTITION BY RANGE (ts);

-- Партиции на месяц (автосоздание недостающих — в шлюзе/миграции ниже).
CREATE TABLE IF NOT EXISTS measurements_hist_2026_09 PARTITION OF measurements_hist
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS measurements_hist_2026_10 PARTITION OF measurements_hist
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- 3. Журнал аудита с цепочкой sha256 (модуль 09).
CREATE TABLE IF NOT EXISTS audit_log (
    id        bigserial PRIMARY KEY,
    entity    text NOT NULL,
    entity_id int NOT NULL,
    action    text NOT NULL,
    data      jsonb NOT NULL,
    ts        timestamptz NOT NULL DEFAULT now(),
    prev_hash text NOT NULL,
    row_hash  text NOT NULL
);

CREATE OR REPLACE FUNCTION audit_chain_hash(
    prev text, entity text, entity_id int, action text, data jsonb, ts timestamptz
) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(digest(
        prev || '|' || entity || '|' || entity_id::text || '|' || action
             || '|' || data::text || '|' || ts::text, 'sha256'), 'hex');
$$;

CREATE OR REPLACE FUNCTION audit_seed() RETURNS text
    LANGUAGE sql IMMUTABLE AS $$ SELECT 'seed_capstone_v1' $$;

CREATE OR REPLACE FUNCTION devices_audit_trigger() RETURNS trigger AS $$
DECLARE prev text; rh text;
BEGIN
    SELECT row_hash INTO prev FROM audit_log ORDER BY id DESC LIMIT 1;
    IF prev IS NULL THEN prev := audit_seed(); END IF;
    rh := audit_chain_hash(prev, 'devices', COALESCE(NEW.id, OLD.id), TG_OP,
                           to_jsonb(COALESCE(NEW, OLD)), now());
    INSERT INTO audit_log (entity, entity_id, action, data, ts, prev_hash, row_hash)
    VALUES ('devices', COALESCE(NEW.id, OLD.id), TG_OP,
            to_jsonb(COALESCE(NEW, OLD)), now(), prev, rh);
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_devices_audit
    AFTER INSERT OR UPDATE OR DELETE ON devices
    FOR EACH ROW EXECUTE FUNCTION devices_audit_trigger();

CREATE OR REPLACE FUNCTION deny_audit_rewrite() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'audit_log защищён'; END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_deny_audit_rewrite
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION deny_audit_rewrite();

-- 4. Seed каталога.
INSERT INTO devices (device_type, tag, model) VALUES
    ('mass_meter', 'M-01-001', 'CMF-300'),
    ('density_meter', 'D-01-001', 'MVD-1')
ON CONFLICT (tag) DO NOTHING;