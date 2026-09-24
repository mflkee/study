-- Пример: первый запрос к стенду (модуль 00).
-- Запуск: infra/apply-db.sh course/00-orientation/examples/01-pervyj-zapros.sql
-- или напрямую:
--   docker compose exec -T postgres psql -U course -d course -f - < этот_файл

-- Версия сервера. На стенде курса ожидается PostgreSQL 18.x.
SELECT version();

-- Смоук-таблица, созданная при инициализации тома (infra/db/init/00-init.sql).
-- Если строка есть — init-скрипты отработали, стенд живой.
SELECT id, note, ts FROM public.smoke;

-- Текущее время с таймзоной (тип timestamptz — ключевой для телеметрии).
SELECT now() AS now_timestamptz;

-- База данных и пользователь, от имени которого мы подключились.
SELECT current_database(), current_user;