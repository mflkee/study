# Урок 02: Стенд и первый запрос к PostgreSQL

**Закрывает цель:** воспроизводимый стенд PostgreSQL для всех модулей курса
**Время:** 45 мин

## Зачем это нужно

Все SQL-примеры курса выполняются на живом PostgreSQL, а не «в голове». Стенд поднимается одной командой и одинаков на любой машине — это устраняет класс проблем «на моей машине работает». Заодно это первое знакомство с тем, как в реальной работе поднимают базу в контейнере.

## Ключевые идеи (сжато)

- Стенд описан декларативно в `infra/docker-compose.yml`: что поднять, какие порты, какие тома.
- PostgreSQL 18.6 слушает на хосте порт **15432** (в контейнере — 5432), доступ: `course` / `course` / база `course`.
- psql запускается внутри контейнера: `docker compose exec postgres psql`.
- При первом создании тома выполняются скрипты из `infra/db/init/` — в них лежит смоук-таблица `public.smoke`.

## Разбор на примере

Из каталога `infra/`:

```bash
# 1. Поднять стенд (PostgreSQL; compose сам скачает образ postgres:18)
docker compose up -d

# 2. Убедиться, что всё поднялось и здорово
docker compose ps
# NAME                SERVICE   STATUS
# pg-course-postgres  postgres  Up (healthy)

# 3. Первый запрос: версия сервера
docker compose exec -T postgres psql -U course -d course -c "SELECT version();"

# 4. Смоук-данные, которые создал init-скрипт
docker compose exec -T postgres psql -U course -d course -c "SELECT * FROM public.smoke;"
#  id |    note     |              ts
# ----+-------------+-------------------------------
#   1 | stand is up | 2026-09-24 15:36:19.805422+00
```

Разбор команды `docker compose exec -T postgres psql ...`:
- `docker compose exec` — выполнить команду внутри запущенного контейнера;
- `-T` — отключить псевдо-TTY (нужно для неинтерактивных вызовов из скриптов);
- `postgres` — имя сервиса из compose-файла;
- `psql -U course -d course` — подключиться от пользователя `course` к базе `course`;
- `-c "SQL"` — выполнить один запрос и выйти.

Дальше — как применять изменения схемы. SQL-файл применить одной командой:

```bash
printf 'CREATE TABLE demo (v int);\nINSERT INTO demo VALUES (1);\n' > /tmp/demo.sql
./apply-db.sh /tmp/demo.sql
# ok: applied /tmp/demo.sql

docker compose exec -T postgres psql -U course -d course -tAc "SELECT count(*) FROM demo;"
# 1
```

`apply-db.sh` — обёртка вокруг `psql -v ON_ERROR_STOP=1`: при ошибке SQL скрипт вернёт ненулевой код и ничего не «проглотит».

## Как это устроено под капотом

- `docker compose up -d` создаёт сеть, том и контейнер по описанию из YAML. `restart: unless-stopped` — контейнер поднимется сам после перезагрузки машины.
- **Volume** (`pg_data`) хранит данные вне контейнера: `docker compose down` не удаляет данные, `docker compose down -v` — удаляет вместе с томом (и init-скрипты выполнятся заново).
- **Healthcheck** (`pg_isready -U course -d course`) — compose ждёт, пока PostgreSQL примет подключения; без него «Up» появляется раньше, чем база реально готова.
- Скрипты из `docker-entrypoint-initdb.d` выполняются один раз при инициализации пустого тома — это штатный механизм образа `postgres`.
- PostgreSQL 18+ хранит данные в подкаталоге внутри тома (изменился layout): в compose-файле том монтируется в `/var/lib/postgresql`, а не `/var/lib/postgresql/data` — иначе образ откажется стартовать (граница mount не совпадает с ожидаемой).

## Типичные ошибки и грабли

1. **`port is already allocated`** при `up -d` — порт занят другим сервисом. Проверь: `ss -tln | grep 15432`. Сменить порт можно в compose-файле (и в коде подключения). На машине автора курса 5433/5434 были заняты, поэтому стенд изначально на 15432/15433.
2. **`psql: error: could not connect`** — контейнер ещё не healthy, или пароль/порт не совпадает. Подожди пару секунд, проверь `docker compose ps` и `.env`-значения в compose-файле.
3. **data в volume «не той» версии** — при обновлении образа PostgreSQL данные не мигрируются автоматически; для курса проще `docker compose down -v && docker compose up -d` (данные учебные).
4. **`-v` в `docker compose exec` vs `-v` в `docker compose down`** — не путай: у `exec -T` это отключение TTY, у `down -v` — удаление томов. Набирай внимательно.

## Мини-задание

Подними стенд, выполни `SELECT * FROM public.smoke;` и убедись, что видишь строку со словом «stand». Затем примени через `apply-db.sh` файл с `CREATE TABLE my_first (id int);` и проверь, что таблица появилась.

<details>
<summary>Ответ</summary>

Ожидаемый вывод смоук-запроса:

```
 id |    note     |              ts
----+-------------+-------------------------------
  1 | stand is up | 2026-09-24 15:36:19.805422+00
```

Проверка таблицы: `docker compose exec -T postgres psql -U course -d course -tAc "SELECT to_regclass('public.my_first');"` должна вернуть `my_first`.
</details>

## Как это спросят на собеседовании

1. «Как поднять PostgreSQL локально для разработки и как убедиться, что он готов принимать запросы?» (docker compose + healthcheck/pg_isready)
2. «Чем docker volume отличается от bind mount?»
3. «Что делает `docker-entrypoint-initdb.d` и когда его скрипты выполняются?»

## Что читать дальше

- Docker Compose official docs: <https://docs.docker.com/compose/>
- Образ postgres (переменные, initdb.d, тома): <https://hub.docker.com/_/postgres>
- psql (справочник): <https://www.postgresql.org/docs/18/app-psql.html>