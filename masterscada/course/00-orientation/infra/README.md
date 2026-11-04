# Учебный стенд: PostgreSQL в Docker

Здесь живёт СУБД для проектов MasterSCADA 4D 2.0. Стенд специально сделан **одноразовым и
переносимым**: один файл `docker-compose.yml`, никаких изменений в системе, полный сброс — одной
командой. Это вариант **B** из урока 03 (вариант A — системный PostgreSQL «как у вендора»).

## Что получится

| Параметр | Значение |
|---|---|
| Образ | `masterscada-pg:16-ru` — собирается из `Dockerfile` (postgres:16 + локаль `ru_RU.UTF-8`) |
| Контейнер | `masterscada-pg` |
| Адрес для IDE | `localhost:15444` (Arch/хост) и `192.168.122.1:15444` (среда в VM, см. «Arch + Astra») |
| Доступы | пользователь `course`, пароль `course`, база `course` |
| **Локаль кластера** | **`ru_RU.UTF-8`** (как в инструкции вендора) |
| Данные | том `infra_pgdata` (переживает `down`, удаляется только `down -v`) |
| Доступ извне | loopback (`127.0.0.1`) + адрес моста libvirt (`192.168.122.1`) для VM; в LAN/NetBird не торчит |
| Автозапуск | `restart: unless-stopped` (+ порядок с `libvirtd`, см. «Arch + Astra») |

> Порт **15444**, а не 5432, — чтобы стенд не конфликтовал с системным PostgreSQL. Значение
> задаётся в `.env` (`PG_PORT`) и **обязано совпадать** с настройками среды разработки.

## Требования

```bash
docker --version            # Docker version 2x.x
docker compose version      # Docker Compose version v2.x+
docker info >/dev/null && echo "демон доступен"   # иначе: пользователь не в группе docker
```

Если `permission denied` при обращении к сокету — добавь себя в группу `docker` и перелогинься:

```bash
sudo usermod -aG docker "$USER"
```

## Запуск и проверка

```bash
cd course/00-orientation/infra      # ← отсюда выполняются все команды этого файла
docker compose up -d --build        # первый раз: соберёт образ с локалью (~640 МБ базы + сборка)
docker compose ps                   # ожидаем: Up … (healthy), 127.0.0.1:15444->5432/tcp
```

Ожидаемый `docker compose ps`:

```
NAME             IMAGE         STATUS                    PORTS
masterscada-pg   postgres:16   Up 11 minutes (healthy)   127.0.0.1:15444->5432/tcp, 192.168.122.1:15444->5432/tcp
```

Три независимые проверки, что БД действительно отвечает:

```bash
docker compose exec -T postgres pg_isready -U course -d course
#   /var/run/postgresql:5432 - accepting connections

docker compose exec -T postgres psql -U course -d course -c "SELECT version();"
#   PostgreSQL 16.x …

timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/15444' && echo "порт открыт с хоста"
```

## Повседневные операции

```bash
docker compose logs -f postgres        # смотреть подключения/ошибки «живьём»
docker compose stop                    # пауза (данные на месте)
docker compose start
docker compose restart
docker compose down                    # контейнер убран, том pgdata остался
docker compose down -v                 # ⚠ полный сброс: удалить контейнер И данные
docker compose up -d --build           # пересобрать образ (после правок Dockerfile) и поднять
docker compose exec -it postgres bash  # зайти внутрь контейнера
```

> `down -v` — единственный способ применить изменения, которые действуют только при `initdb`:
> **локаль** кластера и стартовые доступы (`POSTGRES_USER/PASSWORD/DB`).

Резервная копия и восстановление (пригодятся в модуле 07, когда в БД появятся архивы):

```bash
# дамп в файл на хосте
docker compose exec -T postgres pg_dump -U course -d course > ms4d_backup_$(date +%F).sql
# восстановление в чистую БД
docker compose exec -T postgres psql -U course -d course < ms4d_backup_2026-10-11.sql
```

## Локаль кластера: `ru_RU.UTF-8`

Вендор инициализирует кластер с русской локалью (`pg-setup initdb --tune=1c --locale=ru_RU.UTF-8`).
Это влияет на **сортировку и сравнение текста**: `ORDER BY` по русским именам объектов, отчёты,
выборки из архивов (пригодятся в модулях 02 и 07). Поэтому образ собирается из
[`Dockerfile`](Dockerfile): там генерируется `ru_RU.UTF-8`, а compose передаёт её в `initdb`.

Проверка (две команды, обе должны «сойтись»):

```bash
docker compose exec -T postgres psql -U course -d course \
  -c "SELECT datcollate, datctype FROM pg_database WHERE datname='course';"
#   ru_RU.UTF-8 | ru_RU.UTF-8

docker compose exec -T postgres psql -U course -d course \
  -c "SELECT x FROM (VALUES ('яблоко'),('Ёж'),('апельсин'),('Ясень')) t(x) ORDER BY x;"
#   апельсин, Ёж, яблоко, Ясень   ← русский порядок, а не «по кодам символов»
```

