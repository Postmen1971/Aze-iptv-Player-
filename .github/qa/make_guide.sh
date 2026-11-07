#!/usr/bin/env bash
# Bilder für die Anleitung (Hilfe-Seite): der Emulator bedient die echte App und macht Bildschirmfotos.
# Ergebnis: guide/img/*.jpg, danach baut build_guide.py daraus guide/index.html
set -u
PKG=de.aze.iptv
APK="${1:-qa/aze-qa-x86_64.apk}"
OUT=guide/img
mkdir -p "$OUT"
log() { echo "[$(date +%H:%M:%S)] $*"; }
k() { adb shell input keyevent "$1"; sleep "${2:-1.2}"; }
qa() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F "AzeQA"; }
shot() {  # shot <name>: Bildschirmfoto als JPG (1280 breit)
  adb exec-out screencap -p > "/tmp/g.png" 2>/dev/null
  ffmpeg -loglevel error -y -i /tmp/g.png -vf "scale=1280:-2" -q:v 4 "$OUT/$1.jpg" && log "Bild: $1"
}
start() { adb shell am start -S -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/demo.m3u" "$@" >/dev/null; sleep 10; }

adb shell wm density 240 || true   # wie ein Fernseher (1280 × 720 dp)
adb install -r "$APK" >/dev/null 2>&1 || true
adb forward tcp:8080 tcp:8080

# ── Erster Start: Einstellungen „Sender & Playlists“, Handy-Eingabe
adb shell pm clear $PKG >/dev/null; adb logcat -c
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 6
shot erster-start
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN 1.5
k KEYCODE_DPAD_RIGHT 2; k KEYCODE_DPAD_CENTER 3
shot handy-qr
CODE=$(qa | grep -o "PHONE_CODE [0-9]*" | tail -1 | awk '{print $2}')
google-chrome --headless=new --disable-gpu --hide-scrollbars --window-size=390,780 --screenshot=/tmp/phone.png "http://127.0.0.1:8080/?c=$CODE" >/dev/null 2>&1 \
  && ffmpeg -loglevel error -y -i /tmp/phone.png -q:v 4 "$OUT/handy-seite.jpg"
k KEYCODE_BACK 2

# ── Fernsehen: Bild mit Infoleiste, Senderliste, Gruppen, Menü
start
k KEYCODE_DPAD_UP 2.5; shot fernsehen
k KEYCODE_DPAD_LEFT 2; shot senderliste
k KEYCODE_DPAD_LEFT 2; shot gruppen
k KEYCODE_DPAD_LEFT 2; shot menue
# ▶ zurück: Menü → Gruppen → Sender → Liste zu (Zurück in den Gruppen würde die App beenden)
k KEYCODE_DPAD_RIGHT; k KEYCODE_DPAD_RIGHT; k KEYCODE_DPAD_RIGHT 2
k KEYCODE_PROG_GREEN 2; shot schnellmenue; k KEYCODE_BACK 2
k KEYCODE_PROG_YELLOW 2; shot suche; k KEYCODE_BACK 2

# ── Einstellungen: PIN und jeder Bereich (frisch gestartet)
start
k KEYCODE_PROG_BLUE 2; shot pin
for d in 0 0 0 0; do k KEYCODE_$d 0.5; done
sleep 2; shot einst-start
i=0
for n in suchen sender-playlists senderliste-favoriten filme-serien fussball bild-ton programm fernbedienung aussehen pin-kinderschutz sichern system hilfe ueber; do
  k KEYCODE_DPAD_DOWN 1.8; shot "einst-$n"
done
k KEYCODE_BACK 2

# ── Senderliste bearbeiten
start --ez aze_test_listeditor true
sleep 3; shot editor
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_RIGHT 1.5; shot editor-sender

# ── Fußball heute
start --ez aze_test_football true
sleep 6; shot fussball
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_CENTER 4; shot fussball-sender
k KEYCODE_BACK 2; k KEYCODE_BACK 2

# ── Multi-View
start --ez aze_test_multiview true
sleep 6; shot multiview
k KEYCODE_BACK 2

# ── Filme & Serien (Test-Anbieter)
adb shell am start -S -n $PKG/.MainActivity --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true >/dev/null
sleep 14; shot filme
k KEYCODE_DPAD_RIGHT 1.5; k KEYCODE_DPAD_CENTER 3; shot film-details
k KEYCODE_BACK 2

ls -la "$OUT"
