# Aze-IPTV-Player – Projektstand (14.11.2026, Version 1.0.264)

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

## Flüssigkeits-Paket (nach unabhängiger Prüfung, 1.0.121)
- Ursache der restlichen Hänger: Endet ein .ts-Stream beim Anbieter (oder bricht die Leitung ab), merkte Media3 das erst nach dem ganzen Vorrat (bis ~27 s), dann Schwarzbild + Decoder-Neustart + neu puffern.
- Neu `player/LiveSource.kt`: Bei Verbindungsende wird SOFORT eine neue Verbindung als nächstes Stück angehängt (addMediaSource) → nahtloser Übergang ohne Neustart. Leitungsabbruch nach Daten zählt als Ende (EndOnDropDataSource). Schnelle Wiederholung 0,4 s (403/429 beim Zappen), 401/404/410 sofort Fehler.
- Wächter: Zeitsprung schon nach 2 s; „keine Daten“ bei laufendem Sender nach 5 s (Start 8 s), auch leicht neu (außer Portal-Links); Zeitsprung/Standbild nur noch „leicht“ neu synchronisieren (ohne Senderwechsel-Ablauf, letztes Bild bleibt stehen). Keine Verbindung → wie bisher kompletter Neustart.
- HLS live: Geschwindigkeit fest 1,0 (kein Ruckeln durch 0,97–1,03×).
- Player wird nicht mehr bei jedem Senderwechsel neu gebaut (Lippensynchron-Vorrat nach größtem Versatz).
- Bildfläche nur bei echtem Frequenzwechsel neu (DisplayWatch.surfaceTick statt jede Bildschirm-Meldung).
- Multi-View: ein Player je Bild bleibt beim Umschalten, versetzter Start nur beim Öffnen, 10 MB statt 20 MB je Bild, gleicher nahtloser Übergang. Schalter „Multi-View (Beta)“ unter Wiedergabe (aus = nicht im Schnellmenü).
- Messzeile zeichnet nur sich selbst neu; Gruppenliste wird gemerkt statt bei jedem Neuzeichnen gefiltert.
- Bewusst NICHT gemacht (Gerätetest nötig): erzwungenes asynchrones Decodieren, Non-IDR-Keyframes, Baseline-Profile.

## Arbeitsweise gegen späte Fehler (ab 1.0.122)
- Vor größeren Player-Änderungen: erst recherchieren (Media3-Doku/Issues, offene Player wie Kodi/mpv/Just Player), unabhängige Prüfung VOR dem Bauen.
- Hänger-Protokoll auf dem Gerät (`data/HangLog.kt`, Datei `haenger.log`): jede Bildlücke (LUECKE n s + Zustand zu Beginn), Wächter-Eingriff mit Messwerten, Fehler, nahtlose Neuverbindungen (REJOIN/TRANSITION), Multi-View-Ereignisse. Nur Sendernamen, keine Adressen. Einstellungen → „Hänger-Protokoll“ → Anzeigen / Hochladen (paste.rs, Link an Claude schicken – Claude kann den Link lesen, wenn Aziz ihn im Chat schickt) / Leeren.
- Test-Server realistisch: drop.ts 3× schneller als Echtzeit (großer Vorrat wie echter Anbieter), neuer Sender „Test 6 Limit“ (1 Verbindung, 403 bis 2 s nach Freigabe, trennt alle 20 s). Emulator misst „längste Lücke ohne Bild“ (≤ 2 s), Dauerlauf 3 min.

## Fußball: TV-Plan mit Vorschau (1.0.124)
- Im Spiel-Fenster „📺 TV-Plan durchgehen – mit Vorschau“: links Sender laut liveonsat, daneben ähnliche eigene Sender (matchByNameLoose). Verweilen 0,7 s / OK = Sender läuft klein oben rechts (normaler Player wird verkleinert, keine 2. Verbindung). OK auf laufendem Sender = Vollbild + MatchZapPanel rechts, Zuordnung wird gemerkt (Store `losLearned`, verschlüsselt; ★). Gemerkte Zuordnung hat bei der automatischen Erkennung Vorrang.

## Gemeinschaftsliste „gelernte Sender“ (1.0.125)
- Bestätigte Zuordnung speichert jetzt auch den Sendernamen (`losLearnedNames`). Einstellungen → „Fußball: gelernte Sender“ → „Teilen & Link zeigen“ (paste.rs, JSON {liveonsat-Name: eigener Sendername}) / „Alle vergessen“.
- Aziz schickt den Link → Claude prüft und trägt in `community/liveonsat.json` ein (Format: {"map": {"liveonsat name klein": ["Sendername", …]}}). Die App lädt die Datei (raw.githubusercontent, höchstens alle 12 h, Zwischenspeicher) und findet die Sender per Namen (`Football.byOwnName`).
- Vorrang: selbst gemerkt ★ > Gemeinschaft ✓ > automatische Erkennung.

## Fußball direkt in die Vorschau + schnellere Cover (1.0.133)
- Fußball heute: OK auf Spiel = direkt TV-Plan mit Vorschau (wenn liveonsat Sender kennt), OK lang = alte Übersicht.
- Cover: TMDB-Links auf w185 umgeschrieben (`smallPoster`), OkHttp-Dispatcher 64/16 statt 5 je Server, Vorladen erst nach 250 ms Ruhe (24 bzw. 60 voraus).
- Einstellung „Filme & Serien – Cover“: Standard / „Filme & Serien bevorzugt“ (Store `vodFast`: ganze Kategorie bis 400 Cover vorladen, Disk-Cache 1 GB statt 250 MB, Speicher 25 %; gilt ab Neustart).

