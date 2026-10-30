# Aze-IPTV-Player – Projektstand (29.10.2026, Version 1.0.60)

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
- Bildwiederholrate an Sender anpassen (50 Hz bei 25/50 fps) – **Standard aus**, als Test markiert (friert auf Fire TV ein); Tunneling ebenfalls aus, beides schließt sich gegenseitig aus

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

## Zuletzt geändert (1.0.41–1.0.46)
- Bildwiederholrate: Umschalten wie TiviMate (anhalten → umstellen → warten → neu starten), Bild-Wächter; weiter standardmäßig aus
- Hänger behoben: schnelle Verschlüsselung (eigener Datenschlüssel statt Android-Schlüsselspeicher bei jedem Zugriff), VLC wird im Hintergrund freigegeben und LibVLC nur einmal angelegt, Sendersuche im Hintergrund, „Senderliste wird aktualisiert“ kann nicht mehr hängen bleiben, EPG-Namensvergleich schneller
- YouTube: WebView liegt direkt im Fenster hinter der Oberfläche (SHIELD: Ton ohne Bild); neue Einstellung „In der YouTube-App“ abspielen
- Testgerät von Aziz: NVIDIA SHIELD TV (Fire TV beim Bruder zeigt dasselbe Einfrieren)

## Neu in 1.0.48
- „Fußball im TV“: sucht in der Programmzeitschrift der eigenen Sender nach Fußballspielen (heute/morgen), OK schaltet um. Erreichbar über Seitenmenü und Schnellmenü („Spiele im TV“). Datei `data/Football.kt`, `ui/FootballGuide.kt`
- Filme & Serien: kleinere Poster (6 nebeneinander), Vorladen der nächsten Poster, Bild-Zwischenspeicher 250 MB
- Release-Build mit R8-Optimierung (`app/proguard-rules.pro`) – falls Abstürze auftreten, zuerst hier prüfen
- YouTube läuft jetzt direkt im App-Player (WebView liegt im Fenster hinter der Oberfläche)

## Einfrieren bei Bildwiederholrate – Ursache gefunden (1.0.60)
- Beim Umschalten der Frequenz trennt HDMI kurz den Ton → Android meldet „Audio becoming noisy“ → ExoPlayer pausierte (`setHandleAudioBecomingNoisy(true)`); beim Zappen wurde `playWhenReady` nicht wieder gesetzt → Standbild.
- Lösung: auf TV-Geräten kein Noisy-Pausieren, `playWhenReady = true` bei jedem Senderstart, Wächter hebt ungewollte Pause auf.
- Feste Standardwerte (bei Aziz auf der SHIELD bewährt): Bildwiederholrate an Sender anpassen, 6 s Wartezeit, immer beste Stufe.

## Offene Punkte
1. **Bildwiederholrate (AFR) auf Fire TV:** Sobald „An Sender anpassen“ eingeschaltet wird, bleibt das Bild stehen (auch mit TextureView aus 1.0.37). Läuft erst wieder, nachdem man eine Weile im Menü war. Deshalb ab 1.0.40 standardmäßig aus. Nächste Ideen: ExoPlayer-eigene Frequenzwahl abschalten (`setVideoChangeFrameRateStrategy(OFF)`), Wiedergabe vor dem Umschalten stoppen und erst nach dem HDMI-Wechsel (ca. 3–5 s) neu starten, Bild-Wächter, der hängende Bilder erkennt und neu startet, Bildflächen-Neuaufbau/TextureView wieder entfernen. Offene Frage an Aziz: Läuft der Ton weiter, wenn das Bild steht?
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

## v1.0.69 – Großes Sammel-Update
- Einstellungen: Standard/Erweitert (Schalter unten links), aufklappbare Abschnitte; Netzwerk und Wiedergabe-Details nur im Expertenmodus.
- Neue Standardwerte (Migration tuned4): Puffer Mittel, Media3, Bildqualität Automatisch, Bildwiederholrate Nie umschalten.
- Senderliste verwalten wie IBO Player: links Kategorien, rechts Sender; OK = ein/aus, OK lang = verschieben, Menü = umbenennen; Tablet mit ▲▼✓.
- Bedienung: Zappen alle/nur in Gruppe, Sendername beim Umschalten (nie/kurz/immer), frei belegbare Tasten inkl. OK lang (data/Controls.kt).
- Fußball: nach Senderwahl bleibt rechts die Liste aller Sender des Spiels offen (MatchZapPanel), Zurück schließt.
- Sicherheit: kein Klartext-Fallback in Secure (encfail:), HTTPS→HTTP-Umleitungen blockiert, Antworten max. 64 MB, Updater nur Repo-APK mit .part, Größen- und SHA-256-Prüfung.
- Automatische Tests (app/src/test/LogicTest.kt) laufen bei jedem Build: M3U, Nummerierung, XXL-Filter, Tastenbelegung, liveonsat, Fußball-Zuordnung.
- Offen: Release-Signatur nutzt weiter den Test-Schlüssel, bis KEYSTORE_BASE64/KEYSTORE_PASSWORD als GitHub-Secrets gesetzt sind.

