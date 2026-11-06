#!/usr/bin/env bash
# Erklär-Videos aufnehmen: der Emulator bedient die echte App, screenrecord filmt, ffmpeg blendet Texte ein.
# Ergebnis: help-out/hilfe-playlist.mp4, hilfe-senderliste.mp4, hilfe-fussball.mp4
set -u
PKG=de.aze.iptv
APK="${1:-qa/aze-qa-x86_64.apk}"
OUT=help-out
mkdir -p "$OUT" raw caps
FONT=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf

log() { echo "[$(date +%H:%M:%S)] $*"; }
k() { adb shell input keyevent "$1"; sleep "${2:-1.3}"; }
qa() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F "AzeQA"; }

T0=0
CAPS=""
rec() {   # rec <name>: Aufnahme starten
  NAME=$1; CAPS="caps/$NAME.txt"; : > "$CAPS"
  adb shell rm -f /sdcard/rec.mp4
  adb shell screenrecord --size 1280x720 --bit-rate 6000000 --time-limit 175 /sdcard/rec.mp4 &
  sleep 1.5
  T0=$(date +%s.%N)
}
cap() {   # cap "Text": ab jetzt diesen Text unten einblenden
  local t; t=$(awk -v a="$(date +%s.%N)" -v b="$T0" 'BEGIN{printf "%.2f", a-b}')
  echo "$t|$1" >> "$CAPS"
}
stop() {
  sleep 2
  local t; t=$(awk -v a="$(date +%s.%N)" -v b="$T0" 'BEGIN{printf "%.2f", a-b}')
  echo "$t|" >> "$CAPS"
  adb shell pkill -INT screenrecord || true
  sleep 4
  adb pull /sdcard/rec.mp4 "raw/$NAME.mp4" >/dev/null
  log "aufgenommen: $NAME ($t s)"
}

# Texte einbrennen (jede Zeile ab ihrer Zeit bis zur nächsten)
burn() {
  local name=$1 extra=${2:-}
  local f="caps/$name.txt" filters="" i=0 prev_t="" prev_txt=""
  while IFS='|' read -r t txt; do
    if [ -n "$prev_t" ] && [ -n "$prev_txt" ]; then
      i=$((i+1)); printf '%s' "$prev_txt" | fold -s -w 58 > "caps/$name-$i.txt"
      filters="$filters,drawtext=fontfile=$FONT:textfile=caps/$name-$i.txt:fontsize=28:line_spacing=8:fontcolor=white:box=1:boxcolor=0x0b1530@0.88:boxborderw=22:x=(w-text_w)/2:y=h-text_h-48:enable='between(t,$prev_t,$t)'"
    fi
    prev_t=$t; prev_txt=$txt
  done < "$f"
  ffmpeg -loglevel error -y -i "raw/$name.mp4" $extra -vf "fps=30${filters}" \
    -c:v libx264 -preset veryfast -crf 24 -pix_fmt yuv420p -movflags +faststart -an "$OUT/$name.mp4"
  log "fertig: $OUT/$name.mp4"
}

adb shell wm density 320 || true
adb install -r "$APK" >/dev/null || { echo "::error::Installation fehlgeschlagen"; exit 1; }
adb forward tcp:8080 tcp:8080

# ───────────── Video 1: Playlist mit dem Handy eingeben ─────────────
adb shell pm clear $PKG >/dev/null
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 6
rec hilfe-playlist
cap "Beim ersten Start öffnen sich die Einstellungen: „Sender & Playlists“"
sleep 4
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 1.5     # links auf „Sender & Playlists“
k KEYCODE_DPAD_RIGHT 1.5; k KEYCODE_DPAD_DOWN 2   # rechts auf „Handy-Eingabe starten“
cap "„Handy-Eingabe starten“ mit OK drücken"
k KEYCODE_DPAD_CENTER 3
cap "QR-Code mit dem Handy scannen – oder die Adresse im Handy-Browser öffnen"
sleep 5
CODE=$(qa | grep -o "PHONE_CODE [0-9]*" | tail -1 | awk '{print $2}')
# Handy-Seite als Bild (für das Video): so sieht es auf dem Handy aus
google-chrome --headless=new --disable-gpu --hide-scrollbars --window-size=390,780 --screenshot=raw/phone.png "http://127.0.0.1:8080/?c=$CODE" >/dev/null 2>&1 || true
cap "Am Handy: Code vom Fernseher eingeben, Playlist-Link einfügen, „Senden“"
sleep 6
curl -s -o /dev/null -X POST "http://127.0.0.1:8080/add" \
  --data-urlencode "code=$CODE" --data-urlencode "type=m3u" --data-urlencode "name=Meine Playlist" \
  --data-urlencode "url=http://10.0.2.2:8090/demo.m3u" || true
