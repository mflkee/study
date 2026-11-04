-- =============================================================================
-- metroLog — схема базы данных (PostgreSQL 16)
--
-- Проект:      metroLog — система учёта средств измерений и эталонов
-- Репозиторий: https://github.com/mflkee/metroLog
-- Версия кода: 6ed7c5c (release v0.13.0)
-- Дата:        18.10.2026
--
-- Как получен:
--   1. Поднят одноразовый контейнер postgres:16-alpine (база `metrolog`).
--   2. Выполнены все миграции проекта: `alembic upgrade head` (66 ревизий,
--      0001_create_users … 0066_folder_refresh_candidates).
--   3. Схема снята командой
--      `pg_dump --schema-only --no-owner --no-privileges`.
--   4. Из дампа убраны служебные SET/SELECT и комментарии pg_dump; префикс
--      `public.` снят, добавлена эта шапка и `SET search_path`.
--   Скрипт воспроизводит ровно то состояние, которое создают миграции, — это
--   не ручная реконструкция по моделям.
--
-- Состав: 32 таблицы, из них
--   * 30 — таблицы моделей SQLAlchemy (`backend/app/models/*.py`);
--   * auth_codes — устаревшая таблица миграции 0002, в коде не используется
--     (оставлена, чтобы файл совпадал с реальной БД);
--   * alembic_version — служебная таблица Alembic.
--   51 внешний ключ (50 — у таблиц моделей, 1 — у auth_codes),
--   17 UNIQUE-ограничений, 80 индексов (2 уникальных, 8 частичных);
--   у таблиц моделей — 50 FK, 17 UNIQUE и 76 индексов, остальное у auth_codes.
--
-- Примечание: перечисления (роли, статусы, типы) хранятся как VARCHAR(16..48)
-- без CHECK-ограничений — значение проверяет приложение (StrEnum + Pydantic).
-- =============================================================================

SET client_encoding = 'UTF8';
SET search_path TO public;

CREATE TABLE alembic_version (
    version_num character varying(32) NOT NULL
);

