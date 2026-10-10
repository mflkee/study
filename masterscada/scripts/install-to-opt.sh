#!/usr/bin/env bash
# Установка MasterSCADA 4D DT 2.0 в /opt из .deb (Debian-пакет на Arch ставим вручную).
# Запускать от root (нужен доступ к /opt, /usr/share):
#
#   sudo scripts/install-to-opt.sh /path/MasterSCADA4D_2.0.3_Install.deb
#
# После установки запуск: scripts/run-masterscada.sh
set -euo pipefail

DEB="${1:-}"
if [[ -z "$DEB" || ! -f "$DEB" ]]; then
  echo "Использование: sudo $0 /path/MasterSCADA4D_2.0.X_Install.deb" >&2
  exit 1
fi
if [[ $EUID -ne 0 ]]; then
  echo "Нужны права root. Запусти через sudo." >&2
  exit 1
fi

DEST="/opt/MasterSCADA 4D 2.0"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo ">> распаковка $DEB"
ar p "$DEB" data.tar.xz | tar -xJ -C "$TMP"

echo ">> установка в $DEST"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a "$TMP/opt/MasterSCADA 4D 2.0/." "$DEST/"

echo ">> .desktop и иконка"
install -Dm644 "$TMP/usr/share/applications/MasterSCADA4D_2.0.desktop" \
  /usr/share/applications/MasterSCADA4D_2.0.desktop
[[ -f "$DEST/icon.png" ]] && install -Dm644 "$DEST/icon.png" /usr/share/pixmaps/MasterSCADA4D_2.0.png
update-desktop-database /usr/share/applications 2>/dev/null || true

# Песочница Chromium: либо setuid-root, либо запуск с --no-sandbox (наш скрипт уже с флагом).
chown root:root "$DEST/chrome-sandbox" 2>/dev/null || true
chmod 4755 "$DEST/chrome-sandbox" 2>/dev/null || true

echo ">> готово. Запуск:  $DEST/MasterSCADA4D_2.0 --no-sandbox --ozone-platform-hint=auto"
echo ">> или скрипт:      $(cd "$(dirname "$0")" && pwd)/run-masterscada.sh"
