#!/usr/bin/env python3
"""Baut die Hilfe-Seite (guide/index.html) aus den Emulator-Bildern in guide/img/.
Einfach erklärt, Schritt für Schritt – so, dass es auch ein Kind versteht."""
import html, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "guide"
IMG = os.path.join(ROOT, "img")

# (Kapitel, [(Bild, Überschrift, [Schritte])])
CHAPTERS = [
    ("Los geht's", [
        ("erster-start", "Der allererste Start", [
            "Beim ersten Start öffnet sich die App mit den Einstellungen.",
            "Links siehst du eine Liste mit Bereichen. Der hellgrüne Balken zeigt, wo du gerade bist.",
            "Mit den Pfeiltasten ▲ ▼ auf der Fernbedienung wanderst du hoch und runter. Mit OK wählst du aus.",
            "Auf dem Tablet tippst du einfach mit dem Finger drauf.",
        ]),
        ("handy-qr", "Playlist mit dem Handy eingeben", [
            "Geh links auf „Sender & Playlists“ und drücke ▶.",
            "Drücke OK auf „Handy-Eingabe starten“. Jetzt erscheint ein QR-Code und eine Zahl (der Code).",
            "Halte die Handy-Kamera auf den QR-Code und tippe auf den Link, der erscheint.",
            "Handy und Fernseher müssen im selben WLAN sein.",
        ]),
        ("handy-seite", "So sieht es auf dem Handy aus", [
            "Gib den Code vom Fernseher ein (wenn er nicht schon drinsteht).",
            "Füge den Playlist-Link ein, den du von deinem Anbieter bekommen hast.",
            "Tippe auf „Senden“. Ein paar Sekunden später sind die Sender auf dem Fernseher.",
        ]),
    ]),
    ("Fernsehen", [
        ("fernsehen", "Fernsehen schauen", [
            "Das ist das Fernsehbild (hier ein Test-Bild). Unten zeigt die Infoleiste den Sender und die Sendung.",
            "▲ ▼ schaltet zum nächsten oder vorherigen Sender. Du kannst auch die Nummer mit den Zifferntasten eingeben.",
            "Auf dem Tablet: nach oben oder unten wischen schaltet um. Einmal tippen zeigt die Knöpfe.",
        ]),
        ("senderliste", "Die Senderliste", [
            "Drücke ◀ – links erscheint die Liste aller Sender der Gruppe.",
            "Mit ▲ ▼ suchst du einen Sender aus, OK schaltet um.",
            "Auf dem Tablet: auf „☰ Sender“ tippen. Zum Schließen neben die Liste aufs Bild tippen.",
        ]),
        ("gruppen", "Gruppen (z. B. Deutsch, Sport, Türkisch)", [
            "Drücke in der Senderliste nochmal ◀ – jetzt siehst du die Gruppen.",
            "Wähle eine Gruppe mit OK, dann zeigt die Liste nur deren Sender.",
            "Auf dem Tablet: in der Liste nach rechts wischen.",
        ]),
        ("menue", "Das Menü", [
            "Noch einmal ◀ öffnet das Menü: Filme, Serien, Suchen, Fußball heute, Einstellungen und „App beenden“.",
        ]),
        ("schnellmenue", "Das Schnellmenü", [
            "Die grüne Taste (oder OK lang drücken) öffnet unten eine Leiste.",
            "Dort stellst du schnell Bildformat, Ton, Untertitel, Infoleiste und Multi-View ein.",
        ]),
        ("suche", "Sender suchen", [
            "Die gelbe Taste öffnet die Suche. Tippe ein paar Buchstaben, z. B. „sport“.",
            "Die passenden Sender erscheinen sofort – OK schaltet um.",
        ]),
        ("multiview", "Multi-View: zwei Sender gleichzeitig", [
            "Im Schnellmenü „Multi-View“ wählen.",
            "◀ ▶ wählt ein Bild (es bekommt einen grünen Rahmen), OK sucht einen Sender dafür aus.",
            "Auf dem Tablet: ein Bild antippen wählt es aus, nochmal antippen sucht einen Sender.",
            "Zurück beendet Multi-View – der gewählte Sender läuft weiter.",
        ]),
    ]),
    ("Fußball heute", [
        ("fussball", "Alle Spiele von heute", [
            "Im Menü „Fußball heute“ wählen.",
            "Du siehst alle Spiele von heute mit Uhrzeit und Liga. Rechts steht, wie viele Sender sie zeigen.",
        ]),
        ("fussball-sender", "Den richtigen Sender finden", [
            "OK auf ein Spiel: rechts erscheinen die passenden Sender aus deiner Liste.",
            "Beim Durchblättern zeigt das kleine Bild oben rechts den Sender als Vorschau.",
            "OK auf den Sender: Vollbild. Die App merkt sich, welcher Sender das Spiel gezeigt hat.",
        ]),
    ]),
    ("Filme & Serien", [
        ("filme", "Filme aussuchen", [
            "Im Menü „Filme“ oder „Serien“ wählen.",
            "Links die Kategorien, rechts die Cover. Mit den Pfeiltasten wandern, OK öffnet den Film.",
        ]),
        ("film-details", "Film abspielen", [
            "Hier stehen Inhalt, Länge und Bewertung.",
            "„Abspielen“ startet den Film. Hast du schon mal geschaut, kannst du dort weiterschauen.",
        ]),
    ]),
    ("Senderliste ordnen", [
        ("editor", "Senderliste bearbeiten", [
            "Einstellungen → „Senderliste & Favoriten“ → „Senderliste bearbeiten öffnen“.",
            "Links die Gruppen, rechts ihre Sender. Der Haken ☑ heißt: wird angezeigt.",
            "OK nimmt den Haken weg (ausblenden) – nochmal OK zeigt es wieder.",
        ]),
        ("editor-sender", "Sortieren", [
            "Drücke „Sortieren“. Jetzt nimmt OK eine Gruppe oder einen Sender auf.",
            "Mit ▲ ▼ an die neue Stelle schieben, OK legt ab.",
            "Auf dem Tablet: lange drücken, dann mit den Pfeil-Knöpfen ▲ ▼ verschieben, ✓ legt ab.",
        ]),
    ]),
    ("Einstellungen", [
        ("pin", "Die PIN", [
            "Die Einstellungen sind mit einer PIN geschützt, damit Kinder nichts verstellen.",
            "Am Anfang ist die PIN 0000. Du kannst sie unter „PIN & Kinderschutz“ ändern.",
        ]),
        ("einst-start", "Übersicht", [
            "Links die Bereiche, rechts ihr Inhalt. „Fernsehen“ ganz oben bringt dich zurück zum Bild.",
        ]),
        ("einst-suchen", "Suchen", ["Tippe ein Wort, z. B. „PIN“ – die App zeigt dir, wo die Einstellung steht."]),
        ("einst-sender-playlists", "Sender & Playlists", ["Playlists hinzufügen (auch mit dem Handy), freie Sender, YouTube-Livesender, Senderliste neu laden."]),
        ("einst-senderliste-favoriten", "Senderliste & Favoriten", ["Senderliste bearbeiten, sortieren, Favoriten mit eigener Nummer (1, 2, 3 …)."]),
        ("einst-filme-serien", "Filme & Serien", ["Kategorien ausblenden, Cover-Einstellungen."]),
        ("einst-fussball", "Fußball", ["Einstellungen für „Fußball heute“ und SkorText (Ergebnisse im Videotext-Stil)."]),
        ("einst-bild-ton", "Bild & Ton", ["Bildformat, Ton-Verzögerung (wenn Lippen und Ton nicht passen), Untertitel."]),
        ("einst-programm", "Programmzeitschrift", ["Woher das Fernsehprogramm kommt (EPG) und wann es neu geladen wird."]),
        ("einst-fernbedienung", "Fernbedienung", ["Welche Taste was macht – z. B. ▶ lang drücken = Infoleiste."]),
        ("einst-aussehen", "Aussehen", ["Farben der App und Schriftfarbe."]),
        ("einst-pin-kinderschutz", "PIN & Kinderschutz", ["PIN ändern, Erwachsenen-Inhalte ausblenden."]),
        ("einst-sichern", "Sichern", ["Alle Einstellungen sichern. Du bekommst einen Code – damit holst du alles auf einem anderen Gerät zurück."]),
        ("einst-system", "System", ["Autostart beim Einschalten, Speicher leeren, Version."]),
        ("einst-hilfe", "Hilfe", ["Erklär-Videos und diese Anleitung (QR-Code zum Öffnen am Handy)."]),
        ("einst-ueber", "Über", ["Version und Hinweise zur App."]),
    ]),
]

