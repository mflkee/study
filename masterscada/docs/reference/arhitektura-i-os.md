# MasterSCADA 4D DT 2.0 — архитектура и поддерживаемые ОС (конспект)

> Источник: справка вендора `support.mps-soft.ru/ms4d_web` + вскрытие `.deb` (`MasterSCADA4D_2.0.3_Install.deb`).
> Проверено 2026-10-10. Помечено `UNVERIFIED` там, где нет подтверждения запуском.

## Из чего состоит DT 2.0

Среда разработки — связка из трёх частей (это делает её переносимой на любой Linux x64):

| Слой | Технология | Файлы |
|---|---|---|
| UI / редактор проектов | **Electron 28.3.3**, Chromium **120.0.6099.291** | `MasterSCADA4D_2.0`, `resources/app.asar` |
| Ядро сервера / RT | **.NET 8.0**, self-contained (CoreCLR внутри) | `resources/MasterSCADA4D_Server/` (`libcoreclr.so`, `*.dll`) |
| Драйверы и плагины | C++ / **Boost 1.86**, SQLite, soci | `.../Config/MasterPLC/linux/*.so` (138 `.so`) |

Плюс в комплекте — **SwiftShader** (`libvk_swiftshader.so`, `libGLESv2.so`) для программного
рендеринга, если нет GPU-ускорения.

### Зависимости пакета
```
Depends:    libgtk-3-0 libnotify4 libnss3 libxss1 libxtst6 xdg-utils
            libatspi2.0-0 libuuid1 libsecret-1-0
Recommends: libappindicator3-1
```
Требования к системным библиотекам низкие: Electron — до `GLIBC_2.17`, CoreCLR — до `GLIBC_2.16`;
нативные плагины — `GLIBCXX_3.4.32` (GCC 13+). На Arch всё есть из коробки (`gtk3`, `nss`,
`libxss`, `libxtst`, `at-spi2-atk`, `libsecret`, `mesa` и т.д.).

## Системные требования среды разработки (справка вендора)

| | Минимум | Рекомендуется |
|---|---|---|
| ОС | Windows 10 SP1 x64 **или Astra Linux** | Windows 10 x64+ / Astra Linux **не ниже 1.7 (Воронеж)** / SE 1.8 |
| CPU | Intel Core i3, 2.3 ГГц | многоядерный i5, 3.4 ГГц |
| ОЗУ | **8 ГБ** | **16 ГБ** |
| Дисплей | 1280×1024 | 1920×1080 |
| Диск | 10 ГБ свободно | SSD, 100 ГБ |

Вендор: установка в **виртуальной машине допускается, но производительность не гарантируется**.

## Поддерживаемые ОС

| Продукт | Официально поддерживаемые ОС |
|---|---|
| **Среда разработки DT 2.0** | Windows 10/11; **Astra Linux SE 1.7x, SE 1.8x** |
| **Среда исполнения (RT)** | Windows 10+; **Linux** (Astra, Debian 11, ALT Linux 8.x, РЕД ОС) — ядро ≥ 3.18, x86/x64/arm/arm64 |

> ⚠️ Common Edition «Орёл» в списке DT 2.0 **не значится** (это другой продукт, не Special Edition).

## Проекты и PostgreSQL

**Все проекты DT 2.0 хранятся в PostgreSQL** (в 1.3.x — в файлах). Поддерживается **PostgreSQL ≥ 10**,
вендор рекомендует свежую версию; в инструкции установки фигурирует **PostgresPro 1C-14**.
Установка требуется, только если используется **локальная** БД (иначе — сервер в сети).

## Запуск (Linux)

```bash
sudo apt-get install /path/MasterSCADA4D_2.0.4_Install.deb   # установка
cd "/opt/MasterSCADA 4D 2.0" && ./MasterSCADA4D_2.0          # обычный запуск
```
На Arch+niri добавляются флаги `--no-sandbox --ozone-platform-hint=auto` — см.
[`ustanovka-arch-niri.md`](ustanovka-arch-niri.md).
