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
# App-Protokoll: läuft ab dem Start laufend in eine Datei mit. Vorher wurde jedes Mal der Logcat-Puffer im Gerät gelesen –
# der läuft bei diesem langen Test über, alte Zeilen fielen heraus und „warte auf das NÄCHSTE FIRST_FRAME“ zählte falsch
# (1.0.314: Test 15 meldete „kein Bild“, obwohl es nach 0,2 s da war).
QA_FILE="$OUT/azeqa-stream.txt"
QA_LC=""
qa_stream() {  # Mitschnitt starten bzw. nach einem adb-Aussetzer neu starten (-T 1: nur neue Zeilen)
  if [ -z "$QA_LC" ] || ! kill -0 "$QA_LC" 2>/dev/null; then
    adb logcat -T 1 -v threadtime -s AzeQA:I >> "$QA_FILE" 2>/dev/null &
    QA_LC=$!
  fi
}
logclear() { adb logcat -c; : > "$QA_FILE"; }
qlog() { if [ -n "$QA_LC" ]; then qa_stream; cat "$QA_FILE" 2>/dev/null; else adb logcat -d -s AzeQA:I 2>/dev/null; fi; }
qa() { qlog | grep -F "AzeQA" | grep -v " TICK " | grep -v ": BEAT [0-9]*$" ; }
# größte Lücke im Herzschlag (Haupt-Thread) in Sekunden – je Prozess, ab Zeile „seit“ (Uhrzeit HH:MM:SS)
beat_gap() { qlog | grep -E ": BEAT [0-9]+$" | awk -v since="${1:-00:00:00}" '
  { split($2,t,":"); s=t[1]*3600+t[2]*60+t[3]; if ($2 < since) next;
    if (($3 in last) && s-last[$3] > max) max=s-last[$3]; last[$3]=s }
  END { printf "%.1f", max+0 }'; }
# Alarm: Lücke über 1,5 s UND deutlich länger (+0,7 s) als beim normalen Zappen im selben Emulator
hang_check() { local g; g=$(beat_gap "$2"); log "Herzschlag $1: längste Lücke ${g} s (Vergleich Zappen: ${BASE_GAP:-0} s)"
  qa | grep "STALL" | tail -4 | while IFS= read -r l; do log "  $l"; done
  awk -v g="$g" -v b="${BASE_GAP:-0}" 'BEGIN{exit !(g>1.5 && g>b+0.7)}' && fail "$1: App hing ${g} s (Haupt-Thread blockiert)"; true; }
ticks() { qlog | grep -F " TICK " ; }
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

# ── Touch (Tablet): die App (Test-Build) schreibt die Position jedes Knopfs ins Protokoll (POS Name|x|y|sichtbare Höhe)
# echte Bildschirmgröße wie gerade gedreht (wm size zeigt im Querformat die Hochformat-Werte!)
scr() { adb shell dumpsys window displays 2>/dev/null | grep -o 'cur=[0-9]*x[0-9]*' | head -1 | cut -d= -f2; }
tap() {  # tap "Name": Knopf antippen (scrollt bei Bedarf, auch seitlich); 1 = nicht da
  local WH W H; WH=$(scr); W=${WH%x*}; H=${WH#*x}; W=${W:-1000}; H=${H:-1000}
  for try in 1 2 3 4 5 6 7 8; do
    local l; l=$(qa | grep -F -e "POS $1|" -e "GONE $1" | sed 's/.*AzeQA[^:]*: //' | tail -1)
    case "$l" in
      "POS $1|"*) local x y v w; IFS='|' read -r _ x y v w <<<"$l"
        # Mitte außerhalb des Bildschirms (Eintrag nur angeschnitten, z. B. ganz oben y=-10) → erst hinwischen
        if [ "$y" -lt 5 ] || [ "$y" -gt $((H-5)) ] || [ "$x" -lt 5 ] || [ "$x" -gt $((W-5)) ]; then v=0; fi
        if [ "$v" -ge 10 ] && [ "$try" -eq 1 ]; then adb shell input tap "$x" "$y"; sleep 1.5; return 0; fi
        if [ "$v" -ge 10 ]; then
          # nach dem Wischen erst tippen, wenn nichts mehr scrollt (sonst trifft man den Nachbarn)
          sleep 0.6; local l2; l2=$(qa | grep -F -e "POS $1|" -e "GONE $1" | sed 's/.*AzeQA[^:]*: //' | tail -1)
          if [ "$l2" = "$l" ]; then adb shell input tap "$x" "$y"; sleep 1.5; return 0; fi
          continue
        fi
        echo "  (tap $1: verdeckt bei x=$x y=$y, Bildschirm $WH → wischen)"
        if [ "$x" -gt "$W" ]; then adb shell input swipe $((W*3/4)) "$y" $((W/4)) "$y" 500
        elif [ "$x" -lt 0 ]; then adb shell input swipe $((W/4)) "$y" $((W*3/4)) "$y" 500
        elif [ "$y" -gt $((H/2)) ]; then adb shell input swipe "$x" $((H*3/4)) "$x" $((H/4)) 500
        else adb shell input swipe "$x" $((H/4)) "$x" $((H*3/4)) 500; fi
        sleep 2 ;;
      *) sleep 1 ;;
    esac
  done
  return 1
}
# Senderliste per Knopf öffnen (bis zu 3 Versuche)
open_list() { for t in 1 2 3; do local N; N=$(count "LIST_OPEN"); bar; tap "☰ Sender"; sleep 2; [ "$(count "LIST_OPEN")" -gt "$N" ] && return 0; done; return 1; }
# Fernsehbild ohne offenes Fenster, dann Knopfleiste zeigen
list_open() { [ "$(qa | grep -E ": LIST_(OPEN|CLOSED)" | tail -1 | grep -c LIST_OPEN)" -eq 1 ]; }
bar() {
  local WH; WH=$(scr); local W=${WH%x*} H=${WH#*x}
  if list_open; then adb shell input tap $((W-15)) $((H/2)); sleep 1.5; fi
  local l; l=$(qa | grep -F -e "POS ☰ Sender|" -e "GONE ☰ Sender" | tail -1)
  case "$l" in *"POS ☰ Sender|"*) return 0 ;; esac
  adb shell input tap $((W/2)) $((H*2/3)); sleep 2
}
last_is() { [ "$(qa | grep -E "$1" | tail -1 | sed 's/.*AzeQA[^:]*: //')" = "$2" ]; }

channel() {  # Sender per Zifferntaste wählen
  adb shell input keyevent "KEYCODE_$1"; sleep 0.3; adb shell input keyevent KEYCODE_DPAD_CENTER
}

# Bildschirm wie ein Fernseher: 1920×1080 quer, 960×540 dp (der Standard-Emulator war ein kleines Handy, 320 dp breit –
# „Senderliste verwalten“ hatte dort für die Sender-Spalte 0 px Höhe)
adb shell wm size 1920x1080 || true
adb shell wm density 320 || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
log "Installiere $APK"
adb install -r "$APK" || { echo "::error::Installation fehlgeschlagen"; exit 1; }
adb logcat -G 16M 2>/dev/null || true
adb logcat -c
: > "$QA_FILE"
qa_stream
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
# tote Verbindung: entweder der Wächter greift oder (Wächter: Vorrat leer, 5 s ohne Fortschritt) wird nahtlos neu verbunden
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
# Server liefert 3× schnell und trennt nach 15 s: die neue Verbindung kommt erst kurz vor Ende des Vorrats (~3 s vorher,
# sonst liefe dieselbe Szene doppelt) und muss dann nahtlos übernehmen
wait_for "REJOIN Test 4" 1 75
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

