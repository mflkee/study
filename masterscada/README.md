# MasterSCADA 4D 2.0 — учебный стенд и курс

Учебный каталог по **MasterSCADA 4D 2.0** (среда разработки DT 2.0) для АСУ ТП.
Здесь: дистрибутив и инструкция по запуску на **Arch Linux + Wayland/niri**, конспекты
документации, офлайн-зеркало справки и **курс обучения** (`course/`), построенный по образцу
`../postgresql/course`.

> Первый вопрос: «а SCADA вообще заводится на моём niri?» — **да, заводится нативно в Wayland.**
> Проверено 2026-10-10: Electron 28 поднимает `--ozone-platform=wayland`, `.NET`-ядро стартует,
> открываются окна редактора и «Создание проекта». Детали — в
> [`course/00-orientation/lessons/02-zapusk-na-arch-niri.md`](course/00-orientation/lessons/02-zapusk-na-arch-niri.md).

## Состав

| Каталог | Что это |
|---|---|
| [`course/`](course/) | Курс: модули, уроки, упражнения, лабы (`PLAN.md`, `PROGRESS.md`) |
| [`docs/`](docs/) | Индекс документации, локальные конспекты, скрипт офлайн-зеркала справки |
| [`scripts/`](scripts/) | Скрипт запуска SCADA на хосте |
| [`AGENTS.md`](AGENTS.md) | Инструкция для ИИ: где документация и как с ней работать |

## Быстрый старт

```bash
# 1. запустить среду разработки (нужен установленный /opt/MasterSCADA 4D 2.0)
scripts/run-masterscada.sh

# 2. читать курс
$EDITOR course/README.md
```

## Ключевое о стенде (кратко)

- **Продукт:** MasterSCADA 4D DT 2.0, пакет `MasterSCADA4D_2.0.4_Install.deb`, ставится в `/opt/MasterSCADA 4D 2.0`.
- **Стек:** Electron 28.3.3 (Chromium 120) UI + self-contained **.NET 8** сервер + C++/Boost плагины; **проекты хранятся в PostgreSQL**.
- **Запуск на Arch+niri:** `--no-sandbox --ozone-platform-hint=auto` (см. `scripts/run-masterscada.sh`).
- **Официально** DT 2.0 поддерживает Windows и Astra Linux SE 1.7/1.8 — на Arch это **не** вендорская конфигурация.
- **Документация:** онлайн-справка 2.0 — `https://support.mps-soft.ru/ms4d_web/` (см. [`AGENTS.md`](AGENTS.md) §3).

## Статус

| Часть | Статус |
|---|---|
| Запуск SCADA на Arch+niri | ✅ проверено |
| Структура курса, модуль 00 | 🚧 черновик |
| Модули 01+ | ⬜ не начаты |
| Офлайн-зеркало справки | ⬜ скрипт готов, не запускалось |
