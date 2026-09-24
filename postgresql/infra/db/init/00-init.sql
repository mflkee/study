-- Инициализация схемы при первом поднятии PostgreSQL (docker-entrypoint-initdb.d).
-- Выполняется один раз при создании тома pg_data. Дальнейшие изменения схемы
-- применяются скриптами модулей через infra/apply-db.sh или sqlx migrate.

-- Смоук-таблица: на ней проверяется, что стенд отвечает (модуль 00).
CREATE TABLE IF NOT EXISTS public.smoke (
    id    serial PRIMARY KEY,
    note  text NOT NULL,
    ts    timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.smoke (note) VALUES ('stand is up');