#!/usr/bin/env bash
# Langzeit-Test im Android-Emulator mit ECHTEN freien Sendern (iptv-org, vorab geprüft von langzeit_liste.py).
# Ergänzt den normalen Emulator-Test (künstliche Test-Sender, simulierte Abbrüche): hier laufen echte Server, echte
# Videos und lange Laufzeiten – Speicher, Abstürze, Wächter-Fehlalarme, Lücken und Umschaltzeiten je Sender.
#
# Ablauf (Faktor 1 ≈ 85 min):
#   A  Dauerlauf: erster Sender 25 min
#   B  Durchzappen: jeder Sender 45 s
#   C  Schnell zappen: 3 × 12 Tastendrücke im 0,8-s-Takt, danach je 40 s laufen lassen
#   D  Sender länger: 4 Sender je 8 min
#   E  Zappen im 10-s-Takt: 2 Runden durch alle Sender
#   F  Schlusslauf: 10 min
#   G  Wackelige Leitung: 10 Runden – 30 s volles Netz, 15 s gedrosselt (300 kbit/s, weniger als der Sender braucht),
#      jede dritte Runde stattdessen 8 s fast kein Netz. So entstehen echte Bildlücken wie bei schwachem WLAN/Anbieter:
#      zeigt, wie lang die Lücken sind, ob der schnelle Wiederanlauf greift und ob der Wächter unnötig eingreift.
#   H  Erholung: 3 min volles Netz – Bild muss normal weiterlaufen
#   I  Hintergrund: 3× Home-Taste, 60 s warten, zurück – das Bild muss wiederkommen (App räumt im Hintergrund auf)
# Es werden KEINE Bildschirmfotos gemacht (fremde Sendungen), nur Text-Protokolle.
#
# Aufruf: run_langzeit_test.sh <qa.apk> <Ausgabeordner> [Faktor] [Anzahl Sender]
set -uo pipefail
APK="$1"
OUT="${2:-langzeit-out}"
F="${3:-1}"
N="${4:-12}"
PKG=de.aze.iptv
mkdir -p "$OUT"
PHASE="Start"
CRASH=0
PID0=""

log() { echo "[$(date +%H:%M:%S)] $*"; }
# Markierung ins App-Protokoll (gleicher Kanal wie die App-Meldungen → richtige Reihenfolge in der Auswertung)
mark() { adb shell "log -t AzeQA -p i '$*'" >/dev/null 2>&1; log "$*"; }
secs() { awk -v s="$1" -v f="$F" 'BEGIN { printf "%d", s * f }'; }

# App-Protokoll und Absturz-Protokoll laufend in Dateien schreiben (der Logcat-Puffer im Gerät reicht nicht für Stunden)
start_logcat() {
  adb logcat -v threadtime -s AzeQA:I >> "$OUT/azeqa.log" 2>/dev/null &
  LC=$!
  adb logcat -v threadtime -b crash >> "$OUT/crash.log" 2>/dev/null &
  LCC=$!
  # „App reagiert nicht“ (ANR), Abstürze und Beenden wegen Speichermangel (System-Ereignisse)
  adb logcat -v threadtime -b events am_anr:I am_crash:I am_kill:I am_low_memory:I '*:S' >> "$OUT/ereignisse.log" 2>/dev/null &
  LCE=$!
}
logcat_alive() {
  kill -0 "$LC" 2>/dev/null || { log "Protokoll-Mitschnitt neu gestartet"; adb logcat -T 1 -v threadtime -s AzeQA:I >> "$OUT/azeqa.log" 2>/dev/null & LC=$!; }
}

