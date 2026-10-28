# Aze-IPTV-Player – Projektstand (28.10.2026, Version 1.0.37)

## Wo alles liegt
- **GitHub-Repository:** https://github.com/Postmen1971/Aze-iptv-Player- (Branch `main`)
- **Download (immer neueste Version):**
  - Fire TV / waipu.tv Stick / ältere Geräte: `https://github.com/Postmen1971/Aze-iptv-Player-/releases/latest/download/Aze-IPTV-Player-armeabi-v7a.apk`
  - Weitere Varianten mit gleichem Namensschema: `-arm64-v8a`, `-x86`, `-x86_64`, `-universal`
- **SkorText (Fußball-Videotext):** https://github.com/Postmen1971/SkorText – wird beim Bauen eingebunden (siehe unten)
- **Simulator (Vorschau im Browser):** claude.ai-Artifact „Aze-IPTV-Simulator“ – zeigt noch den älteren Stand (ohne Seitenmenü und neue Farbschemata)

## Technik
- Kotlin + Jetpack Compose, Paket/App-ID `de.aze.iptv`, minSdk 21, targetSdk 35
- Player: Media3/ExoPlayer 1.5.1 (Standard) + libVLC 3.6.0 (Ausweich-Player), Coil für Bilder, ZXing für QR-Codes
- **Bauen:** Jeder Push auf `main` startet GitHub Actions („APK bauen“) → Release `v1.0.<Laufnummer>` mit festen Dateinamen
- Der Build holt automatisch die neuesten SkorText-Seiten aus dem SkorText-Repo (`app/src/main/assets/www` → `app/src/main/assets/skortext`)
- Compiler-Fehler erscheinen als Hinweise („Annotations“) am Build-Lauf (die Log-Dateien sind aus der Claude-Umgebung nicht abrufbar)

### Wichtige Dateien (`app/src/main/java/de/aze/iptv/`)
| Datei | Inhalt |
|---|---|
| `AppController.kt` | zentraler Zustand: Playlists, Favoriten, Nummerierung, EPG, Einstellungen, Filme/Serien-Quellen |
| `data/Repository.kt` | Playlists laden (M3U, Xtream-API, Stalker), Kopie auf dem Gerät |
| `data/M3uParser.kt`, `Numbering.kt`, `ListLayout.kt` | Liste lesen, Nummern (Favoriten zuerst), eigene Sortierung/Ausblendung |
| `data/Vod.kt` | Filme & Serien: `XtreamVod`, `M3uVod` (aus M3U-Einträgen), `HybridVod` (erkennt Xtream hinter M3U) |
| `data/Stalker.kt` | Portal + MAC (Stalker/Ministra) |
| `data/FreeLists.kt` | freie Sender von iptv-org (Deutschland, Türkei) |
| `data/PhoneInputServer.kt` | Playlist-Eingabe vom Handy (Heimnetz, Code, 5 Min.) |
| `data/WebEditServer.kt` | Senderliste am Handy/PC per Ziehen sortieren (Heimnetz, Code, 30 Min.) |
| `data/Secure.kt`, `Store.kt` | Verschlüsselung (Android Keystore), PIN, Einstellungen |
| `player/FrameRate.kt` | Bildwiederholrate anpassen (AFR), TextureView-Umschaltung |
| `ui/PlayerScreen.kt` | Live-TV, Tasten, Schnellmenü, Videotext |
| `ui/ChannelOverlay.kt` | Senderliste, Gruppen, Seitenmenü |
| `ui/VodScreen.kt` | Filme & Serien inkl. eigenem Film-Player |
| `ui/SettingsScreen.kt` | Einstellungen (links Bereiche, rechts Inhalt) |
| `ui/Ui.kt` | Farbschemata, Bedien-Bausteine |
| `.github/workflows/build.yml` | Build, Signatur, Release |

## Was fertig ist
**Bedienung**
- Steuerung mit Fernbedienung (Fire TV, waipu.tv) und Touch; Zifferneingabe
- Favoriten bekommen 1, 2, 3 …, alle anderen Sender zählen weiter
- Beim Umschalten keine Einblendung; nur Nummer + Name in der Ecke, solange das Bild schwarz ist
- OK kurz = letzter Sender; OK lang/Menü = Schnellmenü (Fußball/SkorText, Bild, Ton, Player, Puffer, Filme, Serien, Einstellungen)
- ◀ = Gruppen, ◀◀ = Seitenmenü (TV, Filme, Serien, Suchen, Playlist, Einstellungen)
- Zurück beim Fernsehen: Senderliste → Gruppen → App beenden
- Suche, Autostart, mehrere Playlists gemeinsam (umschaltbar über „Playlist: …“)

