# Aze-IPTV-Player

IPTV-Player für Android (Handy, Tablet, Android TV, Fire TV Stick), ab Android 5.0.

## Funktionen (Version 1)

- Playlist per **M3U-Link**, **Server + Benutzer + Passwort** (Xtream) oder **Portal + MAC** (Stalker/Ministra, nur Live-TV)
- Sender nach **Gruppen** getrennt, wie in der Playlist (`group-title`)
- **Favoriten** bekommen die Nummern 1, 2, 3 …, alle anderen Sender zählen danach weiter und rücken automatisch nach
- **Direktwahl** mit den Zifferntasten, Hoch/Runter schaltet um
- Beim Umschalten keine Einblendungen – nur solange kein Bild da ist, steht klein „Nummer + Name“ oben rechts
- OK öffnet die Senderliste (Gruppen links, Sender in der Mitte), OK lang = Favorit an/aus
- Senderliste automatisch aktualisieren: nur manuell, 24 / 48 / 72 Stunden oder alle zwei Wochen
- **App-Update** manuell in den Einstellungen (lädt das neueste Release von GitHub)

### Version 2

- Mehrere Playlists erscheinen zusammen untereinander; oben in der Senderliste „Playlist: Alle“ oder nur eine; Playlists umbenennen und sortieren
- Puffer Klein / Mittel / Groß (Groß spielt etwas später als live, dafür sehr stabil), Speichergrenze für schwache Geräte
- Automatisches Neuverbinden bei Abbrüchen, Ausweich-Decoder
- Schnellmenü im Bild (durchsichtige Leiste unten): Bildformat, Tonspur, Untertitel, Puffer
- Proxy (HTTP/SOCKS5) nur für die App, Anzeige ob ein VPN aktiv ist

### Version 3 – Sicherheit

- Einstellungen mit **PIN** geschützt (Standard `0000`, änderbar; gespeichert nur als Prüfsumme)
- Playlists und Proxy-Passwort **verschlüsselt** im Android-Schlüsselspeicher (ab Android 6)
- Links werden nur **gekürzt** angezeigt (`http://server:port/…`)
- **Keine Backups** in die Cloud oder auf andere Geräte
- **Playlist vom Handy eingeben:** In den Einstellungen starten, am Handy die angezeigte Adresse öffnen und den Code vom Fernseher eingeben. Nur im Heimnetz, höchstens 5 Minuten, schließt sich nach der Eingabe.

### Version 4

- **Filme & Serien** (bei Server + Login): Kategorien, Poster, Detailseite mit Beschreibung, Staffeln und Folgen, Weiterschauen
- **EPG**: laufende Sendung mit Fortschritt in der Senderliste (Anbieter-EPG, url-tvg aus der M3U oder eigene XMLTV-Adresse)
- **VLC** als Ausweich-Player: automatisch für Sender, die Media3 nicht abspielen kann, oder fest eingestellt
- **Autostart** beim Einschalten (Einstellungen → Autostart)
- **Fußball-Videotext (SkorText)** über dem Programm: OK lang → Fußball
- APKs je Prozessor-Typ (armeabi-v7a, arm64-v8a, x86, x86_64) und „universal“; Fire TV Stick: armeabi-v7a

### Bedienung

| Taste | Funktion |
|---|---|
| Hoch / Runter | Sender wechseln |
| 0–9 | Direktwahl |
| OK kurz | zum zuletzt geschauten Sender springen |
| OK lang / Menütaste | Schnellmenü (Bild, Ton, Untertitel, Puffer) |
| Links / Rechts | Senderliste |
| In der Liste: OK lang | Favorit an/aus |

Touch: Tippen = Senderliste, doppelt Tippen = letzter Sender, lange Tippen = Schnellmenü.


## Installieren

1. Unter **Releases** die neueste `Aze-IPTV-Player-….apk` herunterladen.
2. Fire TV: App „Downloader“ nutzen und „Apps unbekannter Herkunft“ für Downloader erlauben.

## Build

Jeder Push auf `main` baut über GitHub Actions eine APK.
Damit ein **Release** mit signierter APK entsteht (nötig für Updates), müssen im Repository unter
*Settings → Secrets and variables → Actions* zwei Secrets angelegt sein:

- `KEYSTORE_BASE64` – der Signaturschlüssel (Base64)
- `KEYSTORE_PASSWORD` – das Passwort dazu

Ohne diese Secrets entsteht nur eine Test-APK unter *Actions → Lauf → Artifacts*.

## Rechtliches

Die App liefert keine Inhalte. Nur Playlists verwenden, für die Nutzungsrechte bestehen.
Verwendete Bibliotheken: AndroidX Media3, Jetpack Compose, AndroidX, Kotlin Coroutines (Apache License 2.0).