# Lebenszeichen + Speicher (alle 30 s)
check() {
  logcat_alive
  local p kb
  p=$(adb shell pidof $PKG 2>/dev/null | tr -d '\r')
  if [ -z "$p" ]; then
    CRASH=$((CRASH+1)); mark "LZ_ABSTURZ App nicht mehr da (Phase $PHASE)"
    adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1; sleep 15; PID0=""
    return
  fi
  if [ -n "$PID0" ] && [ "$p" != "$PID0" ]; then CRASH=$((CRASH+1)); mark "LZ_NEUSTART Prozess $PID0 -> $p (Phase $PHASE)"; fi
  PID0="$p"
  kb=$(adb shell dumpsys meminfo $PKG 2>/dev/null | tr -d '\r' | awk '/TOTAL PSS:/ { print $3; exit } /^ *TOTAL +[0-9]/ { print $2; exit }')
  echo "$(date +%s) $PHASE ${kb:-0}" >> "$OUT/speicher.txt"
}
# $1 Sekunden laufen lassen, dabei alle 30 s prüfen
dwell() {
  local s=$1 t=0 d
  while [ "$t" -lt "$s" ]; do
    d=$(( s - t < 30 ? s - t : 30 )); sleep "$d"; t=$((t + d)); check
  done
}
phase() { PHASE="$1"; mark "LZ_PHASE $1"; }
zap() { adb shell input keyevent KEYCODE_CHANNEL_UP; }
first_frame_seen() { grep -q " FIRST_FRAME " "$OUT/azeqa.log" 2>/dev/null; }

# Bildschirm wie ein Fernseher (wie im normalen Test)
adb shell wm size 1920x1080 || true
adb shell wm density 320 || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
log "Installiere $APK"
adb install -r "$APK" || { echo "::error title=Langzeit-Test::Installation fehlgeschlagen"; exit 1; }
adb logcat -c
: > "$OUT/azeqa.log"; : > "$OUT/crash.log"; : > "$OUT/speicher.txt"; : > "$OUT/ereignisse.log"
start_logcat
mark "LZ_START Faktor $F, $N Sender"
adb shell am start -W -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8091/list.m3u"

# erstes Bild abwarten (Liste laden + erster Sender)
for i in $(seq 1 180); do first_frame_seen && break; sleep 1; done
if ! first_frame_seen; then
  echo "::error title=Langzeit-Test::kein erstes Bild nach 3 Minuten – Abbruch"
  tail -40 "$OUT/azeqa.log"
  kill "$LC" "$LCC" "$LCE" 2>/dev/null; exit 1
fi
log "erstes Bild ist da"
check

phase "A_Dauerlauf"
dwell "$(secs 1500)"

phase "B_Durchzappen_45s"
for i in $(seq 1 "$N"); do zap; dwell "$(secs 45)"; done

phase "C_Schnellzappen"
for b in 1 2 3; do
  for i in $(seq 1 12); do zap; sleep 0.8; done
  dwell 40
done

phase "D_Sender_je_8min"
for i in 1 2 3 4; do zap; dwell "$(secs 480)"; done

phase "E_Zappen_10s"
for r in 1 2; do
  for i in $(seq 1 "$N"); do zap; sleep 10; done
  check
done

phase "F_Schlusslauf"
zap
dwell "$(secs 600)"

# Netz des Emulators (Konsole): Geschwindigkeit in kbit/s, „full“ = ungebremst
net() { local r; r=$(adb emu network speed "$1" 2>&1 | tr -d '\r' | head -2 | tr '\n' ' '); mark "LZ_NETZ $1"; log "Netz $1: $r"; }
trap 'adb emu network speed full >/dev/null 2>&1' EXIT

phase "G_Wackelige_Leitung"
for r in $(seq 1 10); do
  dwell 30
  if [ $((r % 3)) -eq 0 ]; then net 1; sleep 8; else net 300; sleep 15; fi
  net full
done

phase "H_Erholung"
dwell "$(secs 180)"

phase "I_Hintergrund"
for r in 1 2 3; do
  ff=$(grep -c " FIRST_FRAME " "$OUT/azeqa.log")
  adb shell input keyevent KEYCODE_HOME; dwell 60
  adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1
  ok=0; for i in $(seq 1 30); do [ "$(grep -c " FIRST_FRAME " "$OUT/azeqa.log")" -gt "$ff" ] && { ok=1; break; }; sleep 1; done
  [ $ok -eq 1 ] && log "zurück aus dem Hintergrund: Bild nach ${i} s" || mark "LZ_FEHLER kein Bild 30 s nach Rückkehr aus dem Hintergrund (Runde $r)"
  dwell 30
done

phase "ENDE"
sleep 3
kill "$LC" "$LCC" "$LCE" 2>/dev/null || true
log "Langzeit-Test fertig: $(wc -l < "$OUT/azeqa.log") Protokollzeilen, Abstürze/Neustarts: $CRASH"
echo "$CRASH" > "$OUT/abstuerze.txt"
exit 0