log "== Test 4d: zäher Nachschub (nach 12 s nur noch 0,25-fach) – neu verbinden statt lange auf Tröpfchen warten"
ln=$(count "LANGSAM_NEU")
channel 8
wait_for "FIRST_FRAME Test 8" 1 60
sleep 2
tb=$(ticks | wc -l)
sleep 55
g=$(max_gap "$tb")
l=$(( $(count "LANGSAM_NEU") - ln ))
moving=$(ticks | tail -15 | grep -cE 'img=([5-9]|[1-9][0-9])' || true)
note "Test 4d Zäher Nachschub" "neu verbunden: ${l}× · längste Lücke ohne Bild: ${g} s (ohne Neuverbinden 8–16 s) · Bild am Ende: ${moving}/15 s · $(qa | grep -E 'LANGSAM|NEUVERBINDEN|WATCHDOG .*Test 8' | tail -4)"
[ "$l" -ge 1 ] || fail "Zäher Nachschub: nicht neu verbunden"
[ "$g" -le 9 ] || fail "Zäher Nachschub: ${g} s ohne Bild"
[ "$moving" -ge 12 ] || fail "Zäher Nachschub: Bild läuft am Ende nicht flüssig ($moving von 15 s)"
shot "test4d-zaeher-nachschub"

log "== Test 4e: Anbieter sperrt den Sender 20 s lang (HTTP 403) – Wächter wartet, Hinweis in der Ecke, Bild kommt gleich danach"
w=$(count "WATCHDOG")
channel 9
wait_for "ABGELEHNT HTTP 403 Test 9" 1 25 && shot "test4e-gesperrt-hinweis"
wait_for "FIRST_FRAME Test 9" 1 45
zt=$(qa | grep -o "ZAPTIME [0-9]* Test 9" | tail -1 | awk '{print $2}')
wait_for "ABGELEHNT_ENDE" 1 10
note "Test 4e Gesperrt" "Bild nach ${zt:-?} ms (Sperre 20 s) · Anfragen beim Server: $(grep -c 'reject: abgelehnt' qa-server.log 2>/dev/null || echo ?) · $(qa | grep -E 'ABGELEHNT|ERROR .*Test 9' | tail -3)"
[ "$(count "WATCHDOG")" -eq "$w" ] || fail "Gesperrter Sender: Wächter griff trotzdem ein"
[ "${zt:-99999}" -le 26000 ] || fail "Gesperrter Sender: Bild erst nach ${zt:-?} ms (Sperre endete nach 20 s)"
# Test 5 beginnt wie bisher bei Sender 6
channel 6
sleep 3

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
  run=$(qa | grep -o "ZAPLAUF [0-9]*" | tail -10 | awk '{print $2}' | sort -n)
  rmed=$(echo "$run" | awk 'NF{a[++n]=$1} END{if(n) print a[int((n+1)/2)]; else print "-"}')
  note "Bild läuft" "Median ${rmed} ms bis Bild und Ton laufen · Messungen: $(echo $run | tr '\n' ' ') · Planung fest: $(qa | grep -o "PLANUNG_FEST [A-Za-z]*" | tail -1)"
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
logclear
adb shell am start -W -n $PKG/.MainActivity --ez aze_test_multiview true
wait_for "MV_FIRST_FRAME 0" 1 90
wait_for "MV_FIRST_FRAME 1" 1 60
# Bild 2 = „Test 4 Abbruch“: Server beendet den Stream nach 15 s → Bild muss sich OHNE Wächter selbst erholen
wait_for "MV_REJOIN 1" 1 75
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
note "Sender sortieren" "$(qa | grep -E "SORT_(LAYOUT|RIGHT|CH_START|CH_END|CH_FOCUS 0)|BRING" | tail -12)"
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
# Zugang laut Anbieter (Ablaufdatum wie bei TiviMate): Test-Anbieter meldet „in 109 Tagen“, 1 von 2 Verbindungen
wait_re "ZUGANG .*Gültig bis" 30 >/dev/null || fail "Zugang: Ablaufdatum des Test-Anbieters nicht abgefragt ($(qa | grep ZUGANG | tail -1))"
note "Zugang" "$(qa | grep "ZUGANG" | tail -1)"
qa | grep "ZUGANG" | tail -1 | grep -q "noch 10[89] Tage · Verbindungen 1 von 2" || fail "Zugang: falsch angezeigt – $(qa | grep ZUGANG | tail -1)"
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
# Eigene Suche nur in Filmen: ◀ zurück in die Kategorien, ▲ bis ganz oben zur Suchzeile, OK, tippen, Enter
nf=$(count "FOCUS VOD_SUCHE"); ns=$(count "VOD_SEARCH")
adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 1
for i in $(seq 1 12); do [ "$(count "FOCUS VOD_SUCHE")" -gt "$nf" ] && break; adb shell input keyevent KEYCODE_DPAD_UP; sleep 0.7; done
if [ "$(count "FOCUS VOD_SUCHE")" -gt "$nf" ]; then
  adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 2
  adb shell input text "kahramanmaras"; sleep 1
  adb shell input keyevent KEYCODE_ENTER
  # Enter übernimmt (Tastatur „Fertig“); sonst ▼ auf „Übernehmen“ und OK
  for i in $(seq 1 10); do [ "$(count "VOD_SEARCH")" -gt "$ns" ] && break; sleep 1; done
  if [ "$(count "VOD_SEARCH")" -le "$ns" ]; then
    adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; adb shell input keyevent KEYCODE_DPAD_CENTER
  fi
  wait_for "VOD_SEARCH" $((ns+1)) 40 >/dev/null
  sleep 3; shot "test10-filme-suche"
  hit=$(qa | grep "VOD_SEARCH" | tail -1)
  note "Filme-Suche" "$hit"
  # „kahramanmaras“ (ohne ş) findet genau „Der Schatz von Kahramanmaraş“
  echo "$hit" | grep -q " 1 Treffer (Filme" || fail "Filme-Suche: erwartet genau 1 Treffer – $hit"
  adb shell input keyevent KEYCODE_BACK; sleep 2   # Suche beenden
  # OK lang gedrückt in den Kacheln öffnet die Suche direkt (ohne nach oben zur Suchzeile)
  nl=$(count "VOD_SUCHE_LANG")
  adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
  adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
  adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
  adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2
  shot "test10-filme-suche-lang"
  [ "$(count "VOD_SUCHE_LANG")" -gt "$nl" ] || fail "Filme: OK lang gedrückt öffnet die Suche nicht ($(qa | grep -E 'FOCUS ' | tail -1))"
  adb shell input keyevent KEYCODE_BACK; sleep 1   # Suchfenster zu
else
  fail "Filme-Suche: Suchzeile über den Kategorien nicht erreichbar ($(qa | grep -E 'FOCUS ' | tail -2 | tr '\n' ' '))"
fi
# zurück zum Fernsehen: der Cover-Vorrat füllt sich jetzt im Hintergrund – Umschalten muss trotzdem flüssig bleiben
T10b=$(adb shell date +%H:%M:%S | tr -d '\r')
for i in 1 2 3 4; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2
for i in $(seq 1 8); do adb shell input keyevent KEYCODE_CHANNEL_UP; sleep 2; done
sleep 30
alive || fail "Cover-Vorrat: App nicht mehr da"
hang_check "Fernsehen während Cover-Vorrat" "$T10b"
note "Cover-Vorrat" "$(qa | grep -E "COVER_STOCK|VOD_ALL" | tail -5)"

