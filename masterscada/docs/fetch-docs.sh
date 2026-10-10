#!/usr/bin/env bash
# Офлайн-зеркало онлайн-справки MasterSCADA 4D 2.0 (WebHelp).
# Качает страницы + ресурсы (css/js/картинки) в docs/offline/. Требует wget.
#
# Использование:  bash docs/fetch-docs.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$SCRIPT_DIR/offline"
BASE="https://support.mps-soft.ru/ms4d_web/"

if ! command -v wget >/dev/null 2>&1; then
  echo "Нужен wget. Установи: sudo pacman -S wget" >&2
  exit 1
fi

mkdir -p "$OUT"
echo "Качаю справку в $OUT (это ~1050 страниц, может занять несколько минут)…"

wget \
  --recursive --mirror --page-requisites --adjust-extension --convert-links \
  --no-parent --restrict-file-names=windows \
  -e robots=off --timeout=30 --tries=3 \
  --directory-prefix="$OUT" \
  "$BASE"

echo
echo "Готово. Открыть локально:"
find "$OUT" -name 'index.html' -path '*ms4d_web*' | head -1
