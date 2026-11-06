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
qa() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F "AzeQA" | grep -v " TICK " | grep -v ": BEAT$" ; }
# größte Lücke im Herzschlag (Haupt-Thread) in Sekunden – je Prozess, ab Zeile „seit“ (Uhrzeit HH:MM:SS)
beat_gap() { adb logcat -d -s AzeQA:I 2>/dev/null | grep ": BEAT$" | awk -v since="${1:-00:00:00}" '
  { split($2,t,":"); s=t[1]*3600+t[2]*60+t[3]; if ($2 < since) next;
    if (($3 in last) && s-last[$3] > max) max=s-last[$3]; last[$3]=s }
  END { printf "%.1f", max+0 }'; }
# Alarm: Lücke über 1,5 s UND deutlich länger (+0,7 s) als beim normalen Zappen im selben Emulator
hang_check() { local g; g=$(beat_gap "$2"); log "Herzschlag $1: längste Lücke ${g} s (Vergleich Zappen: ${BASE_GAP:-0} s)"
  qa | grep "STALL" | tail -4 | while IFS= read -r l; do log "  $l"; done
  awk -v g="$g" -v b="${BASE_GAP:-0}" 'BEGIN{exit !(g>1.5 && g>b+0.7)}' && fail "$1: App hing ${g} s (Haupt-Thread blockiert)"; true; }
ticks() { adb logcat -d -s AzeQA:I 2>/dev/null | grep -F " TICK " ; }
count() { qa | grep -cF -- "$1" || true; }
shot() { STEP=$((STEP+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $STEP)-$1.png" 2>/dev/null || true; }
alive() { [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; }
FAILLIST=""
fail() { log "FEHLER: $*"; echo "::error title=Emulator-Test::$*"; FAILS=$((FAILS+1)); FAILLIST="$FAILLIST | $*"; shot "fehler"; }
# Mehrzeiligen Text als einen Hinweis am Lauf zeigen (Logs/Artefakte sind von außen nicht immer abrufbar)
note() { local t="$1"; shift; local m; m=$(printf '%s' "$*" | tail -c 30000 | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g'); echo "::notice title=$t::$m"; }

# wartet, bis $1 mindestens $2-mal im Protokoll steht (max. $3 s)
wait_for() {
  local pat="$1" n="$2" t="$3" i=0 dead=0
  while [ $i -lt "$t" ]; do
    [ "$(count "$pat")" -ge "$n" ] && { log "ok: '$pat' ($n×) nach ${i}s"; return 0; }
    # erst nach 3 Sekunden ohne laufende App als Absturz werten (direkt nach dem Start ist sie evtl. noch nicht da)
    if alive; then dead=0; else dead=$((dead+1)); fi
    [ "$dead" -ge 3 ] && { fail "App läuft nicht mehr (Absturz?) beim Warten auf '$pat'"; return 1; }
    sleep 1; i=$((i+1))
  done
  fail "'$pat' kam nicht ${n}× innerhalb ${t}s"
  return 1
}

# wartet, bis eines der Muster (Regex, mit | getrennt) im Protokoll steht (max. $2 s)
wait_re() {
  local i=0
  while [ $i -lt "$2" ]; do
    [ "$(qa | grep -cE -- "$1" || true)" -ge 1 ] && { log "ok: '$1' nach ${i}s"; return 0; }
    sleep 1; i=$((i+1))
  done
  fail "'$1' kam nicht innerhalb ${2}s"; return 1
}

# wartet bis zu $2 s auf ein NEUES Sekunden-Protokoll mit laufendem Bild
picture_back() {
  local base i=0
  base=$(ticks | wc -l)
  while [ $i -lt "$2" ]; do
    if ticks | tail -n +$((base+1)) | grep -q " img=[1-9]"; then log "ok: Bild läuft ($1) nach ${i}s"; return 0; fi
    sleep 1; i=$((i+1))
  done
  fail "Bild kam nicht wieder ($1)"; return 1
}

# längste Folge von Sekunden ohne neues Bild (nach dem ersten Bild) ab Protokollzeile $1
max_gap() {
  ticks | tail -n +$(($1+1)) | awk '{ if ($0 ~ / img=0 /) { if (seen) { run++; if (run>max) max=run } } else { seen=1; run=0 } } END { print max+0 }'
}

channel() {  # Sender per Zifferntaste wählen
  adb shell input keyevent "KEYCODE_$1"; sleep 0.3; adb shell input keyevent KEYCODE_DPAD_CENTER
}

log "Installiere $APK"
adb install -r "$APK" || { echo "::error::Installation fehlgeschlagen"; exit 1; }
adb logcat -G 16M 2>/dev/null || true
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/list.m3u"

log "== Test 1: normaler Sender startet und läuft ruhig"
wait_for "FIRST_FRAME Test 1" 1 120 && shot "test1-bild"
sleep 45
w=$(count "WATCHDOG"); [ "$w" -eq 0 ] || fail "Wächter hat einen normal laufenden Sender neu gestartet ($w×)"
alive || fail "App nach Test 1 nicht mehr da"
shot "test1-nach-45s"

log "== Test 1b: Senderliste – ▲ oben springt ans Ende, nie in die Gruppen-Kopfzeile"
adb shell input keyevent KEYCODE_BACK   # Zurück öffnet die Senderliste
sleep 3
for i in $(seq 1 30); do adb shell input keyevent KEYCODE_DPAD_UP; done
sleep 2
shot "test1b-liste-nach-hoch"
note "Senderliste ▲" "$(qa | grep -E "LIST_|ROOT_KEY" | head -40)"
[ "$(count "LIST_HEAD_FOCUS")" -eq 0 ] || fail "Senderliste: ▲ ist in der Gruppen-Kopfzeile gelandet"
[ "$(count "LIST_WRAP")" -ge 1 ] || fail "Senderliste: ▲ oben ist nicht ans Ende gesprungen"
o=$(count "LIST_OPEN")
adb shell input keyevent KEYCODE_DPAD_RIGHT   # Rechts schließt die Liste
sleep 3
[ "$(count "LIST_CLOSED")" -ge 1 ] || fail "Senderliste: ▶ hat die Liste nicht geschlossen"
[ "$(count "LIST_OPEN")" -eq "$o" ] || fail "Senderliste: ▶ hat die Liste geschlossen und gleich wieder geöffnet"
# ▶ öffnet die Liste (kurz), ▶ nochmal schließt sie wieder
c=$(count "LIST_CLOSED")
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 3
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 3
[ "$(count "LIST_CLOSED")" -gt "$c" ] || fail "Senderliste: ▶ ▶ geht nicht zurück zum laufenden Sender"
o=$(count "LIST_OPEN"); sleep 2
[ "$(count "LIST_OPEN")" -eq "$o" ] || fail "Senderliste: nach ▶ ▶ ging die Liste wieder auf"

log "== Test 2: Server schickt keine Daten mehr (tote Verbindung)"
channel 2
wait_for "FIRST_FRAME Test 2" 1 60
# tote Verbindung: entweder der Wächter greift oder (nach 10 s Lesezeit) wird nahtlos neu verbunden
wait_re "WATCHDOG starved Test 2|TRANSITION Test 2" 50
picture_back "Test 2" 30 && shot "test2-wieder-da"

log "== Test 3: Bild bleibt stehen, Ton läuft weiter (Bild hört 15 s nach Start auf)"
w=$(count "WATCHDOG")
channel 3
wait_for "FIRST_FRAME Test 3" 1 60
# spätestens ~15 s Bild + 12 s Erkennung → Neustart
wait_for "WATCHDOG" $((w+1)) 35
wait_for "FIRST_FRAME Test 3" 2 30 && shot "test3-wieder-da"
note "Zustand Test 3" "$(ticks | tail -45)"

log "== Test 4: Server trennt die Verbindung"
w=$(count "WATCHDOG")
channel 4
wait_for "FIRST_FRAME Test 4" 1 60
sleep 2
tb=$(ticks | wc -l)  # Lücken erst ab laufendem Bild zählen (nicht die Umschaltzeit)
# Server trennt nach 15 s: neue Verbindung muss SOFORT angehängt werden und nahtlos übernehmen
wait_for "REJOIN Test 4" 1 40
# Übergang kommt erst, wenn der alte Vorrat (bei 3× Lieferung ~30 s) abgespielt ist
wait_for "TRANSITION Test 4" 1 60 && shot "test4-uebergang"
picture_back "Test 4" 10
sleep 5
[ "$(count "ENDED Test 4")" -eq 0 ] || fail "Stream-Ende nicht nahtlos (Notlösung mit Neustart gebraucht)"
[ "$(count "WATCHDOG")" -eq "$w" ] || fail "Nach Stream-Ende musste der Wächter eingreifen"
g=$(max_gap "$tb")
note "Test 4 Übergang" "längste Lücke ohne Bild: ${g} s"
[ "$g" -le 1 ] || fail "Übergang nach Stream-Ende nicht flüssig: ${g} s ohne Bild"

log "== Test 4b: Zeitsprung im Stream (Bildzeit springt 60 s vor, Ton läuft weiter)"
channel 5
wait_for "FIRST_FRAME Test 5" 1 60
# 15 s normal, dann Zeitsprung → spätestens nach ~3 s Erkennung neu synchronisieren, Bild wieder da
# je nach Zeitpunkt meldet der Player „läuft“ (Zeitsprung) oder „puffert“ (keine verwertbaren Daten) – beides muss schnell greifen
wait_re "WATCHDOG (jump|starved) Test 5" 35
wait_for "FIRST_FRAME Test 5" 2 15 && shot "test4b-nach-zeitsprung"
note "Zustand Test 4b" "$(ticks | tail -25)"
channel 4
sleep 3

log "== Test 4c: Anbieter mit nur 1 Verbindung, trennt alle 20 s – 3 Minuten Dauerlauf"
w=$(count "WATCHDOG"); e=$(count "ENDED")
channel 6
wait_for "FIRST_FRAME Test 6" 1 60
sleep 2
tb=$(ticks | wc -l)  # Lücken erst ab laufendem Bild zählen (nicht die Umschaltzeit)
sleep 180
t=$(count "TRANSITION Test 6")
g=$(max_gap "$tb")
note "Test 4c Dauerlauf" "nahtlose Übergänge: $t · längste Lücke ohne Bild: ${g} s · abgelehnte Verbindungen werden schnell neu versucht"
[ "$t" -ge 4 ] || fail "Dauerlauf: zu wenige nahtlose Neuverbindungen ($t)"
[ "$g" -le 1 ] || fail "Dauerlauf: ${g} s ohne Bild"
[ "$(count "WATCHDOG")" -eq "$w" ] || fail "Dauerlauf: Wächter musste eingreifen"
[ "$(count "ENDED")" -eq "$e" ] || fail "Dauerlauf: Notlösung mit Neustart gebraucht"
shot "test4c-dauerlauf"

log "== Test 5: schnelles Zappen (20× hoch/runter), danach muss Test 1 laufen"
T5=$(adb shell date +%H:%M:%S | tr -d '\r')
for i in $(seq 1 10); do adb shell input keyevent KEYCODE_DPAD_UP; adb shell input keyevent KEYCODE_DPAD_DOWN; done
sleep 3
n=$(count "FIRST_FRAME Test 1")
channel 1
wait_for "FIRST_FRAME Test 1" $((n+1)) 60 && shot "test5-nach-zappen"
sleep 20
alive || fail "App nach dem Zappen nicht mehr da"

BASE_GAP=$(beat_gap "$T5"); log "Herzschlag beim normalen Zappen (Vergleichswert): ${BASE_GAP} s"
note "Herzschlag Zappen" "längste Lücke beim normalen Zappen: ${BASE_GAP} s"
log "== Test 5b: Umschaltzeit messen (10× zwischen zwei normalen Sendern)"
z0=$(count "ZAPTIME")
for i in $(seq 1 5); do
  channel 7; wait_for "ZAPTIME" $((z0+1)) 30 >/dev/null; z0=$(count "ZAPTIME"); sleep 4
  channel 1; wait_for "ZAPTIME" $((z0+1)) 30 >/dev/null; z0=$(count "ZAPTIME"); sleep 4
done
vals=$(qa | grep -o "ZAPTIME [0-9]*" | tail -10 | awk '{print $2}' | sort -n)
n=$(echo "$vals" | grep -c . || true)
if [ "$n" -ge 6 ]; then
  med=$(echo "$vals" | awk '{a[NR]=$1} END{print a[int((NR+1)/2)]}')
  mx=$(echo "$vals" | tail -1)
  base=$(cat .github/qa/zap_baseline.txt 2>/dev/null | tr -dc 0-9)
  # Grenze: höchstens 0,5 s im Median (Ziel ~0,3 s; der Emulator ist etwas langsamer als ein echtes Gerät)
  limit=500
  note "Umschaltzeit" "Median ${med} ms · langsamster ${mx} ms · Messungen: $(echo $vals | tr '\n' ' ') · Grenze ${limit} ms (Basis ${base:-keine})"
  [ "$med" -le "$limit" ] || fail "Umschalten zu langsam: Median ${med} ms (Grenze ${limit} ms)"
else
  fail "Umschaltzeit: zu wenige Messungen ($n)"
fi

log "== Test 6: Lippensynchron +3000 ms (2000 allgemein + 1000 Sender, Ton später) – Bild darf nicht stehen bleiben"
adb shell am force-stop $PKG
sleep 2
w=$(count "WATCHDOG")
n=$(count "FIRST_FRAME Test 1")
adb shell am start -W -n $PKG/.MainActivity --es aze_test_playlist "http://10.0.2.2:8090/list.m3u" --ei aze_test_audio_delay 2000 --es aze_test_channel_delay_url "http://10.0.2.2:8090/ok.ts" --ei aze_test_channel_delay 1000
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
adb shell am start -W -n $PKG/.MainActivity --ei aze_test_audio_delay 0 --es aze_test_channel_delay_url "http://10.0.2.2:8090/ok.ts" --ei aze_test_channel_delay 0
sleep 5

log "== Test 7: Zwischenspeicher leeren, während ein Sender läuft"
adb shell am force-stop $PKG
sleep 2
n=$(count "FIRST_FRAME")
adb shell am start -W -n $PKG/.MainActivity --ez aze_test_clear_cache true
wait_for "CACHE_CLEARED" 1 60
sleep 15
alive || fail "App nach dem Leeren des Zwischenspeichers nicht mehr da"
[ "$(count FIRST_FRAME)" -gt "$n" ] || fail "Nach dem Leeren kein Bild"
shot "test7-nach-leeren"

log "== Test 8: Multi-View mit zwei Sendern gleichzeitig"
adb shell am force-stop $PKG
sleep 2
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity --ez aze_test_multiview true
wait_for "MV_FIRST_FRAME 0" 1 90
wait_for "MV_FIRST_FRAME 1" 1 60
# Bild 2 = „Test 4 Abbruch“: Server beendet den Stream nach 15 s → Bild muss sich OHNE Wächter selbst erholen
wait_for "MV_REJOIN 1" 1 40
wait_for "MV_TRANSITION 1 Test 4" 1 60
sleep 5
[ "$(count "MV_ENDED 1")" -eq 0 ] || fail "Multi-View: Stream-Ende nicht nahtlos (Notlösung mit Neustart gebraucht)"
[ "$(count "MV_WATCHDOG 1")" -eq 0 ] || fail "Multi-View: nach Stream-Ende musste der Wächter eingreifen (Neuverbindung zu langsam)"
# Bild 2 wählen und über die Senderauswahl (OK → Gruppe → Sender) einen anderen Sender setzen
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 1
n=$(count "MV_FIRST_FRAME 1")
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_DOWN
sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER
# ▼▼ in der Senderliste: dritter Sender der Gruppe („Test 3“) muss gewählt werden – prüft, dass die Pfeiltasten in der Liste wirken
wait_for "MV_FIRST_FRAME 1 Test 3" 1 40 || fail "Multi-View-Senderauswahl: Pfeiltasten wirken nicht (falscher Sender gewählt)"
shot "test8-nach-auswahl"
# Das Bild OHNE Ton (Bild 1, nachdem Bild 2 gewählt ist) muss weiterlaufen – kein Wächter-Neustart in 40 s
w=$(count "MV_WATCHDOG 0")
sleep 40
[ "$(count "MV_WATCHDOG 0")" -eq "$w" ] || fail "Multi-View: Bild ohne Ton blieb stehen (Wächter musste neu starten)"
[ "$(count "MV_AUDIO 0 aus")" -ge 1 ] || fail "Multi-View: Bild 1 hat seine Tonspur nicht abgegeben"
adb shell input keyevent KEYCODE_MENU
sleep 10
alive || fail "App nach Multi-View nicht mehr da"
shot "test8-multiview"
adb shell input keyevent KEYCODE_BACK
sleep 3
alive || fail "App nach Verlassen von Multi-View nicht mehr da"

log "== Test 9: Senderliste verwalten – Gruppe von ganz unten nach ganz oben verschieben (Liste muss mitscrollen)"
adb shell am start -S -n $PKG/.MainActivity --ez aze_test_listeditor true >/dev/null
sleep 6
wait_for "SORT_OPEN" 1 30 || fail "Senderliste verwalten ging nicht auf"
sleep 2
for i in $(seq 1 30); do adb shell input keyevent KEYCODE_DPAD_DOWN; done
sleep 1
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
sleep 2
wait_for "SORT_START" 1 10 || fail "Sortieren: langes OK hat das Verschieben nicht gestartet"
for i in $(seq 1 30); do adb shell input keyevent KEYCODE_DPAD_UP; done
sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
shot "test9-sortiert"
note "Sortieren" "$(qa | grep -E "SORT_" | head -8; echo ...; qa | grep -E "SORT_" | tail -8; echo; qa | grep -vE "TICK|SORT_" | tail -6)"
qa | grep -q "SORT_END .* 0$" || fail "Sortieren: Gruppe kam nicht ganz nach oben (Fokus verloren / Liste scrollt nicht mit)"

log "== Test 9b: Sender von ganz unten nach ganz oben verschieben (Gruppe „Viele Sender“, 35 Sender)"
# die eben nach oben verschobene Gruppe ist „Viele Sender“ und hat den Fokus → ▶ zu ihren Sendern
for i in 1 2 3; do
  [ "$(count "SORT_CH_FOCUS")" -ge 1 ] && break
  adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
done
wait_for "SORT_CH_FOCUS" 1 5 || fail "Sortieren: ▶ kommt nicht zu den Sendern"
for i in $(seq 1 40); do adb shell input keyevent KEYCODE_DPAD_DOWN; done
sleep 1
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
sleep 2
wait_for "SORT_CH_START" 1 10 || fail "Sortieren: langes OK auf dem Sender hat das Verschieben nicht gestartet"
qa | grep -q "SORT_CH_START .* 34$" || fail "Sortieren: ▼ kam nicht bis zum letzten Sender"
for i in $(seq 1 40); do adb shell input keyevent KEYCODE_DPAD_UP; done
sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
shot "test9b-sender-sortiert"
note "Sender sortieren" "$(qa | grep -E "SORT_CH_(START|END)")"
qa | grep -q "SORT_CH_END .* 0$" || fail "Sortieren: Sender kam nicht ganz nach oben (Fokus verloren / Liste scrollt nicht mit)"
alive || fail "App nach dem Sortieren nicht mehr da"

hang_check "Sortieren"

log "== Test 10: Filme & Serien mit Test-Anbieter (3000 Filme, 600 Serien) – Cover, Cover-Vorrat, keine Hänger"
T10=$(adb shell date +%H:%M:%S | tr -d '\r')
adb shell am start -S -n $PKG/.MainActivity --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true >/dev/null
wait_for "VOD_CAT" 1 60 || fail "Filme: erste Kategorie kam nicht"
T10=$(adb shell date +%H:%M:%S | tr -d '\r')   # ab hier: Filme sind offen (App-Start zählt nicht)
sleep 15
shot "test10-filme"
c=$(qa | grep -c "COVER " || true)
note "Filme" "$(qa | grep -E "VOD_|COVER" | head -20)"
[ "$(count "VOD_CAT_ERROR")" -eq 0 ] || fail "Filme: Kategorie konnte nicht geladen werden"
# durch die Kategorien und das Raster blättern
for i in 1 2 3 4 5; do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; done
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
for i in $(seq 1 15); do adb shell input keyevent KEYCODE_DPAD_DOWN; done
sleep 3
alive || fail "Filme: App nach dem Blättern nicht mehr da"
hang_check "Filme blättern" "$T10"
# zurück zum Fernsehen: der Cover-Vorrat füllt sich jetzt im Hintergrund – Umschalten muss trotzdem flüssig bleiben
T10b=$(adb shell date +%H:%M:%S | tr -d '\r')
for i in 1 2 3 4; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2
for i in $(seq 1 8); do adb shell input keyevent KEYCODE_CHANNEL_UP; sleep 2; done
sleep 30
alive || fail "Cover-Vorrat: App nicht mehr da"
hang_check "Fernsehen während Cover-Vorrat" "$T10b"
note "Cover-Vorrat" "$(qa | grep -E "COVER_STOCK|VOD_ALL" | tail -5)"

log "== Test 11: Rundgang – Schnellmenü, Suche, Infoleiste (▶ lang), Senderliste, Einstellungen"
adb shell am start -S -n $PKG/.MainActivity >/dev/null
sleep 3; wait_for "FIRST_FRAME" 1 30; sleep 3
T11=$(adb shell date +%H:%M:%S | tr -d '\r')
adb shell input keyevent --longpress KEYCODE_DPAD_RIGHT; sleep 2; shot "test11-infoleiste"
[ "$(count "INFO_BAR an")" -ge 1 ] || fail "Infoleiste: ▶ lang hat sie nicht geöffnet ($(qa | grep -E "ARROW|INFO_BAR" | tail -3 | tr '\n' ' '))"
sleep 6
adb shell input keyevent KEYCODE_PROG_GREEN; sleep 2; shot "test11-schnellmenue"; adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_PROG_YELLOW; sleep 2; shot "test11-suche"; adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 2; shot "test11-senderliste"
[ "$(count "LIST_OPEN")" -ge 1 ] || fail "Senderliste: ◀ hat sie nicht geöffnet"
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2
adb shell input keyevent KEYCODE_PROG_BLUE; sleep 3
for d in 0 0 0 0; do adb shell input keyevent KEYCODE_$d; done   # Standard-PIN 0000
sleep 3; shot "test11-einstellungen"
for i in $(seq 1 18); do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; done   # jeden Bereich einmal öffnen
shot "test11-einstellungen-ende"
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
alive || fail "Rundgang: App nicht mehr da"
hang_check "Rundgang" "$T11"

log "== Absturz-Prüfung"
alive || fail "App ist am Ende nicht mehr da"
if adb logcat -d -b crash | grep -q "$PKG"; then
  fail "Absturz im Protokoll gefunden"
  log "Absturz-Protokoll:"; adb logcat -d -b crash | grep -v "^---" | head -60
fi

adb logcat -d > "$OUT/logcat.txt"
qa > "$OUT/azeqa.txt"; ticks > "$OUT/ticks.txt"
log "Protokoll (AzeQA):"; cat "$OUT/azeqa.txt"
note "AzeQA-Protokoll" "$(cat "$OUT/azeqa.txt")"
note "App-Fehler im Protokoll" "$(grep -E 'AndroidRuntime|FATAL|de\.aze|ExoPlayerImpl|MediaCodec.*(rror|xception)' "$OUT/logcat.txt" | grep -v 'AzeQA' | tail -60)"
if [ $FAILS -gt 0 ]; then log "ERGEBNIS: $FAILS Fehler:$FAILLIST"; echo "::notice title=Fehlerliste::$FAILLIST"; exit 1; fi
log "ERGEBNIS: alle Tests bestanden"
echo "::notice title=Emulator-Test::Alle Tests bestanden"
