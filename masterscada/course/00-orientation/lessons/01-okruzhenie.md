# Урок 01: Окружение и подготовка дистрибутива

**Закрывает цель:** зависимости на месте, дистрибутив SCADA доступен, все инструменты проверяются.
**Время:** 50 мин

## Зачем это нужно

Курс построен на выполнении: без рабочей среды разработки и PostgreSQL каждый следующий модуль
начнётся с «у меня не работает». Один раз настраиваем — дальше только учимся.

## Ключевые идеи (сжато)

- Среда разработки — **Electron-приложение**; ей нужны штатные GTK-библиотеки, а не «магия Astra».
- `.deb` на Arch не ставится через `apt`, но его можно **распаковать** (`ar` + `tar`) — внутри дерево `/opt`.
- PostgreSQL — отдельный сервис; держим его в **Docker**, чтобы не трогать системы.
- Все проверки — однострочные команды; вывод версии = «готово».

## Разбор на примере

### 1. Проверка зависимостей

```bash
for p in gtk3 nss libxss libxtst at-spi2-atk at-spi2-core libnotify libsecret xdg-utils cups alsa-lib mesa nspr dbus libxkbcommon; do
  pacman -Qq "$p" >/dev/null 2>&1 && echo "OK   $p" || echo "MISS $p"
done
```
Почти всё уже стоит. `libsecret`, `libnotify`, `libxss`, `libxtst`, `at-spi2-*` — доставить при необходимости:
```bash
sudo pacman -S gtk3 nss libxss libxtst at-spi2-atk libsecret libnotify xdg-utils
```

### 2. Дистрибутив SCADA

Файл `MasterSCADA4D_2.0.4_Install.deb` (у автора — в `~/Downloads`, `~/projects/SIKN1520SCADA`,
или в сетевой «Папка группы/АСУ ТП/Дистрибутивы»). Проверь, что пакет целый:
```bash
ls -lh MasterSCADA4D_2.0.4_Install.deb      # ожидаем ~679 МБ
file MasterSCADA4D_2.0.4_Install.deb        # Debian binary package (format 2.0), data compression xz
```
Если размер 0 или есть `.part` — докачка не завершена.

### 3. Извлечение (вариант без установки)

```bash
cd /tmp && mkdir -p ms4d && cd ms4d
ar p ~/Downloads/MasterSCADA4D_2.0.4_Install.deb data.tar.xz | tar -xJ
ls "opt/MasterSCADA 4D 2.0/" | head
```
Внутри: `MasterSCADA4D_2.0` (Electron), `resources/app.asar` (UI), `resources/MasterSCADA4D_Server/`
(.NET-ядро и драйверы).

### 4. Docker и psql

```bash
docker --version            # Docker version 2x.x
docker compose version      # Docker Compose version v2.x
psql --version              # psql (PostgreSQL) 16.x (или клиент из контейнера)
```

## Как это устроено под капотом

- **`.deb`** — это архив `ar` из трёх частей: `debian-binary`, `control.tar.*` (метаданные: имя, зависимости),
  `data.tar.*` (сами файлы). Поэтому распаковка `ar p … data.tar.xz | tar -xJ` кладёт ровно то дерево,
  что и `dpkg -x`.
- **Зависимости** из `control`: `libgtk-3-0, libnotify4, libnss3, libxss1, libxtst6, xdg-utils,
  libatspi2.0-0, libuuid1, libsecret-1-0`. Это имена Debian-пакетов; в Arch соответствуют `gtk3`,
  `libnotify`, `nss`, `libxss`, `libxtst`, `xdg-utils`, `at-spi2-atk`, `util-linux`(uuid), `libsecret`.
- **Docker** — клиент-сервер: CLI общается с демоном через сокет; ошибка «permission denied» = пользователь не в группе `docker`.

## Типичные ошибки и грабли

1. `tar: … xz: Cannot exec` — нет `xz` (в Arch он обычно есть); `sudo pacman -S xz`.
2. Пакет «целый», но `ar p` ничего не выводит — файл ещё качается (`.part`), дождаться.
3. `docker: permission denied` — `sudo usermod -aG docker $USER` и перелогиниться.
4. Порт 5432 занят другим PostgreSQL — в курсе свой порт 15432, конфликта не будет.

## Мини-задание

Выполни проверки зависимостей, подтверди размер `.deb`, распакуй его в `/tmp/ms4d` и покажи первые
строки `ls "opt/MasterSCADA 4D 2.0/"`.

<details>
<summary>Ответ (что должно получиться)</summary>

```
OK   gtk3
OK   nss
…
-664M  MasterSCADA4D_2.0.4_Install.deb
…
MasterSCADA4D_2.0
icon.png
resources
…
```
Точный набор `OK/MISS` зависит от системы; важно, чтобы к концу модуля все критичные были `OK`.
</details>

## Как это спросят на собеседовании

1. «Что внутри `.deb` и чем `dpkg -x` отличается от `dpkg -i`?»
2. «Почему SCADA на Electron переносима между дистрибутивами Linux?»
3. «Зачем держать БД SCADA отдельным сервисом?»

## Что читать дальше

- Справка 2.0, установка на Linux: `https://support.mps-soft.ru/ms4d_web/ustanovka_dt_linux.html`
- Системные требования: `…/sistemnwe_trebovaniya_dt.html`
