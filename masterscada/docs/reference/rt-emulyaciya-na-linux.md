# Запуск RT-эмуляции MasterSCADA 4D в среде разработки на Linux (рецепт)

> Проверено 2026-10-10 на реальном проекте **СИКН 1520 ИНК** (после миграции 1.3.x→2.0).
> Среда разработки: DT 2.0 (`MasterSCADA4D_2.0`), узел ARM_1, конфигурация MasterPLC.

При нажатии **Исполнение → Подключиться в режиме эмуляции** всё валится с
`System.Exception: Not permited` (стек: `Controllers.RT.IPCServer.ProcessStartService.GetClient()`).
Ниже — почему и как починить (три правки).

## 1. Каталог приложения должен быть writable

`mplc` (MasterPLC RT) при старте пишет рядом с собой `pid` и `mplc_core.conf`, а `nginx` —
логи. При установке `.deb` в `/opt/...` каталог root-owned → `EACCES` → падение.
Ставили `sudo chown -R "$USER" "/opt/MasterSCADA 4D 2.0"`.

Симптом в strace: `openat(..., "pid", O_WRONLY|O_CREAT) = -1 EACCES`.

## 2. Обёртка над `mplc` (ICU 78 + библиотеки)

Две проблемы сразу:
- встроенный в `mplc` рантайм **.NET 8.0.12** не распознаёт **ICU 78** из Arch →
  `Couldn't find a valid ICU package` → `FailFast` (в IDE выглядит как «Not permited»);
- у `mplc` **RPATH `./`**, поэтому `opcua.so`/`masterplc.so`/`mplcshare.so` находятся
  только если `cwd` = каталог `MasterPLC/linux`.

Решение — заменить `MasterPLC/linux/mplc` тонкой обёрткой
([`scripts/mplc-wrapper.sh`](../../scripts/mplc-wrapper.sh)); оригинал переименовать в `mplc.real`:

```sh
DIR="$(cd "$(dirname "$0")" && pwd)"
export LD_LIBRARY_PATH="$DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1
exec "$DIR/mplc.real" "$@"
```

> Глобально выставлять `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1` нельзя — падает среда разработки.

## 3. IPC-сервер нужно поднять вручную

Среда разработки **сама не запускает** IPC-сервер: она пытается подключиться к уже
существующему сокету `/tmp/CoreFxPipe_MasterSCADA.Controllers.IPCServer` и, не найдя,
падает с «Not permited» (в норме IPC-сервер поднимает установленная RT-среда).
В DT-сборке его запускаем сами (скрипт [`scripts/start-rt-ipc.sh`](../../scripts/start-rt-ipc.sh)):

```sh
cd "/opt/MasterSCADA 4D 2.0/resources/MasterSCADA4D_Server/ipc_server"
./MasterSCADA.Controllers.IPCServer MasterSCADA.Controllers.IPCServer &
```

## Порядок запуска

```sh
scripts/run-masterscada.sh &        # 1. среда разработки (окно)
scripts/start-rt-ipc.sh             # 2. IPC-сервер (сокет)
# 3. в IDE: Открыть проект → СИКН 1520 ИНК → Исполнение → Подключиться в режиме эмуляции
```

Что должно подняться:

| Процесс / порт | Назначение |
|---|---|
| `mplc.real … /udp:30900 /fastcgi:31100 /telnet:31750 /log:…` | PLC-ядро RT |
| `nginx_imit … conf/nginx-mplc-imit.conf` → **:8045** | веб-визуализация |
| `MasterSCADA4DClient -u "<Проект> (АРМ 1)" http://127.0.0.1:8045/index.html` | клиент визуализации |
| **:30900** | API/сокет узла |

В логе — `Контроллер АРМ 1 … запущен`, `Проект запущен`; открывается окно «… (АРМ 1)».

## 4. Порт fastcgi в nginx (бесконечное «Подключение к серверу»)

Симптом: веб-клиент открывается (`http://127.0.0.1:8045/index.html`), но висит на
«Подключение к серверу»; в `web_imit/logs/error.log` —
`connect() failed (111: Connection refused) ... upstream: "fastcgi://127.0.0.1:30750"`.

Причина: в сгенерированном `Debug/web_imit/conf/nginx-mplc-imit.conf` блок
`upstream fcgi_backend` содержит **жёстко прописанный** `127.0.0.1:30750`, тогда как `mplc`
запущен с `/fastcgi:31100` (и рядом в конфиге есть `set $fastcgi_port_base 31100;`).
Чинится заменой порта в upstream и `-s reload` nginx:

```sh
PREF=~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/ProjectsServiceData/<Проект>_<guid>/Debug/web_imit
sed -i 's/127\.0\.0\.1:30750/127.0.0.1:31100/' "$PREF/conf/nginx-mplc-imit.conf"
"/opt/MasterSCADA 4D 2.0/resources/MasterSCADA4D_Server/Config/MasterPLC/linux/nginx/sbin/nginx_imit" \
  -p "$PREF/" -c conf/nginx-mplc-imit.conf -s reload
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8045/Methods/GetState   # → 200
```

## Прочее (на будущее)

- Повторный запуск эмуляции без остановки предыдущей → «Выбранный проект уже открыт»:
  проект держится advisory-lock'ом в его БД. Лечится закрытием/перезапуском IDE (или
  снятием зависших подключений).
- `DetailLog: true` в `IdeSettings.json` раздувает лог (у нас до 112 МБ).
