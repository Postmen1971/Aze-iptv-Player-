"""Test-Server für den Emulator-Test: liefert Test-Sender wie ein IPTV-Anbieter in Echtzeit.
  /list.m3u   Playlist
  /ok.ts      läuft normal
  /stall.ts   nach 15 s kommen keine Daten mehr, Verbindung bleibt offen (tote Verbindung)
  /vstop.ts   nach 15 s nur noch Ton, kein Bild
  /drop.ts    wie ein echter Anbieter 3× schneller als Echtzeit (großer Vorrat), nach 15 s trennt der Server
  /one.ts     „Anbieter mit nur 1 Verbindung“: 2× schneller, trennt alle 20 s; eine neue Verbindung wird abgelehnt (403),
              solange die alte läuft und noch 2 s danach (Anbieter gibt den Platz verzögert frei)
"""
import os, sys, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MEDIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "media")
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
DURATION = {"ok.ts": 240.0, "vstop.ts": 150.0, "jump.ts": 135.0}

PLAYLIST = """#EXTM3U
#EXTINF:-1 group-title="QA",Test 1 Normal
http://10.0.2.2:{p}/ok.ts
#EXTINF:-1 group-title="QA",Test 2 Haenger
http://10.0.2.2:{p}/stall.ts
#EXTINF:-1 group-title="QA",Test 3 Bild steht
http://10.0.2.2:{p}/vstop.ts
#EXTINF:-1 group-title="QA",Test 4 Abbruch
http://10.0.2.2:{p}/drop.ts
#EXTINF:-1 group-title="QA",Test 5 Zeitsprung
http://10.0.2.2:{p}/jump.ts
#EXTINF:-1 group-title="QA",Test 6 Limit
http://10.0.2.2:{p}/one.ts
#EXTINF:-1 group-title="QA",Test 7 Normal B
http://10.0.2.2:{p}/ok2.ts
""".format(p=PORT) + "".join(
    # 25 weitere Gruppen (je 1 Sender) – damit „Senderliste verwalten“ scrollen muss (Sortier-Test)
    '#EXTINF:-1 group-title="Gruppe {n:02d}",Extra {n:02d}\nhttp://10.0.2.2:{p}/ok2.ts?g={n}\n'.format(n=n, p=PORT) for n in range(1, 26)
) + "".join(
    # letzte Gruppe mit 35 Sendern – für den Sender-Sortier-Test (Liste muss scrollen)
    '#EXTINF:-1 group-title="Viele Sender",Viel {n:02d}\nhttp://10.0.2.2:{p}/ok2.ts?v={n}\n'.format(n=n, p=PORT) for n in range(1, 36)
)


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


ONE_LOCK = threading.Lock()
ONE_BUSY = False
ONE_FREE_AT = 0.0


