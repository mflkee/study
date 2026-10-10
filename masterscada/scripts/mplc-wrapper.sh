#!/bin/sh
# Обёртка над mplc.real для запуска MasterPLC RT на Arch (ОС не поддерживается вендором).
# Раскладывается в MasterPLC/linux/mplc (оригинал — рядом, mplc.real).
#  1) mplc несёт .NET 8.0.12, который НЕ понимает ICU 78 из Arch → падает с
#     "Couldn't find a valid ICU package" → IDE показывает это как "Not permited".
#     Лечим инвариантной глобализацией (только для mplc, чтобы не задеть IDE-сервер).
#  2) у mplc RPATH=./ , поэтому opcua.so/masterplc.so/mplcshare.so находятся
#     только если cwd = этот каталог; добавим его в LD_LIBRARY_PATH.
DIR="$(cd "$(dirname "$0")" && pwd)"
export LD_LIBRARY_PATH="$DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1
exec "$DIR/mplc.real" "$@"
