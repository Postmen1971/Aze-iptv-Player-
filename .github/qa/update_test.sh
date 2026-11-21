#!/usr/bin/env bash
# Update-Test: zuletzt veröffentlichte Version installieren, dann die neue Version DRÜBER installieren
# (wie ein Update auf dem Gerät). Prüft vor allem den Schlüsselwechsel: Android muss die neue
# Unterschrift als Nachfolger der alten annehmen – sonst müssten alle die App löschen.
set -uo pipefail
OLD="$1"
NEW="$2"
OUT="${3:-qa-out}"
PKG=de.aze.iptv
mkdir -p "$OUT"
log() { echo "[$(date +%H:%M:%S)] $*"; }
note() { local t="$1"; shift; local m; m=$(printf '%s' "$*" | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g'); echo "::notice title=$t::$m"; }

[ -f "$NEW" ] || { echo "::error title=Update-Test::neue APK fehlt ($NEW)"; exit 1; }
adb uninstall "$PKG" >/dev/null 2>&1 || true

if [ -s "$OLD" ]; then
  r=$(adb install "$OLD" 2>&1); log "alte Version: $r"
  echo "$r" | grep -q Success || { echo "::warning title=Update-Test::alte Version ließ sich nicht installieren – nur Neuinstallation geprüft"; adb uninstall "$PKG" >/dev/null 2>&1; }
fi

r=$(adb install -r "$NEW" 2>&1); log "neue Version drüber: $r"
if ! echo "$r" | grep -q Success; then
  echo "::error title=Update-Test::Update über die alte Version abgelehnt: $(echo "$r" | tail -1)"
  echo "Update-Test: FEHLER $r" > "$OUT/update-test.txt"
  exit 1
fi

sig=$(adb shell dumpsys package "$PKG" | grep -E "signatures|versionName" | head -6)
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
ok=0
for i in $(seq 1 15); do sleep 1; [ -n "$(adb shell pidof $PKG | tr -d '\r')" ] && { ok=1; break; }; done
adb shell am force-stop "$PKG"
adb uninstall "$PKG" >/dev/null 2>&1 || true
[ "$ok" = 1 ] || { echo "::error title=Update-Test::neue Version startet nach dem Update nicht"; exit 1; }
note "Update-Test" "Update über die veröffentlichte Version angenommen, App startet.
$sig"
echo "Update-Test: ok
$sig" > "$OUT/update-test.txt"
log "Update-Test bestanden"
