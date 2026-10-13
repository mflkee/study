# Запуск MasterSCADA 4D DT 2.0 на Arch Linux + Wayland/niri (конспект)

> **Статус: ✅ проверено 2026-10-12** на Arch + niri. Рабочий режим — **X11 (XWayland)**: полный
> функционал (ПКМ-меню, горячие клавиши, диалоги). Нативное окно Wayland тоже запускается, но
> **ломает контекстное меню** (см. грабл. 10b) — годится только там, где меню не нужны.
> Вендором такая конфигурация **не поддерживается** — это эксперимент, не прод-инсталляция
> (официально: Windows 10+ и Astra Linux SE 1.7x/1.8x, где сессия — X.Org; Wayland в справке
> вендора не упоминается вообще).
>
> **Дополнено 2026-10-11:** сборка `rc` (имена путей/пакетов), стенд PostgreSQL в Docker
> (`localhost:15444`, локаль `ru_RU.UTF-8`), поведение раскладки клавиатуры в XWayland-приложениях.
> **Дополнено 2026-10-12:** режимы запуска X11/Wayland, поломка ПКМ-меню в нативном Wayland,
> «зависание на заставке» при незакрытой прежней копии (грабли 10b и 10c).

## Что понадобится

| Компонент | Где взять |
|---|---|
| Дистрибутив | `MasterSCADA4D_2.0.4_Install.deb` (в `~/Downloads`, `~/projects/SIKN1520SCADA` или «Папка группы/АСУ ТП/Дистрибутивы») |
| Зависимости | `gtk3 nss libxss libxtst at-spi2-atk at-spi2-core libnotify libsecret xdg-utils cups alsa-lib mesa nspr dbus libxkbcommon` (в Arch почти всё есть) |
| PostgreSQL | для проектов (см. `ust_postgresql_linux.html`); в курсе — **стенд в Docker** (`course/00-orientation/infra`): `localhost:15444`, `course/course/course`, локаль `ru_RU.UTF-8`. Альтернатива — системный PostgreSQL на `5432` |

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
Затем запуск — через `scripts/run-masterscada.sh` (режим задаётся `MASTERSCADA_MODE`, по умолчанию `x11`).

## Почему именно такие флаги

| Флаг | Зачем |
|---|---|
| `--no-sandbox` | В `.deb` `chrome-sandbox` **не** setuid-root; без флага Electron падает с ошибкой песочницы. Альтернатива — `sudo chown root:root chrome-sandbox && sudo chmod 4755 chrome-sandbox`. |
| *без ozone-флага* | Electron идёт через **XWayland** — только так ПКМ-меню и диалоги работают штатно (см. грабл. 10b). Подтверждение: у окна в niri `pid` = процесс `xwayland-satellite`. |
| `--ozone-platform-hint=auto` | Нужен только для режима `MASTERSCADA_MODE=wayland`: Chromium сам выбирает нативный Wayland. Подтверждение: `ps -eo args \| grep -o -- '--ozone-platform=wayland'`. |

## Проверка, что всё поднялось

```bash
# окно должно появиться в списке окон niri
niri msg --json windows | grep -i masterscada
# в процессах — родитель, GPU-процесс, рендереры и .NET-сервер:
ps -eo pid,args | grep -i masterscada | grep -v grep
```
Ожидаемо: окно `"title":"MasterSCADA 4D 2.0"` (+ при старте диалог `«Создание проекта»`),
и процесс `resources/MasterSCADA4D_Server/MasterSCADA4D_Server --server-startup-port=44000`.

> **Сборки с суффиксом.** Если установлена сборка `rc`/`beta`/`branch`, меняются имена:
> каталог `/opt/MasterSCADA 4D RC`, бинарник `MasterSCADA4D_2.0_RC`, пакет `masterscada4d-rc`,
> данные UI — `~/.config/MasterSCADA 4D RC/`. Проверено 2026-10-11 на такой сборке.

## Где настройки, логи и проекты (проверено)

