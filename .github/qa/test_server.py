"""Test-Server für den Emulator-Test: liefert Test-Sender wie ein IPTV-Anbieter in Echtzeit.
  /list.m3u   Playlist
  /ok.ts      läuft normal
  /stall.ts   nach 15 s kommen keine Daten mehr, Verbindung bleibt offen (tote Verbindung)
  /vstop.ts   nach 15 s nur noch Ton, kein Bild
  /drop.ts    nach 15 s trennt der Server die Verbindung
"""
import os, sys, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MEDIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "media")
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
DURATION = {"ok.ts": 240.0, "vstop.ts": 150.0}

PLAYLIST = """#EXTM3U
#EXTINF:-1 group-title="QA",Test 1 Normal
http://10.0.2.2:{p}/ok.ts
#EXTINF:-1 group-title="QA",Test 2 Haenger
http://10.0.2.2:{p}/stall.ts
#EXTINF:-1 group-title="QA",Test 3 Bild steht
http://10.0.2.2:{p}/vstop.ts
#EXTINF:-1 group-title="QA",Test 4 Abbruch
http://10.0.2.2:{p}/drop.ts
""".format(p=PORT)


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, *a):
        pass

    def do_GET(self):
        path = self.path.split("?")[0].lstrip("/")
        log(f"GET /{path}")
        if path == "list.m3u":
            body = PLAYLIST.encode()
            self.send_response(200)
            self.send_header("Content-Type", "audio/x-mpegurl")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        mode = {"ok.ts": ("ok.ts", None), "stall.ts": ("ok.ts", "stall"),
                "vstop.ts": ("vstop.ts", None), "drop.ts": ("ok.ts", "drop")}.get(path)
        if mode is None:
            self.send_error(404)
            return
        name, behaviour = mode
        f = os.path.join(MEDIA, name)
        size = os.path.getsize(f)
        rate = size / DURATION[name]  # Bytes pro Sekunde = Echtzeit
        self.send_response(200)
        self.send_header("Content-Type", "video/mp2t")
        self.end_headers()
        start = time.time()
        sent = 0
        burst = rate * 2  # wie echte Anbieter: kleiner Vorrat sofort
        try:
            with open(f, "rb") as fh:
                while True:
                    elapsed = time.time() - start
                    if behaviour and elapsed >= 15:
                        if behaviour == "stall":
                            log("stall: keine Daten mehr")
                            time.sleep(90)
                        else:
                            log("drop: Verbindung getrennt")
                        return
                    allowed = burst + rate * elapsed
                    if sent >= allowed:
                        time.sleep(0.05)
                        continue
                    chunk = fh.read(min(16384, int(allowed - sent) + 188))
                    if not chunk:
                        return
                    self.wfile.write(chunk)
                    sent += len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            log(f"/{path}: Client hat getrennt")


ThreadingHTTPServer.daemon_threads = True
log(f"Test-Server auf Port {PORT}")
ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
