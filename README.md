# Aze IPTV Player

IPTV-Player für Android (Handy, Tablet, Android TV, Fire TV Stick), ab Android 5.0.

## Funktionen (Version 1)

- Playlist per **M3U-Link** oder **Server + Benutzer + Passwort** (Xtream)
- Sender nach **Gruppen** getrennt, wie in der Playlist (`group-title`)
- **Favoriten** bekommen die Nummern 1, 2, 3 …, alle anderen Sender zählen danach weiter und rücken automatisch nach
- **Direktwahl** mit den Zifferntasten, Hoch/Runter schaltet um
- Beim Umschalten keine Einblendungen – nur solange kein Bild da ist, steht klein „Nummer + Name“ oben rechts
- OK öffnet die Senderliste (Gruppen links, Sender in der Mitte), OK lang = Favorit an/aus
- Senderliste automatisch aktualisieren: nur manuell, 24 / 48 / 72 Stunden oder alle zwei Wochen
- **App-Update** manuell in den Einstellungen (lädt das neueste Release von GitHub)

Geplant: Filme & Serien im TiviMate-Stil, YouTube-Live als eigene Gruppe, Eingabe der Playlist vom Handy aus, libVLC als zusätzlicher Player.

## Installieren

1. Unter **Releases** die neueste `AzeIPTV-….apk` herunterladen.
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
