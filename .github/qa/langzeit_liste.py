#!/usr/bin/env python3
"""Langzeit-Test: frei empfangbare Sender aus der öffentlichen iptv-org-Liste suchen und vorab prüfen.

Nur Sender, die der GitHub-Rechner wirklich bekommt (nicht ländergesperrt, Server antwortet, Daten fließen),
kommen in die Test-Liste. Es wird nichts aufgenommen oder weitergegeben – die App spielt die Sender nur ab.

Aufruf: langzeit_liste.py <Ausgabeordner> [max_de] [max_tr]
Ergebnis: <Ordner>/list.m3u (für die App) und <Ordner>/sender.txt (Prüfwerte, für den Bericht)
"""
import concurrent.futures as cf
import os
import re
import sys
import time
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
LISTS = {
    "DE": "https://iptv-org.github.io/iptv/countries/de.m3u",
    "TR": "https://iptv-org.github.io/iptv/countries/tr.m3u",
}
GROUP = {"DE": "Deutschland", "TR": "Türkei"}
# Öffentlich-rechtliche zuerst: laufen erfahrungsgemäß rund um die Uhr und stabil
PREFER = {
    "DE": re.compile(r"^(Das Erste|ZDF|ZDFneo|ZDFinfo|3sat|arte|tagesschau24|phoenix|KiKA|ONE|ARD|BR|SWR|WDR|NDR|MDR|hr|rbb|DW)\b", re.I),
    "TR": re.compile(r"^(TRT|TBMM)", re.I),
}
# Vorab-Prüfung besteht, im Emulator aber jedes Mal HTTP 403 (Sender sperrt; Langzeit-Läufe 23.–25.11.) – sonst
# verschwendet so ein Sender jedes Mal 8 Minuten im Abschnitt D
AUSSCHLIESSEN = {"trt 3"}
MAX_HEIGHT = 576  # Emulator dekodiert in Software – große Auflösungen würden nur den Emulator testen, nicht die App
TOKENISH = re.compile(r"(token|expires|hdnts|hdnea|signature|sig=|policy|e=\d{9,}|st=\d{9,})", re.I)


def get(url, limit=None, timeout=10.0):
    """Lädt url (höchstens limit Bytes). Gibt (bytes, endgültige Adresse, Sekunden) zurück."""
    t0 = time.time()
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        if r.status >= 400:
            raise IOError(f"HTTP {r.status}")
        data = b""
        while True:
            chunk = r.read(65536 if limit is None else min(65536, limit - len(data)))
            if not chunk:
                break
            data += chunk
            if limit is not None and len(data) >= limit:
                break
            if time.time() - t0 > timeout:
                raise IOError("zu langsam")
        return data, r.geturl(), time.time() - t0


def parse_m3u(text):
    out = []
    attrs, name, opts = None, None, False
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("#EXTINF"):
            attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', line))
            i = line.rfind('",')
            name = line[i + 2:] if i >= 0 else line.split(",", 1)[-1]
            opts = "http-referrer" in attrs or "http-user-agent" in attrs
        elif line.startswith("#EXTVLCOPT"):
            opts = True
        elif line and not line.startswith("#") and name is not None:
            out.append({"name": name.strip(), "attrs": attrs or {}, "url": line, "needs_headers": opts})
            attrs, name, opts = None, None, False
    return out


def clean(name):
    name = re.sub(r"\s*\[[^\]]*\]", "", name)
    name = re.sub(r"\s*\(\d+[pi]\)", "", name)
    return name.strip()


