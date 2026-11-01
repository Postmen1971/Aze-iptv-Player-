#!/usr/bin/env bash
# Test-Videos für den Emulator-Test (frei erzeugt, keine echten Sender)
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p media
V="-c:v libx264 -preset veryfast -profile:v main -pix_fmt yuv420p -g 50 -b:v 600k -maxrate 700k -bufsize 1400k"
A="-c:a aac -b:a 96k -ar 48000"
# Normales Programm, 4 Minuten
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=25" -f lavfi -i "sine=frequency=440:sample_rate=48000" \
  -t 240 $V $A -f mpegts media/ok.ts
# Bild hört nach 15 s auf, Ton läuft weiter (stehendes Bild)
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=25:duration=15" -f lavfi -i "sine=frequency=660:sample_rate=48000:duration=150" \
  $V $A -f mpegts media/vstop.ts
# Zeitsprung: 15 s normal, danach springt die BILD-Zeit um 60 s nach vorn, der Ton läuft normal weiter
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=25" -f lavfi -i "sine=frequency=500:sample_rate=48000" \
  -t 15 $V $A -f mpegts media/jA.ts
ffmpeg -loglevel error -y -itsoffset 60 -f lavfi -i "testsrc2=size=640x360:rate=25" -f lavfi -i "sine=frequency=500:sample_rate=48000" \
  -t 120 -fps_mode passthrough $V $A -output_ts_offset 15 -f mpegts media/jB.ts
cat media/jA.ts media/jB.ts > media/jump.ts
rm -f media/jA.ts media/jB.ts
ls -la media