DEMO_GROUPS = [
    ("DE Deutsch", ["Das Erste HD", "ZDF HD", "RTL HD", "SAT.1 HD", "ProSieben HD", "VOX HD", "kabel eins HD", "RTL ZWEI HD", "Nitro HD", "3sat HD"]),
    ("DE Sport", ["Sky Sport Bundesliga 1 HD", "Sky Sport Bundesliga 2 HD", "Sky Sport Top Event HD", "Sky Sport Mix HD", "DAZN 1 HD", "DAZN 2 HD", "Sport1 HD", "Eurosport 1 HD", "sportdigital HD", "MagentaSport 1"]),
    ("TR Ulusal", ["TRT 1 HD", "ATV HD", "Show TV HD", "Kanal D HD", "Star TV HD", "TV8 HD", "NOW HD", "Beyaz TV HD"]),
    ("TR Spor", ["beIN SPORTS 1 HD", "beIN SPORTS 2 HD", "beIN SPORTS 3 HD", "S Sport HD", "S Sport 2 HD", "TRT Spor HD", "A Spor HD", "Tivibu Spor 1 HD"]),
    ("UK Sport", ["Sky Sports Main Event HD", "Sky Sports Premier League HD", "TNT Sports 1 HD", "TNT Sports 2 HD"]),
]
DEMO = "#EXTM3U\n" + "".join(
    '#EXTINF:-1 group-title="{g}",{n}\nhttp://10.0.2.2:{p}/ok2.ts?d={i}\n'.format(g=g, n=n, p=8090, i=abs(hash(n)) % 100000)
    for g, names in DEMO_GROUPS for n in names
)

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, *a):
        pass

    def do_GET(self):
        path = self.path.split("?")[0].lstrip("/")
        log(f"GET /{path}")
        if path == "player_api.php":
            return self.xtream()
        if path == "get.php":  # Live-Liste des Test-Anbieters: leer (die Sender kommen aus list.m3u)
            return self.send_body(b"#EXTM3U\n", "audio/x-mpegurl")
        if path.startswith("poster/"):
            time.sleep(0.05)
            with open(os.path.join(MEDIA, "poster.jpg"), "rb") as fh:
                return self.send_body(fh.read(), "image/jpeg")
        if path == "demo.m3u":  # wirklichkeitsnahe Namen für die Erklär-Videos (Bild kommt vom Test-Sender)
            return self.send_body(DEMO.encode(), "audio/x-mpegurl")
        if path == "list.m3u":
            body = PLAYLIST.encode()
            self.send_response(200)
            self.send_header("Content-Type", "audio/x-mpegurl")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        mode = {"ok.ts": ("ok.ts", None), "stall.ts": ("ok.ts", "stall"),
                "vstop.ts": ("vstop.ts", None), "drop.ts": ("ok.ts", "drop"), "jump.ts": ("jump.ts", None),
                "one.ts": ("ok.ts", "one"), "ok2.ts": ("ok.ts", None)}.get(path)
        if mode is None:
            self.send_error(404)
            return
        name, behaviour = mode
        global ONE_BUSY, ONE_FREE_AT
        if behaviour == "one":
            with ONE_LOCK:
                if ONE_BUSY or time.time() < ONE_FREE_AT:
                    log("one: abgelehnt (403) – Verbindung noch belegt")
                    self.send_error(403)
                    return
                ONE_BUSY = True
            try:
                self.stream(name, behaviour, speed=2.0, cut=20)
            finally:
                with ONE_LOCK:
                    ONE_BUSY = False
                    ONE_FREE_AT = time.time() + 2.0
            return
        self.stream(name, behaviour, speed=3.0 if behaviour == "drop" else 1.0, cut=15)

    def send_body(self, body, ctype):
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def xtream(self):
        """Test-Anbieter für Filme & Serien: 6 Kategorien, 3000 Filme, 600 Serien – mit Cover-Adressen."""
        import json, urllib.parse
        q = urllib.parse.parse_qs(self.path.split("?", 1)[1] if "?" in self.path else "")
        action = (q.get("action") or [""])[0]
        cat = (q.get("category_id") or [""])[0]
        base = "http://10.0.2.2:{p}".format(p=PORT)
        cats = [("1", "DE | Filme Action"), ("2", "DE | Filme Komödie"), ("3", "TR | Yerli Filmler"),
                ("4", "EN | Movies"), ("5", "DE | Kinder"), ("6", "TR | Aksiyon")]
        if action in ("get_vod_categories", "get_series_categories"):
            data = [{"category_id": c, "category_name": n} for c, n in cats]
        elif action == "get_vod_streams":
            data = [{"stream_id": i, "name": "Testfilm %d (2024)" % i, "stream_icon": base + "/poster/m%d.jpg" % i,
                     "rating": "7.1", "container_extension": "mp4", "category_id": str(i % 6 + 1)} for i in range(1, 3001)]
            if cat: data = [d for d in data if d["category_id"] == cat]
        elif action == "get_series":
            data = [{"series_id": i, "name": "Testserie %d" % i, "cover": base + "/poster/s%d.jpg" % i,
                     "rating": "8.0", "category_id": str(i % 6 + 1)} for i in range(1, 601)]
            if cat: data = [d for d in data if d["category_id"] == cat]
        elif action == "get_vod_info":
            data = {"info": {"plot": "Ein Test-Film für die Anleitung: Zwei Freunde suchen einen verlorenen Schatz und erleben dabei ein großes Abenteuer.",
                             "genre": "Abenteuer, Familie", "cast": "Anna Beispiel, Max Muster", "director": "Erika Test",
                             "duration": "01:42:00", "releasedate": "2024-05-01", "rating": "7.1"}, "movie_data": {}}
        elif action == "get_series_info":
            data = {"info": {"plot": "Eine Test-Serie für die Anleitung.", "genre": "Familie", "rating": "8.0", "releaseDate": "2023-01-01"},
                    "episodes": {"1": [{"id": "9%02d" % e, "title": "Folge %d" % e, "container_extension": "mp4", "info": {"duration": "00:45:00"}} for e in range(1, 7)]}}
        elif action == "":
            data = {"user_info": {"auth": 1, "status": "Active"}, "server_info": {"url": "10.0.2.2"}}
        else:
            data = []
        return self.send_body(json.dumps(data).encode(), "application/json")

    def stream(self, name, behaviour, speed, cut):
        path = name
        f = os.path.join(MEDIA, name)
        size = os.path.getsize(f)
        # Bytes pro Sekunde = Echtzeit. vstop hat nach 15 s nur noch Ton (wenige Bytes): mit der Rate des normalen
        # Senders liefern, sonst kommt der Bild-Teil viel zu langsam (das wäre ein anderer Fall: „Anbieter zu langsam“)
        ok = os.path.join(MEDIA, "ok.ts")
        rate = os.path.getsize(ok) / DURATION["ok.ts"] * speed
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
                    if behaviour and elapsed >= cut:
                        if behaviour == "stall":
                            log("stall: keine Daten mehr")
                            time.sleep(90)
                        else:
                            log(f"{behaviour}: Verbindung getrennt")
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