def probe(entry):
    """Prüft einen Sender. Gibt dict mit Ergebnis zurück (ok True/False, grund, url, …)."""
    url = entry["url"]
    res = {"ok": False, "grund": "", "url": url, "art": "", "hoehe": 0, "kbps": 0, "ms": 0}
    try:
        t0 = time.time()
        head, final, _ = get(url, limit=400_000, timeout=8)
        if head.lstrip()[:7] == b"#EXTM3U":
            res["art"] = "HLS"
            text = head.decode("utf-8", "replace")
            play_url, media_text, media_final = url, text, final
            if "#EXT-X-STREAM-INF" in text:
                variants = []
                lines = text.splitlines()
                for i, l in enumerate(lines):
                    if l.startswith("#EXT-X-STREAM-INF"):
                        bw = int((re.search(r"BANDWIDTH=(\d+)", l) or [0, 0])[1] or 0)
                        m = re.search(r"RESOLUTION=\d+x(\d+)", l)
                        h = int(m.group(1)) if m else 0
                        j = i + 1
                        while j < len(lines) and (not lines[j].strip() or lines[j].startswith("#")):
                            j += 1
                        if j < len(lines):
                            variants.append((h, bw, urllib.parse.urljoin(final, lines[j].strip())))
                if not variants:
                    raise IOError("keine Varianten")
                fit = [v for v in variants if 0 < v[0] <= MAX_HEIGHT]
                if fit:
                    pick = max(fit, key=lambda v: (v[0], v[1]))
                else:
                    small = [v for v in variants if v[1] and v[1] <= 3_500_000]
                    pick = max(small, key=lambda v: v[1]) if small else min(variants, key=lambda v: v[1] or 1 << 40)
                res["hoehe"] = pick[0]
                media, media_final, _ = get(pick[2], limit=400_000, timeout=8)
                media_text = media.decode("utf-8", "replace")
                # Variante direkt nehmen (weniger Rechenlast im Emulator) – außer die Adresse trägt einen Zugangs-Schlüssel,
                # der während des Tests ablaufen könnte: dann die Hauptliste
                play_url = pick[2] if not TOKENISH.search(urllib.parse.urlparse(pick[2]).query) else url
            if "#EXT-X-ENDLIST" in media_text:
                raise IOError("kein Live-Sender (ENDLIST)")
            segs = [l.strip() for l in media_text.splitlines() if l.strip() and not l.startswith("#")]
            if len(segs) < 2:
                raise IOError("zu wenige Stücke")
            seg_url = urllib.parse.urljoin(media_final, segs[-1])
            data, _, secs = get(seg_url, limit=3_000_000, timeout=12)
            if len(data) < 40_000:
                raise IOError(f"Stück zu klein ({len(data)} B)")
            res["kbps"] = int(len(data) * 8 / 1000 / max(secs, 0.05))
            res["url"] = play_url
        else:
            res["art"] = "TS"
            if b"\x47" not in head[:1024]:
                raise IOError("weder HLS noch TS")
            if len(head) < 200_000:
                raise IOError("liefert zu wenig")
        res["ms"] = int((time.time() - t0) * 1000)
        res["ok"] = True
    except Exception as e:  # noqa: BLE001 – jeder Fehler heißt: Sender nicht nehmen
        res["grund"] = str(e)[:80] or e.__class__.__name__
    return res


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "langzeit-list"
    want = {"DE": int(sys.argv[2]) if len(sys.argv) > 2 else 8, "TR": int(sys.argv[3]) if len(sys.argv) > 3 else 6}
    os.makedirs(out_dir, exist_ok=True)
    chosen, report = [], []
    for land, list_url in LISTS.items():
        try:
            text = get(list_url, timeout=30)[0].decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            report.append(f"{land}: Liste nicht ladbar ({e})")
            continue
        entries = [e for e in parse_m3u(text)
                   if not e["needs_headers"]
                   and not re.search(r"\[(Geo-blocked|Not 24/7)\]", e["name"], re.I)
                   and clean(e["name"]).lower() not in AUSSCHLIESSEN
                   and e["url"].startswith("http")]
        seen = set()
        uniq = []
        for e in sorted(entries, key=lambda e: 0 if PREFER[land].search(clean(e["name"])) else 1):
            key = clean(e["name"]).lower()
            if key in seen:
                continue
            seen.add(key)
            uniq.append(e)
        cands = uniq[:60]
        report.append(f"{land}: {len(entries)} Einträge ohne Sperr-/Kopfzeilen-Hinweis, {len(cands)} geprüft")
        with cf.ThreadPoolExecutor(max_workers=12) as ex:
            results = list(ex.map(probe, cands))
        got = 0
        for e, r in zip(cands, results):
            # gleiche Adresse unter zwei Namen (z. B. „BR Fernsehen“ Nord/Süd): die App führt sie nur einmal
            if r["ok"] and any(r["url"] == c[2]["url"] for c in chosen):
                report.append(f"  doppelt: {clean(e['name'])} (gleiche Adresse)")
                continue
            if r["ok"] and got < want[land]:
                got += 1
                chosen.append((land, e, r))
            elif not r["ok"]:
                report.append(f"  aus: {clean(e['name'])} – {r['grund']}")
        report.append(f"{land}: {got} von {want[land]} gewünschten Sendern erreichbar")
    with open(os.path.join(out_dir, "list.m3u"), "w", encoding="utf-8") as f:
        f.write("#EXTM3U\n")
        for land, e, r in chosen:
            f.write(f'#EXTINF:-1 tvg-id="{e["attrs"].get("tvg-id", "")}" group-title="{GROUP[land]}",{land}: {clean(e["name"])}\n')
            f.write(r["url"] + "\n")
    with open(os.path.join(out_dir, "sender.txt"), "w", encoding="utf-8") as f:
        f.write("Gewählte Sender (Vorab-Prüfung vom GitHub-Rechner aus):\n")
        for land, e, r in chosen:
            f.write(f"  {land}: {clean(e['name'])} · {r['art']}{' ' + str(r['hoehe']) + 'p' if r['hoehe'] else ''}"
                    f" · Prüfung {r['ms']} ms · Stück mit {r['kbps']} kbit/s geladen\n")
        f.write("\n" + "\n".join(report) + "\n")
    print(open(os.path.join(out_dir, "sender.txt"), encoding="utf-8").read())
    if len(chosen) < 4:
        print(f"::error title=Langzeit-Test::nur {len(chosen)} freie Sender erreichbar – zu wenig für den Test")
        sys.exit(1)
    print(f"::notice title=Langzeit-Sender::{len(chosen)} Sender: " + ", ".join(f"{l}: {clean(e['name'])}" for l, e, _ in chosen))


if __name__ == "__main__":
    main()