CSS = """
:root{--bg:#0b1530;--card:#13214a;--text:#eef3ff;--muted:#a9b6d8;--accent:#3ad6a8;--line:#24356a}
@media (prefers-color-scheme: light){:root:not([data-theme=dark]){--bg:#f3f6fc;--card:#fff;--text:#0b1530;--muted:#4a587c;--accent:#0f9d76;--line:#d5ddef}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:17px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{padding:28px 16px 8px;max-width:980px;margin:auto}h1{margin:0 0 6px;font-size:30px}header p{color:var(--muted);margin:0}
nav{max-width:980px;margin:12px auto;padding:0 16px;display:flex;flex-wrap:wrap;gap:8px}
nav a{color:var(--text);text-decoration:none;background:var(--card);border:1px solid var(--line);padding:7px 14px;border-radius:999px;font-weight:600;font-size:15px}
main{max-width:980px;margin:auto;padding:0 16px 60px}h2{margin:38px 0 12px;font-size:24px;border-bottom:2px solid var(--accent);padding-bottom:6px}
.step{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin:14px 0;display:grid;gap:14px}
@media (min-width:820px){.step{grid-template-columns:1.35fr 1fr;align-items:start}}
.step img{width:100%;border-radius:10px;border:1px solid var(--line);display:block}
.step img.phone{max-width:260px;margin:auto}
h3{margin:0 0 8px;font-size:20px}ol{margin:0;padding-left:22px}li{margin:6px 0}
.tip{color:var(--muted);font-size:14px;text-align:center;margin-top:30px}
"""

