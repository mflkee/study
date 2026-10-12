# MasterSCADA 4D 2.0 — учебный стенд и курс

Учебный каталог по **MasterSCADA 4D 2.0** (среда разработки DT 2.0) для АСУ ТП.
Здесь: дистрибутив, конспекты и офлайн-зеркало справки вендора, скрипты запуска и **курс обучения**
(`course/`), построенный по образцу `../postgresql/course`.

> Курс рассчитан на **любой Linux x64**: основная дорога — так, как это делает вендор
> (**Astra Linux SE 1.7/1.8**), а различия «неподдерживаемых» систем (например, Arch + Wayland/niri)
> собраны в отдельные помеченные блоки. Проверенный учебный вариант запуска —
> [`course/00-orientation/lessons/02-zapusk-sredy-razrabotki.md`](course/00-orientation/lessons/02-zapusk-sredy-razrabotki.md)
> и конспект [`docs/reference/ustanovka-arch-niri.md`](docs/reference/ustanovka-arch-niri.md).

## Состав

| Каталог | Что это |
|---|---|
| [`course/`](course/) | Курс: модули, уроки, упражнения, лабы (`PLAN.md`, `PROGRESS.md`) |
| [`docs/`](docs/) | Индекс документации, локальные конспекты, скрипт офлайн-зеркала справки |
| [`scripts/`](scripts/) | Скрипт запуска SCADA на хосте |
| [`AGENTS.md`](AGENTS.md) | Инструкция для ИИ: где документация и как с ней работать |

## Быстрый старт

```bash
# 1. поднять СУБД для проектов (учебный стенд; либо системный PostgreSQL — см. урок 03)
cd course/00-orientation/infra && docker compose up -d --build && cd -

# 2. запустить среду разработки (нужен установленный /opt/MasterSCADA 4D 2.0)
scripts/run-masterscada.sh

# 3. читать курс
$EDITOR course/README.md
```

## Ключевое о стенде (кратко)

- **Продукт:** MasterSCADA 4D DT 2.0, пакет `MasterSCADA4D_2.0.4_Install.deb`, ставится в `/opt/MasterSCADA 4D 2.0`.
- **Стек:** Electron 28.3.3 (Chromium 120) UI + self-contained **.NET 8** сервер + C++/Boost плагины; **проекты хранятся в PostgreSQL**.
- **СУБД для проектов:** учебный стенд `localhost:15444` (`course/course/course`, том `pgdata`) или системный PostgreSQL на 5432.
- **Официально** DT 2.0 поддерживает Windows 10+ и Astra Linux SE 1.7/1.8; RT — шире (Debian, ALT, РЕД ОС).
- **Проверенный учебный вариант** (вне поддержки вендора): Arch + niri/Wayland, запуск **X11-режимом** (`--no-sandbox`, без ozone-флагов) — так работает контекстное меню (ПКМ); нативное окно Wayland ломает ПКМ-меню (диагностика в `docs/reference/ustanovka-arch-niri.md`, грабли 10b–10c). Режимы — `scripts/run-masterscada.sh` (`MASTERSCADA_MODE=x11|wayland`).
- **Документация:** онлайн-справка 2.0 — `https://support.mps-soft.ru/ms4d_web/` (см. [`AGENTS.md`](AGENTS.md) §3).

## Статус

| Часть | Статус |
|---|---|
| Запуск SCADA на Arch+niri | ✅ проверено |
| Стенд PostgreSQL (Docker, `00-orientation/infra`) | ✅ проверено |
| Модуль 00 (уроки, стенд, упражнение, лаба) | 🚧 материалы переработаны, проходит пользователь |
| Модули 01+ | ⬜ не начаты |
| Офлайн-зеркало справки | ⬜ скрипт готов, не запускалось |