| Что | Путь / где |
|---|---|
| Настройки IDE (в т.ч. подключение к БД) | `~/.config/MPSSoft/MasterSCADA4D_DT_2.0[_RC]/IdeSettings.json` — ключи `ProjectsStoreServer/Port/User/Password` |
| Логи IDE | `~/.config/MPSSoft/MasterSCADA4D_DT_2.0[_RC]/sys_log/ГГГГ_ММ_ДД.log` — старт (`Start application 1.3.10.33235(2.0.4_RC_…)`) и ошибки подключения к хранилищу |
| Данные UI (кэш, `SingletonLock`) | `~/.config/MasterSCADA 4D 2.0[_RC]/` |
| **Проекты** | в PostgreSQL (не в файлах): у учебного стенда — `localhost:15444`, `course/course/course` |

Проверка, что IDE ходит в БД именно стенда (и что проект там появился):

```bash
cd course/00-orientation/infra
docker compose up -d --build            # поднять стенд (образ с локалью ru_RU.UTF-8)
docker compose exec -T postgres psql -U course -d course -c "\l"   # список БД
docker compose exec -T postgres psql -U course -d course -c "\dt"  # таблицы/схемы продукта
```

## Грабли

1. **Порт `9222`**. Если запускать с `--remote-debugging-port=9222`, порт может занять сам сервер
   СКАДЫ — отладчик не поднимется. Не используйте 9222 для отладки Electron здесь.
2. **Повторный запуск**: Electron использует single-instance-lock (`~/.config/MasterSCADA 4D 2.0/SingletonLock`).
   Если предыдущий процесс завис и не умирает по SIGTERM — `pkill -9 -x MasterSCADA4D_2` и
   удалить `Singleton*`, иначе новый инстанс просто «покажет» старый.
3. **Fontconfig-warnings** (`48-guessfamily.conf ...`) в stderr — безвредны.
4. **Скорость GUI**: рендер может идти через SwiftShader. Для редактора мнемосхем это заметно.
   При необходимости — аппаратное ускорение GPU.
5. **Проекты**: без запущенного PostgreSQL создать/сохранить проект не получится. Ошибка в логе —
   `Ошибка подключения: Connection refused` (нет сервера **или не тот порт**; у стенда — `15444`).
6. **Данные приложения** пишутся в `~/.config/MasterSCADA 4D 2.0[_RC]/` (Electron user-data).
7. **Раскладка в X11/XWayland-приложениях не переключается** (проверено 2026-10-11). У X-сервера
   (`xwayland-satellite`) **своё** клавиатурное состояние: niri отдал ему одну раскладку и о своих
   переключениях не сообщает, поэтому в приложении раскладка «залипает», а `Super+Space` доходит до
   приложения как пробел (если запускать без ozone-флага, т.е. через XWayland). Лечится заданием
   раскладки самому X-серверу:

   ```bash
   DISPLAY=:0 setxkbmap -layout us,ru -option grp:win_space_toggle
   # в niri — автоматически при старте:
   # spawn-sh-at-startup "for i in $(seq 1 60); do DISPLAY=${DISPLAY:-:0} setxkbmap -layout us,ru -option grp:win_space_toggle 2>/dev/null && exit 0; sleep 0.5; done"
   ```

   Запуск **нативно** в Wayland (`--ozone-platform-hint=auto`) этой проблемы не имеет: раскладку даёт
   композитор. Подробнее — урок 02, §5.
8. **Внешний вид окна зависит от способа запуска**: XWayland-окно niri обрамляет рамкой/фокус-кольцом,
   а нативное Wayland-окно Chromium — frameless (клиентские декорации), и niri его «не украшает».
9. **`Access to the path '/usr/share/MPSSoft' is denied`** (`UnauthorizedAccessException`) при создании
   или открытии проекта. Причина: при распаковке `.deb` (вариант A/B выше) **не выполняется
   `postinst`** пакета, а он создаёт общий каталог данных IDE:

   ```bash
   sudo mkdir -p /usr/share/MPSSoft
   sudo chmod a+rwx /usr/share/MPSSoft     # вендорский `chmod a+rw` без «x» каталог не откроет
   ```

   Проверка, что делает `postinst` (без установки пакета):
   `ar p ~/Downloads/MasterSCADA4D_2.0.4_Install.deb control.tar.gz | tar -xzO ./postinst`.
   Там же — список `chmod +x` на бинарники в `/opt/…/resources` (их тоже стоит применить).
