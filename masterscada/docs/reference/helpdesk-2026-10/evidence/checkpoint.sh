#!/usr/bin/env bash
# checkpoint.sh — точный снимок состояния проекта «Быстрый старт» для доказательств в HelpDesk.
#
# Запуск (на хосте, БД стенда доступна):
#     ./checkpoint.sh before
#     ...  делаем ОДНУ операцию в среде внутри Astra  ...
#     ./checkpoint.sh after
#     diff -u checkpoints/before-*.txt checkpoints/after-*.txt
#
# Идея: «воспроизвести точно» = прогнать один и тот же шаг и получить однозначную разницу
# между тем, что среда записала в модель, и тем, что должна была. Дифф двух снимков и есть
# доказательство для заявки (что записалось / что не записалось).
#
# Что попадает в снимок:
#   1) учётная запись проекта и max(trans_id) — граница журнала;
#   2) дерево отслеживаемых элементов (окна библиотечного типа + мнемосхема станции) до 4 уровней,
#      с типом, именем и геометрией (X/Y/Width/Height), плюс их собственные свойства;
#   3) журнал операций среды (trans_log) с машиной-источником и всеми записями в модель по каждой
#      операции (trans_props_num/str) — видно, что именно и куда записала каждая операция.
#
# Отслеживаемые элементы задаются ниже (TRACKED) или через CHECKPOINT_TRACKED. По умолчанию:
#   42089 «Окно 1» библиотечного типа, 49150 «Мнемосхема» станции.
#   Если элементы переносишь/добавляешь — передай нужные id, например:
#   CHECKPOINT_TRACKED="42089 52055 49150" ./checkpoint.sh s3-after
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
LABEL="${1:-snap}"
TAIL_N="${2:-15}"                  # сколько последних операций журнала выводить
TRACKED="${CHECKPOINT_TRACKED:-42089 49150}"

OUT_DIR="$DIR/checkpoints"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/${LABEL}-$(date +%Y%m%d-%H%M%S).txt"

export PGPASSWORD="${PGPASSWORD:-course}"
PSQL=(psql -h 127.0.0.1 -p 15444 -U course -X -q -A -t -F ' | ')
DB_APP=ms4d_project_2
DB_REG=ms4d_projects

roots=$(echo "$TRACKED" | tr ' ' ',')

