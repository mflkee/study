#!/usr/bin/env bash
# Запуск RT-эмуляции MasterSCADA 4D в среде разработки (DT) на Linux.
#
# Обнаружено (2026-10-10): в DT-сборке среда разработки НЕ поднимает IPC-сервер
# сама — она ждёт уже работающий (в норме его запускает установленная RT-среда).
# Без него при «Подключиться в режиме эмуляции» падает с "Not permited".
# Этот скрипт поднимает IPC-сервер в фоне (сокет в /tmp/CoreFxPipe_...).
#
# Запускать ПОСЛЕ старта среды разработки (scripts/run-masterscada.sh) и ДО
# нажатия «Исполнение → Подключиться в режиме эмуляции».
set -euo pipefail

APP_DIR="${MASTERSCADA_DIR:-/opt/MasterSCADA 4D 2.0}"
IPC_DIR="$APP_DIR/resources/MasterSCADA4D_Server/ipc_server"
PIPE_NAME="MasterSCADA.Controllers.IPCServer"
SOCK="/tmp/CoreFxPipe_$PIPE_NAME"

if [[ ! -x "$IPC_DIR/MasterSCADA.Controllers.IPCServer" ]]; then
  echo "Не найден IPC-сервер: $IPC_DIR/MasterSCADA.Controllers.IPCServer" >&2; exit 1
fi

if [[ -S "$SOCK" ]] && pgrep -x MasterSCADA.Con >/dev/null 2>&1; then
  echo "IPC-сервер уже работает ($SOCK)"; exit 0
fi

cd "$IPC_DIR"
setsid nohup ./MasterSCADA.Controllers.IPCServer "$PIPE_NAME" \
  >/tmp/opencode-ipcserver.log 2>&1 </dev/null &
sleep 2
[[ -S "$SOCK" ]] && echo "IPC-сервер запущен: $SOCK" || { echo "Не удалось поднять IPC-сервер" >&2; exit 1; }
