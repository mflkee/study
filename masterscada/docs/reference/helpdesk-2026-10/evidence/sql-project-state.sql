-- Выписка состояния проекта «Быстрый старт» из хранилища PostgreSQL — доказательная база
-- для заявок в HelpDesk по дефектам 2 (размер окна) и 3 (Ctrl+X/Ctrl+V теряет размеры).
--
-- СУБД: PostgreSQL 16 (кластер ru_RU.UTF-8), проект хранится в базе ms4d_project_2
--       (запись о проекте — в служебной базе ms4d_projects, project_id = 2).
-- Запуск:
--   PGPASSWORD=course psql -h 127.0.0.1 -p 15444 -U course -d ms4d_project_2 \
--       -f evidence/sql-project-state.sql
--
-- Как устроено хранилище (проверено вскрытием схемы):
--   items(item_id, type_id)                                  — элементы проекта
--   props_num / props_str / props_bin(item_id, type_id, value) — сохранённые свойства
--   trans_log(trans_id, session_id, trans_name, commit_time) — журнал операций среды
--   trans_props_*(trans_id, item_id, type_id, action_type, old_value, new_value)
--                                                            — изменения по каждой операции
--   types(type_id, kind, name)                               — реестр имён свойств
--   sessions(session_id, user_id, host_name, start_time, end_time)
--
-- action_type: 0 = свойство добавлено, 1 = изменено, 2 = удалено (тогда new_value = NULL)
-- Используемые type_id: 701 SiblingIndex · 741 X · 742 Y · 743 Z · 750 TabIndex ·
--                       751 Designer.Layout.Width · 752 Designer.Layout.Height · 767 DisplayName
-- ВАЖНО (снимок): файл sql-snimok-2026-10-16-polnaya-vypiska.out снят 2026-10-16 14:16 по хосту
-- (хост живёт в UTC+8; это 06:16 UTC — ДО правок в сессии Astra #16, начавшейся 06:59 UTC).
-- В той сессии в Astra были удалены «Окно управления» (49260) и «Овал 1»/«Кнопка 1»/«Кнопка 2»
-- (49303/49308), поэтому разделы 5–6 в свежем выводе (sql-project-state.out) почти пусты.
-- История (trans_log, trans_props_*) append-only: записи об операциях остались — именно они и есть
-- доказательство; снимок лишь фиксирует, как выглядело *сохранённое* состояние на тот момент.
-- Время в commit_time — UTC (контейнер стенда в Europe/Moscow, хост — UTC+8; проверено 2026-10-16).
--
-- Ключевые элементы: 42089 — окно «Окно 1» (насос 1); 49303/49308 — «Кнопка 1»/«Кнопка 2»,
--   вставленные из буфера 2026-10-12 15:19:37 (trans 298) в «Окно управления» (Id 49260 → схема 49293).

\echo '### 0. Снимок снят (дата/время — время хоста):'
SELECT now() AS snimok;

\echo '### 1. Учётная запись проекта (выполнять в служебной базе ms4d_projects:'
\echo '###    psql ... -d ms4d_projects -c "SELECT * FROM projects WHERE project_id = 2")'

\echo '### 2. Окно 42089 «Окно 1»: ВСЕ сохранённые свойства (props_*)'
SELECT 'props_num' AS src, ty.name, p.value::text AS value
FROM props_num p JOIN types ty ON ty.type_id = p.type_id WHERE p.item_id = 42089
UNION ALL
SELECT 'props_str', ty.name, p.value
FROM props_str p JOIN types ty ON ty.type_id = p.type_id WHERE p.item_id = 42089
UNION ALL
SELECT 'props_bin', ty.name, '(bytea, ' || octet_length(p.value) || ' байт)'
FROM props_bin p JOIN types ty ON ty.type_id = p.type_id WHERE p.item_id = 42089;

\echo '### 3. Окно 42089: были ли Designer.Layout.Width/Height (751/752) хоть в одной операции?'
SELECT count(*) AS strok_istorii FROM trans_props_num WHERE item_id = 42089 AND type_id IN (751, 752);

\echo '### 4. Окно 42089: вся история операций над ним (только свойства)'
SELECT l.trans_id, l.commit_time, l.trans_name
FROM trans_log l
WHERE l.trans_id IN (SELECT DISTINCT trans_id FROM trans_props_num WHERE item_id = 42089)
   OR l.trans_id IN (SELECT DISTINCT trans_id FROM trans_props_str WHERE item_id = 42089)
ORDER BY l.trans_id;

\echo '### 5. Кнопки 49303/49308: сохранённые свойства (props_*)'
SELECT p.item_id, ty.name, p.value::text AS value
FROM props_num p JOIN types ty ON ty.type_id = p.type_id
WHERE p.item_id IN (49303, 49308) ORDER BY p.item_id, ty.name;

\echo '### 6. Кнопки 49303/49308: история изменения геометрии в trans_props_num'
SELECT n.trans_id, l.commit_time, n.item_id, n.type_id, t.name,
       n.action_type,
       CASE n.action_type WHEN 0 THEN 'добавлено' WHEN 1 THEN 'изменено' WHEN 2 THEN 'УДАЛЕНО' END AS chto,
       n.old_value, n.new_value
FROM trans_props_num n
JOIN types t ON t.type_id = n.type_id
JOIN trans_log l ON l.trans_id = n.trans_id
WHERE n.item_id IN (49303, 49308) AND n.type_id IN (741, 742, 751, 752)
ORDER BY n.trans_id, n.item_id, n.type_id;

\echo '### 7. Журнал операций среды (trans_log) — события, изменившие окно 42089 и кнопки'
SELECT l.trans_id, l.session_id, l.commit_time, l.trans_name
FROM trans_log l
WHERE l.trans_id IN (298, 299, 300, 307, 308, 311, 475, 476, 485, 486)
ORDER BY l.trans_id;

\echo '### 8. Для сравнения: элементы проекта С ненулевыми шириной/высотой (сколько и примеры)'
SELECT ty.name, count(*) AS elementov
FROM props_num p JOIN types ty ON ty.type_id = p.type_id
WHERE ty.name IN ('Designer.Layout.Width', 'Designer.Layout.Height') AND p.value > 0
GROUP BY ty.name;
SELECT p.item_id, ty.name, p.value
FROM props_num p JOIN types ty ON ty.type_id = p.type_id
WHERE ty.name IN ('Designer.Layout.Width', 'Designer.Layout.Height') AND p.value > 0
ORDER BY p.item_id, ty.name LIMIT 10;

\echo '### 9. Сессии: с каких машин проект правился'
SELECT session_id, host_name, start_time, end_time FROM sessions ORDER BY start_time;

\echo '### 10. Что именно делали в сессиях Astra (host_name = astra-vm) — операции по времени'
SELECT l.trans_id, l.session_id, l.commit_time, l.trans_name
FROM trans_log l JOIN sessions s ON s.session_id = l.session_id
WHERE s.host_name LIKE 'astra%'
ORDER BY l.trans_id;

\echo '### 11. Текущее состояние элементов (если удалены в сессии #16 — строк не будет)'
SELECT i.item_id, i.type_id,
       coalesce((SELECT value FROM props_str s WHERE s.item_id = i.item_id AND s.type_id = 767), '-') AS display_name
FROM items i WHERE i.item_id IN (42089, 49260, 49303, 49308) ORDER BY i.item_id;

\echo '### 12. Параметры окна 42089 «Ширина»(42094)/«Высота»(42095): история MasterSCADA.Var.InitialValue (771)'
-- Эти значения в среде правятся через «Параметры → Ширина/Высота → Начальное значение» и В МОДЕЛЬ ПИШУТСЯ.
-- Для сравнения: «Расположение → Ширина/Высота» окна (Designer.Layout.Width/Height = 751/752) не пишет ничего (см. §3).
SELECT l.trans_id, l.commit_time, s.host_name, n.item_id, n.action_type,
       CASE n.action_type WHEN 0 THEN 'добавлено' WHEN 1 THEN 'изменено' WHEN 2 THEN 'удалено' END AS chto,
       n.old_value, n.new_value
FROM trans_props_str n
JOIN trans_log l ON l.trans_id = n.trans_id
JOIN sessions s ON s.session_id = l.session_id
WHERE n.type_id = 771 AND n.item_id IN (42094, 42095)
ORDER BY n.trans_id;

\echo '### 12b. Текущие начальные значения этих параметров'
SELECT p.item_id, p.value FROM props_str p WHERE p.type_id = 771 AND p.item_id IN (42094, 42095) ORDER BY p.item_id;

\echo '### 13. Контроль: правка размера ЭЛЕМЕНТА (751/752) пишется в модель — пример операции 478'
SELECT trans_id, item_id, type_id, action_type, old_value, new_value
FROM trans_props_num WHERE trans_id = 478;