{
  echo "# checkpoint: $LABEL"
  echo "# время: $(date '+%F %T %Z')   (в журнале время UTC)"
  echo "# проект: $("${PSQL[@]}" -d "$DB_REG" -c \
      "SELECT project_name || ' | version=' || version || ' | changed_time=' || changed_time
         FROM projects WHERE project_id = 2")"
  echo "# max(trans_id): $("${PSQL[@]}" -d "$DB_APP" -c "SELECT coalesce(max(trans_id),0) FROM trans_log")"
  echo "# отслеживаемые элементы: $TRACKED"
  echo
  echo "## 1. Дерево (до 4 уровней): уровень | родитель | элемент | тип | имя | WxH | X,Y | Z | TabIndex | Нач.значение"
  echo "##    (показываем только осмысленные узлы: с именем, с геометрией или с начальным значением)"
  "${PSQL[@]}" -d "$DB_APP" -c "
    WITH RECURSIVE t AS (
      SELECT l.source_id AS parent, l.target_id AS child, 1 AS lvl
        FROM links l WHERE l.source_id IN ($roots)
      UNION ALL
      SELECT l.source_id, l.target_id, t.lvl + 1
        FROM links l JOIN t ON l.source_id = t.child
       WHERE t.lvl < 4
    ), r AS (
      SELECT t.lvl, t.parent, t.child,
             (SELECT type_id FROM items i WHERE i.item_id = t.child) AS type_id,
             (SELECT value FROM props_str s WHERE s.item_id = t.child AND s.type_id = 767) AS name,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 751) AS w,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 752) AS h,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 741) AS x,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 742) AS y,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 743) AS z,
             (SELECT value::text FROM props_num p WHERE p.item_id = t.child AND p.type_id = 750) AS ti,
             (SELECT value FROM props_str p WHERE p.item_id = t.child AND p.type_id = 771) AS initial
        FROM t
    )
    SELECT r.lvl || ' | ' || r.parent || ' | ' || r.child || ' | ' || r.type_id || ' | ' ||
           coalesce(r.name, '-') || ' | ' ||
           coalesce(r.w, '-') || 'x' || coalesce(r.h, '-') || ' | ' ||
           coalesce(r.x, '-') || ',' || coalesce(r.y, '-') || ' | ' ||
           coalesce(r.z, '-') || ' | ' || coalesce(r.ti, '-') || ' | ' || coalesce(r.initial, '-')
      FROM r
     WHERE r.lvl <= 2
        OR r.name IS NOT NULL
        OR r.w IS NOT NULL OR r.h IS NOT NULL OR r.x IS NOT NULL OR r.y IS NOT NULL
        OR r.initial IS NOT NULL
     ORDER BY r.lvl, r.parent, r.child"
  echo
  echo "## 2. Собственные свойства отслеживаемых элементов (кроме служебных Designer.Layout.*)"
  "${PSQL[@]}" -d "$DB_APP" -c "
    SELECT p.item_id || ' | num | ' || t.name || ' = ' || p.value::text
      FROM props_num p JOIN types t ON t.type_id = p.type_id
     WHERE p.item_id IN ($roots) AND t.name NOT LIKE 'Designer.%'
    UNION ALL
    SELECT p.item_id || ' | str | ' || t.name || ' = ' || p.value
      FROM props_str p JOIN types t ON t.type_id = p.type_id
     WHERE p.item_id IN ($roots)
     ORDER BY 1"
  echo
  echo "## 3. Геометрия отслеживаемых элементов (Designer.Layout.*) — «как легло в модель»"
  "${PSQL[@]}" -d "$DB_APP" -c "
    SELECT p.item_id || ' | ' || t.name || ' = ' || p.value::text
      FROM props_num p JOIN types t ON t.type_id = p.type_id
     WHERE p.item_id IN ($roots) AND t.name LIKE 'Designer.%'
     ORDER BY p.item_id, t.name"
  echo
  echo "## 4. Последние $TAIL_N операций журнала + что каждая записала в модель"
  "${PSQL[@]}" -d "$DB_APP" -c "
    SELECT l.trans_id || ' | ' || coalesce(s.host_name, '?') || ' | ' || l.commit_time || ' | ' || coalesce(l.trans_name, '-')
      FROM trans_log l LEFT JOIN sessions s ON s.session_id = l.session_id
     WHERE l.trans_id > (SELECT coalesce(max(trans_id),0) - $TAIL_N FROM trans_log)
     ORDER BY l.trans_id"
  echo
  echo "## 4b. Записи в модель (num: old -> new; action 0=добавлено,1=изменено,2=УДАЛЕНО)"
  "${PSQL[@]}" -d "$DB_APP" -c "
    SELECT n.trans_id || ' | ' || n.item_id || ' | ' || t.name || ' | ' || n.action_type || ' | ' ||
           coalesce(n.old_value::text, '-') || ' -> ' || coalesce(n.new_value::text, '-')
      FROM trans_props_num n JOIN types t ON t.type_id = n.type_id
     WHERE n.trans_id > (SELECT coalesce(max(trans_id),0) - $TAIL_N FROM trans_log)
     ORDER BY n.trans_id, n.item_id, t.name"
  echo
  echo "## 4c. Строковые/бинарные записи в модель по тем же операциям"
  "${PSQL[@]}" -d "$DB_APP" -c "
    SELECT n.trans_id || ' | ' || n.item_id || ' | ' || t.name || ' | ' || n.action_type || ' | ' ||
           coalesce(n.old_value, '-') || ' -> ' || coalesce(n.new_value, '-')
      FROM trans_props_str n JOIN types t ON t.type_id = n.type_id
     WHERE n.trans_id > (SELECT coalesce(max(trans_id),0) - $TAIL_N FROM trans_log)
     ORDER BY n.trans_id, n.item_id, t.name"
} > "$OUT" 2>&1

echo "$OUT"