10. **«Выбранный проект уже открыт»** — проект удерживает advisory-блокировка PostgreSQL, пока
   открыт другой сеанс IDE (второе окно/старый процесс). Проверить, кто держит:
   `SELECT pid, objid FROM pg_locks WHERE locktype='advisory';` — закрыть лишний сеанс.
10a. **Часть горячих клавиш не работает** (`Ctrl+C`, `Ctrl+V`, `Ctrl+D`), при этом меню и ПКМ работают.
   Проверено 2026-10-12: клавиатуру приложение видит (`Ctrl+N` открывает диалог «Создание проекта»
   и в X11-, и в нативном Wayland-запуске), но часть команд в этой RC-сборке к клавишам не привязана.
   Рабочий маршрут: меню **«Правка»** и контекстное меню — «Дублировать», «Копировать», «Вставить»,
   «Вставить как экземпляр». Эталон релиза: `Ctrl+C/V`, `Ctrl+D`, `Ctrl+Shift+V`, `Ctrl+S`, `F2`, `Del`.
10b. **ПКМ-меню в нативном Wayland ломается → запускать в X11-режиме** (проверено 2026-10-12).
   Симптом: по правой кнопке в дереве главное окно на секунду «дёргается», меню не появляется.
   Что видно в логах: Electron/GTK открывает меню **отдельным X11-окном** —
   `xwayland-satellite: new window: … override_redirect: true, width: 300, height: 200`,
   затем `set to Popup … has_transient_for: false` (нет `WM_CLASS`, заголовка, родителя).
   Привязать такое «сиротское» окно не к чему (главное окно приложения — **нативное Wayland**,
   у процесса 0 X11-сокетов), поэтому спутник монтирует его как обычное окно:
   `creating toplevel … fullscreen: false` → `configuring toplevel … 1892x1018`, а niri вставляет
   его в раскладку (`Window opened: pid=1501, is_floating: false, tile_size: 1896x1022,
   pos_in_scrolling_layout: (4,1)`). Меню тут же закрывается, т.к. фокус до него не дошёл
   (`No last focused toplevel, cannot focus window`). Форсирование `GDK_BACKEND=wayland` не помогает.

   Лечение: запускать приложение **целиком X11-клиентом** — `--no-sandbox` и **без** ozone-флагов.
   Тогда окно и меню — один X11-клиент, попап привязывается к своему toplevel, и ПКМ + горячие
   клавиши работают штатно (то же самое, что на Astra, где сессия X.Org). Цена — XWayland
   (и его `setxkbmap`-обход для раскладки, грабл. 7).

   Обёртка + ярлык, перекрывающий пакетный (так удобнее не забывать режим):

   ```bash
   # ~/.local/bin/masterscada-rc — режим по умолчанию x11, wayland — опционально
   : "${MASTERSCADA_MODE:=x11}"
   APP="/opt/MasterSCADA 4D RC/MasterSCADA4D_2.0_RC"
   case "$MASTERSCADA_MODE" in
     wayland) export GDK_BACKEND=wayland; exec "$APP" --no-sandbox --ozone-platform-hint=auto "$@" ;;
     *)       exec "$APP" --no-sandbox "$@" ;;
   esac
   # ~/.local/share/applications/MasterSCADA4D_2.0_RC.desktop → Exec=/home/<user>/.local/bin/masterscada-rc %U
   ```
   Проверка режима: `niri msg --json windows` — у X11-окна `pid` равен процессу `xwayland-satellite`
   (1501), у нативного Wayland-окна — pid самой среды разработки.

   В репозитории курса то же самое лежит в `scripts/run-masterscada.sh` (с автопоиском каталога
   установки RC/DT).