log "== Test 10c: Filme & Serien – Neu hinzugefügt, Weiterschauen, Merkliste, Trailer, nächste Folge"
# Knopf sichtbar? (letzte gemeldete Position, mindestens 20 Pixel zu sehen) – hier schon nötig, nicht erst ab Test 14
pos_visible() {
  local l v; l=$(qa | grep -F -e "POS $1|" | sed 's/.*AzeQA[^:]*: //' | tail -1)
  case "$l" in "POS $1|"*) v=$(echo "$l" | cut -d'|' -f4); [ "${v:-0}" -ge 20 ] ;; *) return 1 ;; esac
}
# sucht Knopf $1 in den Film-Details mit den Pfeiltasten (auf schmalen Bildschirmen brechen die Knöpfe um)
focus_btn() { local k; for k in RIGHT RIGHT RIGHT DOWN LEFT LEFT LEFT RIGHT RIGHT RIGHT; do [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS $1" ] && return 0; adb shell input keyevent KEYCODE_DPAD_$k; sleep 0.9; done; [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS $1" ]; }
# drückt $2, bis die Markierung auf $1 steht (höchstens $3-mal)
focus_to() { local i; for i in $(seq 1 "${3:-8}"); do [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS $1" ] && return 0; adb shell input keyevent "$2"; sleep 0.9; done; [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS $1" ]; }
T10c=$(adb shell date +%H:%M:%S | tr -d '\r')
c=$(count "VOD_CAT")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true >/dev/null
wait_for "VOD_CAT" $((c+1)) 60 >/dev/null; sleep 3
# ▲ aus der ersten Kategorie: „Neu hinzugefügt“ (die neuesten 200, neueste zuerst)
if focus_to "K ✦ Neu hinzugefügt" KEYCODE_DPAD_UP 3; then
  wait_for "VOD_CAT ✦ Neu hinzugefügt" 1 40 >/dev/null
  note "Neu hinzugefügt" "$(qa | grep "VOD_CAT ✦" | tail -1)"
  qa | grep "VOD_CAT ✦ Neu hinzugefügt" | tail -1 | grep -q "Neu hinzugefügt 200 in" || fail "Neu hinzugefügt: erwartet 200 Filme – $(qa | grep "VOD_CAT ✦" | tail -1)"
  sleep 2; shot "test10c-neu-hinzugefuegt"
else fail "Neu hinzugefügt: Kategorie fehlt über den Anbieter-Kategorien ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
# Film öffnen, abspielen, auf 20 % springen (1:12 von 6:00) und zurück → steht in „Weiterschauen“
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 3
if wait_for "FOCUS Abspielen" 1 15 >/dev/null; then
  sleep 1; shot "test10c-film-details"
  pos_visible "▶ Trailer" || fail "Trailer: Knopf fehlt in den Film-Details (Anbieter liefert einen Trailer)"
  pos_visible "Vormerken" || fail "Merkliste: Knopf „Vormerken“ fehlt in den Film-Details"
  np=$(count "VOD_READY")
  adb shell input keyevent KEYCODE_DPAD_CENTER
  wait_for "VOD_READY" $((np+1)) 40 >/dev/null
  note "Film läuft" "$(qa | grep -E "VOD_PLAY|VOD_READY" | tail -2 | tr '\n' ' ')"
  sleep 2; adb shell input keyevent KEYCODE_2; sleep 4; shot "test10c-film-laeuft"
  nw=$(count "WEITERSCHAUEN_NEU")
  for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; [ "$(count "WEITERSCHAUEN_NEU")" -gt "$nw" ] && break; done
  [ "$(count "WEITERSCHAUEN_NEU")" -gt "$nw" ] || fail "Weiterschauen: angefangener Film wurde nicht aufgenommen ($(qa | grep -E 'WEITERSCHAUEN|VOD_' | tail -3 | tr '\n' ' '))"
  sleep 2
  qa | grep -q "POS Weiterschauen ab" || fail "Weiterschauen: Knopf „Weiterschauen ab …“ fehlt nach dem Zurückkommen"
  # Trailer (YouTube) öffnen und mit Zurück schließen
  if focus_btn "▶ Trailer"; then
    nt=$(count "TRAILER "); adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 8; shot "test10c-trailer"
    [ "$(count "TRAILER ")" -gt "$nt" ] || fail "Trailer: OK auf „▶ Trailer“ öffnet nichts"
    adb shell input keyevent KEYCODE_BACK; sleep 3
    qa | grep -o "FOCUS .*" | tail -1 | grep -q "^FOCUS Weiterschauen ab" || fail "Trailer: nach Zurück steht die Markierung nicht wieder auf „Weiterschauen ab …“ ($(qa | grep -o 'FOCUS .*' | tail -1))"
  else fail "Trailer: Knopf mit der Fernbedienung nicht erreichbar ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
  # Vormerken
  nm=$(count "MERKLISTE_NEU")
  if focus_btn "Vormerken"; then
    adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 2
    [ "$(count "MERKLISTE_NEU")" -gt "$nm" ] || fail "Merkliste: „Vormerken“ hat nichts gemerkt"
    shot "test10c-vorgemerkt"
  else fail "Merkliste: „Vormerken“ mit der Fernbedienung nicht erreichbar ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
  adb shell input keyevent KEYCODE_BACK; sleep 3
  qa | grep -q "POS K ▶ Weiterschauen|" || fail "Weiterschauen: Reihe steht nicht oben in den Kategorien"
  qa | grep -q "POS K ★ Merkliste|" || fail "Merkliste: Kategorie steht nicht oben"
  shot "test10c-eigene-listen"
  # Menütaste auf dem Cover entfernt den Eintrag (erst Merkliste, dann Weiterschauen)
  adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 1
  if focus_to "K ★ Merkliste" KEYCODE_DPAD_UP 8 || focus_to "K ★ Merkliste" KEYCODE_DPAD_DOWN 8; then
    sleep 2; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
    ne=$(count "MERKLISTE_ENTFERNT"); adb shell input keyevent KEYCODE_MENU; sleep 2
    [ "$(count "MERKLISTE_ENTFERNT")" -gt "$ne" ] || fail "Merkliste: Menütaste entfernt den Eintrag nicht ($(qa | grep -o 'FOCUS .*' | tail -1))"
  else fail "Merkliste: Kategorie mit der Fernbedienung nicht erreichbar"; fi
  # die Merkliste ist jetzt leer und verschwindet – die Markierung springt auf die Kategorien (sonst ◀ aus den Kacheln)
  sleep 1; qa | grep -o "FOCUS .*" | tail -1 | grep -q "^FOCUS V " && { adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 1; }
  if focus_to "K ▶ Weiterschauen" KEYCODE_DPAD_UP 8; then
    sleep 2; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1; shot "test10c-weiterschauen"
    ne=$(count "WEITERSCHAUEN_ENTFERNT"); adb shell input keyevent KEYCODE_MENU; sleep 2
    [ "$(count "WEITERSCHAUEN_ENTFERNT")" -gt "$ne" ] || fail "Weiterschauen: Menütaste entfernt den Eintrag nicht ($(qa | grep -o 'FOCUS .*' | tail -1))"
  else fail "Weiterschauen: Kategorie mit der Fernbedienung nicht erreichbar ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
else fail "Film-Details: „Abspielen“ bekam keine Markierung ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
# Serie: Folge zu Ende → „Nächste Folge in 10 Sekunden“ → nächste Folge startet; Zurück schließt den Hinweis
c=$(count "VOD_CAT")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true --ez aze_test_series true >/dev/null
wait_for "VOD_CAT" $((c+1)) 60 >/dev/null; sleep 3
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 3
if wait_for "FOCUS Staffel 1" 1 15 >/dev/null && focus_to "E 1.1" KEYCODE_DPAD_DOWN 3; then
  nn=$(count "NAECHSTE_FOLGE "); nx=$(count "VOD_NEXT")
  adb shell input keyevent KEYCODE_DPAD_CENTER
  wait_for "NAECHSTE_FOLGE " $((nn+1)) 60 >/dev/null && { sleep 3; shot "test10c-naechste-folge"; }
  wait_for "VOD_NEXT" $((nx+1)) 20 >/dev/null
  note "Nächste Folge" "$(qa | grep -E "VOD_PLAY|NAECHSTE_FOLGE|VOD_NEXT" | tail -4 | tr '\n' ' ')"
  qa | grep "VOD_PLAY" | tail -1 | grep -q "S1 E2" || fail "Nächste Folge: es startete nicht Folge 2 ($(qa | grep VOD_PLAY | tail -1))"
  # am Ende der zweiten Folge: Zurück schließt den Hinweis (zurück zu den Folgen, keine dritte)
  if wait_for "NAECHSTE_FOLGE " $((nn+2)) 60 >/dev/null; then
    na=$(count "NAECHSTE_FOLGE_AB"); nx=$(count "VOD_NEXT")
    adb shell input keyevent KEYCODE_BACK; sleep 12
    [ "$(count "NAECHSTE_FOLGE_AB")" -gt "$na" ] || fail "Nächste Folge: Zurück schließt den Hinweis nicht"
    [ "$(count "VOD_NEXT")" -eq "$nx" ] || fail "Nächste Folge: trotz Zurück startete die nächste Folge"
    shot "test10c-hinweis-zu"
  fi
