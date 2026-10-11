# Урок 03: PostgreSQL и первый проект

**Цель урока:** поднять СУБД для проектов, подключить к ней среду разработки и создать первый
проект так, чтобы **доказать**: он лежит в базе, а не «где-то в файлах».

**Время:** ~60 мин · **Пререквизиты:** уроки 01–02 (редактор запускается).

---

## 1. Зачем вообще СУБД

В MasterSCADA 4D **2.0 все проекты хранятся в PostgreSQL** (в 1.3.x они были файлами `.fdb`).
Требование продукта — **PostgreSQL ≥ 10**; вендор рекомендует брать свежую версию, в инструкции
фигурирует PostgresPro 1C-14 (`ust_postgresql_linux.html`).

Практические следствия, которые надо принять как факт:

- «сохранить проект» = записать его в БД; пока БД нет, создать/сохранить проект нельзя;
- сервер БД может быть **локальным или сетевым** — установка требуется, только если он локальный;
- в одной БД может быть несколько проектов; у проекта в хранилище есть папка, а при совпадении
  имён к папке дописывается GUID (`<Имя_проекта>_<GUID>`).

Дальше — два варианта стенда. Выбери **один**:

| Вариант | Плюсы | Минусы |
|---|---|---|
| **A. Штатный** (PostgreSQL/PostgresPro на той же машине, как делает вендор) | максимально близко к продуктиве; ничего «лишнего» в системе | ставит и держит сервис в системе |
| **B. Стенд в Docker** (наш учебный, из `infra/`) | не трогает систему, легко удалить/сбросить, один файл конфигурации | нужен Docker; порт нестандартный |

## 2. Вариант A — штатная установка (Astra Linux)

По справке (`ust_postgresql_linux.html`), на примере PostgresPro 1C-14:

```bash
# 1) поставить пакеты, скачанные из репозитория вендора
sudo dpkg -i postgrespro-1c-14-libs_*.deb postgrespro-1c-14-client_*.deb \
             postgrespro-1c-14-contrib_*.deb postgrespro-1c-14-server_*.deb

# 2) инициализировать кластер и включить службу
/opt/pgpro/1c-14/bin/pg-wrapper links update
/opt/pgpro/1c-14/bin/pg-setup initdb --tune=1c --locale=ru_RU.UTF-8
systemctl enable --now postgrespro-1c-14

# 3) правим /var/lib/pgpro/1c-14/data/pg_hba.conf: local all all peer  →  trust
#    затем перезапускаем службу и задаём пароль пользователя postgres
systemctl restart postgrespro-1c-14
/opt/pgpro/1c-14/bin/psql -U postgres -p 5432 -c "alter user postgres with password '<пароль>';"
```

Если в дистрибутиве есть обычный `postgresql` из репозитория (Debian/Astra), эквивалент короче:

```bash
sudo apt install postgresql
sudo systemctl enable --now postgresql
sudo -u postgres psql -c "alter user postgres with password '<пароль>';"
```

Итог варианта A: сервер слушает **5432**, пользователь `postgres`, пароль — твой.

> `pg_hba.conf` — «пропускной пункт» PostgreSQL: `peer` разрешает вход только процессам с тем же
> именем пользователя ОС, `md5/scram-sha-256` — по паролю, `trust` — вообще без пароля.
> Именно `peer` — самая частая причина «я же задал пароль, а меня не пускают».

## 3. Вариант B — учебный стенд в Docker

Один файл: [`../infra/docker-compose.yml`](../infra/docker-compose.yml). Он поднимает
`postgres:16`, публикует порт **только на loopback** и хранит данные в томе `pgdata`.

```bash
cd course/00-orientation/infra

docker compose up -d --build         # поднять (соберёт образ с локалью ru_RU.UTF-8; база ~640 МБ)
docker compose ps                    # postgres должен дойти до Up (healthy)

# проверка «БД жива и отвечает»
docker compose exec -T postgres psql -U course -d course -c "SELECT version();"
#  → PostgreSQL 16.x …
docker compose exec -T postgres psql -U course -d course -c "\l"
#  → course, postgres, template0, template1
```

Прочие полезные операции (полностью — в [`../infra/README.md`](../infra/README.md)):

```bash
docker compose logs -f postgres      # видеть подключения и ошибки «живьём»
docker compose stop / start          # пауза, данные сохраняются
docker compose down                  # удалить контейнер, том pgdata остаётся
docker compose down -v               # снести и данные стенда (полный сброс)
```

Параметры стенда по умолчанию (меняются через `.env`, см. `infra/.env.example`):

| Параметр | Значение по умолчанию |
|---|---|
| сервер:порт | **`localhost:15444`** |
| база / пользователь / пароль | `course` / `course` / `course` |
| локаль кластера | `ru_RU.UTF-8` — как в инструкции вендора (`initdb --locale=ru_RU.UTF-8`) |
| том с данными | `pgdata` |
| доступ извне | закрыт (публикуется на `127.0.0.1`) |

