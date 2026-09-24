# Лаба 01: Стенд поднят и отвечает

**Модуль:** 00-orientation
**Время:** 1.5 ч
**Тип:** повтори за примером + диагностика

## Цель

Поднять стенд курса, убедиться в его работоспособности несколькими способами и освоить инструменты, которыми будешь пользоваться все модули: `docker compose`, psql внутри контейнера, `apply-db.sh`. Научиться диагностировать типовые проблемы подъёма.

## Схема стенда

```
                    host (твой компьютер)
   +---------------------------------------------------+
   |  терминал                                         |
   |    docker compose up -d                           |
   |         |                                         |
   +---------|-----------------------------------------+
             v
   сеть compose (pg-course_default)
   +---------------------+
   |  pg-course-postgres |
   |  postgres:18        |   порт 15432 -> 5432
   |  volume pg_data     |   user/db/pass: course
   +---------------------+
```

Том `pg_data` хранит данные между запусками; при первом создании тома выполняются скрипты `infra/db/init/*.sql` (создают таблицу `public.smoke`).

## Шаги

### Шаг 1. Поднять стенд

Из каталога `infra/`:

```bash
docker compose up -d
```

Ожидаемый вывод: create/start контейнера `pg-course-postgres`, без ошибок «port is already allocated».

### Шаг 2. Дождаться healthy и проверить статус

```bash
docker compose ps
```

Ожидаемый вывод: `STATUS` = `Up (healthy)`.

### Шаг 3. Первый запрос

```bash
docker compose exec -T postgres psql -U course -d course -c "SELECT version();"
```

Ожидаемый вывод: первая строка `PostgreSQL 18.6 ...` (x.y может отличаться в пределах 18.x).

### Шаг 4. Смоук-данные

```bash
docker compose exec -T postgres psql -U course -d course -c "SELECT * FROM public.smoke;"
```

Ожидаемый вывод: строка `1 | stand is up | <время>`.

### Шаг 5. Применить SQL-файл через apply-db.sh

Создай файл `lab01.sql`:

```sql
CREATE TABLE IF NOT EXISTS lab01_events (id serial PRIMARY KEY, note text, created_at timestamptz DEFAULT now());
INSERT INTO lab01_events (note) VALUES ('первая запись');
```

Примени: `./apply-db.sh lab01.sql`. Проверь: `SELECT * FROM lab01_events;` вернёт одну строку.

### Шаг 6. Перезапуск без потери данных

```bash
docker compose down
docker compose up -d
docker compose exec -T postgres psql -U course -d course -c "SELECT count(*) FROM public.smoke;"
```

Ожидаемый вывод: `count = 1` — данные в томе пережили перезапуск (init-скрипты второй раз НЕ выполняются).

## Критерии успеха

- [ ] Шаг 2: `docker compose ps` показывает `Up (healthy)`
- [ ] Шаг 3: `SELECT version()` возвращает PostgreSQL 18.x
- [ ] Шаг 4: смоук-таблица содержит строку «stand is up»
- [ ] Шаг 5: `apply-db.sh` применил файл, таблица `lab01_events` создана и содержит запись
- [ ] Шаг 6: после `down`/`up` данные остались (count = 1)

## Разбор типичных проблем

| Симптом | Причина | Решение |
|---|---|---|
| `port is already allocated` | порт 15432 занят на хосте | найди виновника (`ss -tlnp \| grep 15432`), останови его или смени порт в compose |
| статус `Restarting` | образ не смог стартовать (например, неверная точка монтирования тома) | `docker compose logs postgres` — читать последние строки; для учебных данных допустимо `docker compose down -v` и заново |
| `Cannot connect to the Docker daemon` | демон не запущен или нет прав | `systemctl start docker`; проверь группу `docker` |
| psql: `connection refused` | контейнер ещё не готов | подождать healthy (`docker compose ps`); проверить порт |
| init-скрипты не выполнились | том уже создан ранее (скрипты идут только при первом создании) | `docker compose down -v` (удалит данные — только учебные) |

## Задания «со звёздочкой»

1. Подними профиль `obs` (`docker compose --profile obs up -d`) и убедись, что Grafana открывается на <http://localhost:3000> (admin/admin), а Prometheus — на <http://localhost:9090>. Метрики PostgreSQL появятся после настройки из модуля 13, но сервисы должны быть живы.
   > ⚠️ <!-- UNVERIFIED --> Автор курса НЕ запускал profile `obs` на момент написания лабы (проверен только `docker compose config`): образы Grafana/Prometheus тянутся впервые. Если сервисы не стартуют — см. `docker compose logs` и раздел «Разбор типичных проблем»; фактический запуск profiles `tools`/`obs` будет проверен в модуле 13.
2. Подними профиль `timescale` (`docker compose --profile timescale up -d`) и проверь, что на порту 15433 отвечает PostgreSQL с расширением timescaledb: `SELECT extversion FROM pg_extension WHERE extname='timescaledb';`
3. Разберись, что делает `docker compose down -v` и почему в этом курсе данные считаются одноразовыми.