# Запуск MasterSCADA 4D DT 2.0 на Arch Linux + Wayland/niri (конспект)

> **Статус: ✅ проверено 2026-10-10** на Arch + niri (Wayland). Окно редактора и диалог
> «Создание проекта» открываются **нативно в Wayland** (Chromium ozone), ядро `.NET` стартует.
> Вендором такая конфигурация **не поддерживается** — это эксперимент, не прод-инсталляция.

## Что понадобится

| Компонент | Где взять |
|---|---|
| Дистрибутив | `MasterSCADA4D_2.0.4_Install.deb` (в `~/Downloads`, `~/projects/SIKN1520SCADA` или «Папка группы/АСУ ТП/Дистрибутивы») |
| Зависимости | `gtk3 nss libxss libxtst at-spi2-atk at-spi2-core libnotify libsecret xdg-utils cups alsa-lib mesa nspr dbus libxkbcommon` (в Arch почти всё есть) |
| PostgreSQL | для проектов (см. `ust_postgresql_linux.html`); для курса — Docker |

## Вариант A. Извлечь `.deb` без установки (без `apt`/`dpkg`)

Arch не умеет `.deb` напрямую, но пакет — обычный `ar` + `tar`, и внутри просто дерево `/opt`:

```bash
cd /tmp && mkdir -p ms4d && cd ms4d
ar p ~/Downloads/MasterSCADA4D_2.0.4_Install.deb data.tar.xz | tar -xJ
# появилось ./opt/MasterSCADA 4D 2.0/  и  ./usr/share/applications/MasterSCADA4D_2.0.desktop
```

Запуск из распакованной копии:

```bash
cd "opt/MasterSCADA 4D 2.0"
./MasterSCADA4D_2.0 --no-sandbox --ozone-platform-hint=auto
```

## Вариант B. «Поставить» в `/opt` (как в дистрибутиве)

```bash
sudo mkdir -p "/opt/MasterSCADA 4D 2.0"
sudo tar -xJf <(ar p ~/Downloads/MasterSCADA4D_2.0.4_Install.deb data.tar.xz) -C /
sudo cp "/opt/MasterSCADA 4D 2.0/icon.png" /usr/share/pixmaps/ 2>/dev/null || true
# .desktop из пакета: usr/share/applications/MasterSCADA4D_2.0.desktop
```
Затем запуск — через `scripts/run-masterscada.sh`.

## Почему именно эти два флага

| Флаг | Зачем |
|---|---|
| `--no-sandbox` | В `.deb` `chrome-sandbox` **не** setuid-root; без флага Electron падает с ошибкой песочницы. Альтернатива — `sudo chown root:root chrome-sandbox && sudo chmod 4755 chrome-sandbox`. |
| `--ozone-platform-hint=auto` | Chromium 120 сам выбирает **нативный Wayland** (ozone) вместо XWayland. На niri работает сразу, без переменных окружения. |

Подтверждение из процесса GPU/рендерера: `--ozone-platform=wayland` (т.е. **не** XWayland).

## Проверка, что всё поднялось

```bash
# окно должно появиться в списке окон niri
niri msg --json windows | grep -i masterscada
# в процессах — родитель, GPU-процесс, рендереры и .NET-сервер:
ps -eo pid,args | grep -i masterscada | grep -v grep
```
Ожидаемо: окно `"title":"MasterSCADA 4D 2.0"` (+ при старте диалог `«Создание проекта»`),
и процесс `resources/MasterSCADA4D_Server/MasterSCADA4D_Server --server-startup-port=44000`.

## Грабли

1. **Порт `9222`**. Если запускать с `--remote-debugging-port=9222`, порт может занять сам сервер
   СКАДЫ — отладчик не поднимется. Не используйте 9222 для отладки Electron здесь.
2. **Повторный запуск**: Electron использует single-instance-lock (`~/.config/MasterSCADA 4D 2.0/SingletonLock`).
   Если предыдущий процесс завис и не умирает по SIGTERM — `pkill -9 -x MasterSCADA4D_2` и
   удалить `Singleton*`, иначе новый инстанс просто «покажет» старый.
3. **Fontconfig-warnings** (`48-guessfamily.conf ...`) в stderr — безвредны.
4. **Скорость GUI**: рендер может идти через SwiftShader. Для редактора мнемосхем это заметно.
   При необходимости — аппаратное ускорение GPU.
5. **Проекты**: без запущенного PostgreSQL создать/сохранить проект не получится.
6. **Данные приложения** пишутся в `~/.config/MasterSCADA 4D 2.0/` (Electron user-data).

## Интеграция с лаунчером (Super+D / Noctalia)

Пакетный `.desktop` содержит `Exec` **без** флагов (`--no-sandbox`, ozone) — из лаунчера SCADA
так не запустится. Поэтому ставим свой ярлык. Пользовательский файл с тем же именем
**перекрывает** системный (XDG precedence), sudo не нужен:

```bash
mkdir -p ~/.local/share/applications ~/.local/share/icons/hicolor/256x256/apps
# ~/.local/share/applications/MasterSCADA4D_2.0.desktop — с Exec ... --no-sandbox --ozone-platform-hint=auto
cp "/opt/MasterSCADA 4D 2.0/icon.png" ~/.local/share/icons/hicolor/256x256/apps/MasterSCADA4D_2.0.png
update-desktop-database ~/.local/share/applications
gtk-update-icon-cache -f -t ~/.local/share/icons/hicolor
```

Скрипт `scripts/install-to-opt.sh` уже создаёт такой `.desktop` в `/usr/share/applications`.

- Лаунчер у пользователя: **Noctalia** на `Mod+D` (`noctalia msg panel-toggle launcher`),
  fuzzel — на `Mod+Ctrl+D`.
- Проверить, что ярлык рабочий, без GUI: `gtk-launch MasterSCADA4D_2.0` — поднимет окно из `/opt`.
- Если лаунчер не видит новую запись — перезапустить его (`killall noctalia` или `Mod+D` заново).

## Обратный путь (удаление)
Просто удалить `/opt/MasterSCADA 4D 2.0`, `.desktop` и `~/.config/MasterSCADA 4D 2.0/`.
