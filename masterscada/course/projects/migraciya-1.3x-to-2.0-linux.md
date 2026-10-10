# Конвертация проекта MasterSCADA 4D 1.3.x (Firebird) → 2.0 (PostgreSQL) на Linux

Штатный путь вендора — «открыть проект → диалог **Конвертировать**» (см. справку
«Порядок перехода на новую версию»). Но: движок Firebird у хранилища 1.3.x —
**только Windows** (`fbembed.dll` в комплекте), а IDE на Linux смотрит лишь в
PostgreSQL-реестр, поэтому открыть файловый Firebird-проект штатно не получается.

Мы сделали эквивалент встроенного `FirebirdToPostgreSqlConverter` вручную.

## Что выяснено про хранилище

- 1.3.x: Firebird-база `<Имя>.fdb` (в нашем проекте — ODS **11.2**, Firebird **2.5**).
- 2.0: PostgreSQL — реестр `ms4d_projects.projects` + отдельная БД `ms4d_project_N`
  на проект. Схема 1:1 с Firebird:

| Firebird (1.3.x) | PostgreSQL (2.0) |
|---|---|
| `ITEMS(ITEMID, TYPEID)` | `items(item_id, type_id)` |
| `LINKS(SOURCEID, TARGETID, TYPEID)` | `links(type_id, source_id, target_id)` |
| `LINKTYPES(...)` | `link_types(...)` |
| `PROPSNUM/PROPSSTR/PROPSBLOB` | `props_num/props_str/props_bin` (`VERSION` отбрасывается) |
| `SYSPROPS` | `sys_props` |
| `TYPES(TYPEID,KIND,NAME)` | `types(type_id, kind, name)` |
| `USERS(USERID,USERNAME)` | `users(user_id, user_name)` |
| `TRANS_*`, `SESSIONS` | `trans_*`, `sessions` (необязательно, можно «очистить историю») |

## Рабочий рецепт (Linux-only)

1. **Firebird 2.5 для Linux** (наш `.fdb` = ODS 11.2; FB 3+/LibreOffice НЕ умеют —
   LO-движок отдаёт `unsupported on-disk structure ... found 11.2, support 12.2`).
   Скачать `FirebirdCS-2.5.7.*.amd64.tar.gz` (sourceforge) и распаковать `buildroot.tar.gz`
   → `opt/firebird/lib/libfbembed.so.2.5.7`.
   Нюанс: `libfbembed` требует `libncurses.so.5` (в Arch нет) — сделан симлинк
   `libncurses.so.5 → libncursesw.so.6` (в каталоге lib), ICU 3.0 идёт в комплекте.
2. **Создать БД** `ms4d_project_3` и накатить схему из любой PG-БД проектов:
   `pg_dump --schema-only ms4d_project_1 | psql -d ms4d_project_3`.
3. **Перенести данные** скриптом [`fb2pg-convert.py`](../../scripts/fb2pg-convert.py)
   (Python `fdb` + `psycopg`, COPY).
4. **Вписать проект в реестр** `ms4d_projects.projects` (SQL `INSERT`), затем перезапустить IDE.
5. IDE → **Открыть проект** → при открытии предложит **конвертацию версии схемы**
   (диалог «…→…»; у нас было 1.3.8→1.3.9— это версия схемы, не продукта) → **Конвертировать**.
   Проект открывается, дерево видно.

## Проверено (2026-10-10)

- Проект **СИКН 1520 ИНК** сконвертирован: items 206 927, links 275 822,
  props_num 499 234, props_str 235 775, props_bin 2 107, types 1 075, users 1.
- IDE открыла проект, создала рабочую папку `СИКН 1520 ИНК_<guid>`, прошла проверка
  конфигурации; `sys_props` содержит `RootItemId=2`, `ConfigName=MasterPLC`.

## Известные хвосты

- При попытке **отладки MasterPLC-узла** (ARM_1) вылезает `System.Exception: Not permited`
  (порт 30550, `Config/MasterPLC/linux/nginx`, `ProjectsServiceData/.../Debug/ARM_1/PLC`) —
  это отдельный вопрос запуска исполнения, не миграции.
- История изменений (trans_*) не переносилась — эквивалент опции «Очистить историю действий».