CREATE TABLE auth_codes (
    id integer NOT NULL,
    user_id integer NOT NULL,
    code_type character varying(32) NOT NULL,
    code_hash character varying(128) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE SEQUENCE auth_codes_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE auth_codes_id_seq OWNED BY auth_codes.id;

CREATE TABLE deadline_presets (
    id integer NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    repair_total_days integer NOT NULL,
    registration_after_arrival_days integer NOT NULL,
    incoming_control_after_receipt_days integer NOT NULL,
    payment_after_control_days integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    repair_stage_templates_json json,
    verification_stage_templates_json json
);

CREATE SEQUENCE deadline_presets_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE deadline_presets_id_seq OWNED BY deadline_presets.id;

CREATE TABLE equipment (
    id integer NOT NULL,
    group_id integer,
    object_name character varying(255) NOT NULL,
    equipment_type character varying(32) NOT NULL,
    name character varying(255) NOT NULL,
    modification character varying(255),
    serial_number character varying(255),
    manufacture_year integer,
    status character varying(32) NOT NULL,
    current_location_manual character varying(255),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    folder_id integer,
    compliance_date date,
    compliance_interval_months integer,
    measurement_range_start character varying(255),
    measurement_range_end character varying(255),
    measurement_unit character varying(128),
    created_manually boolean NOT NULL,
    exclude_from_arshin_refresh boolean NOT NULL,
    manual_verification_interval_months integer,
    sort_order integer DEFAULT 0 NOT NULL
);

CREATE TABLE equipment_attachments (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE SEQUENCE equipment_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_attachments_id_seq OWNED BY equipment_attachments.id;

CREATE TABLE equipment_comment_attachments (
    id integer NOT NULL,
    equipment_comment_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE equipment_comment_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_comment_attachments_id_seq OWNED BY equipment_comment_attachments.id;

CREATE TABLE equipment_comments (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    author_user_id integer,
    author_display_name character varying(255) NOT NULL,
    text text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_private boolean NOT NULL
);

CREATE SEQUENCE equipment_comments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_comments_id_seq OWNED BY equipment_comments.id;

CREATE TABLE equipment_esi_composition_entries (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    vri_id character varying(255) NOT NULL,
    arshin_url character varying(1024),
    org_title character varying(255),
    mit_number character varying(255),
    mit_title character varying(255),
    mit_notation character varying(255),
    mi_number character varying(255),
    result_docnum character varying(255),
    verification_date timestamp with time zone,
    valid_date timestamp with time zone,
    raw_payload_json json,
    detail_payload_json json,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    module_kind character varying(32) NOT NULL,
    measurement_limit character varying(255),
    sort_order integer NOT NULL
);

CREATE SEQUENCE equipment_esi_composition_entries_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_esi_composition_entries_id_seq OWNED BY equipment_esi_composition_entries.id;

CREATE TABLE equipment_folder_refresh_rows (
    id integer NOT NULL,
    task_id integer NOT NULL,
    equipment_id integer NOT NULL,
    composition_entry_id integer,
    sort_order integer NOT NULL,
    target_kind character varying(32) NOT NULL,
    module_kind character varying(32),
    equipment_name character varying(255) NOT NULL,
    equipment_modification character varying(255),
    equipment_serial_number character varying(255),
    target_title character varying(255),
    target_serial_number character varying(255),
    target_registry_number character varying(255),
    measurement_limit character varying(255),
    current_certificate_number character varying(255),
    current_verification_date timestamp with time zone,
    current_valid_date timestamp with time zone,
    status character varying(32) NOT NULL,
    uncertain_update boolean NOT NULL,
    stage2_successful boolean,
    modification_relaxed boolean,
    notation_relaxed boolean,
    notes text,
    matched_vri_id character varying(255),
    matched_arshin_url character varying(1024),
    matched_registry_number character varying(255),
    matched_certificate_number character varying(255),
    matched_verification_date timestamp with time zone,
    matched_valid_date timestamp with time zone,
    matched_payload_json json,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    serial_ambiguous boolean DEFAULT false NOT NULL,
    candidates_json json,
    picked_candidate_vri_id character varying(255)
);

CREATE SEQUENCE equipment_folder_refresh_rows_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_folder_refresh_rows_id_seq OWNED BY equipment_folder_refresh_rows.id;

CREATE TABLE equipment_folder_refresh_tasks (
    id integer NOT NULL,
    folder_id integer NOT NULL,
    created_by_user_id integer,
    status character varying(32) NOT NULL,
    progress integer NOT NULL,
    total_rows integer NOT NULL,
    processed_rows integer NOT NULL,
    summary_json json,
    error_message text,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE equipment_folder_refresh_tasks_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_folder_refresh_tasks_id_seq OWNED BY equipment_folder_refresh_tasks.id;

CREATE TABLE equipment_folders (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deadline_preset_id integer,
    deadline_preset_name character varying(255),
    deadline_preset_snapshot_json json
);

CREATE SEQUENCE equipment_folders_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_folders_id_seq OWNED BY equipment_folders.id;

CREATE TABLE equipment_groups (
    id integer NOT NULL,
    folder_id integer NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE SEQUENCE equipment_groups_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_groups_id_seq OWNED BY equipment_groups.id;

CREATE SEQUENCE equipment_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_id_seq OWNED BY equipment.id;

CREATE TABLE equipment_process_subscriptions (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    user_id integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE equipment_process_subscriptions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE equipment_process_subscriptions_id_seq OWNED BY equipment_process_subscriptions.id;

CREATE TABLE event_logs (
    id integer NOT NULL,
    category character varying(32) NOT NULL,
    action character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    user_id integer,
    user_display_name character varying(255) NOT NULL,
    equipment_id integer,
    equipment_name character varying(255),
    equipment_modification character varying(255),
    equipment_serial_number character varying(255),
    folder_id integer,
    folder_name character varying(255),
    batch_key character varying(64),
    event_date date DEFAULT CURRENT_DATE NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE event_logs_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE event_logs_id_seq OWNED BY event_logs.id;

CREATE TABLE folder_process_subscriptions (
    id integer NOT NULL,
    folder_id integer NOT NULL,
    user_id integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE folder_process_subscriptions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE folder_process_subscriptions_id_seq OWNED BY folder_process_subscriptions.id;

CREATE TABLE repair_message_attachments (
    id integer NOT NULL,
    repair_message_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE repair_message_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE repair_message_attachments_id_seq OWNED BY repair_message_attachments.id;

CREATE TABLE repair_messages (
    id integer NOT NULL,
    repair_id integer NOT NULL,
    author_user_id integer,
    author_display_name character varying(255) NOT NULL,
    text text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    batch_key character varying(64),
    is_private boolean NOT NULL
);

CREATE SEQUENCE repair_messages_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE repair_messages_id_seq OWNED BY repair_messages.id;

CREATE TABLE repairs (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    route_destination character varying(255) NOT NULL,
    sent_to_repair_at date NOT NULL,
    repair_deadline_at date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    closed_at date,
    route_city character varying(255) NOT NULL,
    sent_from_repair_at date,
    sent_from_irkutsk_at date,
    arrived_to_lensk_at date,
    actually_received_at date,
    incoming_control_at date,
    paid_at date,
    arrived_to_destination_at date,
    batch_key character varying(64),
    batch_name character varying(255),
    repair_total_days_snapshot integer NOT NULL,
    registration_after_arrival_days_snapshot integer NOT NULL,
    incoming_control_after_receipt_days_snapshot integer NOT NULL,
    payment_after_control_days_snapshot integer NOT NULL,
    is_on_site boolean NOT NULL,
    custom_stages_json json NOT NULL
);

CREATE SEQUENCE repairs_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE repairs_id_seq OWNED BY repairs.id;

CREATE TABLE si_verifications (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    vri_id character varying(255) NOT NULL,
    arshin_url character varying(1024),
    org_title character varying(255),
    mit_number character varying(255),
    mit_title character varying(255),
    mit_notation character varying(255),
    mi_number character varying(255),
    result_docnum character varying(255),
    verification_date timestamp with time zone,
    valid_date timestamp with time zone,
    raw_payload_json json,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    detail_payload_json json,
    certificate_number character varying(255)
);

CREATE SEQUENCE si_verifications_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE si_verifications_id_seq OWNED BY si_verifications.id;

CREATE TABLE task_attachments (
    id integer NOT NULL,
    task_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_attachments_id_seq OWNED BY task_attachments.id;

CREATE TABLE task_checklist_items (
    id integer NOT NULL,
    task_id integer NOT NULL,
    label character varying(255) NOT NULL,
    is_done boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_checklist_items_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_checklist_items_id_seq OWNED BY task_checklist_items.id;

CREATE TABLE task_equipment (
    id integer NOT NULL,
    task_id integer NOT NULL,
    equipment_id integer NOT NULL,
    note text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_equipment_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_equipment_id_seq OWNED BY task_equipment.id;

CREATE TABLE task_message_attachments (
    id integer NOT NULL,
    task_message_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_message_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_message_attachments_id_seq OWNED BY task_message_attachments.id;

CREATE TABLE task_messages (
    id integer NOT NULL,
    task_id integer NOT NULL,
    author_user_id integer,
    author_display_name character varying(255) NOT NULL,
    text text,
    is_private boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_messages_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_messages_id_seq OWNED BY task_messages.id;

CREATE TABLE task_participants (
    id integer NOT NULL,
    task_id integer NOT NULL,
    user_id integer NOT NULL,
    role character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_participants_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_participants_id_seq OWNED BY task_participants.id;

CREATE TABLE task_reminder_log (
    id integer NOT NULL,
    task_id integer NOT NULL,
    reminder_date date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_reminder_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_reminder_log_id_seq OWNED BY task_reminder_log.id;

CREATE TABLE task_subscriptions (
    id integer NOT NULL,
    task_id integer NOT NULL,
    user_id integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE task_subscriptions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE task_subscriptions_id_seq OWNED BY task_subscriptions.id;

CREATE TABLE tasks (
    id integer NOT NULL,
    folder_id integer,
    title character varying(255) NOT NULL,
    description text,
    status character varying(32) NOT NULL,
    priority character varying(16) NOT NULL,
    kind character varying(128),
    tags json,
    due_date date,
    created_by_user_id integer,
    created_by_display_name character varying(255) NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    board_order integer
);

CREATE SEQUENCE tasks_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE tasks_id_seq OWNED BY tasks.id;

CREATE TABLE users (
    id integer NOT NULL,
    email character varying(320) NOT NULL,
    password_hash character varying(512) NOT NULL,
    role character varying(32) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    email_verified_at timestamp with time zone,
    must_change_password boolean DEFAULT false NOT NULL,
    password_changed_at timestamp with time zone,
    phone character varying(64),
    "position" character varying(255),
    facility character varying(255),
    organization character varying(255),
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    patronymic character varying(255),
    theme_preference character varying(32),
    enabled_theme_options json,
    dashboard_folder_id integer,
    dashboard_widget_options json,
    mention_email_notifications_enabled boolean DEFAULT true NOT NULL,
    last_login_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    allowed_folder_ids json,
    hidden_equipment_folder_ids json,
    dashboard_folder_ids json,
    folder_order_ids json,
    dashboard_layout json,
    role_synced_at timestamp with time zone
);

CREATE SEQUENCE users_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users_id_seq OWNED BY users.id;

CREATE TABLE verification_message_attachments (
    id integer NOT NULL,
    verification_message_id integer NOT NULL,
    uploaded_by_user_id integer,
    uploaded_by_display_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_mime_type character varying(255),
    file_size bigint NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE verification_message_attachments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE verification_message_attachments_id_seq OWNED BY verification_message_attachments.id;

CREATE TABLE verification_messages (
    id integer NOT NULL,
    verification_id integer NOT NULL,
    author_user_id integer,
    author_display_name character varying(255) NOT NULL,
    text text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    batch_key character varying(64),
    is_private boolean NOT NULL
);

CREATE SEQUENCE verification_messages_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE verification_messages_id_seq OWNED BY verification_messages.id;

CREATE TABLE verifications (
    id integer NOT NULL,
    equipment_id integer NOT NULL,
    route_city character varying(255) NOT NULL,
    route_destination character varying(255) NOT NULL,
    sent_to_verification_at date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    closed_at date,
    received_at_destination_at date,
    handed_to_csm_at date,
    verification_completed_at date,
    picked_up_from_csm_at date,
    shipped_back_at date,
    returned_from_verification_at date,
    batch_key character varying(64),
    batch_name character varying(255),
    is_on_site boolean NOT NULL,
    flow_mode character varying(48) NOT NULL,
    custom_stages_json json NOT NULL
);

CREATE SEQUENCE verifications_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE verifications_id_seq OWNED BY verifications.id;

ALTER TABLE ONLY auth_codes ALTER COLUMN id SET DEFAULT nextval('auth_codes_id_seq'::regclass);

ALTER TABLE ONLY deadline_presets ALTER COLUMN id SET DEFAULT nextval('deadline_presets_id_seq'::regclass);

ALTER TABLE ONLY equipment ALTER COLUMN id SET DEFAULT nextval('equipment_id_seq'::regclass);

ALTER TABLE ONLY equipment_attachments ALTER COLUMN id SET DEFAULT nextval('equipment_attachments_id_seq'::regclass);

ALTER TABLE ONLY equipment_comment_attachments ALTER COLUMN id SET DEFAULT nextval('equipment_comment_attachments_id_seq'::regclass);

ALTER TABLE ONLY equipment_comments ALTER COLUMN id SET DEFAULT nextval('equipment_comments_id_seq'::regclass);

ALTER TABLE ONLY equipment_esi_composition_entries ALTER COLUMN id SET DEFAULT nextval('equipment_esi_composition_entries_id_seq'::regclass);

ALTER TABLE ONLY equipment_folder_refresh_rows ALTER COLUMN id SET DEFAULT nextval('equipment_folder_refresh_rows_id_seq'::regclass);

ALTER TABLE ONLY equipment_folder_refresh_tasks ALTER COLUMN id SET DEFAULT nextval('equipment_folder_refresh_tasks_id_seq'::regclass);

ALTER TABLE ONLY equipment_folders ALTER COLUMN id SET DEFAULT nextval('equipment_folders_id_seq'::regclass);

ALTER TABLE ONLY equipment_groups ALTER COLUMN id SET DEFAULT nextval('equipment_groups_id_seq'::regclass);

ALTER TABLE ONLY equipment_process_subscriptions ALTER COLUMN id SET DEFAULT nextval('equipment_process_subscriptions_id_seq'::regclass);

ALTER TABLE ONLY event_logs ALTER COLUMN id SET DEFAULT nextval('event_logs_id_seq'::regclass);

ALTER TABLE ONLY folder_process_subscriptions ALTER COLUMN id SET DEFAULT nextval('folder_process_subscriptions_id_seq'::regclass);

ALTER TABLE ONLY repair_message_attachments ALTER COLUMN id SET DEFAULT nextval('repair_message_attachments_id_seq'::regclass);

ALTER TABLE ONLY repair_messages ALTER COLUMN id SET DEFAULT nextval('repair_messages_id_seq'::regclass);

ALTER TABLE ONLY repairs ALTER COLUMN id SET DEFAULT nextval('repairs_id_seq'::regclass);

ALTER TABLE ONLY si_verifications ALTER COLUMN id SET DEFAULT nextval('si_verifications_id_seq'::regclass);

ALTER TABLE ONLY task_attachments ALTER COLUMN id SET DEFAULT nextval('task_attachments_id_seq'::regclass);

ALTER TABLE ONLY task_checklist_items ALTER COLUMN id SET DEFAULT nextval('task_checklist_items_id_seq'::regclass);

ALTER TABLE ONLY task_equipment ALTER COLUMN id SET DEFAULT nextval('task_equipment_id_seq'::regclass);

ALTER TABLE ONLY task_message_attachments ALTER COLUMN id SET DEFAULT nextval('task_message_attachments_id_seq'::regclass);

ALTER TABLE ONLY task_messages ALTER COLUMN id SET DEFAULT nextval('task_messages_id_seq'::regclass);

ALTER TABLE ONLY task_participants ALTER COLUMN id SET DEFAULT nextval('task_participants_id_seq'::regclass);

ALTER TABLE ONLY task_reminder_log ALTER COLUMN id SET DEFAULT nextval('task_reminder_log_id_seq'::regclass);

ALTER TABLE ONLY task_subscriptions ALTER COLUMN id SET DEFAULT nextval('task_subscriptions_id_seq'::regclass);

ALTER TABLE ONLY tasks ALTER COLUMN id SET DEFAULT nextval('tasks_id_seq'::regclass);

ALTER TABLE ONLY users ALTER COLUMN id SET DEFAULT nextval('users_id_seq'::regclass);

ALTER TABLE ONLY verification_message_attachments ALTER COLUMN id SET DEFAULT nextval('verification_message_attachments_id_seq'::regclass);

ALTER TABLE ONLY verification_messages ALTER COLUMN id SET DEFAULT nextval('verification_messages_id_seq'::regclass);

ALTER TABLE ONLY verifications ALTER COLUMN id SET DEFAULT nextval('verifications_id_seq'::regclass);

ALTER TABLE ONLY alembic_version
    ADD CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num);

ALTER TABLE ONLY auth_codes
    ADD CONSTRAINT pk_auth_codes PRIMARY KEY (id);

ALTER TABLE ONLY deadline_presets
    ADD CONSTRAINT pk_deadline_presets PRIMARY KEY (id);

ALTER TABLE ONLY equipment
    ADD CONSTRAINT pk_equipment PRIMARY KEY (id);

ALTER TABLE ONLY equipment_attachments
    ADD CONSTRAINT pk_equipment_attachments PRIMARY KEY (id);

ALTER TABLE ONLY equipment_comment_attachments
    ADD CONSTRAINT pk_equipment_comment_attachments PRIMARY KEY (id);

ALTER TABLE ONLY equipment_comments
    ADD CONSTRAINT pk_equipment_comments PRIMARY KEY (id);

ALTER TABLE ONLY equipment_esi_composition_entries
    ADD CONSTRAINT pk_equipment_esi_composition_entries PRIMARY KEY (id);

ALTER TABLE ONLY equipment_folder_refresh_rows
    ADD CONSTRAINT pk_equipment_folder_refresh_rows PRIMARY KEY (id);

ALTER TABLE ONLY equipment_folder_refresh_tasks
    ADD CONSTRAINT pk_equipment_folder_refresh_tasks PRIMARY KEY (id);

ALTER TABLE ONLY equipment_folders
    ADD CONSTRAINT pk_equipment_folders PRIMARY KEY (id);

ALTER TABLE ONLY equipment_groups
    ADD CONSTRAINT pk_equipment_groups PRIMARY KEY (id);

ALTER TABLE ONLY equipment_process_subscriptions
    ADD CONSTRAINT pk_equipment_process_subscriptions PRIMARY KEY (id);

ALTER TABLE ONLY event_logs
    ADD CONSTRAINT pk_event_logs PRIMARY KEY (id);

ALTER TABLE ONLY folder_process_subscriptions
    ADD CONSTRAINT pk_folder_process_subscriptions PRIMARY KEY (id);

ALTER TABLE ONLY repair_message_attachments
    ADD CONSTRAINT pk_repair_message_attachments PRIMARY KEY (id);

ALTER TABLE ONLY repair_messages
    ADD CONSTRAINT pk_repair_messages PRIMARY KEY (id);

ALTER TABLE ONLY repairs
    ADD CONSTRAINT pk_repairs PRIMARY KEY (id);

ALTER TABLE ONLY si_verifications
    ADD CONSTRAINT pk_si_verifications PRIMARY KEY (id);

ALTER TABLE ONLY task_attachments
    ADD CONSTRAINT pk_task_attachments PRIMARY KEY (id);

ALTER TABLE ONLY task_checklist_items
    ADD CONSTRAINT pk_task_checklist_items PRIMARY KEY (id);

ALTER TABLE ONLY task_equipment
    ADD CONSTRAINT pk_task_equipment PRIMARY KEY (id);

ALTER TABLE ONLY task_message_attachments
    ADD CONSTRAINT pk_task_message_attachments PRIMARY KEY (id);

ALTER TABLE ONLY task_messages
    ADD CONSTRAINT pk_task_messages PRIMARY KEY (id);

ALTER TABLE ONLY task_participants
    ADD CONSTRAINT pk_task_participants PRIMARY KEY (id);

ALTER TABLE ONLY task_reminder_log
    ADD CONSTRAINT pk_task_reminder_log PRIMARY KEY (id);

ALTER TABLE ONLY task_subscriptions
    ADD CONSTRAINT pk_task_subscriptions PRIMARY KEY (id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT pk_tasks PRIMARY KEY (id);

ALTER TABLE ONLY users
    ADD CONSTRAINT pk_users PRIMARY KEY (id);

ALTER TABLE ONLY verification_message_attachments
    ADD CONSTRAINT pk_verification_message_attachments PRIMARY KEY (id);

ALTER TABLE ONLY verification_messages
    ADD CONSTRAINT pk_verification_messages PRIMARY KEY (id);

ALTER TABLE ONLY verifications
    ADD CONSTRAINT pk_verifications PRIMARY KEY (id);

ALTER TABLE ONLY equipment_attachments
    ADD CONSTRAINT uq_equipment_attachments_storage_path UNIQUE (storage_path);

ALTER TABLE ONLY equipment_comment_attachments
    ADD CONSTRAINT uq_equipment_comment_attachments_storage_path UNIQUE (storage_path);

ALTER TABLE ONLY equipment_esi_composition_entries
    ADD CONSTRAINT uq_equipment_esi_composition_entries_equipment_id_vri_id UNIQUE (equipment_id, vri_id);

ALTER TABLE ONLY equipment_folders
    ADD CONSTRAINT uq_equipment_folders_name UNIQUE (name);

ALTER TABLE ONLY equipment_groups
    ADD CONSTRAINT uq_equipment_groups_folder_id_name UNIQUE (folder_id, name);

ALTER TABLE ONLY equipment_process_subscriptions
    ADD CONSTRAINT uq_equipment_process_subscriptions_equipment_id_user_id UNIQUE (equipment_id, user_id);

ALTER TABLE ONLY folder_process_subscriptions
    ADD CONSTRAINT uq_folder_process_subscriptions_folder_id_user_id UNIQUE (folder_id, user_id);

ALTER TABLE ONLY repair_message_attachments
    ADD CONSTRAINT uq_repair_message_attachments_storage_path UNIQUE (storage_path);

ALTER TABLE ONLY si_verifications
    ADD CONSTRAINT uq_si_verifications_equipment_id UNIQUE (equipment_id);

ALTER TABLE ONLY task_attachments
    ADD CONSTRAINT uq_task_attachments_storage_path UNIQUE (storage_path);

ALTER TABLE ONLY task_equipment
    ADD CONSTRAINT uq_task_equipment_task_id_equipment_id UNIQUE (task_id, equipment_id);

ALTER TABLE ONLY task_message_attachments
    ADD CONSTRAINT uq_task_message_attachments_storage_path UNIQUE (storage_path);

ALTER TABLE ONLY task_participants
    ADD CONSTRAINT uq_task_participants_task_id_user_id_role UNIQUE (task_id, user_id, role);

ALTER TABLE ONLY task_reminder_log
    ADD CONSTRAINT uq_task_reminder_log_task_id_reminder_date UNIQUE (task_id, reminder_date);

ALTER TABLE ONLY task_subscriptions
    ADD CONSTRAINT uq_task_subscriptions_task_id_user_id UNIQUE (task_id, user_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

ALTER TABLE ONLY verification_message_attachments
    ADD CONSTRAINT uq_verification_message_attachments_storage_path UNIQUE (storage_path);

CREATE INDEX ix_auth_codes_code_hash ON auth_codes USING btree (code_hash);

CREATE INDEX ix_auth_codes_code_type ON auth_codes USING btree (code_type);

CREATE INDEX ix_auth_codes_expires_at ON auth_codes USING btree (expires_at);

CREATE INDEX ix_auth_codes_user_id ON auth_codes USING btree (user_id);

CREATE UNIQUE INDEX ix_deadline_presets_code ON deadline_presets USING btree (code);

CREATE UNIQUE INDEX ix_deadline_presets_name ON deadline_presets USING btree (name);

CREATE INDEX ix_equipment_attachments_equipment_id ON equipment_attachments USING btree (equipment_id);

CREATE INDEX ix_equipment_attachments_uploaded_by_user_id ON equipment_attachments USING btree (uploaded_by_user_id);

CREATE INDEX ix_equipment_comment_attachments_equipment_comment_id ON equipment_comment_attachments USING btree (equipment_comment_id);

CREATE INDEX ix_equipment_comment_attachments_uploaded_by_user_id ON equipment_comment_attachments USING btree (uploaded_by_user_id);

CREATE INDEX ix_equipment_comments_author_user_id ON equipment_comments USING btree (author_user_id);

CREATE INDEX ix_equipment_comments_equipment_id ON equipment_comments USING btree (equipment_id);

CREATE INDEX ix_equipment_esi_composition_entries_equipment_id ON equipment_esi_composition_entries USING btree (equipment_id);

CREATE INDEX ix_equipment_esi_composition_entries_vri_id ON equipment_esi_composition_entries USING btree (vri_id);

CREATE INDEX ix_equipment_folder_id ON equipment USING btree (folder_id);

CREATE INDEX ix_equipment_folder_refresh_rows_composition_entry_id ON equipment_folder_refresh_rows USING btree (composition_entry_id);

CREATE INDEX ix_equipment_folder_refresh_rows_equipment_id ON equipment_folder_refresh_rows USING btree (equipment_id);

CREATE INDEX ix_equipment_folder_refresh_rows_task_id ON equipment_folder_refresh_rows USING btree (task_id);

CREATE INDEX ix_equipment_folder_refresh_rows_task_sort ON equipment_folder_refresh_rows USING btree (task_id, sort_order, id);

CREATE INDEX ix_equipment_folder_refresh_tasks_created_by_user_id ON equipment_folder_refresh_tasks USING btree (created_by_user_id);

CREATE INDEX ix_equipment_folder_refresh_tasks_folder_id ON equipment_folder_refresh_tasks USING btree (folder_id);

CREATE INDEX ix_equipment_folders_deadline_preset_id ON equipment_folders USING btree (deadline_preset_id);

CREATE INDEX ix_equipment_folders_name ON equipment_folders USING btree (name);

CREATE INDEX ix_equipment_group_id ON equipment USING btree (group_id);

CREATE INDEX ix_equipment_groups_folder_id ON equipment_groups USING btree (folder_id);

CREATE INDEX ix_equipment_process_subscriptions_equipment_id ON equipment_process_subscriptions USING btree (equipment_id);

CREATE INDEX ix_equipment_process_subscriptions_user_id ON equipment_process_subscriptions USING btree (user_id);

CREATE INDEX ix_equipment_serial_number ON equipment USING btree (serial_number);

CREATE INDEX ix_event_logs_action ON event_logs USING btree (action);

CREATE INDEX ix_event_logs_batch_key ON event_logs USING btree (batch_key);

CREATE INDEX ix_event_logs_category ON event_logs USING btree (category);

CREATE INDEX ix_event_logs_created_at ON event_logs USING btree (created_at);

CREATE INDEX ix_event_logs_equipment_id ON event_logs USING btree (equipment_id);

CREATE INDEX ix_event_logs_event_date ON event_logs USING btree (event_date);

CREATE INDEX ix_event_logs_folder_id ON event_logs USING btree (folder_id);

CREATE INDEX ix_event_logs_user_id ON event_logs USING btree (user_id);

CREATE INDEX ix_folder_process_subscriptions_folder_id ON folder_process_subscriptions USING btree (folder_id);

CREATE INDEX ix_folder_process_subscriptions_user_id ON folder_process_subscriptions USING btree (user_id);

CREATE INDEX ix_repair_message_attachments_repair_message_id ON repair_message_attachments USING btree (repair_message_id);

CREATE INDEX ix_repair_message_attachments_uploaded_by_user_id ON repair_message_attachments USING btree (uploaded_by_user_id);

CREATE INDEX ix_repair_messages_author_user_id ON repair_messages USING btree (author_user_id);

CREATE INDEX ix_repair_messages_batch_key ON repair_messages USING btree (batch_key);

CREATE INDEX ix_repair_messages_repair_id ON repair_messages USING btree (repair_id);

CREATE INDEX ix_repairs_active_batch_lookup ON repairs USING btree (batch_key, id) WHERE (closed_at IS NULL);

CREATE INDEX ix_repairs_active_equipment_lookup ON repairs USING btree (equipment_id) WHERE (closed_at IS NULL);

CREATE INDEX ix_repairs_active_queue_order ON repairs USING btree (sent_to_repair_at, created_at, id) WHERE (closed_at IS NULL);

CREATE INDEX ix_repairs_archived_queue_order ON repairs USING btree (closed_at, updated_at, id) WHERE (closed_at IS NOT NULL);

CREATE INDEX ix_repairs_batch_key ON repairs USING btree (batch_key);

CREATE INDEX ix_repairs_equipment_id ON repairs USING btree (equipment_id);

CREATE INDEX ix_si_verifications_equipment_id ON si_verifications USING btree (equipment_id);

CREATE INDEX ix_si_verifications_vri_id ON si_verifications USING btree (vri_id);

CREATE INDEX ix_task_attachments_task_id ON task_attachments USING btree (task_id);

CREATE INDEX ix_task_checklist_items_task_id ON task_checklist_items USING btree (task_id);

CREATE INDEX ix_task_checklist_items_task_id_sort ON task_checklist_items USING btree (task_id, sort_order);

CREATE INDEX ix_task_equipment_equipment_id ON task_equipment USING btree (equipment_id);

CREATE INDEX ix_task_equipment_task_id ON task_equipment USING btree (task_id);

CREATE INDEX ix_task_message_attachments_task_message_id ON task_message_attachments USING btree (task_message_id);

CREATE INDEX ix_task_messages_author_user_id ON task_messages USING btree (author_user_id);

CREATE INDEX ix_task_messages_task_id ON task_messages USING btree (task_id);

CREATE INDEX ix_task_participants_task_id ON task_participants USING btree (task_id);

CREATE INDEX ix_task_participants_user_id ON task_participants USING btree (user_id);

CREATE INDEX ix_task_reminder_log_task_id ON task_reminder_log USING btree (task_id);

CREATE INDEX ix_task_subscriptions_task_id ON task_subscriptions USING btree (task_id);

CREATE INDEX ix_task_subscriptions_user_id ON task_subscriptions USING btree (user_id);

CREATE INDEX ix_tasks_created_by_user_id ON tasks USING btree (created_by_user_id);

CREATE INDEX ix_tasks_due_date_lookup ON tasks USING btree (due_date);

CREATE INDEX ix_tasks_folder_id ON tasks USING btree (folder_id);

CREATE INDEX ix_tasks_folder_id_status ON tasks USING btree (folder_id, status);

CREATE INDEX ix_users_email ON users USING btree (email);

CREATE INDEX ix_verification_message_attachments_uploaded_by_user_id ON verification_message_attachments USING btree (uploaded_by_user_id);

CREATE INDEX ix_verification_message_attachments_verification_message_id ON verification_message_attachments USING btree (verification_message_id);

CREATE INDEX ix_verification_messages_author_user_id ON verification_messages USING btree (author_user_id);

CREATE INDEX ix_verification_messages_batch_key ON verification_messages USING btree (batch_key);

CREATE INDEX ix_verification_messages_verification_id ON verification_messages USING btree (verification_id);

CREATE INDEX ix_verifications_active_batch_lookup ON verifications USING btree (batch_key, id) WHERE (closed_at IS NULL);

CREATE INDEX ix_verifications_active_equipment_lookup ON verifications USING btree (equipment_id) WHERE (closed_at IS NULL);

CREATE INDEX ix_verifications_active_queue_order ON verifications USING btree (sent_to_verification_at, created_at, id) WHERE (closed_at IS NULL);

CREATE INDEX ix_verifications_archived_queue_order ON verifications USING btree (closed_at, updated_at, id) WHERE (closed_at IS NOT NULL);

CREATE INDEX ix_verifications_batch_key ON verifications USING btree (batch_key);

CREATE INDEX ix_verifications_equipment_id ON verifications USING btree (equipment_id);

ALTER TABLE ONLY auth_codes
    ADD CONSTRAINT fk_auth_codes_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_attachments
    ADD CONSTRAINT fk_equipment_attachments_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_attachments
    ADD CONSTRAINT fk_equipment_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_comment_attachments
    ADD CONSTRAINT fk_equipment_comment_attachments_equipment_comment_id_e_00d4 FOREIGN KEY (equipment_comment_id) REFERENCES equipment_comments(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_comment_attachments
    ADD CONSTRAINT fk_equipment_comment_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_comments
    ADD CONSTRAINT fk_equipment_comments_author_user_id_users FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_comments
    ADD CONSTRAINT fk_equipment_comments_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_esi_composition_entries
    ADD CONSTRAINT fk_equipment_esi_composition_entries_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment
    ADD CONSTRAINT fk_equipment_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_folder_refresh_rows
    ADD CONSTRAINT fk_equipment_folder_refresh_rows_composition_entry_id_e_1a52 FOREIGN KEY (composition_entry_id) REFERENCES equipment_esi_composition_entries(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_folder_refresh_rows
    ADD CONSTRAINT fk_equipment_folder_refresh_rows_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_folder_refresh_rows
    ADD CONSTRAINT fk_equipment_folder_refresh_rows_task_id_equipment_fold_a3f7 FOREIGN KEY (task_id) REFERENCES equipment_folder_refresh_tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_folder_refresh_tasks
    ADD CONSTRAINT fk_equipment_folder_refresh_tasks_created_by_user_id_users FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_folder_refresh_tasks
    ADD CONSTRAINT fk_equipment_folder_refresh_tasks_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_folders
    ADD CONSTRAINT fk_equipment_folders_deadline_preset_id_deadline_presets FOREIGN KEY (deadline_preset_id) REFERENCES deadline_presets(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment
    ADD CONSTRAINT fk_equipment_group_id_equipment_groups FOREIGN KEY (group_id) REFERENCES equipment_groups(id) ON DELETE SET NULL;

ALTER TABLE ONLY equipment_groups
    ADD CONSTRAINT fk_equipment_groups_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_process_subscriptions
    ADD CONSTRAINT fk_equipment_process_subscriptions_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY equipment_process_subscriptions
    ADD CONSTRAINT fk_equipment_process_subscriptions_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY event_logs
    ADD CONSTRAINT fk_event_logs_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE SET NULL;

ALTER TABLE ONLY event_logs
    ADD CONSTRAINT fk_event_logs_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE SET NULL;

ALTER TABLE ONLY event_logs
    ADD CONSTRAINT fk_event_logs_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY folder_process_subscriptions
    ADD CONSTRAINT fk_folder_process_subscriptions_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE CASCADE;

ALTER TABLE ONLY folder_process_subscriptions
    ADD CONSTRAINT fk_folder_process_subscriptions_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY repair_message_attachments
    ADD CONSTRAINT fk_repair_message_attachments_repair_message_id_repair_messages FOREIGN KEY (repair_message_id) REFERENCES repair_messages(id) ON DELETE CASCADE;

ALTER TABLE ONLY repair_message_attachments
    ADD CONSTRAINT fk_repair_message_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY repair_messages
    ADD CONSTRAINT fk_repair_messages_author_user_id_users FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY repair_messages
    ADD CONSTRAINT fk_repair_messages_repair_id_repairs FOREIGN KEY (repair_id) REFERENCES repairs(id) ON DELETE CASCADE;

ALTER TABLE ONLY repairs
    ADD CONSTRAINT fk_repairs_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY si_verifications
    ADD CONSTRAINT fk_si_verifications_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_attachments
    ADD CONSTRAINT fk_task_attachments_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_attachments
    ADD CONSTRAINT fk_task_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY task_checklist_items
    ADD CONSTRAINT fk_task_checklist_items_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_equipment
    ADD CONSTRAINT fk_task_equipment_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_equipment
    ADD CONSTRAINT fk_task_equipment_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_message_attachments
    ADD CONSTRAINT fk_task_message_attachments_task_message_id_task_messages FOREIGN KEY (task_message_id) REFERENCES task_messages(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_message_attachments
    ADD CONSTRAINT fk_task_message_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY task_messages
    ADD CONSTRAINT fk_task_messages_author_user_id_users FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY task_messages
    ADD CONSTRAINT fk_task_messages_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_participants
    ADD CONSTRAINT fk_task_participants_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_participants
    ADD CONSTRAINT fk_task_participants_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_reminder_log
    ADD CONSTRAINT fk_task_reminder_log_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_subscriptions
    ADD CONSTRAINT fk_task_subscriptions_task_id_tasks FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;

ALTER TABLE ONLY task_subscriptions
    ADD CONSTRAINT fk_task_subscriptions_user_id_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fk_tasks_created_by_user_id_users FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fk_tasks_folder_id_equipment_folders FOREIGN KEY (folder_id) REFERENCES equipment_folders(id) ON DELETE CASCADE;

ALTER TABLE ONLY verification_message_attachments
    ADD CONSTRAINT fk_verification_message_attachments_uploaded_by_user_id_users FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY verification_message_attachments
    ADD CONSTRAINT fk_verification_message_attachments_verification_messag_d089 FOREIGN KEY (verification_message_id) REFERENCES verification_messages(id) ON DELETE CASCADE;

ALTER TABLE ONLY verification_messages
    ADD CONSTRAINT fk_verification_messages_author_user_id_users FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY verification_messages
    ADD CONSTRAINT fk_verification_messages_verification_id_verifications FOREIGN KEY (verification_id) REFERENCES verifications(id) ON DELETE CASCADE;

ALTER TABLE ONLY verifications
    ADD CONSTRAINT fk_verifications_equipment_id_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE CASCADE;