else fail "Serie: Folgen mit der Fernbedienung nicht erreichbar ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
alive || fail "Filme & Serien: App nicht mehr da"
hang_check "Weiterschauen/Merkliste/Folgen" "$T10c"

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
# Gruppenübersicht wie bei TiviMate: Playlist-Name als Kopfzeile, OK klappt die Gruppen zu und wieder auf
# (◀ öffnet die Liste schon in den Gruppen – ein weiteres ◀ wäre das Menü)
np=$(count "FOCUS P "); ng=$(count "GONE G QA"); nz=$(count "PLAYLIST_ZU"); na=$(count "PLAYLIST_AUF")
for i in $(seq 1 40); do [ "$(count "FOCUS P ")" -gt "$np" ] && break; adb shell input keyevent KEYCODE_DPAD_UP; sleep 0.5; done
[ "$(count "FOCUS P ")" -gt "$np" ] || fail "Gruppen: Playlist-Kopfzeile nicht erreichbar ($(qa | grep -E 'FOCUS (G|P) ' | tail -3 | tr '\n' ' '))"
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 2; shot "test11-playlist-zu"
[ "$(count "PLAYLIST_ZU")" -gt "$nz" ] || fail "Gruppen: Kopfzeile klappt nicht zu"
[ "$(count "GONE G QA")" -gt "$ng" ] || fail "Gruppen: nach dem Zuklappen sind die Gruppen noch zu sehen"
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 2; shot "test11-playlist-auf"
[ "$(count "PLAYLIST_AUF")" -gt "$na" ] || fail "Gruppen: Kopfzeile klappt nicht wieder auf"
note "Playlist-Kopfzeile" "$(qa | grep -E 'FOCUS P |PLAYLIST_' | tail -3)"
# zurück auf die Gruppe QA, dann wie bisher ▶ (Senderliste)
nq=$(count "FOCUS G QA")
for i in $(seq 1 6); do [ "$(count "FOCUS G QA")" -gt "$nq" ] && break; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; done
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2
adb shell input keyevent KEYCODE_PROG_BLUE; sleep 3
for d in 0 0 0 0; do adb shell input keyevent KEYCODE_$d; done   # Standard-PIN 0000
sleep 3; shot "test11-einstellungen"
for i in $(seq 1 18); do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1; done   # jeden Bereich einmal öffnen
shot "test11-einstellungen-ende"
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
alive || fail "Rundgang: App nicht mehr da"
hang_check "Rundgang" "$T11"

log "== Test 12: SkorText öffnet (Hinweis „startet …“, Seite lädt) und schließt wieder"
adb shell am start -S -n $PKG/.MainActivity --ez aze_test_skortext true >/dev/null
wait_for "SKOR_PAGE" 1 40 || fail "SkorText: Seite kam nicht ($(qa | grep -E "SKOR_" | tail -3 | tr '\n' ' '))"
[ "$(count "SKOR_FAIL")" -eq 0 ] || fail "SkorText: Anzeige ließ sich nicht anlegen"
sleep 5; shot "test12-skortext"
[ "$(count "SKOR_CLOSE")" -eq 0 ] || fail "SkorText: ging nach dem Laden von selbst wieder zu"
note "SkorText" "$(qa | grep -E "SKOR_" | head -12)"
adb shell input keyevent KEYCODE_BACK; sleep 2
alive || fail "SkorText: App nach dem Schließen nicht mehr da"

log "== Test 13: Tablet (Touch) – alles antippbar, überall zurück"
T13=$(date +%H:%M:%S)
# wie das Fire Max 11: 1920×1200 quer, 240 dpi (= 1280×800 dp). Handy-Größe kommt mit der Handy-Version eigens dran.
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1
adb shell wm size 1920x1200; adb shell wm density 240; sleep 2
log "Tablet-Größe: $(scr) (soll quer sein)"
adb shell am start -S -n $PKG/.MainActivity >/dev/null; sleep 8
adb shell input tap 300 300; sleep 2; shot "test13-knopfleiste"
tap "⚙ Einstellungen" || fail "Touch: Knopf „⚙ Einstellungen“ nicht da (Knopfleiste nach Tippen aufs Bild)"
sleep 2
for d in 0 0 0 0; do tap "PIN $d" || { fail "Touch: PIN-Taste $d nicht antippbar"; break; }; done
sleep 2; shot "test13-einstellungen"
# Bereich öffnen: tippen und prüfen; rollt die linke Liste noch (Nachbar getroffen), warten bis sie ruht und neu tippen
open_pane() {
  local c="$1" r
  for r in 1 2 3 4 5; do
    tap "SET $c" || return 1
    sleep 1.2
    last_is "SET_PANE" "SET_PANE $c" && return 0
    sleep 1.5   # Liste ausrollen lassen, dann frische Position lesen
  done
  last_is "SET_PANE" "SET_PANE $c"
}
for c in "Sender & Playlists" "Senderliste & Favoriten" "Filme & Serien" "Fußball" "Bild & Ton" "Programmzeitschrift" "Fernbedienung" "Aussehen" "PIN & Kinderschutz" "Sichern" "System" "Hilfe" "Über" "Suchen"; do
  open_pane "$c" || { fail "Touch: Bereich „$c“ öffnet sich nicht ($(qa | grep SET_PANE | tail -1 | sed 's/.*: //'))"; shot "test13-bereich"; }
done
# Sichern: Code muss erscheinen (Bereich sicher öffnen, Knopf erst nach dem Aufbau antippen)
open_pane "Sichern" || fail "Touch: Bereich „Sichern“ öffnet sich nicht"
sleep 1.5
N=$(count "BACKUP_"); tap "Sicherung erstellen" || fail "Touch: „Sicherung erstellen“ nicht antippbar"
for w in $(seq 1 40); do [ "$(count "BACKUP_")" -gt "$N" ] && break; sleep 1.5; done  # paste.rs/dpaste können bis ~40 s brauchen
shot "test13-sicherung"
if [ "$(count "BACKUP_OK")" -gt 0 ] && qa | grep -E "BACKUP_" | tail -1 | grep -q BACKUP_OK; then
  # Löschen mit Sicherheitsfrage
  tap "Sicherung löschen" || fail "Touch: „Sicherung löschen“ nicht da"
  sleep 1.5; shot "test13-loeschen-frage"
  tap "Ja, löschen" || fail "Touch: Sicherheitsfrage „Ja, löschen“ nicht antippbar"
  for w in 1 2 3 4 5 6 7 8; do [ "$(count "BACKUP_DELETED")" -gt 0 ] && break; sleep 1.5; done
  [ "$(count "BACKUP_DELETED")" -gt 0 ] || fail "Touch: Sicherung wurde nicht gelöscht"
elif qa | grep -E "BACKUP_" | tail -1 | grep -q BACKUP_FAIL; then
  why="$(qa | grep BACKUP_FAIL | tail -1 | sed 's/.*BACKUP_FAIL//')"
  # Hochlade-Dienste im Internet vom Test-Rechner aus nicht erreichbar = Störung draußen, kein Fehler der App
  if echo "$why" | grep -qiE "failed to connect|timed out|timeout|UnknownHost|Unable to resolve|Trust anchor|ECONNREFUSED|Network is unreachable"; then
    note "Sichern" "Hochlade-Dienst gerade nicht erreichbar (übersprungen):$why"
  else fail "Sichern geht nicht:$why"; fi