Важные следствия:

- **Локаль задаётся только при `initdb`**, то есть при первом создании тома `pgdata`. Сменить её
  «на живом» кластере нельзя: нужен `docker compose down -v` и поднять заново (данные стенда
  потеряются — для учебного это нормально).
- Если убрать `POSTGRES_INITDB_ARGS` из compose, кластер создастся с локалью образа
  (`en_US.utf8`) — `SELECT … ORDER BY` по кириллице даст «непривычный» порядок. Это и есть разница,
  ради которой мы возимся с локалью.
- Альтернатива без сборки образа — ICU-коллации (`ru-RU-x-icu`), они есть в `postgres:16` «из
  коробки». Минус: это уже не тот путь, что делает вендор, и настройка размазывается по каждой
  базе/колонке. Для учебного стенда выбрана простая и совместимая локаль libc.

## Настройка под себя (`.env`)

```bash
cp .env.example .env      # дальше правим .env; файл в git не коммитится
# применить: docker compose up -d --build (пересоздаст контейнер при изменениях)
```

**Важно про смену доступов:** переменные `POSTGRES_*` срабатывают только при **создании** тома.
Если том уже есть, смена логина/пароля/имени БД в `.env` ничего не изменит — нужно либо сбросить
стенд (`down -v`), либо менять роли уже внутри PostgreSQL (`ALTER ROLE … PASSWORD …`).

Смена порта (`PG_PORT=5433`) — тоже требует обновить параметры подключения в среде разработки
(Настройки среды → База данных либо `IdeSettings.json`).

## Arch + Astra: два места разработки на одном стенде

Типовая путаница: «работаю то в Arch, то в Astra — что менять каждый раз?». Ответ — **ничего**.
Стенд один (PostgreSQL в Docker на хосте), а Arch и Astra — это два *клиента* к нему:

| Место разработки | Где живёт | Адрес БД в настройках среды |
|---|---|---|
| Arch (niri, хост) | сама машина стенда | `localhost`, порт `15444` |
| Astra SE (KVM/QEMU) | VM на той же машине; хост для гостя = шлюз `virbr0` | `192.168.122.1`, порт `15444` |

Поэтому **адрес в IDE задаётся один раз на каждую ОС** (`~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/IdeSettings.json`
внутри соответствующей системы: `ProjectsStoreServer/Port/User/Password`; в VM — файл внутри её диска).
А `.env`/`docker-compose.yml` — настройка **сервера**: на какие адреса хоста опубликовать порт. Чтобы не
переключать `PG_BIND` туда-сюда, порт публикуется сразу на оба адреса:

```yaml
ports:
  - "${PG_BIND:-127.0.0.1}:${PG_PORT:-15444}:5432"       # Arch (хост)
  - "${PG_BIND_VM:-192.168.122.1}:${PG_PORT:-15444}:5432" # Astra (VM через virbr0)
```

> Один и тот же склад = **одни и те же проекты** в Arch и в Astra (это обычно и нужно: проект,
> начатый в VM, открывается на хосте). Нужны независимые наборы — держи на каждой ОС свой Postgres,
> тогда `.env` одинаковый (`127.0.0.1:15444`), а проекты таскаются через `pg_dump`/`psql`.

### Грабли: гонка загрузки `docker.service` и `libvirtd`

Адрес `192.168.122.1` назначает `libvirtd` при старте. Если Docker поднимется раньше, публикация
второго порта падает, и это **не** косметика:

```
failed to bind host port 192.168.122.1:15444/tcp: cannot assign requested address
failed to start container … "failed to set up container networking …"
```

Контейнер при этом остаётся `Up (healthy)` (процесс postgres жив), но **без сети и без порта** —
IDE получает `Connection refused`, а в `docker ps` колонка PORTS пуста. Наблюдалось 2026-10-15:
`docker.service` активен в 07:05:29, `libvirtd` — в 07:05:30. Лечится дроп-ином для systemd
(копия лежит рядом, [`systemd/10-wait-libvirt.conf`](systemd/10-wait-libvirt.conf)):

```bash
sudo install -Dm644 systemd/10-wait-libvirt.conf \
  /etc/systemd/system/docker.service.d/10-wait-libvirt.conf
sudo systemctl daemon-reload && sudo systemctl restart docker
```

Проверка после перезагрузки — в `docker compose ps` должны быть видны **оба** адреса:

```bash
docker compose ps
# 127.0.0.1:15444->5432/tcp, 192.168.122.1:15444->5432/tcp
docker compose exec -T postgres psql -U course -d course -tAc "select 'ok'"
PGPASSWORD=course psql -h 192.168.122.1 -p 15444 -U course -d course -tAc "select 'ok'"
```

### Если VM на машине нет