cap "Fertig – die Playlist ist auf dem Fernseher angekommen"
sleep 6
k KEYCODE_BACK 3
cap "Zurück zum Fernsehen: die Sender laufen"
sleep 6
stop

# ───────────── Video 2: Senderliste bearbeiten und sortieren ─────────────
# falls die Handy-Eingabe im ersten Video nicht geklappt hat: Beispiel-Playlist direkt laden
adb shell am start -S -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/demo.m3u" >/dev/null
sleep 10
rec hilfe-senderliste
cap "Einstellungen öffnen (blaue Taste oder Menü) – Standard-PIN 0000"
k KEYCODE_PROG_BLUE 2
for d in 0 0 0 0; do k KEYCODE_$d 0.6; done
sleep 2
cap "Links „Senderliste & Favoriten“ wählen"
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 2
cap "„Senderliste bearbeiten öffnen“ mit OK"
k KEYCODE_DPAD_RIGHT 1.5; k KEYCODE_DPAD_DOWN 1.5; k KEYCODE_DPAD_CENTER 3
cap "Links die Kategorien, rechts ihre Sender"
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 2
cap "Verschieben: OK lang drücken, mit ▲▼ an die neue Stelle, OK legt ab"
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2
k KEYCODE_DPAD_UP; k KEYCODE_DPAD_UP 2
k KEYCODE_DPAD_CENTER 2
cap "▶ wechselt zu den Sendern dieser Kategorie"
k KEYCODE_DPAD_RIGHT 2; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 2
cap "OK blendet einen Sender aus – nochmal OK zeigt ihn wieder"
k KEYCODE_DPAD_CENTER 2.5; k KEYCODE_DPAD_CENTER 2.5
cap "Umbenennen: Menü-Taste. Fertig: Zurück"
sleep 4
k KEYCODE_BACK 2
stop

# ───────────── Video 3: Fußball heute im TV ─────────────
adb shell am start -S -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/demo.m3u" --ez aze_test_football true >/dev/null
sleep 12
rec hilfe-fussball
cap "Menü → „Fußball heute“: alle Spiele von heute mit den Sendern"
sleep 6
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 2
cap "Spiel wählen und OK: die passenden Sender aus deiner Liste"
k KEYCODE_DPAD_CENTER 4
cap "Durchblättern: das Bild rechts oben zeigt den Sender als Vorschau"
k KEYCODE_DPAD_DOWN 3; k KEYCODE_DPAD_DOWN 3
cap "OK auf den laufenden Sender: Vollbild – die App merkt sich den Sender"
sleep 5
k KEYCODE_BACK 2; k KEYCODE_BACK 2
stop

# Handy-Bild ins erste Video einblenden (rechts, während der Handy-Schritte)
if [ -s raw/phone.png ]; then
  PS=$(awk -F'|' '/Am Handy/ {print $1}' caps/hilfe-playlist.txt); PE=$(awk -F'|' '/Fertig/ {print $1}' caps/hilfe-playlist.txt)
  ffmpeg -loglevel error -y -i raw/hilfe-playlist.mp4 -i raw/phone.png \
    -filter_complex "[1]scale=-1:560[p];[0][p]overlay=W-w-40:40:enable='between(t,${PS:-0},${PE:-0})'" \
    -c:v libx264 -preset veryfast -crf 20 -an raw/hilfe-playlist-p.mp4 && mv raw/hilfe-playlist-p.mp4 raw/hilfe-playlist.mp4
fi
for n in hilfe-playlist hilfe-senderliste hilfe-fussball; do burn "$n"; done
ls -la "$OUT"