else note "Sicherung" "nach 60 s noch keine Antwort vom Sicherungs-Dienst (Netz des Test-Rechners) – nicht als Fehler gewertet"; fi
# Links direkt antippen (öffnet den Browser) – Einstellungen frisch öffnen
adb shell am start -S -n $PKG/.MainActivity >/dev/null; sleep 8
bar; tap "⚙ Einstellungen"; sleep 2; for d in 0 0 0 0; do tap "PIN $d"; done; sleep 2
open_pane "Hilfe"; N=$(count "OPEN_URL"); tap "🔗 Anleitung öffnen" || fail "Touch: Hilfe – Link „Anleitung öffnen“ nicht antippbar"
sleep 3; [ "$(count "OPEN_URL")" -gt "$N" ] || fail "Touch: Hilfe – Anleitung-Link öffnet nichts"
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
open_pane "Sender & Playlists"; tap "Handy-Eingabe starten" && { sleep 2; N=$(count "OPEN_URL"); tap "🔗 Hier auf dem Gerät öffnen" || fail "Touch: Handy-Eingabe – Link nicht antippbar"
  sleep 3; [ "$(count "OPEN_URL")" -gt "$N" ] || fail "Touch: Handy-Eingabe – Link öffnet nichts"
  adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3; tap "Beenden"; }