Закомментируй вторую строку в `ports` (или убери `PG_BIND_VM`): публикация на несуществующий адрес
не даст контейнеру подняться вообще.

### Если Astra окажется на другой машине (не VM тут)

Тогда публикуй порт на `0.0.0.0` (или на конкретный адрес NetBird-оверлея) и в `IdeSettings.json`
той машины укажи `<адрес-хоста-стенда>:15444`. Всё равно один раз, а не «при каждом переключении».
Осторожно: `0.0.0.0` выставляет БД в LAN и в NetBird — либо прикрой nftables, либо оставь
учётные данные не учебными.

## Что IDE создаёт в БД (проверено 2026-10-11)

После создания проекта в редакторе в стенде появляются:

| Объект | Что это |
|---|---|
| `ms4d_projects` → таблица `public.projects` | **реестр проектов**: `project_id, project_name, type_name, config_name, version, changed_time, database_version` |
| `ms4d_project_<N>` | **БД проекта** (`ms4d_project_1` ≈ 69 МБ после шаблона). Внутри — объектный граф: `items`, `links`, `types`, `props_str/num/bin`, `trans_*`, `users`, `sessions`, `log` |

```bash
docker compose exec -T postgres psql -U course -d ms4d_projects  -c "SELECT * FROM projects;"
docker compose exec -T postgres psql -U course -d ms4d_project_1 -c "SELECT count(*) FROM items;"
```

Нюансы, на которые легко наткнуться:

- `changed_time` хранится в **UTC** (`timestamp without time zone`) — не пугайся «сдвига» относительно
  локального времени машины.
- `type_name` показывает, по какому **шаблону** создан проект (`MasterSCADA.Project.HMI` =
  «Проект визуализации»). Для полного цикла с узлом RT нужен шаблон «Общий проект» (**UNVERIFIED**).
- Проекты живут **только** в томе `pgdata`: `docker compose down -v` уничтожит их все.

## Диагностика

| Симптом | Причина | Что делать |
|---|---|---|
| `Bind for 0.0.0.0:15444 failed: port is already allocated` | порт занят (другой контейнер/сервис) | `ss -ltnp \| grep 15444`, сменить `PG_PORT` |
| Контейнер `Up (healthy)`, но в `PORTS` пусто, IDE — `Connection refused` | публикация на `192.168.122.1` не удалась: docker стартовал раньше `libvirtd` (адреса ещё нет) | `docker compose up -d --force-recreate`; поставить дроп-ин (см. «Arch + Astra»), чтобы не повторялось при загрузке |
| `docker: permission denied … /var/run/docker.sock` | пользователь не в группе `docker` | `sudo usermod -aG docker $USER`, перелогин |
| `docker compose ps` показывает `unhealthy` | контейнер не может инициализироваться | `docker compose logs postgres` |
| IDE пишет `Connection refused` | IDE смотрит не на тот порт, стенд остановлен | `docker compose ps`; сверить порт |
| IDE пишет `password authentication failed` | другой логин/пароль (или том от старой конфигурации) | сверить `.env` и настройки IDE |
| `role "…" does not exist`, `permission denied to create database` | роль без нужных прав | дать роль `CREATEDB`/`SUPERUSER` (стенд: `course` — суперпользователь) |
| `ORDER BY` по кириллице даёт «странный» порядок | кластер создан до правки локали (том от старой конфигурации) | `down -v` → `up -d --build`; сверить `datcollate` |
| сборка образа падает на `localedef` | нет исходников локали в базовом образе | проверить `docker compose build` (в `postgres:16` `ru_RU` есть) |

> Ошибки подключения в среде разработки всегда видны в её логе:
> `~/.config/MPSSoft/MasterSCADA4D_DT_2.0/sys_log/<дата>.log`.

## Вариант без Docker (системный PostgreSQL)

Если по условиям нельзя Docker (или хочется «как в продуктиве») — ставь PostgreSQL/PostgresPro в
систему и подключай IDE к `localhost:5432`; подробности в уроке
[`../lessons/03-postgresql-i-pervyy-proekt.md`](../lessons/03-postgresql-i-pervyy-proekt.md), §2.
Отличия от стенда: порт `5432`, свой пароль, служба управляется `systemctl`, данные лежат в
`/var/lib/postgresql/…` (а не в docker-томе).

## Ограничения стенда

- Пароль `course` — учебный. Не используй этот стенд как продовый: как минимум смени доступы.
- Образ собирается локально (`masterscada-pg:16-ru`) — после правок `Dockerfile` нужен
  `docker compose up -d --build`; локаль и стартовые доступы применяются только при `initdb`
  (то есть при создании тома).
- Данные лежат в docker-томе `infra_pgdata`, а **не** в репозитории: переезд на другую машину —
  это `pg_dump`/`pg_restore` (или `down -v` и создать проекты заново: для обучения нормально).
- Портал/веб-клиент визуализации, который поднимает RT, использует свои порты — они не связаны с
  портом БД.