> Порт **15444**, а не 5432 — чтобы стенд не конфликтовал с системным PostgreSQL и другими
> контейнерами. Важно: **и в IDE, и в проверках порт должен быть одним и тем же**.

**Локаль.** Кластер поднимается с `ru_RU.UTF-8` — как в инструкции вендора. Это влияет на
сортировку русских имён (`ORDER BY`) и отчёты. Проверить, что локаль действительно русская:

```bash
docker compose exec -T postgres psql -U course -d course \
  -c "SELECT datcollate, datctype FROM pg_database WHERE datname='course';"   # ru_RU.UTF-8 | ru_RU.UTF-8
docker compose exec -T postgres psql -U course -d course \
  -c "SELECT x FROM (VALUES ('яблоко'),('Ёж'),('апельсин')) t(x) ORDER BY x;"  # апельсин, Ёж, яблоко
```

Локаль задаётся только при **создании** тома: «на живом» кластере её не сменить (`down -v` и заново —
 см. [`../infra/README.md`](../infra/README.md)).

## 4. Подключаем среду разработки к БД

> Справка: «Прежде чем начать работать нужно настроить среду разработки. Указать параметры
> подключения к базе данных PostgreSQL» (`bs__urok_1__sozdanie_proekta.html`).

**Настройки среды → вкладка Базы данных** (`vkladka_bazy_dannyh.html`):

| Поле | Вариант A (штатный) | Вариант B (стенд) |
|---|---|---|
| Сервер | `localhost` | `localhost` |
| Порт | `5432` | `15444` |
| Пользователь | `postgres` | `course` |
| Пароль | твой пароль | `course` |
| Путь к PostgreSQL | папка `bin` установки PostgreSQL | можно оставить пустым |

Затем — кнопка **«Проверка подключения к базе данных»**: должна пройти успешно. Если нет —
смотри таблицу граблей (§6), а не перебор случайных значений.

Проверить (и при необходимости исправить руками) сохранённые параметры можно и без GUI — они
лежат в `IdeSettings.json` (проверено на стенде). У сборок с суффиксом (rc/beta/branch) каталог
тоже с суффиксом, поэтому берём его переменной:

```bash
IDE_CFG="$HOME/.config/MPSSoft/MasterSCADA4D_DT_2.0/IdeSettings.json"   # rc: MasterSCADA4D_DT_2.0_RC
python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print({k:v for k,v in d.items() if 'Store' in k})" "$IDE_CFG"
# ProjectsStoreServer/Port/User/Password, LibrariesStore… и
# UseProjectsConnectionSettingsForLibraries (библиотеки берут настройки проектного хранилища)
```

> Правь этот файл только при **закрытом** редакторе: при выходе приложение перезаписывает его
> своими текущими настройками из памяти.

## 5. Создаём первый проект и доказываем, что он в БД

1. В стартовом окне — **«Создать проект»** (или меню **Проект → Создать**).
2. Заполняем диалог (справка `dialog_sozdaniya_proekta.html`) — это и есть **параметры хранилища**:

| Поле диалога | Значение |
|---|---|
| Название | например `Обучение` (по умолчанию предлагается `<Проект> N`) |
| Сервер / Порт / Пользователь / Пароль | те же, что в §4 |
| Тип/шаблон проекта | для обучения бери **«Общий проект»** (полноценный проект с деревом системы и узлом); «Проект визуализации» — только визуализация, узла RT в нём может не быть → команды запуска будут неактивны (**UNVERIFIED**, проверяем на стенде) |

3. **«Создать проект»** → редактор откроет рабочую область.

Теперь — доказательство (обязательная часть урока). **До** создания и **после** выполни:

```bash
# Вариант B: снаружи, через контейнер (для варианта A — тем же psql с -h localhost -U postgres)
docker compose exec -T postgres psql -U course -d course -c "\l"
# Вариант A/B — содержимое служебной БД (реестр проектов):
docker compose exec -T postgres psql -U course -d ms4d_projects -c "SELECT * FROM projects;"
```

Что реально создаёт редактор (**проверено 2026-10-11** на этом стенде):

| Что | Как выглядит |
|---|---|
| Служебная БД | `ms4d_projects` → таблица `public.projects` (реестр): `project_id, project_name, type_name, config_name, version, changed_time, database_version` |
| БД проекта | по одной на проект: `ms4d_project_<N>` (у нас `ms4d_project_1`, ~69 МБ после шаблона) |
| Запись в реестре | `1 \| Быстрый старт \| MasterSCADA.Project.HMI \| MasterPLC \| 1 \| 2026-10-11 09:54:24 \| 16.15 (Debian …)` |
| Модель проекта внутри БД | объектный граф: `items` (объекты) + `links` (связи) + `types` (типы) + `props_str/num/bin` (свойства) + `trans_*` (транзакционные версии) + `users`, `sessions`, `log` |