**Quellen**
- M3U-Link, Server + Login (Xtream), Portal + MAC (Stalker, nur Live-TV)
- get.php-Links werden automatisch zu Server + Login
- Eingabe vom Handy im selben WLAN, mit QR-Code (Code schon eingetragen)
- YouTube-Live als eigene Gruppe; Kanäle, die Einbetten sperren: OK öffnet sie in der YouTube-App
- Freie Sender (iptv-org) Deutschland/Türkei per Knopf, jeweils eigene Gruppe
- Senderliste bearbeiten am Fernseher oder am Handy/PC (Ziehen, ein-/ausblenden, umbenennen), mit QR-Code

**Wiedergabe**
- Puffer-Stufen, VLC als Ausweich-Player (automatisch je Sender), Programmzeitschrift (EPG)
- Bildqualität: „Automatisch (schnelles Umschalten)“ (Standard) oder „Immer beste Stufe“, Tunneling optional
- Bildwiederholrate an Sender anpassen (50 Hz bei 25/50 fps), Standard an

**Filme & Serien**
- Bedienung wie Live-TV (◀ = Seitenmenü), Anzahl je Kategorie, durchnummerierte Poster, Suche
- Auch bei M3U-Playlists (Xtream dahinter wird erkannt, sonst Einträge aus der M3U)
- Film-Player: ◀ ▶ spulen (10 s → 30 s → 1 min → 5 min beim Halten), OK Pause, 0–9 springt auf 0–90 %, Ton-/Untertitel-Auswahl, Bildformat, Weiterschauen

**Aussehen**
- Neun dunkle Farbschemata: Mitternacht, Schiefer, Ozean, Petrol, Graphit, Smaragd, Aubergine, Bordeaux, Kupfer
- Kompakte Einstellungen (links Bereiche, rechts Inhalt); Enigma2-Ansicht wurde entfernt

**Sicherheit**
- PIN für Einstellungen (Standard 0000, gespeichert als Prüfsumme)
- Zugangsdaten, Favoriten und Layout verschlüsselt; Links nur gekürzt angezeigt; keine Backups (`allowBackup=false`)
- Keine Senderlisten in der App (Urheberrecht); Handy-/Web-Zugriff nur im Heimnetz mit Code

## Offene Punkte
1. **Einfrieren nach dem Umschalten auf 50 Hz testen** (Version 1.0.37): Jetzt wird bei angepasster Bildwiederholrate eine TextureView statt der eigenen Video-Ebene benutzt. Falls es trotzdem einfriert: Tunneling aus? „Bildwiederholrate“ auf Aus → hilft das? Dann weiter untersuchen (z. B. Wiedergabe nach dem HDMI-Umschalten neu starten, oder Umschaltung vor dem Start des Senders).
2. **SkorText-Update direkt im Player:** Beim Öffnen von SkorText prüfen, ob es auf GitHub eine neuere Version gibt, und die Seiten selbst nachladen (ohne neuen Player-Build). Der Update-Knopf in SkorText soll das auch auslösen (im Player derzeit ohne Funktion, `SkorBridge.installApk` ist leer).
3. **Eigenen Signaturschlüssel einrichten** (siehe unten).
4. Stalker-Portal: Filme/Serien und EPG fehlen noch; an einem echten Portal ungetestet.
5. Simulator auf den aktuellen Stand bringen (optional).

## Signaturschlüssel
- Zurzeit wird mit dem **öffentlichen Test-Schlüssel** `keystore/test.jks` signiert (Passwort `aze-test-key`, steht absichtlich offen im Repo). Das reicht zum Testen.
- Für den eigenen Schlüssel werden im Repository unter *Settings → Secrets and variables → Actions* zwei Secrets angelegt:
  - `KEYSTORE_BASE64` – die Schlüsseldatei (.jks) als Base64-Text
  - `KEYSTORE_PASSWORD` – Passwort (gilt für Datei und Schlüssel, Alias `aze`)
- Die Secrets muss Aziz selbst eintragen (Claude gibt keine Passwörter in Webseiten ein).
- **Nach dem Umstieg** muss die Testversion einmal deinstalliert werden (andere Signatur), danach gehen Updates normal.
- Ab dann den Schlüssel + Passwort **unbedingt sicher aufbewahren** (z. B. Passwort-Manager): Ohne ihn lassen sich keine Updates mehr über die installierte App spielen.

## Entscheidungen
- Keine Senderlisten in der App; freie Listen nur auf Knopfdruck aus dem Internet
- YouTube-Streams werden nicht „abgegriffen“ (Sperre der Sender respektieren); stattdessen YouTube-App bzw. offizielle Streams
- Standard-Bildqualität = schnelles Umschalten (gewünscht)
- Bedienung im TiviMate-Stil, aber keine TiviMate-Namen in der App