def main():
    out = ['<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
           '<title>Aze-IPTV Anleitung</title><style>' + CSS + '</style></head><body>',
           '<header><h1>Aze-IPTV – Anleitung</h1><p>Schritt für Schritt mit Bildern aus der echten App. Das Fernsehbild ist ein Test-Bild.</p></header><nav>']
    for i, (chap, _) in enumerate(CHAPTERS):
        out.append(f'<a href="#k{i}">{html.escape(chap)}</a>')
    out.append('</nav><main>')
    for i, (chap, steps) in enumerate(CHAPTERS):
        out.append(f'<h2 id="k{i}">{html.escape(chap)}</h2>')
        for img, title, items in steps:
            f = f"img/{img}.jpg"
            has = os.path.exists(os.path.join(ROOT, f))
            out.append('<section class="step">')
            if has:
                cls = ' class="phone"' if img == "handy-seite" else ''
                out.append(f'<img{cls} src="{f}" alt="{html.escape(title)}" loading="lazy">')
            out.append(f'<div><h3>{html.escape(title)}</h3><ol>' + ''.join(f'<li>{html.escape(s)}</li>' for s in items) + '</ol></div></section>')
    out.append('<p class="tip">Fragen? In der App unter Einstellungen → Hilfe gibt es auch Erklär-Videos.</p></main></body></html>')
    os.makedirs(ROOT, exist_ok=True)
    with open(os.path.join(ROOT, "index.html"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))
    print("Anleitung gebaut:", os.path.join(ROOT, "index.html"))

if __name__ == "__main__":
    main()
