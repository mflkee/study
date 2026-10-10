#!/usr/bin/env bash
# Запуск среды разработки MasterSCADA 4D DT 2.0 на Arch Linux + Wayland (niri).
#
# Почему флаги:
#   --no-sandbox            : chrome-sandbox в deb не setuid-root; без флага Electron падает.
#   --ozone-platform-hint=auto : Chromium 120 сам выберет нативный Wayland (ozone) вместо XWayland.
#
# Проверено 2026-10-10: окно редактора + диалог «Создание проекта» открываются
# нативно (renderer/gpu-process идут с --ozone-platform=wayland).
set -euo pipefail

APP_DIR="${MASTERSCADA_DIR:-/opt/MasterSCADA 4D 2.0}"
BIN="$APP_DIR/MasterSCADA4D_2.0"

if [[ ! -x "$BIN" ]]; then
  echo "Не найден бинарь: $BIN" >&2
  echo "Установи пакет:  sudo apt-get install /path/MasterSCADA4D_2.0.4_Install.deb" >&2
  echo "или запусти распакованную копию через MASTERSCADA_DIR=..." >&2
  exit 1
fi

cd "$APP_DIR"
exec "$BIN" --no-sandbox --ozone-platform-hint=auto "$@"
