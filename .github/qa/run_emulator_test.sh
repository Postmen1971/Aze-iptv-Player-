#!/usr/bin/env bash
# Automatischer Test im Android-Emulator: App starten, Test-Sender abspielen, Hänger simulieren.
# Prüft: App stürzt nicht ab, Bild kommt, Wächter startet hängende Sender neu und das Bild kommt wieder,
# und ein normal laufender Sender wird NICHT unnötig neu gestartet.
set -uo pipefail
APK="$1"
OUT="${2:-qa-out}"
PKG=de.aze.iptv
mkdir -p "$OUT"
FAILS=0
STEP=0

log() { echo "[$(date +%H:%M:%S)] $*"; }
qa() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F "AzeQA" | grep -v " TICK " ; }
ticks() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F " TICK " ; }
count() { qa | grep -cF -- "$1" || true; }
shot() { STEP=$((STEP+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $STEP)-$1.png" 2>/dev/null || true; }
alive() { [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; }
fail() { log "FEHLER: $*"; echo "::error title=Emulator-Test::$*"; FAILS=$((FAILS+1)); shot "fehler"; }
# Mehrzeiligen Text als einen Hinweis am Lauf zeigen (Logs/Artefakte sind von außen nicht immer abrufbar)
note() { local t="$1"; shift; local m; m=$(printf '%s' "$*" | tail -c 30000 | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g'); echo "::notice title=$t::$m"; }

# wartet, bis $1 mindestens $2-mal im Protokoll steht (max. $3 s)
wait_for() {
  local pat="$1" n="$2" t="$3" i=0
  while [ $i -lt "$t" ]; do
    [ "$(count "$pat")" -ge "$n" ] && { log "ok: '$pat' ($n×) nach ${i}s"; return 0; }
    alive || { fail "App läuft nicht mehr (Absturz?) beim Warten auf '$pat'"; return 1; }
    sleep 1; i=$((i+1))
  done
  fail "'$pat' kam nicht ${n}× innerhalb ${t}s"
  return 1
}

channel() {  # Sender per Zifferntaste wählen
  adb shell input keyevent "KEYCODE_$1"; sleep 0.3; adb shell input keyevent KEYCODE_DPAD_CENTER
}

log "Installiere $APK"
adb install -r "$APK" || { echo "::error::Installation fehlgeschlagen"; exit 1; }
adb logcat -c
adb shell am start -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/list.m3u"

log "== Test 1: normaler Sender startet und läuft ruhig"
wait_for "FIRST_FRAME Test 1" 1 120 && shot "test1-bild"
sleep 45
w=$(count "WATCHDOG"); [ "$w" -eq 0 ] || fail "Wächter hat einen normal laufenden Sender neu gestartet ($w×)"
alive || fail "App nach Test 1 nicht mehr da"
shot "test1-nach-45s"

log "== Test 2: Server schickt keine Daten mehr (tote Verbindung)"
channel 2
wait_for "FIRST_FRAME Test 2" 1 60
wait_for "WATCHDOG starved Test 2" 1 50
wait_for "FIRST_FRAME Test 2" 2 40 && shot "test2-wieder-da"

log "== Test 3: Bild bleibt stehen, Ton läuft weiter (Bild hört 15 s nach Start auf)"
w=$(count "WATCHDOG")
channel 3
wait_for "FIRST_FRAME Test 3" 1 60
# spätestens ~15 s Bild + 12 s Erkennung → Neustart
wait_for "WATCHDOG" $((w+1)) 35
wait_for "FIRST_FRAME Test 3" 2 30 && shot "test3-wieder-da"
note "Zustand Test 3" "$(ticks | tail -45)"

log "== Test 4: Server trennt die Verbindung"
channel 4
wait_for "FIRST_FRAME Test 4" 1 60
wait_for "FIRST_FRAME Test 4" 2 60 && shot "test4-wieder-da"

log "== Test 5: schnelles Zappen (20× hoch/runter), danach muss Test 1 laufen"
for i in $(seq 1 10); do adb shell input keyevent KEYCODE_DPAD_UP; adb shell input keyevent KEYCODE_DPAD_DOWN; done
sleep 3
n=$(count "FIRST_FRAME Test 1")
channel 1
wait_for "FIRST_FRAME Test 1" $((n+1)) 60 && shot "test5-nach-zappen"
sleep 20
alive || fail "App nach dem Zappen nicht mehr da"

log "== Test 6: Lippensynchron +1500 ms (Ton später) – Bild darf nicht stehen bleiben"
adb shell am force-stop $PKG
sleep 2
w=$(count "WATCHDOG")
n=$(count "FIRST_FRAME Test 1")
adb shell am start -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/list.m3u" --ei aze_test_audio_delay 1500
wait_for "FIRST_FRAME Test 1" $((n+1)) 90
sleep 40
w2=$(count "WATCHDOG")
[ "$w2" -eq "$w" ] || fail "Lippensynchron: Wächter musste $((w2-w))× neu starten (Bild blieb stehen)"
# in den letzten 10 Sekunden müssen laufend Bilder kommen
moving=$(ticks | tail -10 | grep -cE 'img=([5-9]|[1-9][0-9])' || true)
[ "$moving" -ge 8 ] || fail "Lippensynchron: Bild steht (nur $moving von 10 Sekunden mit Bildern)"
note "Zustand Test 6" "$(ticks | tail -25)"
shot "test6-lippensynchron"
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity --ei aze_test_audio_delay 0
sleep 5

log "== Absturz-Prüfung"
alive || fail "App ist am Ende nicht mehr da"
if adb logcat -d -b crash | grep -q "$PKG"; then fail "Absturz im Protokoll gefunden"; fi

adb logcat -d > "$OUT/logcat.txt"
qa > "$OUT/azeqa.txt"; ticks > "$OUT/ticks.txt"
log "Protokoll (AzeQA):"; cat "$OUT/azeqa.txt"
note "AzeQA-Protokoll" "$(cat "$OUT/azeqa.txt")"
note "App-Fehler im Protokoll" "$(grep -E 'AndroidRuntime|FATAL|de\.aze|ExoPlayerImpl|MediaCodec.*(rror|xception)' "$OUT/logcat.txt" | grep -v 'AzeQA' | tail -60)"
if [ $FAILS -gt 0 ]; then log "ERGEBNIS: $FAILS Fehler"; exit 1; fi
log "ERGEBNIS: alle Tests bestanden"
echo "::notice title=Emulator-Test::Alle Tests bestanden"