## v1.0.70 – EPG-Zuordnung und Lippensynchron
- EPG-Zuordnung: eigene Zuordnung → tvg-id (Groß/klein egal) → tvg-name → Sendername ohne Länder-Vorsatz/HD. tvg-name wird aus der M3U gelesen.
- Alle url-tvg/x-tvg-url-Adressen der M3U werden genutzt; beliebig viele weitere EPG-Quellen (Einstellungen → Programm (EPG)), Vorschläge epgshare01 DE1/TR1.
- Schnellmenü (OK lang) → „Programm (EPG)“: Sender von Hand einem EPG-Sender zuordnen (Liste aus epg_channels.txt, Vorschläge nach Namensähnlichkeit, Suche).
- Lippensynchron: allgemein in Einstellungen → Wiedergabe, zusätzlich je Sender im Schnellmenü. Media3 über verschobene Ton-Uhr (player/AudioSync.kt), VLC über setAudioDelay. Filme/Serien nutzen den allgemeinen Wert.

## v1.0.73 – Stabilität
- YouTube: eine WebView für alle YouTube-Sender (nur Adresse wechselt, 250 ms Wartezeit beim Durchschalten), Qualität nur in den ersten Sekunden setzen (ständiges Setzen verursachte Neu-Puffern), Hänger-Erkennung (15 s Standbild → neu laden, max. 3×), Absturz der Web-Anzeige (onRenderProcessGone) beendet die App nicht mehr.
- AppController: eigener SupervisorJob + CoroutineExceptionHandler – Fehler in Hintergrundaufgaben werden als Meldung gezeigt statt die App zu beenden.
- XXL-Filter beim Aufbau der Senderliste im Hintergrund statt auf dem Haupt-Thread.
- Absturz-Protokoll (filesDir/letzter_absturz.txt), Ursache wird beim nächsten Start angezeigt.
- Senderliste: Rechts schließt die Liste (Spultasten wechseln die Gruppe). EPG-Zuordnung lädt höchstens einmal nach.

## v1.0.74–1.0.81 – Zappen und Hänger (von Aziz bestätigt: läuft wunderbar)
- Ursache der Hänger (per Video gefunden): Neustart-Schleife durch zu eifrigen Wächter (Neustart beim Puffern) + sehr schnelle Wiederholungen → schwarzes Bild, bunte Klötzchen, erst nach Aufgeben des Wächters lief es.
- Jetzt: kein Neustart beim Puffern; Wächter nur bei „läuft“ + 10 s weder Bild noch Zeit, max. 2×. Wiederholung bei HTTP-Ablehnung 1,5/2,5/4/8/15 s, sonst 2/4/8/15 s. Start-Puffer Mittel 1,5 s (Rebuffer 4 s), VLC Mittel 1,5 s.
- Lehre: Wächter dürfen nie bei Puffern neu starten; Änderungen am Abspielverhalten immer einzeln ausliefern.
- Weitere: EPG an/aus, Zeitzonen-Fix (+0300 ohne Leerzeichen), Zeitverschiebung je Quelle, EPG-Zuordnung nach Land, EPG-Laden mit niedriger Priorität, Sendung in dezentem Gelb, Wiedergabe-Optionen (Puffer/Player/Bildqualität/Bildwiederholrate) wieder immer sichtbar, Player ohne Ton-Uhr-Verschiebung solange kein Versatz eingestellt.

## v1.0.82–1.0.86 – Speicher
- liveonsat-Seite nur noch als Datei (cacheDir/liveonsat.html), Download max. 12 MB.
- Xtream get_live_streams per JsonReader (stückweise), VOD-Anbieter nur einer je Playlist (alte verworfen), Coil-RAM-Cache 15 %.
- Videotext-WebView sofort nach dem Schließen freigegeben.
- EPG an/aus mit Beschreibung (aus = am flüssigsten). Keine EPG-Rasteransicht gewünscht (Aziz: macht die App langsamer).
- CacheCleaner: beim Start Zwischenspeicher aufräumen (außer Logos „bilder“), WebView-Cache, alte Update-APKs, .tmp; onTrimMemory leert Bild-RAM; Knopf „Alles leeren“ unter Einstellungen → Über.

## Für einen neuen Chat
- Code: https://github.com/Postmen1971/Aze-iptv-Player- (main). Jeder Push baut per GitHub Actions v1.0.<Lauf> inkl. Tests (app/src/test/LogicTest.kt) und veröffentlicht 5 APKs (armeabi-v7a, arm64-v8a, x86, x86_64, universal).
- Download: https://github.com/Postmen1971/Aze-iptv-Player-/releases/latest/download/Aze-IPTV-Player-armeabi-v7a.apk (SHIELD: arm64-v8a geht auch).
- Build-Fehler: Logs sind gesperrt → Fehler stehen als ::error::-Annotationen am Check-Run (gh api …/check-runs/<job>/annotations).
- Testgerät: NVIDIA SHIELD (Bruder: Fire TV). Aziz testet selbst und schickt Fotos/Videos.
- Arbeitsweise mit Aziz: Antworten auf Deutsch, einfach erklärt; viele Wünsche gern in einem Build; Änderungen am Abspielverhalten (Puffer, Wächter, Wiederholungen) aber einzeln ausliefern. Ruhiges, dezentes Design, keine TiviMate-Namen. Sicherheitsregel: Passwörter/Secrets trägt Aziz selbst ein.
- Offen: eigener Signatur-Schlüssel (GitHub-Secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD durch Aziz), A2-YouTube-Link fehlt, .xz-EPG (Rytec) nicht unterstützt, Option „Zeitzone der Quelle“ statt ±1 h angeboten (noch nicht gewünscht), Update-Hinweis beim Start und automatische Logos angeboten.
