#!/usr/bin/env bash
# Wgrywa właściwy APK na każde podłączone urządzenie (po modelu).
# Użycie:
#   ./dev-install.sh                  # wgraj na to, co połączone
#   ./dev-install.sh <IP>:<PORT>      # najpierw połącz zegarek po WiFi (adb), potem wgraj
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
WEAR_APK="$ROOT/wear/build/outputs/apk/debug/wear-debug.apk"

if [ $# -ge 1 ]; then
    echo "» łączę z zegarkiem $1"
    "$ADB" connect "$1" || true
fi

SERIALS=()
while IFS= read -r line; do
    [ -n "$line" ] && SERIALS+=("$line")
done < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [ ${#SERIALS[@]} -eq 0 ]; then
    echo "Brak podłączonych urządzeń. Wepnij telefon (USB) lub połącz zegarek: ./dev-install.sh <ip:port>"
    exit 1
fi

for s in "${SERIALS[@]}"; do
    model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    if echo "$model" | grep -qi "L335\|Watch"; then
        echo "» ZEGAREK ($model / $s) ← wear-debug.apk"
        "$ADB" -s "$s" install -r "$WEAR_APK" | tail -1
        "$ADB" -s "$s" shell am start -n com.akond.ecoflowpv/com.akond.ecoflowpv.wear.WearMainActivity >/dev/null 2>&1 || true
    else
        echo "» TELEFON ($model / $s) ← app-debug.apk"
        "$ADB" -s "$s" install -r "$APP_APK" | tail -1
    fi
done
echo "Gotowe."