Порядок величин на «пустом» проекте из шаблона: `items` ≈ 38 700, `links` ≈ 46 000, `types` ≈ 819,
`props_str` ≈ 45 900, `props_num` ≈ 133 700. Полезно увидеть: **проект — это не файл, а данные в
СУБД**, и «дерево объектов» в редакторе — это таблицы `items` + `links` + `props_*`.

Проверить, что проект «живой» (реквизиты объекта/связи и т.п.):

```bash
docker compose exec -T postgres psql -U course -d ms4d_project_1 \
  -c "SELECT count(*) AS items FROM items;" \
  -c "SELECT name FROM types ORDER BY name LIMIT 10;"
```

> **Грабля времени.** `projects.changed_time` хранится в **UTC** (`timestamp without time zone`):
> у нас проект создан в 17:54 по машине (UTC+8), а в реестре — `09:54:24`. Не пугайся «сдвига».

> **Тип проекта.** В реестре видно `type_name` — `MasterSCADA.Project.HMI` (шаблон «Проект
> визуализации»). Для полного цикла с запуском узла RT бери шаблон **«Общий проект»**: у HMI-проекта
> узла в дереве системы может не быть (**UNVERIFIED** — проверяется в уроке 04).

## 6. Читаем ошибки, а не угадываем

Лог IDE — `~/.config/MPSSoft/MasterSCADA4D_DT_2.0/sys_log/<дата>.log`. Реальный пример, когда БД
не поднята (ошибка при «Создать проект»):

```
CreateProject Обучение (Path-'' Template-Проект визуализации)
System.InvalidOperationException: Ошибка подключения:
Connection refused (Проверьте правильность введённых данных для подключения)
   at InSAT.Framework.DbStore.Services.Store.PostgreSql.PostgreSqlStoreService…
```

`Connection refused` = «на этом адресе/порту никто не слушает» (сервер не запущен либо другой
порт). Пароль/логин дают **другую** ошибку — `password authentication failed`.

| Симптом | Причина | Что делать |
|---|---|---|
| `could not connect … Connection refused` | сервер не запущен или **другой порт** | `docker compose ps` / `systemctl status postgresql`; сверить порт в IDE |
| `password authentication failed for user "…"` | неверные логин/пароль или `pg_hba` | проверить §4 и `pg_hba.conf` |
| `role "…" does not exist` | нет такого пользователя в СУБД | создать роль или выбрать существующую |
| Проект создан, но «не сохраняется» | IDE смотрит на другую БД/порт | сверь `IdeSettings.json` и порт стенда |
| `permission denied to create database` | у роли нет права создавать БД | дать `CREATEDB` (или использовать суперпользователя) |
| Порт занят при `up -d` | 15444 кем-то занят | сменить порт в `.env` **и** в настройках IDE |

> Признак того, что дело в «правах, а не в связи»: соединение устанавливается, но любое действие
> по созданию объекта падает с `permission denied`. Держи эту границу в голове.

## 7. Проверка себя (чеклист урока)

- [ ] Могу объяснить, почему в 2.0 проекты в СУБД, а не в файлах.
- [ ] Стенд поднимается одной командой, `SELECT version();` возвращает PostgreSQL 16.x (вариант B).
- [ ] В настройках среды заданы сервер/порт/пользователь/пароль, «Проверка подключения» проходит.
- [ ] Проект создан и открывается; в БД видны объекты, созданные редактором.
- [ ] Отличаю `Connection refused` (нет сервера/порта) от `password authentication failed` (доступ) и от `permission denied` (права).
- [ ] Знаю, как сбросить стенд (`down -v`) и что при этом теряется.

## 8. Задание

1. Покажи `SELECT version();` и список баз **до** создания проекта.
2. Создай проект, сохрани, закрой и снова открой редактор — проект должен открыться из БД.
3. Покажи список баз/схем **после** создания и выпиши, что именно добавил редактор.
4. Докажи на логе, что редактор действительно ходил в PostgreSQL (не «просто создал папку»).

## 9. Как это спросят на собеседовании

1. «Почему в 2.0 проекты в СУБД? Плюсы и минусы против файлов 1.3.x.»
2. «Как развернуть PostgreSQL для SCADA и чем проверить доступность?»
3. «Чем `peer`, `md5` и `trust` в `pg_hba.conf` отличаются и что безопаснее?»
4. «Что произойдёт с проектом/архивом, если БД недоступна во время работы RT?»

## 10. Источники

- Справка 2.0: `ust_postgresql_linux.html`, `vkladka_bazy_dannyh.html`,
  `dialog_sozdaniya_proekta.html`, `bs__urok_1__sozdanie_proekta.html`
- Стенд курса: [`../infra/README.md`](../infra/README.md), [`../infra/docker-compose.yml`](../infra/docker-compose.yml)
- Лаба: [`../labs/01-bd-nedostupna.md`](../labs/01-bd-nedostupna.md)
