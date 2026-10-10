#!/usr/bin/env bash
# После старта RT-эмуляции: выровнять fastcgi-порт в nginx-конфиге под фактический порт mplc
# (в сгенерированном conf upstream fcgi_backend жёстко = 30750, а mplc слушает /fastcgi:<PORT>),
# затем перезагрузить nginx_imit. Иначе веб-клиент висит на «Подключение к серверу».
#
# Запускать ПОСЛЕ появления процессов mplc и nginx_imit (т.е. после «Подключиться в режиме эмуляции»).
set -euo pipefail

BASE="$HOME/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/ProjectsServiceData"

# фактический fastcgi-порт mplc
PORT="$(pgrep -af 'mplc|MasterPLC' 2>/dev/null | grep -oE '/fastcgi:[0-9]+' | head -1 | cut -d: -f2 || true)"
if [[ -z "${PORT:-}" ]]; then
  echo "Не найден процесс mplc с /fastcgi:<port> — сначала запусти эмуляцию." >&2; exit 1
fi
echo "Фактический fastcgi-порт mplc: $PORT"

# патчим все сгенерированные imit-конфиги
found=0
while IFS= read -r f; do
  sed -i -E "s#(upstream fcgi_backend \{ server 127\.0\.0\.1:)[0-9]+#\1$PORT#" "$f"
  echo "  обновлён: $f"; found=1
done < <(grep -rl 'upstream fcgi_backend' "$BASE" 2>/dev/null)
[[ $found -eq 1 ]] || { echo "Не нашёл nginx-конфигов под $BASE" >&2; exit 1; }

# reload nginx_imit (prefix = cwd мастер-процесса)
NPID="$(pgrep -f 'nginx_imit: master|nginx: master' 2>/dev/null | head -1 || true)"
[[ -n "${NPID:-}" ]] || NPID="$(pgrep -x nginx_imit 2>/dev/null | head -1 || true)"
if [[ -n "${NPID:-}" ]]; then
  PREF="$(readlink -f "/proc/$NPID/cwd" 2>/dev/null || true)"
  BIN="/opt/MasterSCADA 4D 2.0/resources/MasterSCADA4D_Server/Config/MasterPLC/linux/nginx/sbin/nginx_imit"
  [[ -n "$PREF" && -x "$BIN" ]] && "$BIN" -p "$PREF/" -c conf/nginx-mplc-imit.conf -s reload && echo "nginx_imit перезагружен ($PREF)"
fi
echo "Готово. Проверка: curl -s -o /dev/null -w '%{http_code}\\n' -X POST http://127.0.0.1:8045/Methods/GetState"