## Redesign „Aurora“ (1.0.138)
- Konzept: claude.ai Design-Canvas „Aze-IPTV Redesign Aurora“ (https://claude.ai/artifact/7eMpFnULA4ati4jj2Sg8r6).
- Neues Farbschema „Aurora“ (Nachtblau, Minz-Akzent #3EE6C1, Rot nur LIVE), hinten an `Schemes` angehängt (gespeicherte Indizes bleiben gültig); einmalig automatisch eingestellt (Store `auroraApplied`). Knöpfe runder (14 dp).
- Sendername beim Umschalten: neue Wahl 3 = „Infoleiste unten“ (`ZapBar`: Nummer, Name, LIVE, Auflösung, Sendung mit Zeit/Fortschritt, „Danach …“, Uhr; 4 s). Standard bleibt die bisherige Anzeige – Wunsch von Aziz.

## Filme/Serien schneller laden + Uhr (1.0.143)
- Ursache „Kategorien hängen, Cover nach 3 min“: `counts()` lud bei JEDEM Öffnen die komplette Filmliste (viele MB) und verstopfte die Leitung. Jetzt: Gesamtliste einmal je Programmstart (statisch gecacht je Zugang), erst nach der ersten Kategorie + 1,5 s; danach Kategorien sofort aus dem Speicher (VodItem.cat). Fehlermeldung verschwindet bei Erfolg.
- Cover: Bilderdienst schaltet sich nach 3 langsamen (>3 s) oder fehlgeschlagenen Bildern für den Programmstart ab; OkHttp 8 je Server, Timeouts 6/10 s.
- Hänger-Protokoll: VOD_CAT (Ladezeit je Kategorie), VOD_ALL (Gesamtliste), COVER (erste 6 Cover mit Zeit/Quelle).
- Uhr (`AzeClock`, Wochentag/Datum/Zeit) unter dem Logo im Seitenmenü und in den Einstellungen; aktualisiert sich zur vollen Minute.

## 1.0.144–1.0.235 (03.–07.11.) – Zusammenfassung
**Design**
- Aurora als Farbfamilie (Minz, Blau, Bernstein, Violett, Koralle); Einstellungen im Aurora-Stil (Seitenleiste mit Logo/Version, Karten, echte Schalter).
- **Neu Standard (1.0.235): Design A „Aze Nachtblau“** – Türkis/Nachtblau, Senderliste mit Fokus-Rahmen, sanfter Vergrößerung und Verlauf, Fortschrittsbalken im Verlauf, ruhigere Infoleiste.
- Infoleiste unten ist jetzt Standard (einmalig für alle gesetzt), Anzeigedauer 1–7 s (Standard 4 s); Taste belegbar „Infoleiste ein/aus“, Rechts lang = Infoleiste.
- Einstellungen einfacher (TiviMate-Stil): 14 Bereiche mit Symbol + Erklärzeile, Suche; neuer Bereich beginnt oben.

**Senderliste**
- ▲▼ im Rundlauf (oben ↔ unten), nie in die Gruppen-Kopfzeile; ▲/▼/◀/▶/OK holen den Fokus zurück in die Liste. ▶ schließt die Liste wieder.
- Senderliste verwalten: Knopf „Sortieren“ (OK nimmt auf/legt ab), mitscrollen beim Verschieben, ▶ = erster Sender / ◀ = Kategorie; Absturz BringIntoViewRequester behoben (feste Fokus-Anker, Schutz nur gegen diesen Compose-Fehler).

**Filme & Serien / Cover**
- Listen als Kopie auf dem Gerät (sofort da, Auffrischen im Hintergrund).
- Cover-Vorrat: alle Cover einmal klein (WebP 200×300) auf dem Gerät, im Hintergrund, bevorzugte Sprache zuerst (Deutsch → Türkisch), offene Kategorie zuerst; ausgeblendete Kategorien nicht. Läuft im Hintergrund-Thread (vorher Hänger/OOM), 2 gleichzeitig, 15 s Pause nach dem Umschalten.
- TMDB-Ersatz bei fehlendem/kaputtem/langsamem Cover (Schlüssel aus Secret `TMDB_KEY`), Schalter „Cover zuerst von TMDB“; Bilderdienst (weserv) standardmäßig aus.
- Tablet: Film-Player per Touch (Steuerleiste, Zeitleiste ziehen, doppelt tippen ±10 s, ✕).

**Fußball / SkorText**
- SkorText 3.14/3.15 eingebaut; U-19 nicht mit Profis verwechseln; Vereins-Namenstabelle (Internazionale = Inter Milan), > 25 Sender je Spiel.
- TV-Plan mit Vorschau: kein automatisches Umschalten (OK = Vorschau, OK lang = ★ merken), am Tablet antippbar.
- Sperrt liveonsat (HTTP 403) → Ersatzliste aus dem SkorText-Feed auf GitHub.
- SkorText öffnete nicht/ging sofort zu → behoben (kein Vorwärmen im Hintergrund-Thread, else-Fehler), Emulator-Test 12.

**Sichern & Hilfe**
- Einstellungen sichern & wiederherstellen per Code: auf dem Gerät verschlüsselt, gzip, paste.rs (Ersatz dpaste.com). Code bleibt gespeichert + QR-Code, „Sicherung löschen“ mit Sicherheitsfrage, alte Sicherung wird beim Neu-Sichern aufgeräumt.
- Hilfe: Anleitung mit Bildern als Webseite (Emulator-Fotos, GitHub Pages) + QR-Code ganz oben; Erklär-Videos im Release „hilfe“ (Workflow nur mit [skip ci]).

**Tablet/Handy (Touch)**
- Knopfleiste und Schnellmenü brechen auf schmalen Bildschirmen um, PIN-Feld quer kompakt, Einstellungs-Bereiche antippbar, Links antippbar, Multi-View per Tippen.

**Tests / CI**
- Emulator wie Fernseher (1920×1080); Komplett-Test mit Herzschlag-Hängerprüfung, Test-Anbieter mit 3000 Filmen/600 Serien, Umschaltzeit-Messung (ZAPTIME, Median ≤ 0,5 s), Lücke nach Stream-Ende ≤ 1 s (Neuverbindung 0,3 s).
- Emulator-Test 13: Touch in Tablet-Größe (1920×1200, 240 dpi wie Fire Max 11) – alles antippbar, überall zurück. Test-Bilder als Release „qa-bilder“.
- Emulator-Test per manuellem Start überspringbar.

## Stil wählbar (1.0.236)
- Einstellungen → Aussehen → **Stil**: Aze (Standard) / Kino (Netflix-ähnlich: fast schwarz, kantig, weiße Auswahl, Rot für Laufendes, Poster wachsen) / Glas (Apple-ähnlich: weiche Flächen, runde Ecken, helle Auswahl mit Schatten, Blau für Laufendes). Keine fremden Namen in der App.
- `AppStyle` in `ui/Ui.kt` (Ecken, Zeilen-/Poster-Vergrößerung), `Palette.style`, `Modifier.rowFocus()`; Store `appStyle`; `AppController.updateStyle()` stellt das passende Farbschema mit ein (Schemata „Kino“, „Glas“ angehängt).
- Aziz: App darf dadurch NICHT langsamer werden → nur Zeichnen, kein Weichzeichnen (Blur), keine zusätzlichen Ladevorgänge. Poster-Reihen wie Netflix bewusst noch nicht (würden mehrere Kategorien gleichzeitig laden).

## Filme & Serien: Vorschau oben (1.0.239)
- Nach Aziz' Entwurf („A · Nachtblau – Filme“ / „B · Kino – Filme“): oben `VodHero` in `ui/VodScreen.kt` – „WEITERSCHAUEN“ bzw. Kategorie, Titel groß, Jahr · Film/Serie · ★, Hinweise „▶ Weiter ab …“ und „OK = Details“, Poster rechts als ausgeblendetes Hintergrundbild. Folgt dem fokussierten Poster (PosterCard `onFocus`), sonst erstes der Liste.
- Kein zusätzlicher Download: dasselbe Poster wie die Kachel (Coil-Zwischenspeicher), kein Backdrop vom Anbieter. Kategorien bleiben links, Raster darunter (Netflix-Reihen würden viele Kategorien gleichzeitig laden).
- „Weiter ab“/„Details“ sind noch Hinweise, keine Knöpfe – mögliche nächste Stufe.
- Touch-Test: Einstellungs-Bereiche heißen im Test „SET <Name>“ (vorher Verwechslung von „Suchen“ mit dem Seitenmenü dahinter), `open_pane` mit bis zu 5 Versuchen.

## Handy-Ansicht Filme (1.0.240)
- Aziz testete auf Xiaomi 12 Pro: Abspielen-Knopf in den Film-Details war nicht zu sehen (Seite zu hoch, nicht scrollbar).
- `DetailView`: `compact` = Höhe < 520 dp oder Breite < 600 dp → Knöpfe direkt unter Titel/Meta (FlowRow, bricht um), Film-Spalte scrollbar, Poster 110 dp, hochkant (< 600 dp breit) ohne Poster; Serien kompakt ohne Regie/Darsteller, Inhalt 3 Zeilen.
- Browse: Kategorien-Spalte max. 32 % der Breite; Vorschau oben nur ab 480 dp Höhe und 600 dp Breite.
- Fernseher (960×540 dp) und Tablet unverändert.

## Geräte-Erkennung, Tempo, Stabilität (1.0.241–1.0.243)
- `ui/Device.kt`: `DeviceKind` TV / TABLET / PHONE. Erkennung: UI-Modus Fernseher, leanback, Fire TV oder kein Touch → TV; sonst kleinste Breite ≥ 600 dp → Tablet, darunter Handy. `Device.isTouch` ersetzt alle `FrameRate.isTv`-Abfragen der Oberfläche. Einstellungen → Aussehen → **Gerät** (Automatisch / Fernseher / Tablet / Handy; Store `deviceMode`).
- Handy: immer Querformat (`MainActivity.applyOrientation`, SENSOR_LANDSCAPE). Touch: Zeilen/Knöpfe 6 dp höher (`Device.touchPad`), Hinweise fürs Tippen (`Device.hint`).
- **Sofortstart:** `Store.saveLast/lastChannel` merkt den letzten Sender komplett; `AppController.start()` spielt ihn sofort, die Senderliste wird danach gelesen (gleiche Adresse → kein Neustart). Startbildschirm mind. 0,6 s statt 1,2 s.
- Cover: fester `memoryCacheKey` + `placeholderMemoryCacheKey` in `posterRequest` → geladene Cover beim Zurückblättern sofort da. `CoverStore.init` zählt Dateien im Hintergrund.
- `qaLog`: reine Bedien-Meldungen (LIST_, SORT_, SET_ …) nur noch im Test-Build (vorher bei jedem ▲▼ Datei-Schreiben + volles Hänger-Protokoll).
- Filme: `VodHeroSlot` liest die Auswahl in eigenem Bereich (vorher zeichnete jeder Fokuswechsel die ganze Seite neu); Vorschau 172 dp hoch (150 schnitt „OK = Details“ ab).
- Einstellungen: Inhalt rechts folgt dem Fokus mit 0,1 s Verzögerung (`shownCat`), Antippen/OK sofort.
- `android:largeHeap="true"` (sehr große Playlists); Infoleiste kürzt lange Sendernamen.
- **Emulator-Test:** Tests 1–12 liefen nie in Fernseher-Größe, sondern 320×516 dp hochkant (wm size wird auf 2× der echten Emulator-Größe begrenzt). Neu: Start-Parameter `aze_test_device` (tv/tablet/phone), **Test 14 Handy** (576×1280, 235 dpi → 871×368 dp, prüft „Abspielen“ sichtbar) und **Test 15 Fernseher** (540×960, 160 dpi → 960×516 dp), beide mit Fotos. In Test 15 öffnet die blaue Taste die Einstellungen nicht (Foto zeigt nur das Bild) – noch klären.
- Nicht geprüft: echtes Handy/SHIELD – nur Emulator.

## Bildformat je Sender, Film-Inhalt, SkorText im Menü (1.0.245)
- Schnellmenü „Bild“ gilt nur für den laufenden Sender: `AppController.channelAspects` / `aspectFor(key)` / `updateChannelAspect`, Store `chAspects` (verschlüsselt). Ohne Eintrag gilt das allgemeine Format (Einstellungen → Bild & Ton, dort auch „Eigene Formate der Sender zurücksetzen“). Anzeige „· nur hier“.
- Automatische Erkennung schwarzer Balken (2,35:1 im 16:9-Bild) NICHT eingebaut: bräuchte Bildauswertung (PixelCopy), geht mit Tunneling nicht, kostet Leistung – Aziz angeboten als eigener Schalter (Standard aus).
- Film-Vorschau: Genre, Laufzeit und 2 Zeilen Inhaltsangabe. Lädt erst nach 0,6 s Ruhe auf einem Film (`VodHeroSlot`), Speicher `HeroInfo` (300 Einträge), Film-Details nutzen ihn sofort. Höhe 186 dp.
- 1.0.246: Vorschau-Angaben oben ausgerichtet (Aziz: „mehr nach oben“), Inhaltsangabe bis 4 Zeilen, Textbreite 72 %.
- 1.0.247 Handy: Seitenmenü scrollt bei < 480 dp Höhe (vorher Einstellungen/App beenden unerreichbar), Schnellmenü bei Touch 166 dp je Kachel → alle Kacheln sichtbar (Handy 5 je Zeile). Film-Vorschau auf dem Handy bewusst aus (Aziz einverstanden: übersichtlich + gut bedienbar wichtig).
- Seitenmenü: Eintrag „SkorText“ unter „Fußball heute“, Symbol `LineIcon.SKORTEXT` (Bildschirm mit Textzeilen + Ergebnis-Kästchen). Aus Filme/Serien über `AppController.pendingSkor`.
- Emulator-Test: angeschnittene Einträge (Mitte außerhalb des Bildschirms) werden erst hingewischt; Knopfleisten-Prüfung am Handy liest die letzte POS-Zeile (Leiste blendet sich selbst aus).

## Umschalten schneller + Wächter-Stufe 2 (1.0.248–1.0.251)
- Aziz fragte nach Rust-Neubau → abgelehnt/erklärt (Bild macht der Video-Chip; Zeit steckt in Verbindung, Puffer, Wartezeiten).
- 1.0.248: einzelner Senderwechsel wartet 60 ms statt 220 ms (`settleMs`, `lastZapAt`; schnelles Durchzappen < 0,7 s Abstand und Neustarts weiter 220 ms). Emulator: Median 0,33 s → 0,15 s. Aziz: „läuft superschnell“. Risiko: Anbieter mit 1 Verbindung lehnen evtl. öfter ab (403 → Wiederholung 0,4 s).
- 1.0.249: Einstellungen → System → „Tempo ausprobieren“: Schalter „Schnelles Umschalten“ (Store `zapFast`, Standard ein) und „Stehendes Bild schneller beheben“ (`freezeFast`, Standard aus: frozen 4 s statt 6 s).
- 1.0.251: Aziz: Bild bleibt manchmal stehen und läuft erst nach Hoch/Runter-Schalten. Wächter: 1. Eingriff leicht (stop+prepare), 2. Eingriff ohne gesundes Bild dazwischen = kompletter Neustart (`restartTick`, `fullRestart` setzt `retries` zurück) – vorher immer nur leicht mit Pausen 5/15/30/60 s. Neu im Hänger-Protokoll: „LUECKE n s WEGGESCHALTET“ (Hänger, bei denen der Zuschauer selbst umschaltet, fehlten bisher). Ursache nur vermutet – bei erneutem Hänger Protokoll-Link von Aziz holen. Nicht abgedeckt: endloses Puffern bei tröpfelnden Daten.
- Emulator-Lauf 251 lief beim ersten Mal in die 40-min-Grenze (hing in Test 11 bei den Einstellungen), Wiederholung bestand in 30 min → vermutlich Emulator-Zufall, beobachten.

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

## v1.0.87 – Stabilität & flüssiger (ohne Änderung am Abspielverhalten)
- Manifest: weitere configChanges (density, uiMode, fontScale, locale …) – Auflösungs-/HDMI-Wechsel startet die App nicht mehr komplett neu.
- VLC: alte Player werden über EINE Warteschlange freigegeben (VlcEngine.release) statt je Sender ein eigener Thread – schnelles Zappen bei VLC-Sendern kann nicht mehr mehrere Freigaben gleichzeitig auslösen.
- EPG: Zuordnung Sender → EPG-Kennung wird je Epg-Objekt zwischengespeichert (Senderliste scrollt flüssiger).
- Live-TV bleibt unter PIN/Einstellungen bestehen (PlayerScreen `active`-Parameter, CoverLayer fängt Berührungen ab) – beim Zurückkommen ist das Bild sofort da. Filme & Serien ersetzen den Player weiterhin.
- Nächste Schritte (einzeln ausliefern, weil Abspielverhalten): TS-Extractor-Flags (FLAG_ALLOW_NON_IDR_KEYFRAMES, FLAG_DETECT_ACCESS_UNITS) für schnelleres erstes Bild; danach FFmpeg-Ton-Decoder (AC3/E-AC3/MP2/DTS).

## v1.0.88 – Wächter gegen stehendes Bild (Abspielverhalten – einzeln ausgeliefert)
- Aziz (1.0.86): Bild bleibt stehen, kommt viel später wieder oder bleibt ganz eingefroren.
- Lücken im alten Wächter: griff nur bei „Bild UND Zeit stehen“ (Ton läuft weiter → nie), nie beim Puffern (tote Verbindung → bis 20 s Lese-Zeitüberschreitung), höchstens 2× je Sender ohne Rücksetzen (danach dauerhaft eingefroren).
- Neu: Neustart bei 6 s ohne neues Bild im Zustand „läuft“ (auch wenn Ton läuft; bei Tunneling weiter Bild+Zeit) oder 8 s „puffert“ ganz ohne neue Daten (bufferedPosition unverändert). Puffern mit ankommenden Daten wird nie unterbrochen. Nur wenn seit dem (Neu-)Start schon Bilder kamen (FpsProbe.framesAtStart). Max. 3 Neustarts, nach 60 s flüssigem Bild wieder verfügbar. Diagnose zeigt zusätzlich „Vorrat“.
- Falls es weiter hängt: Diagnose-Anzeige einschalten und Foto vom Moment des Hängers (Bilder/s, Zeit, Zustand, Vorrat, Ton).

## Automatischer Emulator-Test (ab v1.0.89)
- Ablauf bei jedem Push: build → emulator-test → release. Veröffentlicht wird NUR, wenn der Emulator-Test besteht.
- Test-Build „qa“ (wie Release mit R8, Debug-Signatur, `BuildConfig.TEST_HOOKS = true`): Start-Parameter `aze_test_playlist` legt eine Test-Playlist an. Im normalen Build ist das aus.
- `.github/qa/make_media.sh` erzeugt Test-Videos mit ffmpeg, `.github/qa/test_server.py` liefert sie in Echtzeit: Normal, tote Verbindung (stall), Bild hört auf/Ton läuft (vstop), Verbindungsabbruch (drop).
- `.github/qa/run_emulator_test.sh` (Android 11, x86_64): App startet, Bild kommt, normaler Sender 45 s ohne Wächter-Neustart, Wächter erholt sich bei stall/vstop, drop erholt sich, 20× schnelles Zappen, kein Absturz. Liest die logcat-Zeilen mit Kennung `AzeQA` (FIRST_FRAME, WATCHDOG, ERROR, ENDED – `qaLog()` in PlayerScreen.kt).
- Ergebnis (Bildschirmfotos, Protokoll) als Artefakt „emulator-test-ergebnis“ am Lauf.
- Grenze: echter Anbieter und Video-Chip von SHIELD/Fire TV sind nicht nachgebildet – Test am Fernseher bleibt letzter Schritt.
- Zusätzlich vor jeder Version: unabhängige Code-Prüfung durch eine frische KI-Instanz (ohne Kenntnis der Änderungen).

## v1.0.92 – nach unabhängiger Prüfung (erste Version, die den Emulator-Test bestanden hat)
- Unabhängige KI-Prüfung fand u. a.: Wächter gab nach 3 Neustarts auf; Senderstart ohne Daten nicht erkannt; VLC ohne Hänger-Erkennung; „Umschalten“ (AFR/50 Hz) konnte bei Abbruch hängen bleiben (dann startet kein Sender mehr); Tasten gingen unter den Einstellungen an den Player; FpsProbe-Wettlauf; Film-Player ohne Speichergrenze und lief bei Home weiter; WebView-Anlegen ungesichert; lastChannel schrieb bei jedem Zappen die große verschlüsselte Datei.
- Umgesetzt: Wächter gibt nie auf (Pausen 5/15/30/60 s), Start ohne Daten → nach 10 s + 8 s neu; `firstFrameSeen` statt Bildzähler (geht auch mit Tunneling); keine Neustarts bei unterdrücktem Ton (Alexa); kein Start im Hintergrund; AFR/50 Hz/Zurücksetzen immer anhalten → umschalten → warten → neu starten (try/finally); Bildfläche nach Frequenzwechsel neu (nur mit AFR); VLC-Wächter (8 s keine Zeit-Bewegung), `--http-reconnect` entfernt, Netzwerkfehler (2xxx) schieben Sender nicht mehr zu VLC; Lese-Zeitüberschreitung Live 10 s; Film-Player LoadControl + Pause bei ON_STOP; lastChannel in eigener Datei `aze_last`; WebView-Anlegen abgesichert.
- Emulator-Ergebnis: tote Verbindung → Bild nach ~8 s Hänger wieder da; Bild steht/Ton läuft → nach ~6 s wieder da; Abbruch → 1 s; 20× Zappen stabil; normaler Sender ohne Fehl-Neustart.
- Beobachtung: Liefert der Anbieter langsamer als Echtzeit (Daten kommen, aber zu wenig), puffert der Player lange – der Wächter greift bewusst nicht ein. Dann hilft nur größerer Puffer.
- Noch offen aus der Prüfung: Lippensynchron mit positivem Versatz kann Bilder verwerfen (Start-Puffer an Versatz anpassen); EPG-Speicher bei sehr großen Listen (kompakter speichern); Store-Entschlüsselung beim Start auf dem Haupt-Thread.

## Lippensynchron-Standbild behoben
- Aziz bestätigt: Bild blieb stehen, wenn Lippensynchron > 0 (Ton später).
- Ursache: Bild wird um den Versatz früher gezeigt (verschobene Ton-Uhr, AudioSync.kt), bei Live aber nur ~1,5 s Bild im Vorrat → Bilder fehlen/werden verworfen.
- Lösung: PlaybackConfig.loadControl(audioLeadMs): Start-Vorrat = max(Stufe, Versatz + 2 s), Rebuffer = max(Stufe, Versatz + 3 s). Player wird je 0,5-s-Stufe des wirksamen Versatzes neu angelegt (kurzes Schwarz beim Ändern des Werts bzw. beim Wechsel auf Sender mit anderem Versatz). Umschalten dauert mit Versatz entsprechend länger.
- Emulator-Test 6 (Lippensynchron 2000 + 1000 ms je Sender, Start-Parameter `aze_test_audio_delay`, `aze_test_channel_delay_url`/`aze_test_channel_delay` im qa-Build) prüft 40 s ohne Wächter-Neustart und laufende Bilder.
- WICHTIG: Gegenprobe mit dem Stand OHNE Korrektur lief im Emulator ebenfalls flüssig – der Emulator (Software-Decoder) stellt dieses Standbild NICHT nach. Die Korrektur beruht auf Code-Analyse + Aziz' Beobachtung; Bestätigung nur auf SHIELD/Fire TV möglich (v1.0.96).

## Playlists pausieren + Fußball-Zuordnung (nach v1.0.96)
- Neu: Playlist „Pausieren“/„Einschalten“ (Einstellungen → Playlists). `PlaylistSource.enabled` (gespeichert, Standard an). Pausierte werden nicht geladen/aktualisiert, nicht angezeigt, keine EPG-/Film-Quelle; Zugangsdaten + letzte Liste (Datei) bleiben → Einschalten zeigt sie sofort, lädt nur neu, wenn keine Kopie da ist. `AppController.activeSources`, `setSourceEnabled()`.
- Fußball „laut liveonsat“: Länder im liveonsat-Namen wurden beim Vergleich verworfen → „Disney+ Norge“ passte auf jeden Disney-Sender. Jetzt muss das Land passen (`Football.listedCountry` vs. `Epg.countryOf`; eigene Sender ohne Land nur für DE/TR/AT/CH). `Epg.countryOf` erkennt jetzt auch „(TR)“ / „[DE]“.
- Fußball „vermutlich“ (EPG-Notlösung): Sendungen, die einen ANDEREN Wettbewerb nennen („Fußball: Premier League“ bei Süper-Lig-Spiel), zählen nicht mehr (`Football.namesOtherLeague`, 2. Bundesliga ≠ Bundesliga).
- liveonsat: Jugend-/Frauen-Spiele derselben Vereine werden nicht mehr verwechselt.
- Land eigener Sender: `Football.ownCountry` (Vorsatz/Gruppe/tvg-id, Länderwort wie „FRANCE“, Kürzel vorne/hinten wie „… FR“); „Sports“ = „Sport“ = „Spor“ beim Namensvergleich.
- „Laut liveonsat außerdem“: jeder Eintrag ist anwählbar → OK zeigt ähnliche eigene Sender ohne Länder-Prüfung (`Football.matchByNameLoose`), passendes Land zuerst; Zurück = Übersicht.
- Werkzeug `.github/workflows/liveonsat-check.yml` (nur von Hand): lädt liveonsat bei GitHub und zeigt den Abschnitt zu einer Mannschaft als Hinweis – zum Prüfen, was liveonsat wirklich auflistet.

## „Alles leeren“ stürzte ab (behoben)
- Ursache: CacheCleaner.clearAll lief auf dem Bedien-Thread (bis 250 MB Logos löschen → „App reagiert nicht“) und löschte auch Ordner, die Bild-Lader/Web-Anzeige offen hatten.
- Jetzt: suspend im Hintergrund (Dispatchers.IO), Bilder-Ablage leert Coil selbst, Web-Anzeige-Ordner bleiben; Knopf zeigt „Wird geleert …“.
- Emulator-Test 7 (Start-Parameter `aze_test_clear_cache`): Leeren während ein Sender läuft, App muss weiterlaufen.

## Gruppen ausblenden (Dialog wie IBO Player) + Design-Entwürfe
- Senderliste → Gruppenübersicht: unten „Gruppen ausblenden …“ oder OK lang auf einer Gruppe → Dialog mit Haken (Haken = ausgeblendet), OK · Alle auswählen · Abbrechen. Mindestens eine Gruppe muss sichtbar bleiben. `ui/HideGroupsDialog.kt`, `AppController.setHiddenGroups()` (Gruppen pausierter Playlists behalten ihre Einstellung).
- Design-Entwürfe (claude.ai-Artifact „Aze-IPTV Design & Logo“): 4 Logos (A Play-A, B Zapp-Z, C Signal, D Orbit), Startbildschirm mit Logo + dünnem Ladebalken, Seitenmenü mit dünnen Linien-Symbolen (Suchen, TV, Filme, Serien, Fußball heute, Playlist, Einstellungen), Gruppen-Ausblenden-Dialog. Schrift im Entwurf Manrope. Aziz wählt Logo/Richtung – danach App-weit umsetzen (ruhig, dünn, einheitlich, nicht „klotzig“).

## Design „Orbit“ (von Aziz gewählt: Logo D)
- `ui/Brand.kt`: `AzeLogo` (Kreis + schräge Umlaufbahn + Satellit + Abspielzeichen, gezeichnet, folgt dem Farbschema), `AzeWordmark` („AZE“ dünn, gesperrt), `LineIconView`/`LineIcon` (dünne Linien-Symbole 1,6 im 24er-Raster), `SplashScreen`, `SectionLabel`.
- Startbildschirm (MainActivity): Logo + dünner laufender Balken, solange die Senderliste beim Start lädt (mind. 1,2 s, max. 25 s); Player darunter erst danach bedienbar.
- Seitenmenü (`SideMenu`, auch in Filme/Serien): Logo oben, Symbole + Text, Fokus = helle Pille, Einstellungen/App beenden unten.
- Gruppenliste: „GRUPPEN“-Kopf, ruhiger Hintergrund, aktive Gruppe leicht im Akzentton.
- App-Symbol und TV-Banner neu (Orbit-Logo auf #07152B, Schriftzug als dünne Linien).
- Schrift einheitlich wie Seitenmenü: Gruppen und Sender 15 sp normal (Fokus halbfett), Nummern/Bereiche 13 sp gedämpft; laufender Sender leicht blau hinterlegt statt Rahmen.
- Einstellungen: linke Leiste scrollt (auf Aziz' TV waren „Über“/„Erweitert“ abgeschnitten), Stil wie Seitenmenü mit Logo. Farbschema + Schriftfarbe jetzt unter Bedienung → „Aussehen“. „Senderliste bearbeiten“ eigener, gut sichtbarer Abschnitt oben unter Senderliste.
- Nächste Schritte Design: Schnellmenü, Filme/Serien, Abschnitts-Kästen der Einstellungen im gleichen Stil angleichen.

## Filme/Serien: Kategorien ausblenden
- Wie bei Live-TV: in der Kategorienliste GANZ OBEN „Kategorien ausblenden …“ (vorher unten – Aziz fand es nicht), zusätzlich Einstellungen → Senderliste → Abschnitt „Senderliste bearbeiten“: unter „Senderliste bearbeiten öffnen“ zwei gleich aussehende Knöpfe „Filme-Kategorien ausblenden“ / „Serien-Kategorien ausblenden“ (je Playlist). Kein eigener Abschnitt „Ausblenden“ und kein zweiter Live-TV-Knopf (Aziz: doppelt gemoppelt). Dialog: Kästchen rechts bündig untereinander oder OK lang auf einer Kategorie → Dialog mit Haken. Getrennt je Playlist und Filme/Serien.
- Gespeichert in Store.vodHidden ("playlistId|m|katId" bzw. "|s|"), `AppController.setVodHidden/isVodHidden`.
- Dialog verallgemeinert: `HideListDialog(title, hint, entries: List<HideEntry>, onApply)`; `HideGroupsDialog` nutzt ihn für Live-TV.
- Kategorienliste im gleichen Schriftstil wie Gruppen/Sender (15 sp normal, Zahlen 13 sp gedämpft, aktive leicht blau).

## Update-Hinweis
- Beim Start (nach dem Startbildschirm, ca. 4 s) einmal `Updater.check()`; gibt es eine neuere Version, erscheint unten rechts ca. 3 s ein kleiner Hinweis „Update 1.0.x verfügbar · Einstellungen → App-Update“ (Logo + Text, kein Fenster, kein Fokus). Aziz: nur kurz einblenden, mehr nicht.

## App-Update: Installation zuverlässiger
- Aziz: Download bis 100 %, dann passiert nichts; erst nach erneutem Drücken (neuer Download) startet die Installation.
- Jetzt: Anzeige „Download fertig – wird geprüft …“ während der SHA-256-Prüfung; fertige Datei wird behalten (`Updater.cached`) → erneutes Drücken öffnet nur den Installer („jetzt installieren“); Installer über ACTION_INSTALL_PACKAGE (Ersatz ACTION_VIEW); nach 3 s wird geprüft, ob die App in den Hintergrund ging – sonst einmal automatisch neu öffnen, danach klarer Hinweis; nach Erteilen von „Unbekannte Apps installieren“ geht es beim Zurückkommen automatisch weiter.

## Multi-View (mehrere Sender gleichzeitig)
- Aziz: Ausblenden NUR in den Einstellungen (PIN-geschützt) → Einträge „… ausblenden“ und OK lang in Gruppen-/Kategorienliste entfernt.
- Neu `ui/MultiView.kt`: Schnellmenü (OK lang) → „Multi-View“. Anordnungen: Nebeneinander (2), Bild im Bild (2, kleines Bild als TextureView), 4 Bilder. Je Bild eigener ExoPlayer, Ton nur im gewählten Bild (blauer Rahmen).
- Bedienung: ◀ ▶ Bild wählen, ▲ ▼/CH± umschalten im Bild, Ziffern Direktwahl, OK = Senderauswahl (Gruppen → Sender), OK lang/Menü = Anordnung wechseln, Zurück = Ende, Sender des gewählten Bildes läuft im Vollbild weiter. Normaler Player gestoppt, solange Multi-View offen. YouTube geht im Multi-View nicht.
- Anbieter muss mehrere gleichzeitige Verbindungen erlauben (sonst Hinweis „Anbieter lehnt ab (403) …“ im Bild).
- Aziz (1.0.111): ein Bild blieb stehen; Senderauswahl wirkte eingefroren (kein Eintrag markiert = Fokus nicht in der Liste). Behoben: Fokus wird bis zu 30× nachgefordert und nur zurückgeholt, wenn die Liste keinen hat (1.0.112 fing dabei auch ▲▼ ab → Liste „eingefroren“; ab 1.0.113 behoben, Test 8 prüft jetzt, dass ▼▼ den dritten Sender wählt); PlayerViews blockieren Fokus (FOCUS_BLOCK_DESCENDANTS, auch im normalen Player); Wächter je Bild (6 s Standbild / 8 s ohne Daten → stop+prepare, danach 10 s Pause).
- Aziz (1.0.113): das Bild OHNE Ton friert nach einer Weile ein, das gewählte läuft. Ursache (Analyse): Video folgt der Ton-Uhr; das stumme Bild (volume 0) öffnete trotzdem einen Tonausgang – bei Dolby-Durchleitung zur Soundbar gibt es nur einen → Ton-Uhr steht → Standbild. Lösung: inaktive Bilder ohne Tonspur (setTrackTypeDisabled AUDIO), Ton wandert mit der Auswahl; Wächter zusätzlich „10 s kein neues Bild, egal welcher Zustand“. Emulator stellt das nicht nach (kein HDMI-Passthrough) – Bestätigung nur auf SHIELD.
- Emulator-Test 8 (`aze_test_multiview`): zwei Bilder bekommen Bild, Anordnung wechseln, Zurück – kein Absturz. Log-Kennungen MV_FIRST_FRAME/MV_ERROR.

## Multi-View: Standbild abwechselnd links/rechts (Analyse 2) + Feinschliff
- Aziz (1.0.114): mal links, mal rechts bleibt ein Bild stehen, Wächter holt es nach ~6 s zurück. Vermutung: Verbindungsgrenze des Anbieters – beim Neuverbinden ist die alte Verbindung noch offen → Anbieter trennt das ANDERE Bild (Ping-Pong); beim Start ebenso mit der noch offenen Verbindung des normalen Players.
- Jetzt: Multi-View fragt bei Server + Login `player_api.php` → user_info.max_connections/active_cons und zeigt es an (Log MV_CONNECTIONS); Bilder starten nacheinander (1,2 s + 1,2 s je Bild); Wächter wartet 2,5 s zwischen stop und prepare, danach 12 s Ruhe; Puffer je Bild max. 20 MB.
- Sendername: bei „Nebeneinander“ ÜBER dem Bild im freien Bereich; bei Bild-im-Bild/4 Bilder nur 4 s nach dem Umschalten im Bild.
- Senderauswahl: ◀ in der Senderliste = zurück zu den Gruppen.
- Multi-View-Bilder: max. 720p (kleines Bild-im-Bild 480p) über setMaxVideoSize – wirkt nur bei HLS mit mehreren Stufen.
- Aziz: Multi-View läuft bei TiviMate auf demselben Gerät/Abo sauber → Verbindungsgrenze allein ist NICHT die Ursache; Hauptverdacht jetzt Puffer-Speicher (vorher bis 64 MB je Player) und überlappende Verbindungen.
- Aziz (1.0.116): weiterhin abwechselnd „Bild hing“ nach ~6 s. Jetzt MESSEN statt raten: je Bild Messzeile (Zustand, Bilder/s, Uhr läuft?, Daten kommen?, Vorrat, verworfene Bilder, Decoder, Auflösung). Beim Hänger 20 s „Beim Hänger: …“ unten im Bild (Foto!), mit Fehlersuche-Anzeige immer. Grund im Text („Bild steht, Player läuft“ / „keine Daten mehr“ / „kein neues Bild“). Log MV_TICK/MV_WATCHDOG im Test-Build.
- URSACHE GEFUNDEN (Foto der Messzeile, 1.0.117): „Ende · Daten keine · verworfen +0 · Nvidia.h264“ – der Anbieter beendet Streams ab und zu (STATE_ENDED). Multi-View rief bei ENDED nur prepare() auf – das ist im Zustand ENDED wirkungslos → Standbild bis zum Wächter (~6 s). Fix: bei ENDED nach 0,3 s stop()+prepare(). Emulator-Test 8: Bild 2 = „Test 4 Abbruch“, muss sich ohne Wächter erholen. Lehre: erst messen (Messzeile), dann ändern. Normaler Player ebenso: ENDED → 0,3 s → stop()+prepare() (vorher 1 s, seekToDefaultPosition); Test 4 verlangt Bild zurück in ≤5 s ohne Wächter.
- 2. Foto (1.0.119): „läuft · Bilder 0/s · Uhr läuft · Vorrat 27 s · verworfen +0“ = ZEITSPRUNG im Stream (Bild-PTS springt vor, Ton/Uhr läuft, Player wartet bis die Uhr aufholt → „Bild kommt viel später“ – auch Aziz' ursprüngliches Problem im normalen Player). Fix in beiden Playern: Uhr läuft + Vorrat > 1 s + 3 s kein Bild → sofort neu (Wächter-Grund „jump“). Test-Sender 5 „Zeitsprung“ (jump.ts: nach 15 s Bild-PTS +60 s, Ton weiter), Emulator-Test 4b.
- Neues Farbschema „Stadion“ (Grüntöne nach Aziz' Foto), wählbar unter Bedienung → Aussehen.

## „Fertig machen“: zwei unabhängige Prüfungen + Recherche (1.0.252–1.0.264)
- 1.0.252: Handy – beim App-Update wird das Drehen freigegeben (`Device.freeRotation`), solange der Installer offen ist (Xiaomi 12: Fingerabdruck-Abfrage war im erzwungenen Querformat nicht zu sehen); 5 s nach Rückkehr wieder Querformat. Kollege muss EINMAL per Browser (arm64-APK) installieren, um diese Korrektur zu bekommen.
- 1.0.253 (1. Prüfung, 12 Funde): Portal-Sender versuchen es nach Fehlschlag selbst neu (2/4/8/15 s); `rebuild()` dreht einen Senderwechsel während des Listen-Neuaufbaus nicht mehr zurück; VLC: `retries = 0` nach erstem Bild; Xtream-Zugangsdaten im Pfad kodiert (`pathUser/pathPass`); Hinweis „Bildschirm wird umgestellt …“ beim Frequenzwechsel.
- 1.0.254 (Abspielverhalten): Wächter – Sperrzeit/Zähler gelten nicht mehr für den nächsten Sender; neu Grund „blind“: Player READY, aber 10 s nie ein erstes Bild → kompletter Neustart, max. 2× je Sender (Kollege: nach Einschalten alle Sender schwarz, erst nach 10–12× Umschalten Bild – Ursache unbestätigt, Hänger-Protokoll fehlt noch).
- 1.0.255 (Abspielverhalten): tote Leitung (offen, aber stumm) – Live-Sender lesen mit 4 s statt 10 s Geduld (`httpFast` → `LiveSource(context, http, httpFast)`), danach nahtloser Folge-Verbindungspfad. Sicherung: antwortet ein Server beim Verbinden langsamer als 4 s oder gibt es 3 Lesezeit-Überschreitungen in 2 min, gilt für diesen Host wieder 10 s (`slowHosts` in `EndOnDropDataSource`). Multi-View unverändert (10 s). Emulator-Test 2 bestanden; Zeitgewinn dort nicht gemessen (Log-Puffer überschrieben).
- 1.0.256 (2. Prüfung über Einstellungen, Senderliste, Fußball, Filme/Serien, Multi-View, Suche, Store, Webserver; 12 Funde): Web-Bearbeitung zählt Fehlversuche auch bei `/liveonsat` (Code ließ sich durchprobieren); Filme/Serien: abgebrochenes Laden überschreibt die neue Kategorie nicht mehr (CancellationException weiterwerfen), Lazy-Schlüssel mit Index (kein Absturz bei doppelten Kennungen); EPG-Zählung in Einstellungen im Hintergrund; liveonsat ohne `putIfAbsent` (Android 5/6); Weiterschauen: LinkedHashMap, neueste 300 bleiben.
- 1.0.257: Filme/Serien – nach Details/Film zurück wieder in derselben Kategorie/Suche beim geöffneten Eintrag (`BrowseMemory` in VodScreen; Emulator prüft nur „kein Absturz“, Aziz soll die Stelle selbst ansehen).
- 1.0.258 (Abspielverhalten): nur TV/Box – Wiedergabe 20 s wegen „Ton kurz abgegeben“ unterdrückt → Ton neu anfordern (playWhenReady aus/an), höchstens alle 60 s; Handy/Tablet unverändert (Telefonat). ERLEDIGT damit aus Prüfung 1: Audio-Fokus; aus Prüfung 2: Zurück-Stelle bei Filmen/Serien (SettingsScreen-`cat` weiter offen).
- 1.0.259 (Abspielverhalten): Bremse beim Neuverbinden – Folge-Verbindung nur sofort, wenn die letzte ≥3 s lief oder ≥512 KB brachte, sonst wachsend 0,3 → 8 s (`shortRuns` in `LiveSource.attach`); Stream-Ende mehrmals in 30 s → Pause 0,3 → 5 s (`endedRuns`).
- 1.0.260 (Abspielverhalten): Multi-View – Portal-Sender holen beim Neuversuch nach Fehler einen frischen Link (`reload`).
- 1.0.261 (Abspielverhalten): VLC-Umschaltung bei Format-/Decoder-Fehler (Gruppe 3/4) erst beim zweiten Fehler ohne Bild dazwischen (`retries >= 1`).
- 1.0.262: Senderliste `focusKick` → zuletzt markierter Sender; PhoneInputServer je Anfrage ein Thread, max. 64 Kopfzeilen (auch WebEdit), parseForm robust; Web-Bearbeitung speichert nur geänderte Gruppen; Serien mit `episodes` als Liste.
- 1.0.263 gibt es NICHT als Download (Emulator-Job lief in die 40-min-Grenze, zweites Mal nach Lauf 251 – Ursache unklar, vermutlich langsamer Emulator; nicht neu starten, sonst wäre 263 „neueste“ Version). Inhalt steckt in 1.0.264: Einstellungen merken den Bereich nach Senderliste bearbeiten/PIN/Hilfe-Video (`cat/shownCat/onTv` vor den Unterseiten, `catFocus`, `visited`).
- 1.0.264 (Abspielverhalten, von Aziz ausdrücklich gewünscht): Media3 1.5.1 → 1.9.4, minSdk 21 → 23 (Aziz: niemand nutzt Android 5). Bewusst nicht 1.11: die braucht Kotlin ≥ 2.1 (Media3 1.11 ist mit Kotlin 2.2 gebaut, Projekt hat 2.0.21) und schaltet „dynamic scheduling“ standardmäßig ein. 1.9.x enthält #3016 (ProgressiveMediaSource entfernte eingereihte Perioden) und #2069 (TS hängt auf manchen Geräten). `StuckPlayerException` (androidx.media3.common.util, ab 1.9: 10 s READY ohne Fortschritt) → normaler Neuversuch, zählt nicht für VLC, Log STUCK. Rückfall ohne neues Media3: 1.0.262. Nächster möglicher Schritt: Kotlin 2.1/2.2 + Media3 1.11.x (#3206 letztes TS-Bild, Ton-Unterlauf-Toleranz) – eigene Version.
- ERLEDIGT damit auch: Bremse, VLC-Merken, Multi-View-Portal, focusKick, Web-Bearbeitung, Serien-Liste, PhoneInputServer, Einstellungen-Bereich. Aus Prüfung 1 noch offen: `SystemClock.elapsedRealtime()` im Wächter/VLC, verspätete Ereignisse des alten Senders, 50 Hz früher setzen.
- NOCH OFFEN aus Prüfung 1 (alle Abspielverhalten → einzeln): Wiedergabe nach Audio-Fokus-Verlust bleibt unterdrückt (nach ~20 s stop/prepare, max. alle 60 s); nahtloses Neuverbinden ohne Bremse bei sehr kurzen Antworten (nur anhängen ab ≥3 s Ladezeit oder ≥512 KB); ein einzelner Fehler der Gruppe 3 merkt den Sender für die Sitzung als VLC (erst beim zweiten); Wächter/VLC auf `SystemClock.elapsedRealtime()`; verspätete Ereignisse des alten Senders; 50 Hz früher setzen.
- NOCH OFFEN aus Prüfung 2: Filme/Serien – nach Details/Film zurück landet man in Kategorie 1 oben (Zustand `selected/query/Rasterposition` nach `VodScreen` heben; gleiches Muster `cat` in SettingsScreen nach Unterseiten); Multi-View: Portal-Sender holen beim Neuversuch keinen neuen Link (Abspielverhalten); ChannelOverlay `focusKick` zielt nach erstem Öffnen auf Zeile 0 (besser `wrap(focusedIdx)`); Web-Bearbeitung speichert die Reihenfolge ALLER Gruppen (nur geänderte ablegen); Serien mit `episodes` als Liste statt Objekt; PhoneInputServer bedient nacheinander / Kopfzeilen ohne Obergrenze.
- Recherche „Hänger < 0,3 s“ (als Hinweis, vor Umsetzung am Quellcode prüfen): unter 0,3 s nur bei sauberem Verbindungsende mit Vorrat; tote Leitung realistisch 1,5–3 s Erkennung. Empfohlene Reihenfolge: Media3 1.5.1 → 1.11.x (Fehler #3016/#3206 im Folge-Verbindungspfad; Achtung neue StuckPlayerException ab 1.9 → Doppel-Eingriffe mit Wächter); Bytefluss-Wächter in der DataSource; Wartezeiten gestaffelt statt 10 × 0,3 s; alte Verbindung nachweislich schließen, bei Bildschirm aus stop; nach Aufwachen auf geprüftes Netz warten (NetworkCallback VALIDATED), HDMI-Plug → Surface neu; TS-Flags DETECT_ACCESS_UNITS | ALLOW_NON_IDR_KEYFRAMES; Verbindungs-Timeout 4–6 s; Vorrat 4–6 s je Sender lernend; Option „Ton als PCM“; Stalker-Watchdog abfragen.
- Ehrliche Grenzen: alles nur im Emulator geprüft (kein HDMI/Passthrough, keine echte SHIELD/Xiaomi); in Test 15 öffnete die blaue Taste die Einstellungen nicht (unklar ob Test oder App).

## Für einen neuen Chat
- Code: https://github.com/Postmen1971/Aze-iptv-Player- (main). Jeder Push baut per GitHub Actions v1.0.<Lauf> inkl. Tests (app/src/test/LogicTest.kt) und veröffentlicht 5 APKs (armeabi-v7a, arm64-v8a, x86, x86_64, universal).
- Download: https://github.com/Postmen1971/Aze-iptv-Player-/releases/latest/download/Aze-IPTV-Player-armeabi-v7a.apk (SHIELD: arm64-v8a geht auch).
- Build-Fehler: Logs sind gesperrt → Fehler stehen als ::error::-Annotationen am Check-Run (gh api …/check-runs/<job>/annotations).
- Testgerät: NVIDIA SHIELD (Bruder: Fire TV). Aziz testet selbst und schickt Fotos/Videos.
- Arbeitsweise mit Aziz: Antworten auf Deutsch, einfach erklärt; viele Wünsche gern in einem Build; Änderungen am Abspielverhalten (Puffer, Wächter, Wiederholungen) aber einzeln ausliefern. Ruhiges, dezentes Design, keine TiviMate-Namen. Sicherheitsregel: Passwörter/Secrets trägt Aziz selbst ein.
- Offen: eigener Signatur-Schlüssel (GitHub-Secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD durch Aziz), A2-YouTube-Link fehlt, .xz-EPG (Rytec) nicht unterstützt, Option „Zeitzone der Quelle“ statt ±1 h angeboten (noch nicht gewünscht), Update-Hinweis beim Start und automatische Logos angeboten.
