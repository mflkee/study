# Прогресс курса «MasterSCADA 4D 2.0 для СИКН»

Легенда: ✅ пройдено · 🚧 в процессе · ⬜ не начато.

## Сводка по модулям

| # | Модуль | Статус | Заметки |
|---|---|---|---|
| 00 | 00-orientation | 🚧 | материалы созданы, проходит пользователь |
| 01 | project-basics | ⬜ | |
| 02 | tags-parameters | ⬜ | |
| 03 | object-approach | ⬜ | |
| 04 | fbd-st-programs | ⬜ | |
| 05 | hmi-windows | ⬜ | |
| 06 | channels-protocols | ⬜ | |
| 07 | archives-postgresql | ⬜ | |
| 08 | alarms-events | ⬜ | |
| 09 | rt-deployment | ⬜ | |
| 10 | redundancy-security | ⬜ | |
| 11 | integration | ⬜ | |
| 12 | capstone-sikn | ⬜ | |
| 13 | interview-prep | ⬜ | |

## Модуль 00 — детальный чеклист

- [ ] Зависимости проверены (gtk3, nss, libxss, libxtst, at-spi2, mesa, …)
- [ ] Среда разработки запускается: `../scripts/run-masterscada.sh`
- [ ] В `niri msg --json windows` есть окно «MasterSCADA 4D 2.0»
- [ ] PostgreSQL поднят: `docker compose up -d` в `00-orientation/infra`
- [ ] `SELECT version();` возвращает PostgreSQL 16.x
- [ ] Создан первый проект (сохранён в PostgreSQL)
- [ ] Пройдены уроки 01–03, заполнена самооценка
- [ ] Лаба «Стенд отвечает» выполнена

## Журнал прохождения

| Дата | Модуль | Что сделано |
|---|---|---|
| 2026-10-10 | 00 | Стенд собран: SCADA запущена на Arch+niri (Wayland), .NET-ядро стартует |
