#!/usr/bin/env bash
# Запуск среды разработки MasterSCADA 4D 2.0 на Linux (в т.ч. Arch + niri/Wayland).
#
# Режимы (переменная MASTERSCADA_MODE):
#   x11     (по умолчанию) — окно через XWayland: полный функционал (ПКМ-меню, горячие
#                            клавиши, диалоги) — по сути как на Astra Linux, где сессия X.Org;
#   wayland                — нативное окно Wayland (раскладку даёт композитор, окно без рамки).
#
# ПОЧЕМУ X11 ПО УМОЛЧАНИЮ (проверено 2026-10-12, Arch + niri, RC 2.0.4):
#   В нативном Wayland основное окно работает, но **контекстное меню (ПКМ) ломается**:
#   Electron/GTK рисует меню отдельным X11-окном (`override_redirect: true`,
#   `has_transient_for: false`, без WM_CLASS и заголовка). xwayland-satellite не может
#   привязать такое «сиротское» окно к родителю и монтирует его как обычное окно —
#   niri на секунду вставляет его в раскладку (`pos_in_scrolling_layout: (4,1)`,
#   `tile_size: 1896x1022`), главное окно дёргается, а меню закрывается, не получив фокуса
#   (`No last focused toplevel, cannot focus window`). Форсирование `GDK_BACKEND=wayland`
#   не помогает. В X11-режиме окно и меню живут в одном X11-клиенте, попап привязывается
#   к своему toplevel — всё работает штатно.
#
# Прочие проверенные подробности:
#   --no-sandbox  : chrome-sandbox в .deb не setuid-root, без флага Electron падает.
#   Раскладка     : X-серверу (XWayland) нужен свой setxkbmap — см. docs/reference/ustanovka-arch-niri.md (грабля 7).
#   Перезапуск    : не должно оставаться процессов прежней копии, иначе новая висит на заставке
#                   (старый .NET-сервер держит порт 44000): pkill -f '^/opt/MasterSCADA 4D' && sleep 3
set -euo pipefail

# Каталог установки: RC-сборка или DT-пакет (можно задать явно через MASTERSCADA_DIR).
if [[ -n "${MASTERSCADA_DIR:-}" ]]; then
  APP_DIR="$MASTERSCADA_DIR"
elif [[ -x "/opt/MasterSCADA 4D RC/MasterSCADA4D_2.0_RC" ]]; then
  APP_DIR="/opt/MasterSCADA 4D RC"
elif [[ -x "/opt/MasterSCADA 4D 2.0/MasterSCADA4D_2.0" ]]; then
  APP_DIR="/opt/MasterSCADA 4D 2.0"
else
  cat >&2 <<'MSG'
Не найден бинарь среды разработки MasterSCADA 4D.
Укажи каталог установки:  MASTERSCADA_DIR="/opt/..." scripts/run-masterscada.sh
MSG
  exit 1
fi

if [[ -x "$APP_DIR/MasterSCADA4D_2.0_RC" ]]; then
  BIN="$APP_DIR/MasterSCADA4D_2.0_RC"
elif [[ -x "$APP_DIR/MasterSCADA4D_2.0" ]]; then
  BIN="$APP_DIR/MasterSCADA4D_2.0"
else
  echo "В каталоге $APP_DIR нет исполняемого файла среды разработки" >&2
  exit 1
fi

: "${MASTERSCADA_MODE:=x11}"
cd "$APP_DIR"

case "$MASTERSCADA_MODE" in
  wayland)
    export GDK_BACKEND=wayland
    exec "$BIN" --no-sandbox --ozone-platform-hint=auto "$@"
    ;;
  *)
    exec "$BIN" --no-sandbox "$@"
    ;;
esac