10c. **Новая копия висит на заставке (splash), если осталась прежняя.** Симптом: окно 680×426 с
   логотипом MasterSCADA 4D висит неограниченно долго, в `sys_log` после строки
   `Start application …` — тишина, окно проекта так и не открывается. Причина: старый
   `.NET`-сервер (`MasterSCADA4D_Server`) не закрылся и держит порт ядра (44000), поэтому UI
   новой копии не может подключиться. Проверка и чистка:

   ```bash
   ss -ltnp | grep -oE '127.0.0.1:44[0-9]{3}.*'      # кто держит порт ядра
   pkill -f '^/opt/MasterSCADA 4D RC'; sleep 3        # снять ВСЕ процессы среды (Electron + сервер)
   pgrep -cf '^/opt/MasterSCADA 4D RC'                # должно быть 0
   ```

   **Не убивайте процессы шаблоном из своей же команды!** `pkill -f '…MasterSCADA4D_2.0_RC'`,
   запущенный из скрипта/шелла, матчит и собственную командную строку — оболочка снесёт саму себя.

10d. **«Fatal error» с кнопкой RELOAD → белый экран** (проверено 2026-10-12). Симптом: в клиенте
   всплывает бейдж `Fatal error | RELOAD`, после нажатия — пустая белая страница, сколько ни
   перезагружай. Причина — падение **серверного** канала Blazor на RC-баге работы с уже удалёнными
   элементами модели; в журнале (`sys_log/<дата>.log`) это видно как

   ```
   [Error] <id>, System.InvalidOperationException: Sequence contains no matching element
      at System.Linq.Enumerable.First[TSource](IEnumerable`1 source, Func`2 predicate)
      at MS4Web.IDE.Blazor.Services.Branches.BranchesService.BranchedItem_ItemChanged(...)   ← панель веток
   !!!Обращение к удаленному элементу : '…' (Id=… <disposed>)                                 ← дизайнер FBD/схем
      … MS4Web.IDE.Blazor.Components.Designers.Schematic.Fbd.Internal.ValueIndicatedFbdComponent
   ```

   Лечение — **перезапуск среды**, данные при этом не теряются (проект лежит в PostgreSQL, при старте
   видно `DbStoreService.RequestAllItems() [END] - Result: Completed`). Быстрая команда:

   ```bash
   pkill -f '^/opt/MasterSCADA 4D RC'; sleep 3; scripts/run-masterscada.sh
   ```

   Практика: чаще сохраняться («Проект → Сохранить») и не держать открытыми много дизайнеров
   (FBD/схемы) при массовых операциях с деревом — именно их `Dispose` и падает.