open_pane "Senderliste & Favoriten"; tap "Senderliste bearbeiten öffnen" || fail "Touch: „Senderliste bearbeiten öffnen“ nicht antippbar"
sleep 3; shot "test13-senderliste"
[ "$(count "SORT_OPEN")" -ge 1 ] || fail "Touch: Senderliste verwalten öffnet sich nicht"
tap "›" || fail "Touch: „›“ (Sender der Kategorie) nicht antippbar"
tap "Sortieren" || fail "Touch: „Sortieren“ nicht antippbar"
tap "Sortieren beenden" || fail "Touch: „Sortieren beenden“ nicht antippbar"
N=$(count "SORT_CLOSE"); adb shell input keyevent KEYCODE_BACK; sleep 2
[ "$(count "SORT_CLOSE")" -gt "$N" ] || fail "Touch: Zurück schließt „Senderliste verwalten“ nicht"
N=$(count "SET_CLOSE"); adb shell input keyevent KEYCODE_BACK; sleep 2
[ "$(count "SET_CLOSE")" -gt "$N" ] || fail "Touch: Zurück schließt die Einstellungen nicht"
for b in "☰ Sender" "⋯ Menü" "⚽ Heute"; do
  bar
  if tap "$b"; then sleep 3; shot "test13-knopf"
    if [ "$b" = "☰ Sender" ]; then   # Senderliste: Tippen aufs Bild daneben schließt sie (Zurück führt zu den Gruppen)
      N=$(count "LIST_CLOSED"); W=$(scr); adb shell input tap $(( ${W%x*} - 15 )) $(( ${W#*x} / 2 )); sleep 2
      [ "$(count "LIST_CLOSED")" -gt "$N" ] || { fail "Touch: Senderliste schließt nicht beim Tippen daneben"; adb shell input keyevent KEYCODE_BACK; }
    else
      adb shell input keyevent KEYCODE_BACK; sleep 2
    fi
  else fail "Touch: Knopf „$b“ nicht da"; fi
  alive || { fail "Touch: App nach „$b“ weg"; break; }
done

# ---- weitere Touch-Wege: Multi-View, Senderliste → Menü → Filme/Suchen, Sender antippen, Fußball, Schnellmenü
WH=$(scr); SW=${WH%x*}; SH=${WH#*x}; log "Bildschirm: $WH"
swipe_r() { adb shell input swipe 60 $((SH/2)) $((SW/2)) $((SH/2)) 300; sleep 1.5; }
seen_log() { [ "$(count "$1")" -gt "$2" ]; }
# Multi-View
bar; tap "⋯ Menü" && sleep 2
if tap "QM Multi-View"; then
  sleep 4; shot "test13-multiview"
  N=$(count "MV_PICK 2")
  tap "MV Bild 2" || fail "Touch: Multi-View – Bild 2 nicht antippbar"
  sleep 2; G=$(qa | grep -o "POS MV [^|]*" | grep -v "MV Bild" | head -1 | sed 's/POS //')
  if [ -n "$G" ] && tap "$G"; then
    sleep 2; C=$(qa | grep -oE "POS MV [^|]*\|[-0-9]*\|[-0-9]*\|[0-9]+" | grep -v "MV Bild" | tail -8 | awk -F'|' '$4>=40' | head -2 | tail -1 | cut -d'|' -f1 | sed 's/POS //')
    [ -n "$C" ] && tap "$C"
  fi
  sleep 3; seen_log "MV_PICK 2" "$N" || fail "Touch: Multi-View – für Bild 2 ließ sich kein Sender wählen"
  N=$(count "MV_TAP 0"); tap "MV Bild 1"; seen_log "MV_TAP 0" "$N" || fail "Touch: Multi-View – Bild 1 nicht wählbar"
  tap "Anordnung" || fail "Touch: Multi-View – „Anordnung“ nicht antippbar"
  sleep 2; N=$(count "MV_CLOSE"); tap "✕ Schließen" || adb shell input keyevent KEYCODE_BACK; sleep 3
  seen_log "MV_CLOSE" "$N" || { fail "Touch: Multi-View schließt nicht"; adb shell input keyevent KEYCODE_BACK; sleep 2; }
else fail "Touch: Schnellmenü – „Multi-View“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; sleep 1; fi
alive || fail "Touch: App nach Multi-View weg"
# Sender in der Liste antippen
open_list || fail "Touch: Senderliste geht über „☰ Sender“ nicht auf"
N=$(count "FIRST_FRAME"); CUR=$(qa | grep -o "FIRST_FRAME .*" | tail -1 | sed 's/FIRST_FRAME //')
C=$(qa | tail -300 | grep -oE "POS C [^|]*\|[-0-9]*\|[-0-9]*\|[0-9]+" | awk -F'|' '$4>=40' | cut -d'|' -f1 | sed 's/POS //' | grep -vxF "C $CUR" | tail -1)
if [ -z "$C" ] || ! tap "$C"; then fail "Touch: Sender in der Liste nicht antippbar (${C:-keiner sichtbar})"; fi
sleep 4; seen_log "FIRST_FRAME" "$N" || fail "Touch: Antippen eines Senders schaltet nicht um"
# Senderliste → Wischen → Gruppen → Menü → Filme
open_list; NG=$(count "POS G "); swipe_r; [ "$(count "POS G ")" -gt "$NG" ] || { swipe_r; [ "$(count "POS G ")" -gt "$NG" ] || fail "Touch: Wischen nach rechts zeigt keine Gruppen"; }
swipe_r
if tap "M Filme"; then
  sleep 6; shot "test13-filme"
  # Filter steht evtl. auf einer Playlist ohne Filme: „Playlist: …“ antippen, bis Cover da sind
  for p in 1 2 3; do qa | tail -400 | grep -q "POS V 1|" && break
    P=$(qa | grep -oE "POS Playlist: [^|]*" | tail -1 | sed 's/POS //'); [ -n "$P" ] && tap "$P"; sleep 4; done
  tap "V 1" && { sleep 3; shot "test13-film"; tap "Zurück" || adb shell input keyevent KEYCODE_BACK; sleep 2; } || fail "Touch: Film-Cover nicht antippbar"
  tap "Serien" || fail "Touch: Filme – „Serien“ nicht antippbar"
  sleep 3; tap "📺 Live-TV" || { fail "Touch: Filme – „📺 Live-TV“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; }
  sleep 3
else fail "Touch: Menü – „Filme“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; fi
alive || fail "Touch: App nach Filme weg"
# Suchen
open_list; swipe_r; swipe_r
if tap "M Suchen"; then sleep 2; shot "test13-suche"; tap "Schließen" || { fail "Touch: Suche – „Schließen“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; }
else fail "Touch: Menü – „Suchen“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; fi
sleep 2
# Fußball heute: Spiel antippen, zurück
bar
if tap "⚽ Heute"; then sleep 8
  if qa | grep -q "POS F 0"; then tap "F 0"; sleep 3; shot "test13-spiel"; adb shell input keyevent KEYCODE_BACK; sleep 2; fi
  adb shell input keyevent KEYCODE_BACK; sleep 2
fi
alive || fail "Touch: App nach Fußball weg"
# Schnellmenü: Infoleiste umschalten
bar; tap "⋯ Menü" && { tap "QM Infoleiste" || fail "Touch: Schnellmenü – „Infoleiste“ nicht antippbar"; adb shell input keyevent KEYCODE_BACK; sleep 1; }
note "Touch-Test" "$(qa | grep -E "SET_|SORT_OPEN|SORT_CLOSE|LIST_OPEN|MV_|FIRST_FRAME" | tail -40)"
alive || fail "Touch: App nicht mehr da"
hang_check "Touch" "$T13"

# sichtbar? (letzte gemeldete Position des Knopfs, mindestens 20 px zu sehen)
pos_visible() {
  # letzte gemeldete Position (die Knopfleiste blendet sich nach ein paar Sekunden von selbst aus – das ist kein Fehler)
  local l v; l=$(qa | grep -F -e "POS $1|" | sed 's/.*AzeQA[^:]*: //' | tail -1)
  case "$l" in "POS $1|"*) v=$(echo "$l" | cut -d'|' -f4); [ "${v:-0}" -ge 20 ] ;; *) return 1 ;; esac
}
is_landscape() { local WH; WH=$(scr); [ "${WH%x*}" -gt "${WH#*x}" ]; }

log "== Test 14: Handy (quer, ca. 870×390 dp wie ein Xiaomi 12 Pro) – Geräte-Erkennung, Abspielen-Knopf sichtbar, Fotos"
T14=$(date +%H:%M:%S)
adb shell wm size 576x1280; adb shell wm density 235; sleep 2
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device phone --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true >/dev/null
sleep 10
log "Handy: Bildschirm $(scr) · $(qa | grep -o "DEVICE .*" | tail -1)"
qa | grep -o "DEVICE .*" | tail -1 | grep -q "^DEVICE PHONE" || fail "Handy: Geräteart ist nicht „Handy“"
is_landscape || fail "Handy: Bildschirm ist nicht quer ($(scr))"
WH=$(scr); SW=${WH%x*}; SH=${WH#*x}
wait_for "VOD_CAT" 1 40 >/dev/null; sleep 6; shot "test14-handy-filme"
if tap "V 1"; then
  sleep 4; shot "test14-handy-film"
  pos_visible "Abspielen" || pos_visible "Von vorn" || fail "Handy: „Abspielen“ ist in den Film-Details nicht zu sehen"
  pos_visible "Zurück" || fail "Handy: „Zurück“ ist in den Film-Details nicht zu sehen"
  # Vormerken antippen (und wieder weg, damit die Liste leer bleibt)
  nm=$(count "MERKLISTE_NEU")
  if tap "Vormerken"; then
    sleep 1; [ "$(count "MERKLISTE_NEU")" -gt "$nm" ] || fail "Handy: „Vormerken“ antippen merkt nichts"
    shot "test14-handy-vorgemerkt"
    tap "✓ Vorgemerkt" || fail "Handy: „✓ Vorgemerkt“ nicht antippbar"; sleep 1
  else fail "Handy: „Vormerken“ ist in den Film-Details nicht antippbar"; fi
  # Wischen durch die Filme der Kategorie: ↑ nächster (mit Beschreibung), ↓ voriger, → zurück zur Kachel-Übersicht
  WH=$(scr); SW=${WH%x*}; SH=${WH#*x}
  adb shell input swipe $((SW/2)) $((SH*3/4)) $((SW/2)) $((SH/4)) 250; sleep 3
  [ "$(count "VOD_WISCHEN 2/")" -ge 1 ] || fail "Handy: nach oben wischen zeigt nicht den nächsten Film"
  shot "test14-handy-film-gewischt"
  adb shell input swipe $((SW/2)) $((SH/4)) $((SW/2)) $((SH*3/4)) 250; sleep 3
  [ "$(count "VOD_WISCHEN 1/")" -ge 1 ] || fail "Handy: nach unten wischen geht nicht zum vorigen Film"
  note "Wischen" "$(qa | grep VOD_WISCHEN | tail -2 | tr '\n' ' ')"
  nv=$(qa | grep -c "POS V 1|" || true)
  adb shell input swipe $((SW/5)) $((SH/2)) $((SW*4/5)) $((SH/2)) 250; sleep 3
  if [ "$(qa | grep -c "POS V 1|" || true)" -le "$nv" ]; then
    fail "Handy: zur Seite wischen führt nicht zurück zur Übersicht"
    tap "Zurück" || adb shell input keyevent KEYCODE_BACK; sleep 2
  fi
else fail "Handy: Film-Cover nicht antippbar"; fi
tap "Serien" && { sleep 4; shot "test14-handy-serien"; }
tap "📺 Live-TV" || { adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; }
sleep 4
adb shell input keyevent KEYCODE_CHANNEL_UP; sleep 1.5; shot "test14-handy-infoleiste"; sleep 5
bar; sleep 1; shot "test14-handy-knopfleiste"
pos_visible "☰ Sender" || fail "Handy: Knopf „☰ Sender“ nicht zu sehen"
pos_visible "✕ Beenden" || pos_visible "⚙ Einstellungen" || fail "Handy: Knopfleiste passt nicht auf den Bildschirm"
if open_list; then sleep 1; shot "test14-handy-senderliste"; swipe_r; shot "test14-handy-gruppen"; swipe_r; shot "test14-handy-menue"
  tap "M Einstellungen" || fail "Handy: „Einstellungen“ im Seitenmenü nicht erreichbar"
  sleep 2; for d in 0 0 0 0; do tap "PIN $d" || break; done; sleep 2
  adb shell input keyevent KEYCODE_BACK; sleep 2
  else fail "Handy: Senderliste geht nicht auf"; fi
bar; tap "⋯ Menü" && { sleep 2; shot "test14-handy-schnellmenue"; adb shell input keyevent KEYCODE_BACK; sleep 1; }
bar
if tap "⚙ Einstellungen"; then
  sleep 2; for d in 0 0 0 0; do tap "PIN $d" || break; done
  sleep 2; shot "test14-handy-einstellungen"
  open_pane "Aussehen" && { sleep 1; shot "test14-handy-aussehen"; }
  open_pane "Sender & Playlists" && { sleep 1; shot "test14-handy-playlists"
    # Ablauf als Ampel: unter der Playlist – hinscrollen (langsam, ohne Schwung)
    for i in 1 2 3; do pos_visible "ZUGANG Test-VOD" && break; adb shell input swipe $((SW*3/4)) $((SH*3/4)) $((SW*3/4)) $((SH/3)) 600; sleep 1.5; done
    shot "test14-handy-ampel"; pos_visible "ZUGANG Test-VOD" || note "Ampel" "nicht ins Bild gescrollt"; }
  # Kategorien ausblenden am Handy: der Dialog muss in die geringe Höhe passen – „Alle auswählen“ und „OK“ sichtbar
  if open_pane "Filme & Serien"; then
    sleep 1; kb=$(qa | grep -o "POS Filme-Kategorien ausblenden[^|]*" | tail -1 | sed 's/^POS //')
    if [ -n "$kb" ] && tap "$kb"; then
      sleep 4; shot "test14-handy-ausblenden"
      pos_visible "Alle auswählen" || fail "Handy: im Dialog „Kategorien ausblenden“ ist „Alle auswählen“ nicht zu sehen"
      pos_visible "ALLE OBEN" || fail "Handy: „Alle auswählen“ oben im Dialog ist nicht zu sehen"
      pos_visible "OK" || fail "Handy: im Dialog „Kategorien ausblenden“ ist „OK“ nicht zu sehen"
      na=$(count "AUSBLENDEN_ALLE")
      tap "Alle auswählen" && { sleep 1; [ "$(count "AUSBLENDEN_ALLE")" -gt "$na" ] || fail "Handy: „Alle auswählen“ hakt nichts an"; shot "test14-handy-alle-ausgewaehlt"; }
      tap "Abbrechen" || adb shell input keyevent KEYCODE_BACK; sleep 2
    else fail "Handy: Knopf „Filme-Kategorien ausblenden“ nicht antippbar"; fi
  fi
  open_pane "Bild & Ton" && { sleep 1; shot "test14-handy-bild-ton"
    pos_visible "PIP SCHALTER" || { adb shell pm list features 2>/dev/null | grep -q picture_in_picture && fail "Handy: Schalter „Bild-im-Bild“ fehlt in Bild & Ton"; }; }
  adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
else fail "Handy: „⚙ Einstellungen“ nicht antippbar"; fi
# Bild-im-Bild: Home-Taste, während ein Sender läuft → kleines Fenster, der Sender läuft weiter
if adb shell pm list features 2>/dev/null | grep -q "picture_in_picture"; then
  np=$(count "BILD_IM_BILD ")
  adb shell input keyevent KEYCODE_HOME; sleep 5; shot "test14-handy-bild-im-bild"
  if [ "$(count "BILD_IM_BILD ")" -gt "$np" ]; then
    picture_back "Bild-im-Bild" 15 || true
    note "Bild-im-Bild" "$(qa | grep "BILD_IM_BILD" | tail -1)"
  else fail "Bild-im-Bild: Home-Taste öffnet kein kleines Fenster ($(qa | grep -E 'BILD_IM_BILD' | tail -1))"; fi
  adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4
else note "Bild-im-Bild" "Emulator kann kein Bild-im-Bild – nicht geprüft"; fi
alive || fail "Handy: App nicht mehr da"
hang_check "Handy" "$T14"

log "== Test 15: Fernseher (960×540 dp, Fernbedienung, keine Touch-Knöpfe) – Fotos der wichtigsten Seiten"
T15=$(date +%H:%M:%S)
adb shell wm size 540x960; adb shell wm density 160; sleep 2
n=$(count "FIRST_FRAME")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv >/dev/null
sleep 3; wait_for "FIRST_FRAME" $((n+1)) 40 >/dev/null; sleep 4
log "Fernseher: Bildschirm $(scr) · $(qa | grep -o "DEVICE .*" | tail -1)"
qa | grep -o "DEVICE .*" | tail -1 | grep -q "^DEVICE TV" || fail "Fernseher: Geräteart ist nicht „Fernseher“"
is_landscape || fail "Fernseher: Bildschirm ist nicht quer ($(scr))"
shot "test15-tv-bild"
adb shell input keyevent --longpress KEYCODE_DPAD_RIGHT; sleep 2; shot "test15-tv-infoleiste"; sleep 6
o=$(count "LIST_OPEN")
adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 2; shot "test15-tv-senderliste"
[ "$(count "LIST_OPEN")" -gt "$o" ] || fail "Fernseher: ◀ öffnet die Senderliste nicht"
adb shell input keyevent KEYCODE_DPAD_DOWN; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 2; shot "test15-tv-gruppen"
adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 2; shot "test15-tv-menue"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2
adb shell input keyevent KEYCODE_PROG_GREEN; sleep 2; shot "test15-tv-schnellmenue"; adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_PROG_BLUE; sleep 3
for d in 0 0 0 0; do adb shell input keyevent KEYCODE_$d; done   # Standard-PIN 0000
sleep 3; shot "test15-tv-einstellungen"
for i in $(seq 1 9); do adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.6; done
sleep 1; shot "test15-tv-aussehen"
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
log "Fernseher: Handy-Eingabe mit der Fernbedienung starten – die Markierung darf nicht aus dem Bereich fallen"
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --ez aze_test_settings true >/dev/null; sleep 6
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.6; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1.5
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
ok=0
for i in 1 2 3 4 5 6; do
  [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS Handy-Eingabe starten" ] && { ok=1; break; }
  adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.8
done
if [ $ok = 1 ]; then
  adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 3; shot "test15-tv-handy-eingabe"
  pane=$(qa | grep -o "SET_PANE .*" | tail -1)
  [ "$pane" = "SET_PANE Sender & Playlists" ] || fail "Fernseher: nach „Handy-Eingabe starten“ springt die Ansicht weg ($pane)"
  note "Fernseher Handy-Eingabe" "Bereich danach: $pane · Markierung danach: $(qa | grep -o 'FOCUS .*' | tail -1)"
else
  note "Fernseher Handy-Eingabe" "Knopf mit der Fernbedienung nicht erreicht: $(qa | grep -o 'FOCUS .*' | tail -4 | tr '\n' ' ')"
fi
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
log "Fernseher: Kategorien ausblenden – „Alle auswählen“ steht oben und hat gleich die Markierung (alles mit der Fernbedienung)"
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --ez aze_test_settings true >/dev/null; sleep 6
if focus_to "SET Filme & Serien" KEYCODE_DPAD_DOWN 14; then
  sleep 1.5; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
  ok=0
  for i in $(seq 1 22); do
    # Filme oder Serien – derselbe Dialog (▼ landet je nach Breite auf dem einen oder anderen Knopf)
    qa | grep -o "FOCUS .*" | tail -1 | grep -qE "^FOCUS (Filme|Serien)-Kategorien ausblenden" && { ok=1; break; }
    adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.8
  done
  if [ $ok = 1 ]; then
    adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 4; shot "test15-tv-ausblenden"
    pos_visible "ALLE OBEN" || fail "Fernseher: „Alle auswählen“ oben im Dialog fehlt"
    [ "$(qa | grep -o "FOCUS .*" | tail -1)" = "FOCUS Alle auswählen" ] || fail "Fernseher: Markierung steht nicht auf „Alle auswählen“ ($(qa | grep -o 'FOCUS .*' | tail -1))"
    na=$(count "AUSBLENDEN_ALLE")
    adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 1.5
    [ "$(count "AUSBLENDEN_ALLE")" -gt "$na" ] || fail "Fernseher: OK auf „Alle auswählen“ hakt nichts an"
    shot "test15-tv-alle-ausgewaehlt"
    adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
    qa | grep -o "FOCUS .*" | tail -1 | grep -q "^FOCUS H " || fail "Fernseher: ▼ unter „Alle auswählen“ führt nicht in die Liste ($(qa | grep -o 'FOCUS .*' | tail -1))"
    adb shell input keyevent KEYCODE_BACK; sleep 2   # schließt, ohne etwas zu übernehmen
  else fail "Fernseher: Knopf „Filme-Kategorien ausblenden“ mit der Fernbedienung nicht erreicht ($(qa | grep -o 'FOCUS .*' | tail -3 | tr '\n' ' '))"; fi
else fail "Fernseher: Bereich „Filme & Serien“ mit der Fernbedienung nicht erreicht ($(qa | grep -o 'FOCUS .*' | tail -2 | tr '\n' ' '))"; fi
for i in 1 2 3; do adb shell input keyevent KEYCODE_BACK; sleep 2; done
c=$(count "VOD_CAT")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --es aze_test_xtream "http://10.0.2.2:8090" --ez aze_test_vod true >/dev/null
wait_for "VOD_CAT" $((c+1)) 40 >/dev/null; sleep 8; shot "test15-tv-filme"
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1; adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2; shot "test15-tv-filme-raster"
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 4; shot "test15-tv-film"
adb shell input keyevent KEYCODE_BACK; sleep 2
alive || fail "Fernseher: App nicht mehr da"
hang_check "Fernseher" "$T15"
adb shell wm size reset; adb shell wm density reset

log "== Test 16: Sender mit Dolby-Ton (AC3) – Ton-Umwandler in der App springt ein, Bild und Ton laufen"
c=$(count "FIRST_FRAME Test D"); cd0=$(count "TON_DECODER"); ce0=$(count "ERROR ")
adb shell wm size reset; adb shell wm density reset
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --es aze_test_playlist "http://10.0.2.2:8090/dolby.m3u" --es aze_test_play_url "http://10.0.2.2:8090/dolby.ts" >/dev/null
if wait_for "FIRST_FRAME Test D" $((c+1)) 90 >/dev/null; then
  sleep 20; shot "test16-dolby"
  dec=$(qa | grep "TON_DECODER" | tail -n +$((cd0+1)) | tail -1 | sed 's/.*TON_DECODER //')
  if [ -z "$dec" ]; then fail "Dolby: kein Ton-Entschlüssler gestartet (Ton fehlt)"
  else note "Dolby-Ton" "Entschlüssler: $dec"; fi
  [ "$(count "ERROR ")" -eq "$ce0" ] || fail "Dolby: Abspielfehler:$(qa | grep 'ERROR ' | tail -1 | sed 's/.*AzeQA *: //')"
  w=$(qa | grep -c "WATCHDOG.*Test D" || true); [ "$w" -eq 0 ] || fail "Dolby: Wächter musste eingreifen ($w×)"
else
  fail "Dolby: Sender mit AC3-Ton startet nicht"
fi
alive || fail "Dolby: App nicht mehr da"

log "== Test 17: Fernhilfe – Code, verschlüsselter Briefkasten, Senderliste aus der Ferne ausblenden/einblenden, Playlist umbenennen"
c=$(count "FH_TEST_OK"); cf=$(count "FH_TEST_FAIL")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --es aze_test_fernhilfe "http://10.0.2.2:8090/ntfy" >/dev/null
for i in $(seq 1 120); do
  [ "$(count "FH_TEST_OK")" -gt "$c" ] && break
  [ "$(count "FH_TEST_FAIL")" -gt "$cf" ] && break
  sleep 2
done
fh=$(qa | grep -E "FH_" | tail -14 | sed 's/.*AzeQA *: //')
if [ "$(count "FH_TEST_OK")" -gt "$c" ]; then note "Fernhilfe" "$fh"
else fail "Fernhilfe:$(qa | grep 'FH_TEST_FAIL' | tail -1 | sed 's/.*AzeQA *: //') · $(echo "$fh" | tail -4 | tr '\n' ' ')"; fi
shot "test17-fernhilfe"
alive || fail "Fernhilfe: App nicht mehr da"
# echter Briefkasten (ntfy.sh) vom Test-Rechner aus: kleine Nachricht + Anhang wie die App sie schickt
t="azefhci$RANDOM$RANDOM"
r1=$(curl -s -m 20 -d "AZF1:test" "https://ntfy.sh/$t" | head -c 200)
head -c 6000 /dev/urandom | base64 -w0 > /tmp/fh_big.txt
r2=$(curl -s -m 30 -X PUT -H "Filename: aze.txt" --data-binary @/tmp/fh_big.txt "https://ntfy.sh/$t" | head -c 300)
sleep 2
r3=$(curl -s -m 20 "https://ntfy.sh/$t/json?poll=1&since=all" | head -c 900)
r4=$(curl -s -N -m 6 "https://ntfy.sh/$t/json?since=all" | head -c 900)
u=$(echo "$r4$r3" | grep -o 'https://ntfy.sh/file/[A-Za-z0-9]*\.txt' | head -1)
r5=$( [ -n "$u" ] && curl -s -m 20 "$u" | wc -c)
cors=$(curl -s -m 10 -o /dev/null -D - -H "Origin: https://postmen1971.github.io" "https://ntfy.sh/$t/json?poll=1" | grep -i "access-control-allow-origin" | tr -d '\r')
cors2=$( [ -n "$u" ] && curl -s -m 10 -o /dev/null -D - -H "Origin: https://postmen1971.github.io" "$u" | grep -i "access-control-allow-origin" | tr -d '\r')
note "Fernhilfe ntfy.sh" "klein: $r1 · Anhang: $r2 · abholen (poll): $r3 · Strom: $r4 · Anhang geladen: $r5 Bytes · CORS: $cors / Anhang: $cors2"

log "== Test 18: Einstellungen öffnen zugeklappt (nur App-Update offen) – auf-/zuklappen mit Fernbedienung und Touch"
adb shell wm size 540x960; adb shell wm density 160; sleep 2
p0=$(count "POS Handy-Eingabe starten|"); a0=$(count "SEC_AUF"); z0=$(count "SEC_ZU"); pA0=$(count "POS Ändern|")
adb shell am start -S -n $PKG/.MainActivity --es aze_test_device tv --ez aze_test_settings true --ez aze_test_sections_closed true >/dev/null; sleep 6
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 0.6; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1.5
shot "test18-zugeklappt"
[ "$(count "POS Handy-Eingabe starten|")" -eq "$p0" ] || fail "Einstellungen: „Playlist vom Handy eingeben“ ist beim Öffnen schon aufgeklappt"
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 1
f1=$(qa | grep -o "FOCUS .*" | tail -1)
case "$f1" in "FOCUS SEC "*) ;; *) fail "Fernbedienung: ▶ landet nicht auf einer Abschnitts-Überschrift ($f1)";; esac
t1="${f1#FOCUS SEC }"; n0=$(count "SEC_")
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 1.5
[ "$(count "SEC_")" -gt "$n0" ] || fail "Fernbedienung: OK klappt „$t1“ nicht auf/zu"
f2=$(qa | grep -o "FOCUS .*" | tail -1); [ "$f2" = "$f1" ] || fail "Fernbedienung: Markierung nach dem Klappen verloren ($f1 → $f2)"
shot "test18-geklappt"
n1=$(count "SEC_")
adb shell input keyevent KEYCODE_DPAD_CENTER; sleep 1.5
[ "$(count "SEC_")" -gt "$n1" ] || fail "Fernbedienung: zweites OK klappt „$t1“ nicht zurück"
f3=$(qa | grep -o "FOCUS .*" | tail -1); [ "$f3" = "$f1" ] || fail "Fernbedienung: Markierung nach dem Zurückklappen verloren ($f1 → $f3)"
last=$(qa | grep -oF -e "SEC_AUF $t1" -e "SEC_ZU $t1" | tail -1)
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
f4=$(qa | grep -o "FOCUS .*" | tail -1)
if [ "${last%% *}" = "SEC_ZU" ]; then
  case "$f4" in "FOCUS SEC "*) [ "$f4" != "$f1" ] || fail "Fernbedienung: ▼ kommt nicht zum nächsten Abschnitt";; *) fail "Fernbedienung: ▼ landet nicht auf dem nächsten Abschnitt ($f4)";; esac
else
  [ "$f4" != "$f1" ] || fail "Fernbedienung: ▼ bewegt die Markierung nicht"
fi
# Touch: Abschnitt „Playlists“ per Antippen auf und zu
T="Freie Sender (iptv-org)"
[ "$(count "POS Ändern|")" -gt "$pA0" ] || fail "Einstellungen: „Playlists“ (grundlegend) ist beim Öffnen nicht offen"
a1=$(count "SEC_AUF $T"); z1=$(count "SEC_ZU $T")
tap "SEC $T" || fail "Touch: Abschnitt „$T“ nicht antippbar"
sleep 1.5; [ "$(count "SEC_AUF $T")" -gt "$a1" ] || fail "Touch: Antippen klappt „$T“ nicht auf"
shot "test18-touch-auf"
tap "SEC $T" || true
sleep 1.5; [ "$(count "SEC_ZU $T")" -gt "$z1" ] || fail "Touch: zweites Antippen klappt „$T“ nicht zu"
# System: App-Update ist von selbst offen
u0=$(count "POS Nach Update suchen|")
tap "SET System" || fail "Touch: Bereich „System“ nicht antippbar"
sleep 2; shot "test18-system"
[ "$(count "POS Nach Update suchen|")" -gt "$u0" ] || fail "Einstellungen: „App-Update“ ist beim Öffnen nicht offen"
note "Einstellungen zugeklappt" "Markierung: $f1 → nach OK $f2 → nach OK $f3 → ▼ $f4 · Abschnitte auf: $(count SEC_AUF) zu: $(count SEC_ZU)"
alive || fail "Einstellungen zugeklappt: App nicht mehr da"
adb shell wm size reset; adb shell wm density reset

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
