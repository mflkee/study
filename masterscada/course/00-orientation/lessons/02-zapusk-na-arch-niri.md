# Урок 02: Запуск SCADA на Arch + Wayland/niri

**Закрывает цель:** среда разработки MasterSCADA 4D 2.0 запускается и её окно видно в niri.
**Время:** 50 мин

## Зачем это нужно

Официально DT 2.0 поддерживает Windows и Astra Linux SE 1.7/1.8. Мы запускаем её на Arch + niri
как эксперимент — и это **работает**: Electron 28 (Chromium 120) сам поднимается нативно в Wayland.
Понимание, *почему* это работает, пригодится и на других «неподдерживаемых» системах.

## Ключевые идеи (сжато)

- Приложение — Electron; на Wayland ему нужен либо XWayland, либо флаг `--ozone-platform-hint=auto`.
- `chrome-sandbox` в `.deb` не setuid → запускаем с `--no-sandbox` (или выставляем права).
- Стартуют три вещи: UI (Electron), .NET-ядро (сервер) и плагины; ожидаем окно + процесс сервера.
- Готовый скрипт: `../../scripts/run-masterscada.sh`.

## Разбор на примере

### Запуск

```bash
cd "/tmp/ms4d/opt/MasterSCADA 4D 2.0"       # или /opt/MasterSCADA 4D 2.0 после установки
./MasterSCADA4D_2.0 --no-sandbox --ozone-platform-hint=auto
```

Или скриптом из репозитория (он же выставляет `cd` и флаги):
```bash
../../scripts/run-masterscada.sh
```

### Проверка, что поднялось

```bash
# 1) окно в niri
niri msg --json windows | python3 -c 'import sys,json
print([w["title"] for w in json.load(sys.stdin) if "MasterSCADA" in w["title"]])'
# ожидаем: ['MasterSCADA 4D 2.0'] (+ 'Создание проекта' в диалоге создания)

# 2) процессы
ps -eo pid,args | grep -i masterscada | grep -v grep
# ожидаем: родитель, —type=gpu-process, —type=renderer, resources/MasterSCADA4D_Server/MasterSCADA4D_Server
```

### Нативный Wayland или XWayland?

Смотрим флаги дочерних процессов:
```
--type=gpu-process … --ozone-platform=wayland
```
Если видишь `--ozone-platform=wayland` — это **нативный Wayland** (не XWayland). Именно так и должно быть с `--ozone-platform-hint=auto`.

## Как это устроено под капотом

- **Chromium ozone** — слой абстракции платформ вывода. `--ozone-platform-hint=auto` разрешает автовыбор
  Wayland, если доступен `WAYLAND_DISPLAY`; иначе — X11.
- **Electron-sandbox**: `chrome-sandbox` должен быть setuid-root, иначе Chromium отказывается запускаться.
  Альтернатива флагу `--no-sandbox`:
  ```bash
  sudo chown root:root "/opt/MasterSCADA 4D 2.0/chrome-sandbox"
  sudo chmod 4755 "/opt/MasterSCADA 4D 2.0/chrome-sandbox"
  ```
- **Ядро .NET** запускается как дочерний процесс из `resources/MasterSCADA4D_Server/` с портом
  `--server-startup-port`. UI общается с ним локально.

## Типичные ошибки и грабли

1. **Порт 9222** — не задавайте `--remote-debugging-port=9222`: его может занять сервер SCADA, и отладчик не поднимется.
2. **Повторный запуск не открывает окно** — внутри Electron single-instance-lock. Если старый процесс завис:
   ```bash
   pkill -9 -x MasterSCADA4D_2; pkill -9 -x MasterSCADA4D_S
   find "$HOME/.config/MasterSCADA 4D 2.0" -maxdepth 1 -name 'Singleton*' -delete
   ```
3. **Fontconfig-warnings** (`48-guessfamily.conf …`) — безвредны, игнорировать.
4. **Медленный GUI** — рендер может идти через SwiftShader; для редактора мнемосхем это заметно.
5. **Официально не поддерживается** — на Arch возможны сюрпризы; фиксируйте их в `PROGRESS.md`.

## Мини-задание

Запусти среду разработки и докажи, что окно поднялось **нативно в Wayland** (а не через XWayland).

<details>
<summary>Ответ (как доказать)</summary>

```bash
ps -eo args | grep -o -- '--ozone-platform=wayland' | head -1
# если строка печатается — используется нативный Wayland
niri msg --json windows | grep -i masterscada
```
Также `niri msg windows` покажет окно в рабочем пространстве niri (а не как «X11-окно»).
</details>

## Как это спросят на собеседовании

1. «Чем отличается запуск Electron в XWayland и в нативном Wayland?»
2. «Зачем Chromium песочница и что делает `--no-sandbox`?»
3. «Как понять, что приложение реально работает, если GUI не поднимается?» (по процессам/логам/портам)

## Что читать дальше

- Наш конспект: [`../../../docs/reference/ustanovka-arch-niri.md`](../../../docs/reference/ustanovka-arch-niri.md)
- Справка 2.0: `…/ustanovka_dt_linux.html`, `…/zapusk_dt_win.html` (раздел запуска)