11. **Эмуляция RT на Linux: `System.Exception: Not permited` → `Connection refused (127.0.0.1:30900)`,
   окно клиента визуализации не открывается.** Проверено 2026-10-12.

   Среда разработки стартует процессы узла (nginx/mplc) **через вспомогательный процесс**
   `MasterSCADA.Controllers.IPCServer`. На Windows она поднимает его сама
   (`cmd.exe /c start /b MasterSCADA.Controllers.IPCServer.exe` — эта строка так и лежит в сборке),
   на Linux ожидает его уже запущенным (в штатной установке его обеспечивает служба `mplc4` из
   дистрибутива RT). При установке DT «распаковкой» его нет → `ProcessStartService.GetClient()`
   падает с `Not permited`, каталог узла остаётся без `logs/`, `temp/proxy`, `temp/client`, nginx
   узла не поднимается, а IDE бесконечно получает `Connection refused` на порт узла `30900`.

   Лечится запуском helper'а (и лучше — user-юнитом, чтобы не делать это руками каждый раз).
   Важно: helper **штатно завершается вместе с сессией эмуляции** (в журнале видно
   `EmulatorSession … Exited` → `Disposed`), поэтому в юните нужен `Restart=always`
   (`on-failure` недостаточно — «чистый» выход не перезапускается, и следующий запуск снова
   падает с `Not permited`).

   ```bash
   # разово:
   setsid nohup "/opt/MasterSCADA 4D RC/resources/MasterSCADA4D_Server/ipc_server/MasterSCADA.Controllers.IPCServer" &

   # постоянная схема: ~/.config/systemd/user/masterscada-ipc.service
   #   [Unit]
   #   StartLimitIntervalSec=0
   #   [Service]
   #   WorkingDirectory=/opt/MasterSCADA 4D RC/resources/MasterSCADA4D_Server/ipc_server
   #   ExecStart="/opt/MasterSCADA 4D RC/resources/MasterSCADA4D_Server/ipc_server/MasterSCADA.Controllers.IPCServer"
   #   Restart=always
   #   RestartSec=2
   systemctl --user daemon-reload && systemctl --user enable --now masterscada-ipc
   systemctl --user status masterscada-ipc        # должно быть active (running)
   systemctl --user restart masterscada-ipc       # если лежит
   ls -l /tmp/CoreFxPipe_MasterSCADA.Controllers.IPCServer   # сокет-«труба» helper'а
   ```

   Ещё одна ошибка из журнала самого RT (не блокирующая, но полезно знать):
   `ERR | Can't write config file: /opt/…/Config/MasterPLC/linux/mplc_core.conf` — RT хочет писать
   свой core-конфиг **рядом со своим бинарником**, а `/opt` принадлежит root. В штатной установке RT
   работает службой от root (`mplc4`), поэтому там этого нет; в эмуляции под пользователем — есть.
   Проверить журнал helper'а/RT: `journalctl --user -u masterscada-ipc -f`.

   Признаки, что всё поехало: в `sys_log` пропадает `Not permited`, появляются `Проект запущен`,
   поднимается `nginx_imit` (порт `8045`) и узел отвечает на `127.0.0.1:30900`. После остановки
   проекта `nginx_imit` может остаться висеть — при необходимости `pkill nginx_imit` (не делай этого,
   пока смотришь мнемосхему в браузере).
   Важно: путь в юните версионный — после обновления среды разработки его надо поправить.
12. **Диалога выбора клиента визуализации нет** — он показывается только при первом запуске;
   выбор хранится в `IdeSettings.json` (`OpenClientMode`), меняется в
   **Настройки среды → вкладка «Клиент визуализации»**. Помни, что мнемосхема может открываться
   **вкладкой в браузере** (это режим «браузер по умолчанию»), а не отдельным окном.

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

## Проверка «а не в правах ли дело» (быстрый чек-лист, проверено 2026-10-13)

Когда что-то «не сохраняется / не даёт править», полезно за 4 команды исключить права:

```bash
# 1. файлы: рабочие папки проектов и настройки
ls -ld /usr/share/MPSSoft /usr/share/MPSSoft/* ; find /usr/share/MPSSoft -not -user "$USER"
find ~/.config/MPSSoft -not -user "$USER" ; find ~/.config/MPSSoft -type d ! -perm -u+w

# 2. БД: роль и владельцы баз (ожидаем Superuser/владельца обеих баз)
docker compose -f course/00-orientation/infra/docker-compose.yml exec -T postgres \
  psql -U course -d postgres -c '\du course' \
  -c "SELECT datname, pg_get_userbyid(datdba) FROM pg_database WHERE datname LIKE 'ms4d%';"

# 3. журнал среды: есть ли отказы по доступу
grep -inE 'not permited|permission|denied|Unauthorized' \
  ~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/sys_log/$(date +%Y_%m_%d).log | tail

# 4. встроенные права MS4D: есть ли записи прав у конкретного элемента
#    (в БД проекта: items с типом MasterSCADA.Security.* под нужным item_id)
```

Итог проверки 2026-10-13 на нашем стенде: `/usr/share/MPSSoft` — `drwxrwxrwx`, подкаталоги
наши; `~/.config/MPSSoft` — всё наше и записываемое; роль `course` — Superuser и владелец
`ms4d_projects`/`ms4d_project_2`; в журнале отказов по доступу нет (единственное совпадение —
известная ошибка узла RT на порту 30900); у наших элементов (окно, картинка, окна станции)
записей прав нет. **Права ни при чём** — «не сохраняется размер окна» это особенность RC-сборки
(размеры **элементов** сохраняются, размер **окна** — нет).
