#!/usr/bin/env python3
"""Конвертация проекта MasterSCADA 4D 1.3.x (Firebird-хранилище .fdb) в
PostgreSQL-хранилище 2.0 — вручную, на Linux (там, где встроенный
FirebirdToPostgreSqlConverter недоступен: в deb только Windows-движок Firebird).

Как это работает
----------------
Хранилище проекта у 1.3.x — это Firebird-база (файл <Имя>.fdb). У 2.0 хранилище —
PostgreSQL: реестр ms4d_projects.projects + по отдельной БД ms4d_project_N на проект
со схемой (items, links, link_types, props_bin/num/str, sys_props, types, users, trans_*).
Схемы почти 1:1, поэтому данные переносятся потаблично.

Требуется движок Firebird 2.5 (наш .fdb — ODS 11.2; FB 3+/LibreOffice не подходят):
  FirebirdCS-2.5.7.<...>.amd64.tar.gz (sourceforge) → lib/libfbembed.so.2.5.7

Запуск (пример):
  FB_LIB=/opt/fb25/lib/libfbembed.so.2.5.7 \
  FB_DB="/path/СИКН 1520 ИНК/СИКН 1520 ИНК.fdb" \
  PG_DSN="host=localhost port=15444 user=course password=course dbname=ms4d_project_3" \
  uv run --with fdb --with 'psycopg[binary]' python fb2pg-convert.py

Предполагается, что целевая БД ms4d_project_N уже создана и в неё накатана схема
(см. ниже «Схема» в комментарии или скопируйте структуру из любой PG-БД проектов:
 pg_dump --schema-only ms4d_project_1 | psql -d ms4d_project_3).
После переноса впишите проект в реестр ms4d_projects.projects и перезапустите IDE.
"""
import os, sys

FB_LIB = os.environ["FB_LIB"]
FB_DB = os.environ["FB_DB"]
PG_DSN = os.environ["PG_DSN"]
FB_USER = os.environ.get("FB_USER", "SYSDBA")
FB_PASS = os.environ.get("FB_PASS", "masterkey")

import fdb, psycopg
fdb.load_api(FB_LIB)
fb = fdb.connect(dsn=FB_DB, user=FB_USER, password=FB_PASS, charset="UTF8")
pg = psycopg.connect(PG_DSN)
pg.autocommit = True


def blob(v):
    return v.read() if hasattr(v, "read") else v


def copy_table(select_sql, pg_table, pg_cols, transform=None, batch=5000):
    cur = fb.cursor(); cur.execute(select_sql)
    n = 0
    with pg.cursor() as pc, pc.copy(f"COPY {pg_table} ({','.join(pg_cols)}) FROM STDIN") as cp:
        while True:
            rows = cur.fetchmany(batch)
            if not rows:
                break
            for r in rows:
                cp.write_row(transform(r) if transform else r)
                n += 1
    print(f"  {pg_table:14} <- {n} rows")
    return n


print("Переношу данные (Firebird -> PostgreSQL):")
copy_table('select ITEMID, TYPEID from ITEMS', 'items', ['item_id', 'type_id'])
copy_table('select TYPEID, SOURCEID, TARGETID from LINKS', 'links', ['type_id', 'source_id', 'target_id'])
copy_table('select TYPEID, SINGLETARGET, SINGLESOURCE, ALLOWPARENTCHILD from LINKTYPES',
           'link_types', ['type_id', 'single_target', 'single_source', 'allow_parent_child'],
           transform=lambda r: (r[0], bool(r[1]), bool(r[2]), bool(r[3])))
copy_table('select ITEMID, TYPEID, "VALUE" from PROPSNUM', 'props_num', ['item_id', 'type_id', 'value'])
copy_table('select ITEMID, TYPEID, "VALUE" from PROPSSTR', 'props_str', ['item_id', 'type_id', 'value'])
copy_table('select ITEMID, TYPEID, "VALUE" from PROPSBLOB', 'props_bin', ['item_id', 'type_id', 'value'],
           transform=lambda r: (r[0], r[1], blob(r[2])))
copy_table('select NAME, "VALUE" from SYSPROPS', 'sys_props', ['name', 'value'])
copy_table('select TYPEID, KIND, NAME from TYPES', 'types', ['type_id', 'kind', 'name'])
copy_table('select USERID, USERNAME from USERS', 'users', ['user_id', 'user_name'])

# История изменений (trans_*/sessions) необязательна — по аналогии с опцией
# «Очистить историю действий» при штатной конвертации, её можно не переносить.

print("Правлю последовательности:")
with pg.cursor() as pc:
    pc.execute("select coalesce(max(item_id),0)+1 from items"); it = pc.fetchone()[0]
    pc.execute("select coalesce(max(type_id),0)+1 from types"); ty = pc.fetchone()[0]
    pc.execute("select coalesce(max(user_id),0)+1 from users"); us = pc.fetchone()[0]
    for seq, val in [("item_id_seq", it), ("id_seq", ty), ("trans_id_seq", 1),
                     ("session_id_seq", 1), ("user_id_seq", us)]:
        pc.execute(f"select setval('{seq}', %s, false)", (val,))
    print(f"  item_id_seq={it} id_seq={ty} user_id_seq={us}")

print("Готово. Не забудьте вписать проект в реестр ms4d_projects.projects и перезапустить IDE.")
