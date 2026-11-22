/* SkorText – weitere Sprachen, gemeinsam für die Android-App (app.js) und das GigaBlue-Plugin (texts.py).
   Deutsch und Türkisch stehen direkt in app.js / texts.py. Fehlt hier ein Text, nimmt SkorText den englischen,
   sonst den deutschen. Wochentage ab Montag. Schlüssel mit e2_ = abweichender Text im GigaBlue-Plugin.
   „tx“: Übersetzung nach dem deutschen Originaltext (Einstellungen, Runden, Meldungen, Ligen).
   „nations“: Ländernamen nach dem englischen Namen der Datenquellen.
   Das GigaBlue-Paket bekommt diese Datei beim Bauen als sprachen.json (tools/sprachen_json.py). */
var SKOR_SPRACHEN = {
 "order": [
  "en",
  "fr",
  "es",
  "it",
  "sv",
  "da",
  "no"
 ],
 "names": {
  "en": "English",
  "fr": "Français",
  "es": "Español",
  "it": "Italiano",
  "sv": "Svenska",
  "da": "Dansk",
  "no": "Norsk"
 },
 "en": {
  "txt": {
   "overview": "Overview",
   "results": "Results",
   "table": "Table",
   "liveTable": "Live table",
   "md": "Matchday %d",
   "live": "Live",
   "allLive": "All live matches",
   "help": "Controls",
   "settings": "Settings",
   "noLive": "No live matches right now",
   "ht": "HT",
   "ft": "FT",
   "pp": "PPD",
   "goal": "GOAL!",
   "club": "Club",
   "pl": "Pos",
   "sp": "P",
   "w": "W",
   "d": "D",
   "l": "L",
   "goals": "Goals",
   "diff": "GD",
   "pts": "Pts",
   "fast": [
    "Germany",
    "Türkiye",
    "Europe",
    "LIVE"
   ],
   "days": [
    "Mon",
    "Tue",
    "Wed",
    "Thu",
    "Fri",
    "Sat",
    "Sun"
   ],
   "notfound": "Page %d not available",
   "stand": "Updated",
   "cDE": "GERMANY",
   "cTR": "TÜRKIYE",
   "cEU": "EUROPE",
   "cNEWS": "NEWS (TURKISH)",
   "openHint": "▲▼◄► select · OK open · BACK exit",
   "home": "BACK = overview",
   "exitAgain": "Overview · BACK again = exit",
   "setHint": "▲▼ select · ◄► change · BACK return",
   "lselRow": "Choose leagues",
   "lselOpen": "open (%d shown)",
   "lselInfo": "What should appear on the main page – OK sets or removes the tick.",
   "lselHead": "Ticked = shown on the main page",
   "lselHint": "▲▼ select · OK tick on/off · BACK",
   "t_lselHint": "Tap = tick on/off · tap ▲▼ = scroll",
   "lselMore": "more",
   "lselOn": "Shown",
   "lselOff": "Hidden",
   "cEUx": "More European leagues",
   "loading": "Loading data …",
   "noData": "No data available.",
   "noData2": "Check the internet connection.",
   "noGames": "No matches found.",
   "noTable": "No table available.",
   "source": "Data: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(current)",
   "mdBrowse": "Matchday",
   "noMore": "No further matchday",
   "newsHint": "▲▼ select · OK read · ◄► section",
   "articleHint": "▲▼ / ◄► next story · OK / BACK",
   "resHint": "◄► league · OK table",
   "tabHint": "◄► league · OK results",
   "cNAT": "INTERNATIONALS",
   "cRANK": "RANKINGS",
   "cMORE": "MORE",
   "more": "▼ more: internationals, rankings",
   "t_more": "swipe ▲ = more: internationals, rankings",
   "ardRow": "ARD Text (German teletext)",
   "ardHint": "▲▼ select page number · OK open · ◄► turn pages · BACK",
   "t_ardHint": "Tap a page number = open · BACK = previous page",
   "ardLoad": "Loading ARD Text …",
   "ardErr": "ARD page %d not reachable",
   "fav": "My club",
   "today": "Today",
   "tv": "On TV today",
   "tvHint": "▲▼ match · OK channels · BACK close",
   "tvNone": "no channels known",
   "yesterday": "Yesterday",
   "tomorrow": "Tomorrow",
   "dayHint": "◄► change day",
   "t_dayHint": "swipe ◄► = change day",
   "last": "Last matches",
   "next": "Next matches",
   "noFav": "No favourite club chosen.",
   "noFav2": "Choose one under Settings (900).",
   "place": "Position",
   "scorers": "Scorers",
   "detHint": "OK = table · ▲▼ scroll",
   "t_detHint": "tap title = table · swipe ▲▼",
   "pen": "P",
   "og": "OG",
   "rkFifa": "FIFA World Ranking",
   "rkUefa": "UEFA country ranking",
   "rkClub": "UEFA club ranking",
   "points": "Points",
   "season": "Season",
   "nation": "Country",
   "asOf": "As of",
   "noMatches": "No matches on this day.",
   "nlHint": "◄► ▲▼ next group",
   "natEU": "European internationals",
   "t_nlHint": "swipe = next group",
   "helpOpen": "OK = open",
   "t_helpOpen": "tap = open",
   "helpInfo": "All buttons and pages at a glance.",
   "updRow": "Check for update",
   "updOpen": "OK = check now",
   "t_updOpen": "tap = check now",
   "updInfo": "Load a new version from GitHub. Installed:",
   "updNone": "SkorText is up to date",
   "updLoad": "Loading update %s …",
   "updPerm": "Please allow “Install unknown apps” for SkorText and come back with BACK – it then continues by itself",
   "updOpening": "Opening installation …",
   "updEmbed": "Inside Aze IPTV Player, SkorText comes with its update (Settings → App update)",
   "updErr": "Update failed",
   "updAvail": "New version %s – Settings → Update",
   "t_openHint": "Tap = open · BACK exit",
   "t_home": "◄ Overview (tap here)",
   "t_resHint": "swipe ◄► league · tap title = table",
   "t_tabHint": "swipe ◄► league · tap title = results",
   "t_setHint": "Tap = change · BACK return",
   "t_newsHint": "Tap = read · swipe ◄► section",
   "t_articleHint": "swipe = next story · tap title = back",
   "t_mdArrows": "swipe ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Change league"
    ],
    [
     "OK",
     "Results ⇄ table"
    ],
    [
     "▲ ▼",
     "Results: matchday back/forward"
    ],
    [
     "▲ ▼",
     "Table: next part"
    ],
    [
     "BACK",
     "Overview, again = close app"
    ],
    [
     "Overview",
     "▲▼◄► select, OK opens"
    ],
    [
     "440–460",
     "Champions / Europa / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Today (◄► day)"
    ],
    [
     "520",
     "On TV today: ▲▼ match, OK all channels"
    ],
    [
     "110",
     "My club"
    ],
    [
     "470",
     "European internationals: NL / EURO / World Cup qualifiers"
    ],
    [
     "480–484",
     "Rankings FIFA / UEFA"
    ],
    [
     "500",
     "All live matches"
    ],
    [
     "600",
     "News (Turkish or German)"
    ],
    [
     "900",
     "Settings (style, language …)"
    ],
    [
     "Colour keys",
     "if present: DE / TR / Europe / Live"
    ],
    [
     "0–9",
     "if present: page number"
    ],
    [
     "Tablet",
     "tap = open · title = table · top = overview"
    ],
    [
     "Swipe",
     "◄► change league · ▲▼ matchday / next part"
    ]
   ],
   "mixOn": "Mix on",
   "mixOff": "Mix off",
   "ticker": "Goal ticker",
   "tkOn": "ON",
   "tkOff": "OFF",
   "tk1": "When the ticker is on, a small window appears",
   "tk2": "bottom left while watching TV, only",
   "tk3": "when a goal is scored. Otherwise the picture stays clear.",
   "tkHint": "▲▼ select · OK = apply + TV picture",
   "tkOffMsg": "Goal ticker off",
   "cNEWSde": "NEWS (GERMAN)",
   "dlRow": "Android app: Downloader code %s  (aftv.news/%s)",
   "noDet": "No details available",
   "rkHint": "◄► ranking · ▲▼ scroll",
   "updAsk": "New version %s found (installed: %s).\nDownload and install now?",
   "updDone": "SkorText %s is installed.\nRestart the GUI now?",
   "e2_openHint": "▲▼◄► select · OK open",
   "e2_home": "0 / EXIT = overview",
   "e2_exitAgain": "Overview · EXIT again = TV picture",
   "e2_setHint": "▲▼ select · ◄► change · EXIT back",
   "e2_lselHint": "▲▼ select · OK tick on/off · EXIT back",
   "e2_noData2": "Check the internet connection and the receiver's clock.",
   "e2_source": "Data: LiveScore, FuPa, ESPN",
   "e2_ardErr": "ARD page %s not reachable",
   "e2_ardHint": "▲▼ select page number · OK open · ◄► turn pages · EXIT back",
   "e2_tvHint": "▲▼ match · OK channels · EXIT close",
   "e2_noFav2": "Choose one under MENU → Settings (900).",
   "e2_updInfo": "Download and install a new version from GitHub. Installed:",
   "e2_updLoad": "Installing update %s …",
   "e2_helpInfo": "All buttons and pages at a glance (also with INFO).",
   "e2_articleHint": "▲▼ / ◄► next story · OK / EXIT back",
   "e2_helpRows": [
    [
     "TEXT long",
     "Start SkorText (from the TV picture)"
    ],
    [
     "TEXT short",
     "in SkorText: Mix on/off"
    ],
    [
     "◄ ►",
     "Change league"
    ],
    [
     "OK",
     "Results ⇄ table"
    ],
    [
     "▲ ▼",
     "Results: matchday back/forward"
    ],
    [
     "RED",
     "Germany (200)"
    ],
    [
     "GREEN",
     "Türkiye (300)"
    ],
    [
     "YELLOW",
     "Europe (400)"
    ],
    [
     "BLUE",
     "All live matches (500)"
    ],
    [
     "0–9",
     "Go to a page, e.g. 301"
    ],
    [
     "0",
     "straight to the overview (100)"
    ],
    [
     "150",
     "Goal ticker in the TV picture on/off"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Today: all matches of the day (◄► day)"
    ],
    [
     "520",
     "On TV today: ▲▼ match, OK shows all channels (liveonsat.com)"
    ],
    [
     "110",
     "My club"
    ],
    [
     "470",
     "European internationals: NL / EURO / World Cup qualifiers (◄►)"
    ],
    [
     "480–484",
     "Rankings: FIFA, UEFA countries, UEFA clubs"
    ],
    [
     "600–660",
     "News (Turkish or German, see Settings) · Turkish: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Overview, EXIT again = TV"
    ],
    [
     "MENU",
     "Settings (900)"
    ],
    [
     "INFO",
     "this help (190)"
    ],
    [
     "CH+ / CH−",
     "next / previous page"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Language / Sprache",
   "Sprache der Seiten.": "Language of the pages.",
   "Stil / Optik": "Style",
   "Videotext (klassisch)": "Teletext (classic)",
   "Modern (wie Webseite)": "Modern (like a website)",
   "Wird sofort umgeschaltet.": "Switches immediately.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Switches immediately – just compare.",
   "Lieblingsverein": "Favourite club",
   "– keiner –": "– none –",
   "Wird in Ergebnissen und Tabellen markiert.": "Highlighted in results and tables.",
   "Startseite": "Start page",
   "100 Übersicht": "100 Overview",
   "500 Live": "500 Live",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Page shown after starting.",
   "Welche Seite nach dem Start erscheint.": "Which page appears after starting.",
   "Aktualisierung": "Refresh",
   "alle 60 s": "every 60 s",
   "alle 30 s": "every 30 s",
   "alle 120 s": "every 120 s",
   "Wie oft neue Daten geholt werden.": "How often new data is fetched.",
   "Unterseiten blättern": "Turn sub-pages",
   "Manuell (▲▼)": "Manual (▲▼)",
   "Automatisch alle 10 s": "Automatically every 10 s",
   "Bei langen Seiten.": "For long pages.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "For groups (2./3. Lig) and long pages.",
   "Hintergrund": "Background",
   "Schwarz": "Black",
   "Schwarz (klassisch)": "Black (classic)",
   "Dunkelblau": "Dark blue",
   "Durchsichtig (Mix)": "Transparent (Mix)",
   "Hintergrundfarbe.": "Background colour.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT short also toggles Mix directly.",
   "Auto-Update": "Auto update",
   "Beim Start prüfen": "Check at start",
   "Aus": "Off",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Looks for a new version on GitHub at start.",
   "Nachrichten (600)": "News (600)",
   "Türkisch (TRT, AA)": "Turkish (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "German (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Turkish or German headlines, world, sport … on pages 600 ff.",
   "Starttaste": "Start button",
   "TEXT lang": "TEXT long",
   "BLAU lang": "BLUE long",
   "Nur Erweiterungen": "Extensions only",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Start straight from the TV picture, applies immediately.",
   "Tor-Ticker im TV (150)": "Goal ticker on TV (150)",
   "Alle Live-Spiele": "All live matches",
   "Nur 1. Liga DE + TR": "Top league DE + TR only",
   "Nur Lieblingsverein": "Favourite club only",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Small goal window bottom left while watching TV.",
   "Achtelfinale": "Round of 16",
   "Sechzehntelfinale": "Round of 32",
   "%s. Runde": "Round %s",
   "1. Runde": "Round 1",
   "Viertelfinale": "Quarter-finals",
   "Halbfinale": "Semi-finals",
   "Endrunde": "Final stage",
   "Finale": "Final",
   "Qualifikation": "Qualifying",
   "Gruppenphase": "Group stage",
   "Play-offs": "Play-offs",
   "Liga %s": "League %s",
   "Gruppe %s": "Group %s",
   "EM-Qualifikation": "EURO qualifiers",
   "WM-Qualifikation": "World Cup qualifiers",
   "Nations League": "Nations League",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Blocked (HTTP 403) – retrying in 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "Data source is blocking access (HTTP 403) – retrying in 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "League not found at the data source",
   "Zeitüberschreitung – Internet langsam oder weg": "Timeout – internet slow or down",
   "Keine Internetverbindung": "No internet connection",
   "Keine Länderspiele gefunden": "No international matches found",
   "Keine Internetverbindung (DNS)": "No internet connection (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "SSL error – check the receiver's clock",
   "Verl": "ET",
   "Elf": "Pens",
   "Kurs": "Rate",
   "EZB": "ECB",
   "Android-App laden: Downloader-Code ": "Android app: Downloader code ",
   "NACHRICHTEN": "NEWS (GERMAN)",
   "TEXT lang → Details": "TEXT long → details",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Champions League",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Korea Republic": "South Korea",
   "IR Iran": "Iran",
   "China PR": "China",
   "Czech Republic": "Czechia",
   "Republic of Ireland": "Ireland",
   "Congo DR": "DR Congo",
   "Côte d'Ivoire": "Ivory Coast",
   "Cabo Verde": "Cape Verde",
   "United States": "USA",
   "Turkey": "Türkiye"
  }
 },
 "fr": {
  "txt": {
   "overview": "Sommaire",
   "results": "Résultats",
   "table": "Classement",
   "liveTable": "Classement en direct",
   "md": "%de journée",
   "live": "Direct",
   "allLive": "Tous les matchs en direct",
   "help": "Commandes",
   "settings": "Réglages",
   "noLive": "Aucun match en direct",
   "ht": "MT",
   "ft": "Fin",
   "pp": "Rep.",
   "goal": "BUT !",
   "club": "Club",
   "pl": "Pl",
   "sp": "J",
   "w": "G",
   "d": "N",
   "l": "P",
   "goals": "Buts",
   "diff": "Diff",
   "pts": "Pts",
   "fast": [
    "Allemagne",
    "Turquie",
    "Europe",
    "DIRECT"
   ],
   "days": [
    "Lun",
    "Mar",
    "Mer",
    "Jeu",
    "Ven",
    "Sam",
    "Dim"
   ],
   "notfound": "Page %d indisponible",
   "stand": "Mis à jour",
   "cDE": "ALLEMAGNE",
   "cTR": "TURQUIE",
   "cEU": "EUROPE",
   "cNEWS": "ACTUALITÉS (TURC)",
   "openHint": "▲▼◄► choisir · OK ouvrir · RETOUR quitter",
   "home": "RETOUR = sommaire",
   "exitAgain": "Sommaire · RETOUR encore = quitter",
   "setHint": "▲▼ choisir · ◄► modifier · RETOUR",
   "lselRow": "Choisir les ligues",
   "lselOpen": "ouvrir (%d affichées)",
   "lselInfo": "Ce qui doit figurer sur la page principale – OK coche ou décoche.",
   "lselHead": "Coché = affiché sur la page principale",
   "lselHint": "▲▼ choisir · OK cocher/décocher · RETOUR",
   "t_lselHint": "Toucher = cocher/décocher · toucher ▲▼ = défiler",
   "lselMore": "suite",
   "lselOn": "Affichée",
   "lselOff": "Masquée",
   "cEUx": "Autres ligues européennes",
   "loading": "Chargement …",
   "noData": "Aucune donnée disponible.",
   "noData2": "Vérifiez la connexion Internet.",
   "noGames": "Aucun match trouvé.",
   "noTable": "Aucun classement disponible.",
   "source": "Données : LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(en cours)",
   "mdBrowse": "Journée",
   "noMore": "Pas d'autre journée",
   "newsHint": "▲▼ choisir · OK lire · ◄► rubrique",
   "articleHint": "▲▼ / ◄► article suivant · OK / RETOUR",
   "resHint": "◄► ligue · OK classement",
   "tabHint": "◄► ligue · OK résultats",
   "cNAT": "SÉLECTIONS",
   "cRANK": "CLASSEMENTS",
   "cMORE": "AUTRES",
   "more": "▼ suite : sélections, classements",
   "t_more": "glisser ▲ = suite : sélections, classements",
   "ardRow": "ARD Text (télétexte allemand)",
   "ardHint": "▲▼ choisir le numéro · OK ouvrir · ◄► feuilleter · RETOUR",
   "t_ardHint": "Toucher un numéro = ouvrir · RETOUR = page précédente",
   "ardLoad": "Chargement d'ARD Text …",
   "ardErr": "Page ARD %d inaccessible",
   "fav": "Mon club",
   "today": "Aujourd'hui",
   "tv": "À la TV aujourd'hui",
   "tvHint": "▲▼ match · OK chaînes · RETOUR fermer",
   "tvNone": "aucune chaîne connue",
   "yesterday": "Hier",
   "tomorrow": "Demain",
   "dayHint": "◄► changer de jour",
   "t_dayHint": "glisser ◄► = changer de jour",
   "last": "Derniers matchs",
   "next": "Prochains matchs",
   "noFav": "Aucun club favori choisi.",
   "noFav2": "À choisir dans Réglages (900).",
   "place": "Place",
   "scorers": "Buteurs",
   "detHint": "OK = classement · ▲▼ défiler",
   "t_detHint": "toucher le titre = classement · glisser ▲▼",
   "pen": "P",
   "og": "csc",
   "rkFifa": "Classement mondial FIFA",
   "rkUefa": "Indice UEFA des pays",
   "rkClub": "Indice UEFA des clubs",
   "points": "Points",
   "season": "Saison",
   "nation": "Pays",
   "asOf": "Au",
   "noMatches": "Aucun match ce jour-là.",
   "nlHint": "◄► ▲▼ groupe suivant",
   "natEU": "Sélections européennes",
   "t_nlHint": "glisser = groupe suivant",
   "helpOpen": "OK = ouvrir",
   "t_helpOpen": "toucher = ouvrir",
   "helpInfo": "Toutes les touches et pages en un coup d'œil.",
   "updRow": "Rechercher une mise à jour",
   "updOpen": "OK = vérifier",
   "t_updOpen": "toucher = vérifier",
   "updInfo": "Charger une nouvelle version depuis GitHub. Installée :",
   "updNone": "SkorText est à jour",
   "updLoad": "Chargement de la mise à jour %s …",
   "updPerm": "Autorisez « Installer des applis inconnues » pour SkorText puis revenez avec RETOUR – la suite est automatique",
   "updOpening": "Ouverture de l'installation …",
   "updEmbed": "Dans Aze IPTV Player, SkorText arrive avec sa mise à jour (Réglages → Mise à jour)",
   "updErr": "Échec de la mise à jour",
   "updAvail": "Nouvelle version %s – Réglages → Mise à jour",
   "t_openHint": "Toucher = ouvrir · RETOUR quitter",
   "t_home": "◄ Sommaire (toucher ici)",
   "t_resHint": "glisser ◄► ligue · toucher le titre = classement",
   "t_tabHint": "glisser ◄► ligue · toucher le titre = résultats",
   "t_setHint": "Toucher = modifier · RETOUR",
   "t_newsHint": "Toucher = lire · glisser ◄► rubrique",
   "t_articleHint": "glisser = article suivant · toucher le titre = retour",
   "t_mdArrows": "glisser ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Changer de ligue"
    ],
    [
     "OK",
     "Résultats ⇄ classement"
    ],
    [
     "▲ ▼",
     "Résultats : journée précédente/suivante"
    ],
    [
     "▲ ▼",
     "Classement : partie suivante"
    ],
    [
     "RETOUR",
     "Sommaire, encore = fermer l'appli"
    ],
    [
     "Sommaire",
     "▲▼◄► choisir, OK ouvre"
    ],
    [
     "440–460",
     "Ligue des champions / Ligue Europa / Conférence"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Aujourd'hui (◄► jour)"
    ],
    [
     "520",
     "À la TV : ▲▼ match, OK toutes les chaînes"
    ],
    [
     "110",
     "Mon club"
    ],
    [
     "470",
     "Sélections européennes : LN / qualif. Euro / Mondial"
    ],
    [
     "480–484",
     "Classements FIFA / UEFA"
    ],
    [
     "500",
     "Tous les matchs en direct"
    ],
    [
     "600",
     "Actualités (turc ou allemand)"
    ],
    [
     "900",
     "Réglages (style, langue …)"
    ],
    [
     "Touches couleur",
     "si présentes : DE / TR / Europe / Direct"
    ],
    [
     "0–9",
     "si présentes : numéro de page"
    ],
    [
     "Tablette",
     "toucher = ouvrir · titre = classement · haut = sommaire"
    ],
    [
     "Glisser",
     "◄► changer de ligue · ▲▼ journée / partie suivante"
    ]
   ],
   "mixOn": "Mix activé",
   "mixOff": "Mix désactivé",
   "ticker": "Alerte but",
   "tkOn": "OUI",
   "tkOff": "NON",
   "tk1": "Si l'alerte est active, une petite fenêtre",
   "tk2": "apparaît en bas à gauche pendant la TV,",
   "tk3": "seulement lors d'un but. Sinon l'image reste libre.",
   "tkHint": "▲▼ choisir · OK = valider + image TV",
   "tkOffMsg": "Alerte but désactivée",
   "cNEWSde": "ACTUALITÉS (ALLEMAND)",
   "dlRow": "Appli Android : code Downloader %s  (aftv.news/%s)",
   "noDet": "Aucun détail disponible",
   "rkHint": "◄► classement · ▲▼ défiler",
   "updAsk": "Nouvelle version %s trouvée (installée : %s).\nTélécharger et installer maintenant ?",
   "updDone": "SkorText %s est installé.\nRedémarrer l'interface maintenant ?",
   "e2_openHint": "▲▼◄► choisir · OK ouvrir",
   "e2_home": "0 / EXIT = sommaire",
   "e2_exitAgain": "Sommaire · EXIT encore = image TV",
   "e2_setHint": "▲▼ choisir · ◄► modifier · EXIT retour",
   "e2_lselHint": "▲▼ choisir · OK cocher/décocher · EXIT retour",
   "e2_noData2": "Vérifiez la connexion Internet et l'heure du récepteur.",
   "e2_source": "Données : LiveScore, FuPa, ESPN",
   "e2_ardErr": "Page ARD %s inaccessible",
   "e2_ardHint": "▲▼ choisir le numéro · OK ouvrir · ◄► feuilleter · EXIT retour",
   "e2_tvHint": "▲▼ match · OK chaînes · EXIT fermer",
   "e2_noFav2": "À choisir dans MENU → Réglages (900).",
   "e2_updInfo": "Télécharger et installer une nouvelle version depuis GitHub. Installée :",
   "e2_updLoad": "Installation de la mise à jour %s …",
   "e2_helpInfo": "Toutes les touches et pages en un coup d'œil (aussi avec INFO).",
   "e2_articleHint": "▲▼ / ◄► article suivant · OK / EXIT retour",
   "e2_helpRows": [
    [
     "TEXT long",
     "Lancer SkorText (depuis l'image TV)"
    ],
    [
     "TEXT court",
     "dans SkorText : Mix oui/non"
    ],
    [
     "◄ ►",
     "Changer de ligue"
    ],
    [
     "OK",
     "Résultats ⇄ classement"
    ],
    [
     "▲ ▼",
     "Résultats : journée précédente/suivante"
    ],
    [
     "ROUGE",
     "Allemagne (200)"
    ],
    [
     "VERT",
     "Turquie (300)"
    ],
    [
     "JAUNE",
     "Europe (400)"
    ],
    [
     "BLEU",
     "Tous les matchs en direct (500)"
    ],
    [
     "0–9",
     "Aller à une page, p. ex. 301"
    ],
    [
     "0",
     "directement au sommaire (100)"
    ],
    [
     "150",
     "Alerte but dans l'image TV oui/non"
    ],
    [
     "320 / 322",
     "2. Lig : Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Aujourd'hui : tous les matchs du jour (◄► jour)"
    ],
    [
     "520",
     "À la TV : ▲▼ match, OK toutes les chaînes (liveonsat.com)"
    ],
    [
     "110",
     "Mon club"
    ],
    [
     "470",
     "Sélections européennes : LN / qualif. Euro / Mondial (◄►)"
    ],
    [
     "480–484",
     "Classements : FIFA, pays UEFA, clubs UEFA"
    ],
    [
     "600–660",
     "Actualités (turc ou allemand, voir Réglages) · turc : 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Sommaire, EXIT encore = TV"
    ],
    [
     "MENU",
     "Réglages (900)"
    ],
    [
     "INFO",
     "cette aide (190)"
    ],
    [
     "CH+ / CH−",
     "page suivante / précédente"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Langue / Language",
   "Sprache der Seiten.": "Langue des pages.",
   "Stil / Optik": "Style",
   "Videotext (klassisch)": "Télétexte (classique)",
   "Modern (wie Webseite)": "Moderne (comme un site web)",
   "Wird sofort umgeschaltet.": "Change immédiatement.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Change immédiatement – comparez.",
   "Lieblingsverein": "Club favori",
   "– keiner –": "– aucun –",
   "Wird in Ergebnissen und Tabellen markiert.": "Mis en évidence dans les résultats et classements.",
   "Startseite": "Page de départ",
   "100 Übersicht": "100 Sommaire",
   "500 Live": "500 Direct",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Page affichée au démarrage.",
   "Welche Seite nach dem Start erscheint.": "Page affichée au démarrage.",
   "Aktualisierung": "Actualisation",
   "alle 60 s": "toutes les 60 s",
   "alle 30 s": "toutes les 30 s",
   "alle 120 s": "toutes les 120 s",
   "Wie oft neue Daten geholt werden.": "Fréquence de mise à jour des données.",
   "Unterseiten blättern": "Sous-pages",
   "Manuell (▲▼)": "Manuel (▲▼)",
   "Automatisch alle 10 s": "Automatique toutes les 10 s",
   "Bei langen Seiten.": "Pour les pages longues.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "Pour les groupes (2./3. Lig) et pages longues.",
   "Hintergrund": "Fond",
   "Schwarz": "Noir",
   "Schwarz (klassisch)": "Noir (classique)",
   "Dunkelblau": "Bleu foncé",
   "Durchsichtig (Mix)": "Transparent (Mix)",
   "Hintergrundfarbe.": "Couleur de fond.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT court bascule aussi le Mix.",
   "Auto-Update": "Mise à jour auto",
   "Beim Start prüfen": "Vérifier au démarrage",
   "Aus": "Désactivé",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Cherche une nouvelle version sur GitHub au démarrage.",
   "Nachrichten (600)": "Actualités (600)",
   "Türkisch (TRT, AA)": "Turc (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Allemand (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Titres turcs ou allemands, monde, sport … pages 600 et suiv.",
   "Starttaste": "Touche de démarrage",
   "TEXT lang": "TEXT long",
   "BLAU lang": "BLEU long",
   "Nur Erweiterungen": "Extensions seulement",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Lancer depuis l'image TV, effet immédiat.",
   "Tor-Ticker im TV (150)": "Alerte but à la TV (150)",
   "Alle Live-Spiele": "Tous les matchs en direct",
   "Nur 1. Liga DE + TR": "Seulement 1re division DE + TR",
   "Nur Lieblingsverein": "Seulement le club favori",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Petite fenêtre de but en bas à gauche pendant la TV.",
   "Achtelfinale": "Huitièmes de finale",
   "Sechzehntelfinale": "Seizièmes de finale",
   "%s. Runde": "%se tour",
   "1. Runde": "1er tour",
   "Viertelfinale": "Quarts de finale",
   "Halbfinale": "Demi-finales",
   "Endrunde": "Phase finale",
   "Finale": "Finale",
   "Qualifikation": "Qualifications",
   "Gruppenphase": "Phase de groupes",
   "Play-offs": "Barrages",
   "Liga %s": "Ligue %s",
   "Gruppe %s": "Groupe %s",
   "EM-Qualifikation": "Qualif. Euro",
   "WM-Qualifikation": "Qualif. Coupe du monde",
   "Nations League": "Ligue des nations",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Bloqué (HTTP 403) – nouvel essai dans 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "La source bloque l'accès (HTTP 403) – nouvel essai dans 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Ligue introuvable chez la source",
   "Zeitüberschreitung – Internet langsam oder weg": "Délai dépassé – Internet lent ou coupé",
   "Keine Internetverbindung": "Pas de connexion Internet",
   "Keine Länderspiele gefunden": "Aucun match de sélection trouvé",
   "Keine Internetverbindung (DNS)": "Pas de connexion Internet (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "Erreur SSL – vérifiez l'heure du récepteur",
   "Verl": "Prol",
   "Elf": "TAB",
   "Kurs": "Cours",
   "EZB": "BCE",
   "Android-App laden: Downloader-Code ": "Appli Android : code Downloader ",
   "NACHRICHTEN": "ACTUALITÉS (ALLEMAND)",
   "TEXT lang → Details": "TEXT long → détails",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Ligue des champions",
   "Europa League": "Ligue Europa",
   "Conference League": "Ligue Conférence"
  },
  "nations": {
   "Germany": "Allemagne",
   "Türkiye": "Turquie",
   "Turkey": "Turquie",
   "Netherlands": "Pays-Bas",
   "Greece": "Grèce",
   "Serbia": "Serbie",
   "Italy": "Italie",
   "Belgium": "Belgique",
   "Spain": "Espagne",
   "England": "Angleterre",
   "Croatia": "Croatie",
   "Czechia": "Tchéquie",
   "Czech Republic": "Tchéquie",
   "Denmark": "Danemark",
   "Norway": "Norvège",
   "Wales": "Pays de Galles",
   "Scotland": "Écosse",
   "Switzerland": "Suisse",
   "Slovenia": "Slovénie",
   "North Macedonia": "Macédoine du Nord",
   "Georgia": "Géorgie",
   "Hungary": "Hongrie",
   "Northern Ireland": "Irlande du Nord",
   "Austria": "Autriche",
   "Ireland": "Irlande",
   "Republic of Ireland": "Irlande",
   "Israel": "Israël",
   "Bosnia and Herzegovina": "Bosnie-Herzégovine",
   "Poland": "Pologne",
   "Romania": "Roumanie",
   "Sweden": "Suède",
   "Albania": "Albanie",
   "Belarus": "Biélorussie",
   "Finland": "Finlande",
   "San Marino": "Saint-Marin",
   "Armenia": "Arménie",
   "Cyprus": "Chypre",
   "Latvia": "Lettonie",
   "Montenegro": "Monténégro",
   "Faroe Islands": "Îles Féroé",
   "Moldova": "Moldavie",
   "Slovakia": "Slovaquie",
   "Bulgaria": "Bulgarie",
   "Estonia": "Estonie",
   "Iceland": "Islande",
   "Lithuania": "Lituanie",
   "Azerbaijan": "Azerbaïdjan",
   "Malta": "Malte",
   "Andorra": "Andorre",
   "Russia": "Russie",
   "Argentina": "Argentine",
   "Brazil": "Brésil",
   "Colombia": "Colombie",
   "Ecuador": "Équateur",
   "Peru": "Pérou",
   "Chile": "Chili",
   "Bolivia": "Bolivie",
   "USA": "États-Unis",
   "United States": "États-Unis",
   "Mexico": "Mexique",
   "Jamaica": "Jamaïque",
   "Morocco": "Maroc",
   "Senegal": "Sénégal",
   "Egypt": "Égypte",
   "Algeria": "Algérie",
   "Tunisia": "Tunisie",
   "Cameroon": "Cameroun",
   "Ivory Coast": "Côte d'Ivoire",
   "South Africa": "Afrique du Sud",
   "DR Congo": "RD Congo",
   "Congo DR": "RD Congo",
   "Cape Verde": "Cap-Vert",
   "Cabo Verde": "Cap-Vert",
   "Japan": "Japon",
   "South Korea": "Corée du Sud",
   "Korea Republic": "Corée du Sud",
   "IR Iran": "Iran",
   "Australia": "Australie",
   "Saudi Arabia": "Arabie saoudite",
   "Iraq": "Irak",
   "Uzbekistan": "Ouzbékistan",
   "Jordan": "Jordanie",
   "United Arab Emirates": "Émirats arabes unis",
   "China PR": "Chine",
   "China": "Chine",
   "New Zealand": "Nouvelle-Zélande"
  }
 },
 "es": {
  "txt": {
   "overview": "Resumen",
   "results": "Resultados",
   "table": "Clasificación",
   "liveTable": "Clasificación en vivo",
   "md": "Jornada %d",
   "live": "En vivo",
   "allLive": "Todos los partidos en vivo",
   "help": "Manejo",
   "settings": "Ajustes",
   "noLive": "Ahora no hay partidos en vivo",
   "ht": "Desc.",
   "ft": "Final",
   "pp": "Apl.",
   "goal": "¡GOL!",
   "club": "Equipo",
   "pl": "Pos",
   "sp": "PJ",
   "w": "G",
   "d": "E",
   "l": "P",
   "goals": "Goles",
   "diff": "Dif",
   "pts": "Pts",
   "fast": [
    "Alemania",
    "Turquía",
    "Europa",
    "EN VIVO"
   ],
   "days": [
    "Lun",
    "Mar",
    "Mié",
    "Jue",
    "Vie",
    "Sáb",
    "Dom"
   ],
   "notfound": "La página %d no existe",
   "stand": "Actualizado",
   "cDE": "ALEMANIA",
   "cTR": "TURQUÍA",
   "cEU": "EUROPA",
   "cNEWS": "NOTICIAS (TURCO)",
   "openHint": "▲▼◄► elegir · OK abrir · ATRÁS salir",
   "home": "ATRÁS = resumen",
   "exitAgain": "Resumen · ATRÁS otra vez = salir",
   "setHint": "▲▼ elegir · ◄► cambiar · ATRÁS volver",
   "lselRow": "Elegir ligas",
   "lselOpen": "abrir (%d visibles)",
   "lselInfo": "Qué debe aparecer en la página principal – OK marca o desmarca.",
   "lselHead": "Marcada = visible en la página principal",
   "lselHint": "▲▼ elegir · OK marcar/desmarcar · ATRÁS",
   "t_lselHint": "Tocar = marcar/desmarcar · tocar ▲▼ = desplazar",
   "lselMore": "más",
   "lselOn": "Visible",
   "lselOff": "Oculta",
   "cEUx": "Otras ligas europeas",
   "loading": "Cargando datos …",
   "noData": "No hay datos disponibles.",
   "noData2": "Comprueba la conexión a Internet.",
   "noGames": "No se encontraron partidos.",
   "noTable": "No hay clasificación.",
   "source": "Datos: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(actual)",
   "mdBrowse": "Jornada",
   "noMore": "No hay más jornadas",
   "newsHint": "▲▼ elegir · OK leer · ◄► sección",
   "articleHint": "▲▼ / ◄► siguiente noticia · OK / ATRÁS",
   "resHint": "◄► liga · OK clasificación",
   "tabHint": "◄► liga · OK resultados",
   "cNAT": "SELECCIONES",
   "cRANK": "RANKINGS",
   "cMORE": "MÁS",
   "more": "▼ más: selecciones, rankings",
   "t_more": "deslizar ▲ = más: selecciones, rankings",
   "ardRow": "ARD Text (teletexto alemán)",
   "ardHint": "▲▼ elegir número · OK abrir · ◄► pasar páginas · ATRÁS",
   "t_ardHint": "Tocar un número = abrir · ATRÁS = página anterior",
   "ardLoad": "Cargando ARD Text …",
   "ardErr": "Página ARD %d no disponible",
   "fav": "Mi equipo",
   "today": "Hoy",
   "tv": "Hoy en TV",
   "tvHint": "▲▼ partido · OK canales · ATRÁS cerrar",
   "tvNone": "sin canales conocidos",
   "yesterday": "Ayer",
   "tomorrow": "Mañana",
   "dayHint": "◄► cambiar día",
   "t_dayHint": "deslizar ◄► = cambiar día",
   "last": "Últimos partidos",
   "next": "Próximos partidos",
   "noFav": "No hay equipo favorito.",
   "noFav2": "Elígelo en Ajustes (900).",
   "place": "Puesto",
   "scorers": "Goleadores",
   "detHint": "OK = clasificación · ▲▼ desplazar",
   "t_detHint": "tocar título = clasificación · deslizar ▲▼",
   "pen": "P",
   "og": "pp",
   "rkFifa": "Ranking mundial FIFA",
   "rkUefa": "Ranking UEFA de países",
   "rkClub": "Ranking UEFA de clubes",
   "points": "Puntos",
   "season": "Temporada",
   "nation": "País",
   "asOf": "A",
   "noMatches": "No hay partidos este día.",
   "nlHint": "◄► ▲▼ siguiente grupo",
   "natEU": "Selecciones europeas",
   "t_nlHint": "deslizar = siguiente grupo",
   "helpOpen": "OK = abrir",
   "t_helpOpen": "tocar = abrir",
   "helpInfo": "Todas las teclas y páginas de un vistazo.",
   "updRow": "Buscar actualización",
   "updOpen": "OK = comprobar ahora",
   "t_updOpen": "tocar = comprobar ahora",
   "updInfo": "Cargar una nueva versión desde GitHub. Instalada:",
   "updNone": "SkorText está actualizado",
   "updLoad": "Cargando actualización %s …",
   "updPerm": "Permite «Instalar apps desconocidas» para SkorText y vuelve con ATRÁS – después sigue solo",
   "updOpening": "Abriendo la instalación …",
   "updEmbed": "En Aze IPTV Player, SkorText llega con su actualización (Ajustes → Actualizar app)",
   "updErr": "Error al actualizar",
   "updAvail": "Nueva versión %s – Ajustes → Actualizar",
   "t_openHint": "Tocar = abrir · ATRÁS salir",
   "t_home": "◄ Resumen (tocar aquí)",
   "t_resHint": "deslizar ◄► liga · tocar título = clasificación",
   "t_tabHint": "deslizar ◄► liga · tocar título = resultados",
   "t_setHint": "Tocar = cambiar · ATRÁS volver",
   "t_newsHint": "Tocar = leer · deslizar ◄► sección",
   "t_articleHint": "deslizar = siguiente noticia · tocar título = volver",
   "t_mdArrows": "deslizar ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Cambiar de liga"
    ],
    [
     "OK",
     "Resultados ⇄ clasificación"
    ],
    [
     "▲ ▼",
     "Resultados: jornada anterior/siguiente"
    ],
    [
     "▲ ▼",
     "Clasificación: parte siguiente"
    ],
    [
     "ATRÁS",
     "Resumen, otra vez = cerrar la app"
    ],
    [
     "Resumen",
     "▲▼◄► elegir, OK abre"
    ],
    [
     "440–460",
     "Liga de Campeones / Europa League / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Hoy (◄► día)"
    ],
    [
     "520",
     "Hoy en TV: ▲▼ partido, OK todos los canales"
    ],
    [
     "110",
     "Mi equipo"
    ],
    [
     "470",
     "Selecciones europeas: LN / clasif. Euro / Mundial"
    ],
    [
     "480–484",
     "Rankings FIFA / UEFA"
    ],
    [
     "500",
     "Todos los partidos en vivo"
    ],
    [
     "600",
     "Noticias (turco o alemán)"
    ],
    [
     "900",
     "Ajustes (estilo, idioma …)"
    ],
    [
     "Teclas de color",
     "si existen: DE / TR / Europa / En vivo"
    ],
    [
     "0–9",
     "si existen: número de página"
    ],
    [
     "Tableta",
     "tocar = abrir · título = clasificación · arriba = resumen"
    ],
    [
     "Deslizar",
     "◄► cambiar de liga · ▲▼ jornada / parte siguiente"
    ]
   ],
   "mixOn": "Mix sí",
   "mixOff": "Mix no",
   "ticker": "Aviso de gol",
   "tkOn": "SÍ",
   "tkOff": "NO",
   "tk1": "Si el aviso está activo, mientras ves la TV",
   "tk2": "aparece abajo a la izquierda una ventana pequeña",
   "tk3": "solo cuando hay un gol. Si no, la imagen queda libre.",
   "tkHint": "▲▼ elegir · OK = aceptar + imagen TV",
   "tkOffMsg": "Aviso de gol desactivado",
   "cNEWSde": "NOTICIAS (ALEMÁN)",
   "dlRow": "App Android: código Downloader %s  (aftv.news/%s)",
   "noDet": "No hay detalles",
   "rkHint": "◄► ranking · ▲▼ desplazar",
   "updAsk": "Nueva versión %s encontrada (instalada: %s).\n¿Descargar e instalar ahora?",
   "updDone": "SkorText %s está instalado.\n¿Reiniciar la interfaz ahora?",
   "e2_openHint": "▲▼◄► elegir · OK abrir",
   "e2_home": "0 / EXIT = resumen",
   "e2_exitAgain": "Resumen · EXIT otra vez = imagen TV",
   "e2_setHint": "▲▼ elegir · ◄► cambiar · EXIT volver",
   "e2_lselHint": "▲▼ elegir · OK marcar/desmarcar · EXIT volver",
   "e2_noData2": "Comprueba la conexión a Internet y la hora del receptor.",
   "e2_source": "Datos: LiveScore, FuPa, ESPN",
   "e2_ardErr": "Página ARD %s no disponible",
   "e2_ardHint": "▲▼ elegir número · OK abrir · ◄► pasar páginas · EXIT volver",
   "e2_tvHint": "▲▼ partido · OK canales · EXIT cerrar",
   "e2_noFav2": "Elígelo en MENU → Ajustes (900).",
   "e2_updInfo": "Descargar e instalar una nueva versión desde GitHub. Instalada:",
   "e2_updLoad": "Instalando actualización %s …",
   "e2_helpInfo": "Todas las teclas y páginas de un vistazo (también con INFO).",
   "e2_articleHint": "▲▼ / ◄► siguiente noticia · OK / EXIT volver",
   "e2_helpRows": [
    [
     "TEXT largo",
     "Iniciar SkorText (desde la imagen TV)"
    ],
    [
     "TEXT corto",
     "en SkorText: Mix sí/no"
    ],
    [
     "◄ ►",
     "Cambiar de liga"
    ],
    [
     "OK",
     "Resultados ⇄ clasificación"
    ],
    [
     "▲ ▼",
     "Resultados: jornada anterior/siguiente"
    ],
    [
     "ROJO",
     "Alemania (200)"
    ],
    [
     "VERDE",
     "Turquía (300)"
    ],
    [
     "AMARILLO",
     "Europa (400)"
    ],
    [
     "AZUL",
     "Todos los partidos en vivo (500)"
    ],
    [
     "0–9",
     "Ir a una página, p. ej. 301"
    ],
    [
     "0",
     "directo al resumen (100)"
    ],
    [
     "150",
     "Aviso de gol en la imagen TV sí/no"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Hoy: todos los partidos del día (◄► día)"
    ],
    [
     "520",
     "Hoy en TV: ▲▼ partido, OK todos los canales (liveonsat.com)"
    ],
    [
     "110",
     "Mi equipo"
    ],
    [
     "470",
     "Selecciones europeas: LN / clasif. Euro / Mundial (◄►)"
    ],
    [
     "480–484",
     "Rankings: FIFA, países UEFA, clubes UEFA"
    ],
    [
     "600–660",
     "Noticias (turco o alemán, ver Ajustes) · turco: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Resumen, EXIT otra vez = TV"
    ],
    [
     "MENU",
     "Ajustes (900)"
    ],
    [
     "INFO",
     "esta ayuda (190)"
    ],
    [
     "CH+ / CH−",
     "página siguiente / anterior"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Idioma / Language",
   "Sprache der Seiten.": "Idioma de las páginas.",
   "Stil / Optik": "Estilo",
   "Videotext (klassisch)": "Teletexto (clásico)",
   "Modern (wie Webseite)": "Moderno (como una web)",
   "Wird sofort umgeschaltet.": "Cambia al instante.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Cambia al instante – compara.",
   "Lieblingsverein": "Equipo favorito",
   "– keiner –": "– ninguno –",
   "Wird in Ergebnissen und Tabellen markiert.": "Se resalta en resultados y clasificaciones.",
   "Startseite": "Página de inicio",
   "100 Übersicht": "100 Resumen",
   "500 Live": "500 En vivo",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Página tras el inicio.",
   "Welche Seite nach dem Start erscheint.": "Qué página aparece al iniciar.",
   "Aktualisierung": "Actualización",
   "alle 60 s": "cada 60 s",
   "alle 30 s": "cada 30 s",
   "alle 120 s": "cada 120 s",
   "Wie oft neue Daten geholt werden.": "Cada cuánto se cargan datos nuevos.",
   "Unterseiten blättern": "Pasar subpáginas",
   "Manuell (▲▼)": "Manual (▲▼)",
   "Automatisch alle 10 s": "Automático cada 10 s",
   "Bei langen Seiten.": "En páginas largas.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "En grupos (2./3. Lig) y páginas largas.",
   "Hintergrund": "Fondo",
   "Schwarz": "Negro",
   "Schwarz (klassisch)": "Negro (clásico)",
   "Dunkelblau": "Azul oscuro",
   "Durchsichtig (Mix)": "Transparente (Mix)",
   "Hintergrundfarbe.": "Color de fondo.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT corto también cambia el Mix.",
   "Auto-Update": "Actualización automática",
   "Beim Start prüfen": "Comprobar al iniciar",
   "Aus": "Desactivado",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Busca una nueva versión en GitHub al iniciar.",
   "Nachrichten (600)": "Noticias (600)",
   "Türkisch (TRT, AA)": "Turco (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Alemán (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Titulares turcos o alemanes, mundo, deporte … en las páginas 600 y ss.",
   "Starttaste": "Tecla de inicio",
   "TEXT lang": "TEXT largo",
   "BLAU lang": "AZUL largo",
   "Nur Erweiterungen": "Solo extensiones",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Iniciar desde la imagen TV, efecto inmediato.",
   "Tor-Ticker im TV (150)": "Aviso de gol en TV (150)",
   "Alle Live-Spiele": "Todos los partidos en vivo",
   "Nur 1. Liga DE + TR": "Solo primera DE + TR",
   "Nur Lieblingsverein": "Solo equipo favorito",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Pequeña ventana de gol abajo a la izquierda en TV.",
   "Achtelfinale": "Octavos de final",
   "Sechzehntelfinale": "Dieciseisavos de final",
   "%s. Runde": "Ronda %s",
   "1. Runde": "Primera ronda",
   "Viertelfinale": "Cuartos de final",
   "Halbfinale": "Semifinales",
   "Endrunde": "Fase final",
   "Finale": "Final",
   "Qualifikation": "Clasificación",
   "Gruppenphase": "Fase de grupos",
   "Play-offs": "Play-offs",
   "Liga %s": "Liga %s",
   "Gruppe %s": "Grupo %s",
   "EM-Qualifikation": "Clasificación Eurocopa",
   "WM-Qualifikation": "Clasificación Mundial",
   "Nations League": "Liga de Naciones",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Bloqueado (HTTP 403) – nuevo intento en 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "La fuente bloquea el acceso (HTTP 403) – nuevo intento en 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Liga no encontrada en la fuente",
   "Zeitüberschreitung – Internet langsam oder weg": "Tiempo agotado – Internet lento o caído",
   "Keine Internetverbindung": "Sin conexión a Internet",
   "Keine Länderspiele gefunden": "No se encontraron partidos de selecciones",
   "Keine Internetverbindung (DNS)": "Sin conexión a Internet (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "Error SSL – comprueba la hora del receptor",
   "Verl": "Pró",
   "Elf": "Pen",
   "Kurs": "Cambio",
   "EZB": "BCE",
   "Android-App laden: Downloader-Code ": "App Android: código Downloader ",
   "NACHRICHTEN": "NOTICIAS (ALEMÁN)",
   "TEXT lang → Details": "TEXT largo → detalles",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Liga de Campeones",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Germany": "Alemania",
   "Türkiye": "Turquía",
   "Turkey": "Turquía",
   "Netherlands": "Países Bajos",
   "Greece": "Grecia",
   "France": "Francia",
   "Italy": "Italia",
   "Belgium": "Bélgica",
   "Spain": "España",
   "England": "Inglaterra",
   "Croatia": "Croacia",
   "Czechia": "Chequia",
   "Czech Republic": "Chequia",
   "Denmark": "Dinamarca",
   "Norway": "Noruega",
   "Wales": "Gales",
   "Scotland": "Escocia",
   "Switzerland": "Suiza",
   "Slovenia": "Eslovenia",
   "North Macedonia": "Macedonia del Norte",
   "Hungary": "Hungría",
   "Northern Ireland": "Irlanda del Norte",
   "Ukraine": "Ucrania",
   "Ireland": "Irlanda",
   "Republic of Ireland": "Irlanda",
   "Bosnia and Herzegovina": "Bosnia y Herzegovina",
   "Poland": "Polonia",
   "Romania": "Rumanía",
   "Sweden": "Suecia",
   "Belarus": "Bielorrusia",
   "Finland": "Finlandia",
   "Cyprus": "Chipre",
   "Latvia": "Letonia",
   "Faroe Islands": "Islas Feroe",
   "Kazakhstan": "Kazajistán",
   "Moldova": "Moldavia",
   "Slovakia": "Eslovaquia",
   "Iceland": "Islandia",
   "Luxembourg": "Luxemburgo",
   "Lithuania": "Lituania",
   "Azerbaijan": "Azerbaiyán",
   "Russia": "Rusia",
   "Brazil": "Brasil",
   "Peru": "Perú",
   "USA": "Estados Unidos",
   "United States": "Estados Unidos",
   "Mexico": "México",
   "Canada": "Canadá",
   "Panama": "Panamá",
   "Morocco": "Marruecos",
   "Egypt": "Egipto",
   "Algeria": "Argelia",
   "Tunisia": "Túnez",
   "Cameroon": "Camerún",
   "Ivory Coast": "Costa de Marfil",
   "Côte d'Ivoire": "Costa de Marfil",
   "Mali": "Malí",
   "South Africa": "Sudáfrica",
   "DR Congo": "RD Congo",
   "Congo DR": "RD Congo",
   "Cape Verde": "Cabo Verde",
   "Japan": "Japón",
   "South Korea": "Corea del Sur",
   "Korea Republic": "Corea del Sur",
   "IR Iran": "Irán",
   "Iran": "Irán",
   "Saudi Arabia": "Arabia Saudí",
   "Qatar": "Catar",
   "Iraq": "Irak",
   "Uzbekistan": "Uzbekistán",
   "Jordan": "Jordania",
   "United Arab Emirates": "Emiratos Árabes",
   "China PR": "China",
   "New Zealand": "Nueva Zelanda"
  }
 },
 "it": {
  "txt": {
   "overview": "Panoramica",
   "results": "Risultati",
   "table": "Classifica",
   "liveTable": "Classifica live",
   "md": "%dª giornata",
   "live": "Live",
   "allLive": "Tutte le partite live",
   "help": "Comandi",
   "settings": "Impostazioni",
   "noLive": "Nessuna partita live al momento",
   "ht": "Int.",
   "ft": "Fine",
   "pp": "Rinv.",
   "goal": "GOL!",
   "club": "Squadra",
   "pl": "Pos",
   "sp": "G",
   "w": "V",
   "d": "N",
   "l": "P",
   "goals": "Gol",
   "diff": "DR",
   "pts": "Pt",
   "fast": [
    "Germania",
    "Turchia",
    "Europa",
    "LIVE"
   ],
   "days": [
    "Lun",
    "Mar",
    "Mer",
    "Gio",
    "Ven",
    "Sab",
    "Dom"
   ],
   "notfound": "Pagina %d non disponibile",
   "stand": "Aggiornato",
   "cDE": "GERMANIA",
   "cTR": "TURCHIA",
   "cEU": "EUROPA",
   "cNEWS": "NOTIZIE (TURCO)",
   "openHint": "▲▼◄► scegli · OK apri · INDIETRO esci",
   "home": "INDIETRO = panoramica",
   "exitAgain": "Panoramica · INDIETRO di nuovo = esci",
   "setHint": "▲▼ scegli · ◄► cambia · INDIETRO",
   "lselRow": "Scegli campionati",
   "lselOpen": "apri (%d visibili)",
   "lselInfo": "Cosa deve apparire nella pagina principale – OK mette o toglie la spunta.",
   "lselHead": "Spunta = visibile nella pagina principale",
   "lselHint": "▲▼ scegli · OK spunta sì/no · INDIETRO",
   "t_lselHint": "Tocca = spunta sì/no · tocca ▲▼ = scorri",
   "lselMore": "altri",
   "lselOn": "Visibile",
   "lselOff": "Nascosto",
   "cEUx": "Altri campionati europei",
   "loading": "Caricamento dati …",
   "noData": "Nessun dato disponibile.",
   "noData2": "Controlla la connessione Internet.",
   "noGames": "Nessuna partita trovata.",
   "noTable": "Nessuna classifica disponibile.",
   "source": "Dati: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(attuale)",
   "mdBrowse": "Giornata",
   "noMore": "Nessun'altra giornata",
   "newsHint": "▲▼ scegli · OK leggi · ◄► rubrica",
   "articleHint": "▲▼ / ◄► notizia successiva · OK / INDIETRO",
   "resHint": "◄► campionato · OK classifica",
   "tabHint": "◄► campionato · OK risultati",
   "cNAT": "NAZIONALI",
   "cRANK": "RANKING",
   "cMORE": "ALTRO",
   "more": "▼ altro: nazionali, ranking",
   "t_more": "scorri ▲ = altro: nazionali, ranking",
   "ardRow": "ARD Text (televideo tedesco)",
   "ardHint": "▲▼ scegli il numero · OK apri · ◄► sfoglia · INDIETRO",
   "t_ardHint": "Tocca un numero = apri · INDIETRO = pagina precedente",
   "ardLoad": "Caricamento di ARD Text …",
   "ardErr": "Pagina ARD %d non raggiungibile",
   "fav": "La mia squadra",
   "today": "Oggi",
   "tv": "Oggi in TV",
   "tvHint": "▲▼ partita · OK canali · INDIETRO chiudi",
   "tvNone": "nessun canale noto",
   "yesterday": "Ieri",
   "tomorrow": "Domani",
   "dayHint": "◄► cambia giorno",
   "t_dayHint": "scorri ◄► = cambia giorno",
   "last": "Ultime partite",
   "next": "Prossime partite",
   "noFav": "Nessuna squadra preferita.",
   "noFav2": "Sceglila in Impostazioni (900).",
   "place": "Posizione",
   "scorers": "Marcatori",
   "detHint": "OK = classifica · ▲▼ scorri",
   "t_detHint": "tocca il titolo = classifica · scorri ▲▼",
   "pen": "R",
   "og": "AG",
   "rkFifa": "Ranking mondiale FIFA",
   "rkUefa": "Ranking UEFA nazioni",
   "rkClub": "Ranking UEFA club",
   "points": "Punti",
   "season": "Stagione",
   "nation": "Paese",
   "asOf": "Al",
   "noMatches": "Nessuna partita in questo giorno.",
   "nlHint": "◄► ▲▼ gruppo successivo",
   "natEU": "Nazionali europee",
   "t_nlHint": "scorri = gruppo successivo",
   "helpOpen": "OK = apri",
   "t_helpOpen": "tocca = apri",
   "helpInfo": "Tutti i tasti e le pagine in breve.",
   "updRow": "Cerca aggiornamenti",
   "updOpen": "OK = controlla ora",
   "t_updOpen": "tocca = controlla ora",
   "updInfo": "Scarica una nuova versione da GitHub. Installata:",
   "updNone": "SkorText è aggiornato",
   "updLoad": "Caricamento aggiornamento %s …",
   "updPerm": "Consenti «Installa app sconosciute» per SkorText e torna con INDIETRO – poi prosegue da solo",
   "updOpening": "Apertura dell'installazione …",
   "updEmbed": "In Aze IPTV Player, SkorText arriva con il suo aggiornamento (Impostazioni → Aggiornamento app)",
   "updErr": "Aggiornamento non riuscito",
   "updAvail": "Nuova versione %s – Impostazioni → Aggiornamento",
   "t_openHint": "Tocca = apri · INDIETRO esci",
   "t_home": "◄ Panoramica (tocca qui)",
   "t_resHint": "scorri ◄► campionato · tocca il titolo = classifica",
   "t_tabHint": "scorri ◄► campionato · tocca il titolo = risultati",
   "t_setHint": "Tocca = cambia · INDIETRO",
   "t_newsHint": "Tocca = leggi · scorri ◄► rubrica",
   "t_articleHint": "scorri = notizia successiva · tocca il titolo = indietro",
   "t_mdArrows": "scorri ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Cambia campionato"
    ],
    [
     "OK",
     "Risultati ⇄ classifica"
    ],
    [
     "▲ ▼",
     "Risultati: giornata prec./succ."
    ],
    [
     "▲ ▼",
     "Classifica: parte successiva"
    ],
    [
     "INDIETRO",
     "Panoramica, di nuovo = chiudi l'app"
    ],
    [
     "Panoramica",
     "▲▼◄► scegli, OK apre"
    ],
    [
     "440–460",
     "Champions / Europa / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Oggi (◄► giorno)"
    ],
    [
     "520",
     "Oggi in TV: ▲▼ partita, OK tutti i canali"
    ],
    [
     "110",
     "La mia squadra"
    ],
    [
     "470",
     "Nazionali europee: NL / qual. Europei / Mondiali"
    ],
    [
     "480–484",
     "Ranking FIFA / UEFA"
    ],
    [
     "500",
     "Tutte le partite live"
    ],
    [
     "600",
     "Notizie (turco o tedesco)"
    ],
    [
     "900",
     "Impostazioni (stile, lingua …)"
    ],
    [
     "Tasti colorati",
     "se presenti: DE / TR / Europa / Live"
    ],
    [
     "0–9",
     "se presenti: numero di pagina"
    ],
    [
     "Tablet",
     "tocca = apri · titolo = classifica · in alto = panoramica"
    ],
    [
     "Scorri",
     "◄► cambia campionato · ▲▼ giornata / parte succ."
    ]
   ],
   "mixOn": "Mix sì",
   "mixOff": "Mix no",
   "ticker": "Avviso gol",
   "tkOn": "SÌ",
   "tkOff": "NO",
   "tk1": "Se l'avviso è attivo, guardando la TV appare",
   "tk2": "in basso a sinistra una piccola finestra",
   "tk3": "solo quando c'è un gol. Altrimenti l'immagine resta libera.",
   "tkHint": "▲▼ scegli · OK = conferma + immagine TV",
   "tkOffMsg": "Avviso gol disattivato",
   "cNEWSde": "NOTIZIE (TEDESCO)",
   "dlRow": "App Android: codice Downloader %s  (aftv.news/%s)",
   "noDet": "Nessun dettaglio disponibile",
   "rkHint": "◄► ranking · ▲▼ scorri",
   "updAsk": "Trovata nuova versione %s (installata: %s).\nScaricare e installare ora?",
   "updDone": "SkorText %s è installato.\nRiavviare l'interfaccia ora?",
   "e2_openHint": "▲▼◄► scegli · OK apri",
   "e2_home": "0 / EXIT = panoramica",
   "e2_exitAgain": "Panoramica · EXIT di nuovo = immagine TV",
   "e2_setHint": "▲▼ scegli · ◄► cambia · EXIT indietro",
   "e2_lselHint": "▲▼ scegli · OK spunta sì/no · EXIT indietro",
   "e2_noData2": "Controlla la connessione Internet e l'ora del ricevitore.",
   "e2_source": "Dati: LiveScore, FuPa, ESPN",
   "e2_ardErr": "Pagina ARD %s non raggiungibile",
   "e2_ardHint": "▲▼ scegli il numero · OK apri · ◄► sfoglia · EXIT indietro",
   "e2_tvHint": "▲▼ partita · OK canali · EXIT chiudi",
   "e2_noFav2": "Sceglila in MENU → Impostazioni (900).",
   "e2_updInfo": "Scarica e installa una nuova versione da GitHub. Installata:",
   "e2_updLoad": "Installazione aggiornamento %s …",
   "e2_helpInfo": "Tutti i tasti e le pagine in breve (anche con INFO).",
   "e2_articleHint": "▲▼ / ◄► notizia successiva · OK / EXIT indietro",
   "e2_helpRows": [
    [
     "TEXT lungo",
     "Avvia SkorText (dall'immagine TV)"
    ],
    [
     "TEXT breve",
     "in SkorText: Mix sì/no"
    ],
    [
     "◄ ►",
     "Cambia campionato"
    ],
    [
     "OK",
     "Risultati ⇄ classifica"
    ],
    [
     "▲ ▼",
     "Risultati: giornata prec./succ."
    ],
    [
     "ROSSO",
     "Germania (200)"
    ],
    [
     "VERDE",
     "Turchia (300)"
    ],
    [
     "GIALLO",
     "Europa (400)"
    ],
    [
     "BLU",
     "Tutte le partite live (500)"
    ],
    [
     "0–9",
     "Vai a una pagina, es. 301"
    ],
    [
     "0",
     "subito alla panoramica (100)"
    ],
    [
     "150",
     "Avviso gol nell'immagine TV sì/no"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Oggi: tutte le partite del giorno (◄► giorno)"
    ],
    [
     "520",
     "Oggi in TV: ▲▼ partita, OK tutti i canali (liveonsat.com)"
    ],
    [
     "110",
     "La mia squadra"
    ],
    [
     "470",
     "Nazionali europee: NL / qual. Europei / Mondiali (◄►)"
    ],
    [
     "480–484",
     "Ranking: FIFA, nazioni UEFA, club UEFA"
    ],
    [
     "600–660",
     "Notizie (turco o tedesco, vedi Impostazioni) · turco: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Panoramica, EXIT di nuovo = TV"
    ],
    [
     "MENU",
     "Impostazioni (900)"
    ],
    [
     "INFO",
     "questo aiuto (190)"
    ],
    [
     "CH+ / CH−",
     "pagina successiva / precedente"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Lingua / Language",
   "Sprache der Seiten.": "Lingua delle pagine.",
   "Stil / Optik": "Stile",
   "Videotext (klassisch)": "Televideo (classico)",
   "Modern (wie Webseite)": "Moderno (come un sito)",
   "Wird sofort umgeschaltet.": "Cambia subito.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Cambia subito – confronta.",
   "Lieblingsverein": "Squadra preferita",
   "– keiner –": "– nessuna –",
   "Wird in Ergebnissen und Tabellen markiert.": "Evidenziata in risultati e classifiche.",
   "Startseite": "Pagina iniziale",
   "100 Übersicht": "100 Panoramica",
   "500 Live": "500 Live",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Pagina mostrata all'avvio.",
   "Welche Seite nach dem Start erscheint.": "Quale pagina appare all'avvio.",
   "Aktualisierung": "Aggiornamento",
   "alle 60 s": "ogni 60 s",
   "alle 30 s": "ogni 30 s",
   "alle 120 s": "ogni 120 s",
   "Wie oft neue Daten geholt werden.": "Ogni quanto si caricano nuovi dati.",
   "Unterseiten blättern": "Sottopagine",
   "Manuell (▲▼)": "Manuale (▲▼)",
   "Automatisch alle 10 s": "Automatico ogni 10 s",
   "Bei langen Seiten.": "Per pagine lunghe.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "Per gironi (2./3. Lig) e pagine lunghe.",
   "Hintergrund": "Sfondo",
   "Schwarz": "Nero",
   "Schwarz (klassisch)": "Nero (classico)",
   "Dunkelblau": "Blu scuro",
   "Durchsichtig (Mix)": "Trasparente (Mix)",
   "Hintergrundfarbe.": "Colore di sfondo.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT breve commuta anche il Mix.",
   "Auto-Update": "Aggiornamento automatico",
   "Beim Start prüfen": "Controlla all'avvio",
   "Aus": "Disattivato",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Cerca una nuova versione su GitHub all'avvio.",
   "Nachrichten (600)": "Notizie (600)",
   "Türkisch (TRT, AA)": "Turco (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Tedesco (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Titoli turchi o tedeschi, mondo, sport … dalle pagine 600.",
   "Starttaste": "Tasto di avvio",
   "TEXT lang": "TEXT lungo",
   "BLAU lang": "BLU lungo",
   "Nur Erweiterungen": "Solo estensioni",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Avvio diretto dall'immagine TV, subito attivo.",
   "Tor-Ticker im TV (150)": "Avviso gol in TV (150)",
   "Alle Live-Spiele": "Tutte le partite live",
   "Nur 1. Liga DE + TR": "Solo massima serie DE + TR",
   "Nur Lieblingsverein": "Solo squadra preferita",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Piccola finestra gol in basso a sinistra in TV.",
   "Achtelfinale": "Ottavi di finale",
   "Sechzehntelfinale": "Sedicesimi di finale",
   "%s. Runde": "%sº turno",
   "1. Runde": "Primo turno",
   "Viertelfinale": "Quarti di finale",
   "Halbfinale": "Semifinali",
   "Endrunde": "Fase finale",
   "Finale": "Finale",
   "Qualifikation": "Qualificazioni",
   "Gruppenphase": "Fase a gironi",
   "Play-offs": "Spareggi",
   "Liga %s": "Lega %s",
   "Gruppe %s": "Girone %s",
   "EM-Qualifikation": "Qualificazioni Europei",
   "WM-Qualifikation": "Qualificazioni Mondiali",
   "Nations League": "Nations League",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Bloccato (HTTP 403) – nuovo tentativo tra 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "La fonte blocca l'accesso (HTTP 403) – nuovo tentativo tra 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Campionato non trovato nella fonte",
   "Zeitüberschreitung – Internet langsam oder weg": "Timeout – Internet lento o assente",
   "Keine Internetverbindung": "Nessuna connessione Internet",
   "Keine Länderspiele gefunden": "Nessuna partita delle nazionali trovata",
   "Keine Internetverbindung (DNS)": "Nessuna connessione Internet (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "Errore SSL – controlla l'ora del ricevitore",
   "Verl": "Supp",
   "Elf": "Rig",
   "Kurs": "Cambio",
   "EZB": "BCE",
   "Android-App laden: Downloader-Code ": "App Android: codice Downloader ",
   "NACHRICHTEN": "NOTIZIE (TEDESCO)",
   "TEXT lang → Details": "TEXT lungo → dettagli",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Champions League",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Germany": "Germania",
   "Türkiye": "Turchia",
   "Turkey": "Turchia",
   "Netherlands": "Paesi Bassi",
   "Greece": "Grecia",
   "France": "Francia",
   "Italy": "Italia",
   "Belgium": "Belgio",
   "Spain": "Spagna",
   "England": "Inghilterra",
   "Portugal": "Portogallo",
   "Croatia": "Croazia",
   "Czechia": "Cechia",
   "Czech Republic": "Cechia",
   "Denmark": "Danimarca",
   "Norway": "Norvegia",
   "Wales": "Galles",
   "Scotland": "Scozia",
   "Switzerland": "Svizzera",
   "North Macedonia": "Macedonia del Nord",
   "Hungary": "Ungheria",
   "Northern Ireland": "Irlanda del Nord",
   "Ukraine": "Ucraina",
   "Ireland": "Irlanda",
   "Republic of Ireland": "Irlanda",
   "Israel": "Israele",
   "Bosnia and Herzegovina": "Bosnia ed Erzegovina",
   "Poland": "Polonia",
   "Sweden": "Svezia",
   "Belarus": "Bielorussia",
   "Finland": "Finlandia",
   "Cyprus": "Cipro",
   "Latvia": "Lettonia",
   "Faroe Islands": "Isole Fær Øer",
   "Kazakhstan": "Kazakistan",
   "Moldova": "Moldavia",
   "Slovakia": "Slovacchia",
   "Iceland": "Islanda",
   "Luxembourg": "Lussemburgo",
   "Lithuania": "Lituania",
   "Azerbaijan": "Azerbaigian",
   "Gibraltar": "Gibilterra",
   "Brazil": "Brasile",
   "Peru": "Perù",
   "Chile": "Cile",
   "USA": "Stati Uniti",
   "United States": "Stati Uniti",
   "Mexico": "Messico",
   "Jamaica": "Giamaica",
   "Morocco": "Marocco",
   "Egypt": "Egitto",
   "Cameroon": "Camerun",
   "Ivory Coast": "Costa d'Avorio",
   "Côte d'Ivoire": "Costa d'Avorio",
   "South Africa": "Sudafrica",
   "DR Congo": "RD Congo",
   "Congo DR": "RD Congo",
   "Cape Verde": "Capo Verde",
   "Cabo Verde": "Capo Verde",
   "Japan": "Giappone",
   "South Korea": "Corea del Sud",
   "Korea Republic": "Corea del Sud",
   "IR Iran": "Iran",
   "Saudi Arabia": "Arabia Saudita",
   "Jordan": "Giordania",
   "United Arab Emirates": "Emirati Arabi",
   "China PR": "Cina",
   "China": "Cina",
   "New Zealand": "Nuova Zelanda"
  }
 },
 "sv": {
  "txt": {
   "overview": "Översikt",
   "results": "Resultat",
   "table": "Tabell",
   "liveTable": "Livetabell",
   "md": "Omgång %d",
   "live": "Live",
   "allLive": "Alla livematcher",
   "help": "Hjälp",
   "settings": "Inställningar",
   "noLive": "Inga livematcher just nu",
   "ht": "Halvt.",
   "ft": "Slut",
   "pp": "Uppsk.",
   "goal": "MÅL!",
   "club": "Lag",
   "pl": "Pl",
   "sp": "S",
   "w": "V",
   "d": "O",
   "l": "F",
   "goals": "Mål",
   "diff": "+/-",
   "pts": "P",
   "fast": [
    "Tyskland",
    "Turkiet",
    "Europa",
    "LIVE"
   ],
   "days": [
    "Mån",
    "Tis",
    "Ons",
    "Tor",
    "Fre",
    "Lör",
    "Sön"
   ],
   "notfound": "Sida %d finns inte",
   "stand": "Uppdaterad",
   "cDE": "TYSKLAND",
   "cTR": "TURKIET",
   "cEU": "EUROPA",
   "cNEWS": "NYHETER (TURKISKA)",
   "openHint": "▲▼◄► välj · OK öppna · TILLBAKA avsluta",
   "home": "TILLBAKA = översikt",
   "exitAgain": "Översikt · TILLBAKA igen = avsluta",
   "setHint": "▲▼ välj · ◄► ändra · TILLBAKA",
   "lselRow": "Välj ligor",
   "lselOpen": "öppna (%d visas)",
   "lselInfo": "Vad som ska stå på huvudsidan – OK sätter eller tar bort bocken.",
   "lselHead": "Bock = visas på huvudsidan",
   "lselHint": "▲▼ välj · OK bock på/av · TILLBAKA",
   "t_lselHint": "Tryck = bock på/av · tryck ▲▼ = bläddra",
   "lselMore": "fler",
   "lselOn": "Visas",
   "lselOff": "Dold",
   "cEUx": "Fler europeiska ligor",
   "loading": "Laddar data …",
   "noData": "Inga data tillgängliga.",
   "noData2": "Kontrollera internetanslutningen.",
   "noGames": "Inga matcher hittades.",
   "noTable": "Ingen tabell tillgänglig.",
   "source": "Data: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(aktuell)",
   "mdBrowse": "Omgång",
   "noMore": "Ingen fler omgång",
   "newsHint": "▲▼ välj · OK läs · ◄► avdelning",
   "articleHint": "▲▼ / ◄► nästa nyhet · OK / TILLBAKA",
   "resHint": "◄► liga · OK tabell",
   "tabHint": "◄► liga · OK resultat",
   "cNAT": "LANDSKAMPER",
   "cRANK": "RANKINGAR",
   "cMORE": "MER",
   "more": "▼ mer: landskamper, rankingar",
   "t_more": "svep ▲ = mer: landskamper, rankingar",
   "ardRow": "ARD Text (tysk text-tv)",
   "ardHint": "▲▼ välj sidnummer · OK öppna · ◄► bläddra · TILLBAKA",
   "t_ardHint": "Tryck på sidnummer = öppna · TILLBAKA = föregående sida",
   "ardLoad": "Laddar ARD Text …",
   "ardErr": "ARD-sida %d nås inte",
   "fav": "Mitt lag",
   "today": "Idag",
   "tv": "På TV idag",
   "tvHint": "▲▼ match · OK kanaler · TILLBAKA stäng",
   "tvNone": "inga kanaler kända",
   "yesterday": "Igår",
   "tomorrow": "Imorgon",
   "dayHint": "◄► byt dag",
   "t_dayHint": "svep ◄► = byt dag",
   "last": "Senaste matcher",
   "next": "Kommande matcher",
   "noFav": "Inget favoritlag valt.",
   "noFav2": "Välj under Inställningar (900).",
   "place": "Plats",
   "scorers": "Målskyttar",
   "detHint": "OK = tabell · ▲▼ bläddra",
   "t_detHint": "tryck på titeln = tabell · svep ▲▼",
   "pen": "S",
   "og": "SM",
   "rkFifa": "FIFA-världsranking",
   "rkUefa": "UEFA-landsranking",
   "rkClub": "UEFA-klubbranking",
   "points": "Poäng",
   "season": "Säsong",
   "nation": "Land",
   "asOf": "Per",
   "noMatches": "Inga matcher denna dag.",
   "nlHint": "◄► ▲▼ nästa grupp",
   "natEU": "Europeiska landskamper",
   "t_nlHint": "svep = nästa grupp",
   "helpOpen": "OK = öppna",
   "t_helpOpen": "tryck = öppna",
   "helpInfo": "Alla knappar och sidor i överblick.",
   "updRow": "Sök efter uppdatering",
   "updOpen": "OK = kontrollera nu",
   "t_updOpen": "tryck = kontrollera nu",
   "updInfo": "Hämta ny version från GitHub. Installerad:",
   "updNone": "SkorText är uppdaterad",
   "updLoad": "Laddar uppdatering %s …",
   "updPerm": "Tillåt ”Installera okända appar” för SkorText och kom tillbaka med TILLBAKA – sedan fortsätter det av sig självt",
   "updOpening": "Installationen öppnas …",
   "updEmbed": "I Aze IPTV Player kommer SkorText med dess uppdatering (Inställningar → Appuppdatering)",
   "updErr": "Uppdateringen misslyckades",
   "updAvail": "Ny version %s – Inställningar → Uppdatering",
   "t_openHint": "Tryck = öppna · TILLBAKA avsluta",
   "t_home": "◄ Översikt (tryck här)",
   "t_resHint": "svep ◄► liga · tryck på titeln = tabell",
   "t_tabHint": "svep ◄► liga · tryck på titeln = resultat",
   "t_setHint": "Tryck = ändra · TILLBAKA",
   "t_newsHint": "Tryck = läs · svep ◄► avdelning",
   "t_articleHint": "svep = nästa nyhet · tryck på titeln = tillbaka",
   "t_mdArrows": "svep ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Byt liga"
    ],
    [
     "OK",
     "Resultat ⇄ tabell"
    ],
    [
     "▲ ▼",
     "Resultat: omgång bakåt/framåt"
    ],
    [
     "▲ ▼",
     "Tabell: nästa del"
    ],
    [
     "TILLBAKA",
     "Översikt, igen = stäng appen"
    ],
    [
     "Översikt",
     "▲▼◄► välj, OK öppnar"
    ],
    [
     "440–460",
     "Champions / Europa / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Idag (◄► dag)"
    ],
    [
     "520",
     "På TV idag: ▲▼ match, OK alla kanaler"
    ],
    [
     "110",
     "Mitt lag"
    ],
    [
     "470",
     "Europeiska landskamper: NL / EM- / VM-kval"
    ],
    [
     "480–484",
     "Rankingar FIFA / UEFA"
    ],
    [
     "500",
     "Alla livematcher"
    ],
    [
     "600",
     "Nyheter (turkiska eller tyska)"
    ],
    [
     "900",
     "Inställningar (stil, språk …)"
    ],
    [
     "Färgknappar",
     "om de finns: DE / TR / Europa / Live"
    ],
    [
     "0–9",
     "om de finns: sidnummer"
    ],
    [
     "Surfplatta",
     "tryck = öppna · titel = tabell · överst = översikt"
    ],
    [
     "Svep",
     "◄► byt liga · ▲▼ omgång / nästa del"
    ]
   ],
   "mixOn": "Mix på",
   "mixOff": "Mix av",
   "ticker": "Målvarning",
   "tkOn": "PÅ",
   "tkOff": "AV",
   "tk1": "När varningen är på visas nere till vänster",
   "tk2": "under TV-tittandet ett litet fönster,",
   "tk3": "bara när det blir mål. Annars är bilden fri.",
   "tkHint": "▲▼ välj · OK = spara + TV-bild",
   "tkOffMsg": "Målvarning av",
   "cNEWSde": "NYHETER (TYSKA)",
   "dlRow": "Android-app: Downloader-kod %s  (aftv.news/%s)",
   "noDet": "Inga detaljer tillgängliga",
   "rkHint": "◄► ranking · ▲▼ bläddra",
   "updAsk": "Ny version %s hittad (installerad: %s).\nLadda ner och installera nu?",
   "updDone": "SkorText %s är installerad.\nStarta om gränssnittet nu?",
   "e2_openHint": "▲▼◄► välj · OK öppna",
   "e2_home": "0 / EXIT = översikt",
   "e2_exitAgain": "Översikt · EXIT igen = TV-bild",
   "e2_setHint": "▲▼ välj · ◄► ändra · EXIT tillbaka",
   "e2_lselHint": "▲▼ välj · OK bock på/av · EXIT tillbaka",
   "e2_noData2": "Kontrollera internetanslutningen och mottagarens klocka.",
   "e2_source": "Data: LiveScore, FuPa, ESPN",
   "e2_ardErr": "ARD-sida %s nås inte",
   "e2_ardHint": "▲▼ välj sidnummer · OK öppna · ◄► bläddra · EXIT tillbaka",
   "e2_tvHint": "▲▼ match · OK kanaler · EXIT stäng",
   "e2_noFav2": "Välj under MENU → Inställningar (900).",
   "e2_updInfo": "Hämta och installera ny version från GitHub. Installerad:",
   "e2_updLoad": "Installerar uppdatering %s …",
   "e2_helpInfo": "Alla knappar och sidor i överblick (även med INFO).",
   "e2_articleHint": "▲▼ / ◄► nästa nyhet · OK / EXIT tillbaka",
   "e2_helpRows": [
    [
     "TEXT länge",
     "Starta SkorText (från TV-bilden)"
    ],
    [
     "TEXT kort",
     "i SkorText: Mix på/av"
    ],
    [
     "◄ ►",
     "Byt liga"
    ],
    [
     "OK",
     "Resultat ⇄ tabell"
    ],
    [
     "▲ ▼",
     "Resultat: omgång bakåt/framåt"
    ],
    [
     "RÖD",
     "Tyskland (200)"
    ],
    [
     "GRÖN",
     "Turkiet (300)"
    ],
    [
     "GUL",
     "Europa (400)"
    ],
    [
     "BLÅ",
     "Alla livematcher (500)"
    ],
    [
     "0–9",
     "Gå till sida, t.ex. 301"
    ],
    [
     "0",
     "direkt till översikten (100)"
    ],
    [
     "150",
     "Målvarning i TV-bilden på/av"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "Idag: alla dagens matcher (◄► dag)"
    ],
    [
     "520",
     "På TV idag: ▲▼ match, OK alla kanaler (liveonsat.com)"
    ],
    [
     "110",
     "Mitt lag"
    ],
    [
     "470",
     "Europeiska landskamper: NL / EM- / VM-kval (◄►)"
    ],
    [
     "480–484",
     "Rankingar: FIFA, UEFA-länder, UEFA-klubbar"
    ],
    [
     "600–660",
     "Nyheter (turkiska eller tyska, se Inställningar) · turk.: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Översikt, EXIT igen = TV"
    ],
    [
     "MENU",
     "Inställningar (900)"
    ],
    [
     "INFO",
     "denna hjälp (190)"
    ],
    [
     "CH+ / CH−",
     "nästa / föregående sida"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Språk / Language",
   "Sprache der Seiten.": "Sidornas språk.",
   "Stil / Optik": "Stil",
   "Videotext (klassisch)": "Text-tv (klassisk)",
   "Modern (wie Webseite)": "Modern (som en webbsida)",
   "Wird sofort umgeschaltet.": "Byts direkt.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Byts direkt – jämför bara.",
   "Lieblingsverein": "Favoritlag",
   "– keiner –": "– inget –",
   "Wird in Ergebnissen und Tabellen markiert.": "Markeras i resultat och tabeller.",
   "Startseite": "Startsida",
   "100 Übersicht": "100 Översikt",
   "500 Live": "500 Live",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Sida som visas vid start.",
   "Welche Seite nach dem Start erscheint.": "Vilken sida som visas vid start.",
   "Aktualisierung": "Uppdatering",
   "alle 60 s": "var 60:e s",
   "alle 30 s": "var 30:e s",
   "alle 120 s": "var 120:e s",
   "Wie oft neue Daten geholt werden.": "Hur ofta nya data hämtas.",
   "Unterseiten blättern": "Bläddra undersidor",
   "Manuell (▲▼)": "Manuellt (▲▼)",
   "Automatisch alle 10 s": "Automatiskt var 10:e s",
   "Bei langen Seiten.": "Vid långa sidor.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "Vid grupper (2./3. Lig) och långa sidor.",
   "Hintergrund": "Bakgrund",
   "Schwarz": "Svart",
   "Schwarz (klassisch)": "Svart (klassisk)",
   "Dunkelblau": "Mörkblå",
   "Durchsichtig (Mix)": "Genomskinlig (Mix)",
   "Hintergrundfarbe.": "Bakgrundsfärg.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT kort växlar även Mix direkt.",
   "Auto-Update": "Automatisk uppdatering",
   "Beim Start prüfen": "Kontrollera vid start",
   "Aus": "Av",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Söker efter ny version på GitHub vid start.",
   "Nachrichten (600)": "Nyheter (600)",
   "Türkisch (TRT, AA)": "Turkiska (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Tyska (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Turkiska eller tyska rubriker, världen, sport … på sidorna 600 ff.",
   "Starttaste": "Startknapp",
   "TEXT lang": "TEXT länge",
   "BLAU lang": "BLÅ länge",
   "Nur Erweiterungen": "Endast tillägg",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Starta direkt från TV-bilden, gäller direkt.",
   "Tor-Ticker im TV (150)": "Målvarning i TV (150)",
   "Alle Live-Spiele": "Alla livematcher",
   "Nur 1. Liga DE + TR": "Bara högstaligan DE + TR",
   "Nur Lieblingsverein": "Bara favoritlaget",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Litet målfönster nere till vänster under TV.",
   "Achtelfinale": "Åttondelsfinal",
   "Sechzehntelfinale": "Sextondelsfinal",
   "%s. Runde": "Omgång %s",
   "1. Runde": "Omgång 1",
   "Viertelfinale": "Kvartsfinal",
   "Halbfinale": "Semifinal",
   "Endrunde": "Slutspel",
   "Finale": "Final",
   "Qualifikation": "Kval",
   "Gruppenphase": "Gruppspel",
   "Play-offs": "Playoff",
   "Liga %s": "Liga %s",
   "Gruppe %s": "Grupp %s",
   "EM-Qualifikation": "EM-kval",
   "WM-Qualifikation": "VM-kval",
   "Nations League": "Nations League",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Spärrad (HTTP 403) – nytt försök om 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "Källan spärrar åtkomsten (HTTP 403) – nytt försök om 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Ligan hittades inte hos källan",
   "Zeitüberschreitung – Internet langsam oder weg": "Tidsgräns – internet långsamt eller nere",
   "Keine Internetverbindung": "Ingen internetanslutning",
   "Keine Länderspiele gefunden": "Inga landskamper hittades",
   "Keine Internetverbindung (DNS)": "Ingen internetanslutning (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "SSL-fel – kontrollera mottagarens klocka",
   "Verl": "Förl",
   "Elf": "Str",
   "Kurs": "Kurs",
   "EZB": "ECB",
   "Android-App laden: Downloader-Code ": "Android-app: Downloader-kod ",
   "NACHRICHTEN": "NYHETER (TYSKA)",
   "TEXT lang → Details": "TEXT länge → detaljer",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Champions League",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Germany": "Tyskland",
   "Türkiye": "Turkiet",
   "Turkey": "Turkiet",
   "Netherlands": "Nederländerna",
   "Greece": "Grekland",
   "Serbia": "Serbien",
   "France": "Frankrike",
   "Italy": "Italien",
   "Belgium": "Belgien",
   "Spain": "Spanien",
   "Croatia": "Kroatien",
   "Czechia": "Tjeckien",
   "Czech Republic": "Tjeckien",
   "Denmark": "Danmark",
   "Norway": "Norge",
   "Scotland": "Skottland",
   "Switzerland": "Schweiz",
   "Slovenia": "Slovenien",
   "North Macedonia": "Nordmakedonien",
   "Georgia": "Georgien",
   "Hungary": "Ungern",
   "Northern Ireland": "Nordirland",
   "Ukraine": "Ukraina",
   "Austria": "Österrike",
   "Ireland": "Irland",
   "Republic of Ireland": "Irland",
   "Bosnia and Herzegovina": "Bosnien och Hercegovina",
   "Poland": "Polen",
   "Romania": "Rumänien",
   "Sweden": "Sverige",
   "Albania": "Albanien",
   "Armenia": "Armenien",
   "Cyprus": "Cypern",
   "Latvia": "Lettland",
   "Faroe Islands": "Färöarna",
   "Kazakhstan": "Kazakstan",
   "Moldova": "Moldavien",
   "Slovakia": "Slovakien",
   "Bulgaria": "Bulgarien",
   "Estonia": "Estland",
   "Iceland": "Island",
   "Luxembourg": "Luxemburg",
   "Lithuania": "Litauen",
   "Azerbaijan": "Azerbajdzjan",
   "Russia": "Ryssland",
   "Brazil": "Brasilien",
   "United States": "USA",
   "Mexico": "Mexiko",
   "Canada": "Kanada",
   "Morocco": "Marocko",
   "Egypt": "Egypten",
   "Algeria": "Algeriet",
   "Tunisia": "Tunisien",
   "Cameroon": "Kamerun",
   "Ivory Coast": "Elfenbenskusten",
   "Côte d'Ivoire": "Elfenbenskusten",
   "South Africa": "Sydafrika",
   "DR Congo": "DR Kongo",
   "Congo DR": "DR Kongo",
   "Cape Verde": "Kap Verde",
   "Cabo Verde": "Kap Verde",
   "South Korea": "Sydkorea",
   "Korea Republic": "Sydkorea",
   "IR Iran": "Iran",
   "Australia": "Australien",
   "Saudi Arabia": "Saudiarabien",
   "Iraq": "Irak",
   "Jordan": "Jordanien",
   "United Arab Emirates": "Förenade Arabemiraten",
   "China PR": "Kina",
   "China": "Kina",
   "New Zealand": "Nya Zeeland"
  }
 },
 "da": {
  "txt": {
   "overview": "Oversigt",
   "results": "Resultater",
   "table": "Stilling",
   "liveTable": "Live-stilling",
   "md": "%d. runde",
   "live": "Live",
   "allLive": "Alle livekampe",
   "help": "Betjening",
   "settings": "Indstillinger",
   "noLive": "Ingen livekampe lige nu",
   "ht": "Pause",
   "ft": "Slut",
   "pp": "Uds.",
   "goal": "MÅL!",
   "club": "Hold",
   "pl": "Pl",
   "sp": "K",
   "w": "V",
   "d": "U",
   "l": "T",
   "goals": "Mål",
   "diff": "+/-",
   "pts": "P",
   "fast": [
    "Tyskland",
    "Tyrkiet",
    "Europa",
    "LIVE"
   ],
   "days": [
    "Man",
    "Tir",
    "Ons",
    "Tor",
    "Fre",
    "Lør",
    "Søn"
   ],
   "notfound": "Side %d findes ikke",
   "stand": "Opdateret",
   "cDE": "TYSKLAND",
   "cTR": "TYRKIET",
   "cEU": "EUROPA",
   "cNEWS": "NYHEDER (TYRKISK)",
   "openHint": "▲▼◄► vælg · OK åbn · TILBAGE afslut",
   "home": "TILBAGE = oversigt",
   "exitAgain": "Oversigt · TILBAGE igen = afslut",
   "setHint": "▲▼ vælg · ◄► skift · TILBAGE",
   "lselRow": "Vælg ligaer",
   "lselOpen": "åbn (%d vises)",
   "lselInfo": "Hvad der skal stå på forsiden – OK sætter eller fjerner fluebenet.",
   "lselHead": "Flueben = vises på forsiden",
   "lselHint": "▲▼ vælg · OK flueben til/fra · TILBAGE",
   "t_lselHint": "Tryk = flueben til/fra · tryk ▲▼ = rul",
   "lselMore": "flere",
   "lselOn": "Vises",
   "lselOff": "Skjult",
   "cEUx": "Flere europæiske ligaer",
   "loading": "Henter data …",
   "noData": "Ingen data tilgængelige.",
   "noData2": "Tjek internetforbindelsen.",
   "noGames": "Ingen kampe fundet.",
   "noTable": "Ingen stilling tilgængelig.",
   "source": "Data: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(aktuel)",
   "mdBrowse": "Runde",
   "noMore": "Ingen flere runder",
   "newsHint": "▲▼ vælg · OK læs · ◄► sektion",
   "articleHint": "▲▼ / ◄► næste nyhed · OK / TILBAGE",
   "resHint": "◄► liga · OK stilling",
   "tabHint": "◄► liga · OK resultater",
   "cNAT": "LANDSKAMPE",
   "cRANK": "RANGLISTER",
   "cMORE": "MERE",
   "more": "▼ mere: landskampe, ranglister",
   "t_more": "stryg ▲ = mere: landskampe, ranglister",
   "ardRow": "ARD Text (tysk tekst-tv)",
   "ardHint": "▲▼ vælg sidenummer · OK åbn · ◄► bladr · TILBAGE",
   "t_ardHint": "Tryk på sidenummer = åbn · TILBAGE = forrige side",
   "ardLoad": "Henter ARD Text …",
   "ardErr": "ARD-side %d kan ikke nås",
   "fav": "Mit hold",
   "today": "I dag",
   "tv": "I tv i dag",
   "tvHint": "▲▼ kamp · OK kanaler · TILBAGE luk",
   "tvNone": "ingen kendte kanaler",
   "yesterday": "I går",
   "tomorrow": "I morgen",
   "dayHint": "◄► skift dag",
   "t_dayHint": "stryg ◄► = skift dag",
   "last": "Seneste kampe",
   "next": "Næste kampe",
   "noFav": "Intet favorithold valgt.",
   "noFav2": "Vælg under Indstillinger (900).",
   "place": "Plads",
   "scorers": "Målscorere",
   "detHint": "OK = stilling · ▲▼ bladr",
   "t_detHint": "tryk på titlen = stilling · stryg ▲▼",
   "pen": "S",
   "og": "SM",
   "rkFifa": "FIFA-verdensrangliste",
   "rkUefa": "UEFA-landerangliste",
   "rkClub": "UEFA-klubrangliste",
   "points": "Point",
   "season": "Sæson",
   "nation": "Land",
   "asOf": "Pr.",
   "noMatches": "Ingen kampe denne dag.",
   "nlHint": "◄► ▲▼ næste gruppe",
   "natEU": "Europæiske landskampe",
   "t_nlHint": "stryg = næste gruppe",
   "helpOpen": "OK = åbn",
   "t_helpOpen": "tryk = åbn",
   "helpInfo": "Alle knapper og sider i overblik.",
   "updRow": "Søg efter opdatering",
   "updOpen": "OK = tjek nu",
   "t_updOpen": "tryk = tjek nu",
   "updInfo": "Hent ny version fra GitHub. Installeret:",
   "updNone": "SkorText er opdateret",
   "updLoad": "Henter opdatering %s …",
   "updPerm": "Tillad »Installer ukendte apps« for SkorText og kom tilbage med TILBAGE – så fortsætter det af sig selv",
   "updOpening": "Installationen åbnes …",
   "updEmbed": "I Aze IPTV Player kommer SkorText med dens opdatering (Indstillinger → App-opdatering)",
   "updErr": "Opdateringen mislykkedes",
   "updAvail": "Ny version %s – Indstillinger → Opdatering",
   "t_openHint": "Tryk = åbn · TILBAGE afslut",
   "t_home": "◄ Oversigt (tryk her)",
   "t_resHint": "stryg ◄► liga · tryk på titlen = stilling",
   "t_tabHint": "stryg ◄► liga · tryk på titlen = resultater",
   "t_setHint": "Tryk = skift · TILBAGE",
   "t_newsHint": "Tryk = læs · stryg ◄► sektion",
   "t_articleHint": "stryg = næste nyhed · tryk på titlen = tilbage",
   "t_mdArrows": "stryg ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Skift liga"
    ],
    [
     "OK",
     "Resultater ⇄ stilling"
    ],
    [
     "▲ ▼",
     "Resultater: runde tilbage/frem"
    ],
    [
     "▲ ▼",
     "Stilling: næste del"
    ],
    [
     "TILBAGE",
     "Oversigt, igen = luk appen"
    ],
    [
     "Oversigt",
     "▲▼◄► vælg, OK åbner"
    ],
    [
     "440–460",
     "Champions / Europa / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "I dag (◄► dag)"
    ],
    [
     "520",
     "I tv i dag: ▲▼ kamp, OK alle kanaler"
    ],
    [
     "110",
     "Mit hold"
    ],
    [
     "470",
     "Europæiske landskampe: NL / EM- / VM-kval"
    ],
    [
     "480–484",
     "Ranglister FIFA / UEFA"
    ],
    [
     "500",
     "Alle livekampe"
    ],
    [
     "600",
     "Nyheder (tyrkisk eller tysk)"
    ],
    [
     "900",
     "Indstillinger (stil, sprog …)"
    ],
    [
     "Farveknapper",
     "hvis de findes: DE / TR / Europa / Live"
    ],
    [
     "0–9",
     "hvis de findes: sidenummer"
    ],
    [
     "Tablet",
     "tryk = åbn · titel = stilling · øverst = oversigt"
    ],
    [
     "Stryg",
     "◄► skift liga · ▲▼ runde / næste del"
    ]
   ],
   "mixOn": "Mix til",
   "mixOff": "Mix fra",
   "ticker": "Målalarm",
   "tkOn": "TIL",
   "tkOff": "FRA",
   "tk1": "Når alarmen er slået til, vises nederst til venstre",
   "tk2": "under tv et lille vindue,",
   "tk3": "kun når der scores. Ellers er billedet frit.",
   "tkHint": "▲▼ vælg · OK = gem + tv-billede",
   "tkOffMsg": "Målalarm fra",
   "cNEWSde": "NYHEDER (TYSK)",
   "dlRow": "Android-app: Downloader-kode %s  (aftv.news/%s)",
   "noDet": "Ingen detaljer tilgængelige",
   "rkHint": "◄► rangliste · ▲▼ bladr",
   "updAsk": "Ny version %s fundet (installeret: %s).\nHent og installer nu?",
   "updDone": "SkorText %s er installeret.\nGenstart brugerfladen nu?",
   "e2_openHint": "▲▼◄► vælg · OK åbn",
   "e2_home": "0 / EXIT = oversigt",
   "e2_exitAgain": "Oversigt · EXIT igen = tv-billede",
   "e2_setHint": "▲▼ vælg · ◄► skift · EXIT tilbage",
   "e2_lselHint": "▲▼ vælg · OK flueben til/fra · EXIT tilbage",
   "e2_noData2": "Tjek internetforbindelsen og modtagerens ur.",
   "e2_source": "Data: LiveScore, FuPa, ESPN",
   "e2_ardErr": "ARD-side %s kan ikke nås",
   "e2_ardHint": "▲▼ vælg sidenummer · OK åbn · ◄► bladr · EXIT tilbage",
   "e2_tvHint": "▲▼ kamp · OK kanaler · EXIT luk",
   "e2_noFav2": "Vælg under MENU → Indstillinger (900).",
   "e2_updInfo": "Hent og installer ny version fra GitHub. Installeret:",
   "e2_updLoad": "Installerer opdatering %s …",
   "e2_helpInfo": "Alle knapper og sider i overblik (også med INFO).",
   "e2_articleHint": "▲▼ / ◄► næste nyhed · OK / EXIT tilbage",
   "e2_helpRows": [
    [
     "TEXT langt",
     "Start SkorText (fra tv-billedet)"
    ],
    [
     "TEXT kort",
     "i SkorText: Mix til/fra"
    ],
    [
     "◄ ►",
     "Skift liga"
    ],
    [
     "OK",
     "Resultater ⇄ stilling"
    ],
    [
     "▲ ▼",
     "Resultater: runde tilbage/frem"
    ],
    [
     "RØD",
     "Tyskland (200)"
    ],
    [
     "GRØN",
     "Tyrkiet (300)"
    ],
    [
     "GUL",
     "Europa (400)"
    ],
    [
     "BLÅ",
     "Alle livekampe (500)"
    ],
    [
     "0–9",
     "Gå til side, f.eks. 301"
    ],
    [
     "0",
     "direkte til oversigten (100)"
    ],
    [
     "150",
     "Målalarm i tv-billedet til/fra"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "I dag: alle dagens kampe (◄► dag)"
    ],
    [
     "520",
     "I tv i dag: ▲▼ kamp, OK alle kanaler (liveonsat.com)"
    ],
    [
     "110",
     "Mit hold"
    ],
    [
     "470",
     "Europæiske landskampe: NL / EM- / VM-kval (◄►)"
    ],
    [
     "480–484",
     "Ranglister: FIFA, UEFA-lande, UEFA-klubber"
    ],
    [
     "600–660",
     "Nyheder (tyrkisk eller tysk, se Indstillinger) · tyrk.: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Oversigt, EXIT igen = tv"
    ],
    [
     "MENU",
     "Indstillinger (900)"
    ],
    [
     "INFO",
     "denne hjælp (190)"
    ],
    [
     "CH+ / CH−",
     "næste / forrige side"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Sprog / Language",
   "Sprache der Seiten.": "Sidernes sprog.",
   "Stil / Optik": "Stil",
   "Videotext (klassisch)": "Tekst-tv (klassisk)",
   "Modern (wie Webseite)": "Moderne (som en hjemmeside)",
   "Wird sofort umgeschaltet.": "Skifter med det samme.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Skifter med det samme – sammenlign.",
   "Lieblingsverein": "Favorithold",
   "– keiner –": "– intet –",
   "Wird in Ergebnissen und Tabellen markiert.": "Fremhæves i resultater og stillinger.",
   "Startseite": "Startside",
   "100 Übersicht": "100 Oversigt",
   "500 Live": "500 Live",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Side der vises ved start.",
   "Welche Seite nach dem Start erscheint.": "Hvilken side der vises ved start.",
   "Aktualisierung": "Opdatering",
   "alle 60 s": "hvert 60. sek.",
   "alle 30 s": "hvert 30. sek.",
   "alle 120 s": "hvert 120. sek.",
   "Wie oft neue Daten geholt werden.": "Hvor ofte nye data hentes.",
   "Unterseiten blättern": "Bladr undersider",
   "Manuell (▲▼)": "Manuelt (▲▼)",
   "Automatisch alle 10 s": "Automatisk hvert 10. sek.",
   "Bei langen Seiten.": "Ved lange sider.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "Ved grupper (2./3. Lig) og lange sider.",
   "Hintergrund": "Baggrund",
   "Schwarz": "Sort",
   "Schwarz (klassisch)": "Sort (klassisk)",
   "Dunkelblau": "Mørkeblå",
   "Durchsichtig (Mix)": "Gennemsigtig (Mix)",
   "Hintergrundfarbe.": "Baggrundsfarve.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT kort skifter også Mix direkte.",
   "Auto-Update": "Automatisk opdatering",
   "Beim Start prüfen": "Tjek ved start",
   "Aus": "Fra",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Søger efter ny version på GitHub ved start.",
   "Nachrichten (600)": "Nyheder (600)",
   "Türkisch (TRT, AA)": "Tyrkisk (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Tysk (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Tyrkiske eller tyske overskrifter, verden, sport … på siderne 600 ff.",
   "Starttaste": "Startknap",
   "TEXT lang": "TEXT langt",
   "BLAU lang": "BLÅ langt",
   "Nur Erweiterungen": "Kun udvidelser",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Start direkte fra tv-billedet, gælder straks.",
   "Tor-Ticker im TV (150)": "Målalarm i tv (150)",
   "Alle Live-Spiele": "Alle livekampe",
   "Nur 1. Liga DE + TR": "Kun bedste liga DE + TR",
   "Nur Lieblingsverein": "Kun favoritholdet",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Lille målvindue nederst til venstre under tv.",
   "Achtelfinale": "Ottendedelsfinale",
   "Sechzehntelfinale": "1/16-finale",
   "%s. Runde": "%s. runde",
   "1. Runde": "1. runde",
   "Viertelfinale": "Kvartfinale",
   "Halbfinale": "Semifinale",
   "Endrunde": "Slutrunde",
   "Finale": "Finale",
   "Qualifikation": "Kvalifikation",
   "Gruppenphase": "Gruppespil",
   "Play-offs": "Playoff",
   "Liga %s": "Liga %s",
   "Gruppe %s": "Gruppe %s",
   "EM-Qualifikation": "EM-kvalifikation",
   "WM-Qualifikation": "VM-kvalifikation",
   "Nations League": "Nations League",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Spærret (HTTP 403) – nyt forsøg om 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "Kilden spærrer adgangen (HTTP 403) – nyt forsøg om 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Ligaen blev ikke fundet hos kilden",
   "Zeitüberschreitung – Internet langsam oder weg": "Timeout – internettet er langsomt eller nede",
   "Keine Internetverbindung": "Ingen internetforbindelse",
   "Keine Länderspiele gefunden": "Ingen landskampe fundet",
   "Keine Internetverbindung (DNS)": "Ingen internetforbindelse (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "SSL-fejl – tjek modtagerens ur",
   "Verl": "Forl",
   "Elf": "Str",
   "Kurs": "Kurs",
   "EZB": "ECB",
   "Android-App laden: Downloader-Code ": "Android-app: Downloader-kode ",
   "NACHRICHTEN": "NYHEDER (TYSK)",
   "TEXT lang → Details": "TEXT langt → detaljer",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Champions League",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Germany": "Tyskland",
   "Türkiye": "Tyrkiet",
   "Turkey": "Tyrkiet",
   "Netherlands": "Holland",
   "Greece": "Grækenland",
   "Serbia": "Serbien",
   "France": "Frankrig",
   "Italy": "Italien",
   "Belgium": "Belgien",
   "Spain": "Spanien",
   "Croatia": "Kroatien",
   "Czechia": "Tjekkiet",
   "Czech Republic": "Tjekkiet",
   "Denmark": "Danmark",
   "Norway": "Norge",
   "Scotland": "Skotland",
   "Switzerland": "Schweiz",
   "Slovenia": "Slovenien",
   "North Macedonia": "Nordmakedonien",
   "Georgia": "Georgien",
   "Hungary": "Ungarn",
   "Northern Ireland": "Nordirland",
   "Austria": "Østrig",
   "Ireland": "Irland",
   "Republic of Ireland": "Irland",
   "Bosnia and Herzegovina": "Bosnien-Hercegovina",
   "Poland": "Polen",
   "Romania": "Rumænien",
   "Sweden": "Sverige",
   "Albania": "Albanien",
   "Belarus": "Hviderusland",
   "Armenia": "Armenien",
   "Cyprus": "Cypern",
   "Latvia": "Letland",
   "Faroe Islands": "Færøerne",
   "Kazakhstan": "Kasakhstan",
   "Slovakia": "Slovakiet",
   "Bulgaria": "Bulgarien",
   "Estonia": "Estland",
   "Iceland": "Island",
   "Lithuania": "Litauen",
   "Azerbaijan": "Aserbajdsjan",
   "Russia": "Rusland",
   "Brazil": "Brasilien",
   "United States": "USA",
   "Morocco": "Marokko",
   "Egypt": "Egypten",
   "Algeria": "Algeriet",
   "Tunisia": "Tunesien",
   "Cameroon": "Cameroun",
   "Ivory Coast": "Elfenbenskysten",
   "Côte d'Ivoire": "Elfenbenskysten",
   "South Africa": "Sydafrika",
   "Congo DR": "DR Congo",
   "Cape Verde": "Kap Verde",
   "Cabo Verde": "Kap Verde",
   "South Korea": "Sydkorea",
   "Korea Republic": "Sydkorea",
   "IR Iran": "Iran",
   "Australia": "Australien",
   "Saudi Arabia": "Saudi-Arabien",
   "Iraq": "Irak",
   "Uzbekistan": "Usbekistan",
   "United Arab Emirates": "Forenede Arabiske Emirater",
   "China PR": "Kina",
   "China": "Kina"
  }
 },
 "no": {
  "txt": {
   "overview": "Oversikt",
   "results": "Resultater",
   "table": "Tabell",
   "liveTable": "Live-tabell",
   "md": "%d. runde",
   "live": "Live",
   "allLive": "Alle livekamper",
   "help": "Betjening",
   "settings": "Innstillinger",
   "noLive": "Ingen livekamper akkurat nå",
   "ht": "Pause",
   "ft": "Slutt",
   "pp": "Uts.",
   "goal": "MÅL!",
   "club": "Lag",
   "pl": "Pl",
   "sp": "K",
   "w": "V",
   "d": "U",
   "l": "T",
   "goals": "Mål",
   "diff": "+/-",
   "pts": "P",
   "fast": [
    "Tyskland",
    "Tyrkia",
    "Europa",
    "LIVE"
   ],
   "days": [
    "Man",
    "Tir",
    "Ons",
    "Tor",
    "Fre",
    "Lør",
    "Søn"
   ],
   "notfound": "Side %d finnes ikke",
   "stand": "Oppdatert",
   "cDE": "TYSKLAND",
   "cTR": "TYRKIA",
   "cEU": "EUROPA",
   "cNEWS": "NYHETER (TYRKISK)",
   "openHint": "▲▼◄► velg · OK åpne · TILBAKE avslutt",
   "home": "TILBAKE = oversikt",
   "exitAgain": "Oversikt · TILBAKE igjen = avslutt",
   "setHint": "▲▼ velg · ◄► endre · TILBAKE",
   "lselRow": "Velg ligaer",
   "lselOpen": "åpne (%d vises)",
   "lselInfo": "Hva som skal stå på forsiden – OK setter eller fjerner haken.",
   "lselHead": "Hake = vises på forsiden",
   "lselHint": "▲▼ velg · OK hake på/av · TILBAKE",
   "t_lselHint": "Trykk = hake på/av · trykk ▲▼ = bla",
   "lselMore": "flere",
   "lselOn": "Vises",
   "lselOff": "Skjult",
   "cEUx": "Flere europeiske ligaer",
   "loading": "Henter data …",
   "noData": "Ingen data tilgjengelig.",
   "noData2": "Sjekk internettforbindelsen.",
   "noGames": "Ingen kamper funnet.",
   "noTable": "Ingen tabell tilgjengelig.",
   "source": "Data: LiveScore, ESPN, FuPa, TFF",
   "mdNow": "(aktuell)",
   "mdBrowse": "Runde",
   "noMore": "Ingen flere runder",
   "newsHint": "▲▼ velg · OK les · ◄► seksjon",
   "articleHint": "▲▼ / ◄► neste nyhet · OK / TILBAKE",
   "resHint": "◄► liga · OK tabell",
   "tabHint": "◄► liga · OK resultater",
   "cNAT": "LANDSKAMPER",
   "cRANK": "RANKINGER",
   "cMORE": "MER",
   "more": "▼ mer: landskamper, rankinger",
   "t_more": "sveip ▲ = mer: landskamper, rankinger",
   "ardRow": "ARD Text (tysk tekst-tv)",
   "ardHint": "▲▼ velg sidenummer · OK åpne · ◄► bla · TILBAKE",
   "t_ardHint": "Trykk på sidenummer = åpne · TILBAKE = forrige side",
   "ardLoad": "Henter ARD Text …",
   "ardErr": "ARD-side %d kan ikke nås",
   "fav": "Mitt lag",
   "today": "I dag",
   "tv": "På TV i dag",
   "tvHint": "▲▼ kamp · OK kanaler · TILBAKE lukk",
   "tvNone": "ingen kjente kanaler",
   "yesterday": "I går",
   "tomorrow": "I morgen",
   "dayHint": "◄► bytt dag",
   "t_dayHint": "sveip ◄► = bytt dag",
   "last": "Siste kamper",
   "next": "Neste kamper",
   "noFav": "Ikke valgt favorittlag.",
   "noFav2": "Velg under Innstillinger (900).",
   "place": "Plass",
   "scorers": "Målscorere",
   "detHint": "OK = tabell · ▲▼ bla",
   "t_detHint": "trykk på tittelen = tabell · sveip ▲▼",
   "pen": "S",
   "og": "SM",
   "rkFifa": "FIFA-verdensranking",
   "rkUefa": "UEFA-landsranking",
   "rkClub": "UEFA-klubbranking",
   "points": "Poeng",
   "season": "Sesong",
   "nation": "Land",
   "asOf": "Per",
   "noMatches": "Ingen kamper denne dagen.",
   "nlHint": "◄► ▲▼ neste gruppe",
   "natEU": "Europeiske landskamper",
   "t_nlHint": "sveip = neste gruppe",
   "helpOpen": "OK = åpne",
   "t_helpOpen": "trykk = åpne",
   "helpInfo": "Alle knapper og sider i oversikt.",
   "updRow": "Se etter oppdatering",
   "updOpen": "OK = sjekk nå",
   "t_updOpen": "trykk = sjekk nå",
   "updInfo": "Hent ny versjon fra GitHub. Installert:",
   "updNone": "SkorText er oppdatert",
   "updLoad": "Henter oppdatering %s …",
   "updPerm": "Tillat «Installer ukjente apper» for SkorText og kom tilbake med TILBAKE – da fortsetter det av seg selv",
   "updOpening": "Installasjonen åpnes …",
   "updEmbed": "I Aze IPTV Player kommer SkorText med dens oppdatering (Innstillinger → App-oppdatering)",
   "updErr": "Oppdateringen mislyktes",
   "updAvail": "Ny versjon %s – Innstillinger → Oppdatering",
   "t_openHint": "Trykk = åpne · TILBAKE avslutt",
   "t_home": "◄ Oversikt (trykk her)",
   "t_resHint": "sveip ◄► liga · trykk på tittelen = tabell",
   "t_tabHint": "sveip ◄► liga · trykk på tittelen = resultater",
   "t_setHint": "Trykk = endre · TILBAKE",
   "t_newsHint": "Trykk = les · sveip ◄► seksjon",
   "t_articleHint": "sveip = neste nyhet · trykk på tittelen = tilbake",
   "t_mdArrows": "sveip ▲▼",
   "helpRows": [
    [
     "◄ ►",
     "Bytt liga"
    ],
    [
     "OK",
     "Resultater ⇄ tabell"
    ],
    [
     "▲ ▼",
     "Resultater: runde tilbake/frem"
    ],
    [
     "▲ ▼",
     "Tabell: neste del"
    ],
    [
     "TILBAKE",
     "Oversikt, igjen = lukk appen"
    ],
    [
     "Oversikt",
     "▲▼◄► velg, OK åpner"
    ],
    [
     "440–460",
     "Champions / Europa / Conference League"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "I dag (◄► dag)"
    ],
    [
     "520",
     "På TV i dag: ▲▼ kamp, OK alle kanaler"
    ],
    [
     "110",
     "Mitt lag"
    ],
    [
     "470",
     "Europeiske landskamper: NL / EM- / VM-kval"
    ],
    [
     "480–484",
     "Rankinger FIFA / UEFA"
    ],
    [
     "500",
     "Alle livekamper"
    ],
    [
     "600",
     "Nyheter (tyrkisk eller tysk)"
    ],
    [
     "900",
     "Innstillinger (stil, språk …)"
    ],
    [
     "Fargeknapper",
     "hvis de finnes: DE / TR / Europa / Live"
    ],
    [
     "0–9",
     "hvis de finnes: sidenummer"
    ],
    [
     "Nettbrett",
     "trykk = åpne · tittel = tabell · øverst = oversikt"
    ],
    [
     "Sveip",
     "◄► bytt liga · ▲▼ runde / neste del"
    ]
   ],
   "mixOn": "Mix på",
   "mixOff": "Mix av",
   "ticker": "Målvarsel",
   "tkOn": "PÅ",
   "tkOff": "AV",
   "tk1": "Når varselet er på, vises nede til venstre",
   "tk2": "mens du ser på TV et lite vindu,",
   "tk3": "bare når det blir mål. Ellers er bildet fritt.",
   "tkHint": "▲▼ velg · OK = lagre + TV-bilde",
   "tkOffMsg": "Målvarsel av",
   "cNEWSde": "NYHETER (TYSK)",
   "dlRow": "Android-app: Downloader-kode %s  (aftv.news/%s)",
   "noDet": "Ingen detaljer tilgjengelig",
   "rkHint": "◄► ranking · ▲▼ bla",
   "updAsk": "Ny versjon %s funnet (installert: %s).\nLaste ned og installere nå?",
   "updDone": "SkorText %s er installert.\nStarte grensesnittet på nytt nå?",
   "e2_openHint": "▲▼◄► velg · OK åpne",
   "e2_home": "0 / EXIT = oversikt",
   "e2_exitAgain": "Oversikt · EXIT igjen = TV-bilde",
   "e2_setHint": "▲▼ velg · ◄► endre · EXIT tilbake",
   "e2_lselHint": "▲▼ velg · OK hake på/av · EXIT tilbake",
   "e2_noData2": "Sjekk internettforbindelsen og mottakerens klokke.",
   "e2_source": "Data: LiveScore, FuPa, ESPN",
   "e2_ardErr": "ARD-side %s kan ikke nås",
   "e2_ardHint": "▲▼ velg sidenummer · OK åpne · ◄► bla · EXIT tilbake",
   "e2_tvHint": "▲▼ kamp · OK kanaler · EXIT lukk",
   "e2_noFav2": "Velg under MENU → Innstillinger (900).",
   "e2_updInfo": "Hent og installer ny versjon fra GitHub. Installert:",
   "e2_updLoad": "Installerer oppdatering %s …",
   "e2_helpInfo": "Alle knapper og sider i oversikt (også med INFO).",
   "e2_articleHint": "▲▼ / ◄► neste nyhet · OK / EXIT tilbake",
   "e2_helpRows": [
    [
     "TEXT lenge",
     "Start SkorText (fra TV-bildet)"
    ],
    [
     "TEXT kort",
     "i SkorText: Mix på/av"
    ],
    [
     "◄ ►",
     "Bytt liga"
    ],
    [
     "OK",
     "Resultater ⇄ tabell"
    ],
    [
     "▲ ▼",
     "Resultater: runde tilbake/frem"
    ],
    [
     "RØD",
     "Tyskland (200)"
    ],
    [
     "GRØNN",
     "Tyrkia (300)"
    ],
    [
     "GUL",
     "Europa (400)"
    ],
    [
     "BLÅ",
     "Alle livekamper (500)"
    ],
    [
     "0–9",
     "Gå til side, f.eks. 301"
    ],
    [
     "0",
     "rett til oversikten (100)"
    ],
    [
     "150",
     "Målvarsel i TV-bildet på/av"
    ],
    [
     "320 / 322",
     "2. Lig: Beyaz / Kırmızı Grup"
    ],
    [
     "295 / 305",
     "DFB-Pokal / Türkiye Kupası"
    ],
    [
     "510",
     "I dag: alle dagens kamper (◄► dag)"
    ],
    [
     "520",
     "På TV i dag: ▲▼ kamp, OK alle kanaler (liveonsat.com)"
    ],
    [
     "110",
     "Mitt lag"
    ],
    [
     "470",
     "Europeiske landskamper: NL / EM- / VM-kval (◄►)"
    ],
    [
     "480–484",
     "Rankinger: FIFA, UEFA-land, UEFA-klubber"
    ],
    [
     "600–660",
     "Nyheter (tyrkisk eller tysk, se Innstillinger) · tyrk.: 650/660 Kahramanmaraş"
    ],
    [
     "EXIT",
     "Oversikt, EXIT igjen = TV"
    ],
    [
     "MENU",
     "Innstillinger (900)"
    ],
    [
     "INFO",
     "denne hjelpen (190)"
    ],
    [
     "CH+ / CH−",
     "neste / forrige side"
    ]
   ]
  },
  "tx": {
   "Sprache / Dil": "Språk / Language",
   "Sprache der Seiten.": "Sidenes språk.",
   "Stil / Optik": "Stil",
   "Videotext (klassisch)": "Tekst-TV (klassisk)",
   "Modern (wie Webseite)": "Moderne (som en nettside)",
   "Wird sofort umgeschaltet.": "Byttes med en gang.",
   "Wird sofort umgeschaltet – einfach vergleichen.": "Byttes med en gang – bare sammenlign.",
   "Lieblingsverein": "Favorittlag",
   "– keiner –": "– ingen –",
   "Wird in Ergebnissen und Tabellen markiert.": "Markeres i resultater og tabeller.",
   "Startseite": "Startside",
   "100 Übersicht": "100 Oversikt",
   "500 Live": "500 Live",
   "200 1. Bundesliga": "200 Bundesliga",
   "Seite nach dem Start.": "Side som vises ved start.",
   "Welche Seite nach dem Start erscheint.": "Hvilken side som vises ved start.",
   "Aktualisierung": "Oppdatering",
   "alle 60 s": "hvert 60. sek.",
   "alle 30 s": "hvert 30. sek.",
   "alle 120 s": "hvert 120. sek.",
   "Wie oft neue Daten geholt werden.": "Hvor ofte nye data hentes.",
   "Unterseiten blättern": "Bla i undersider",
   "Manuell (▲▼)": "Manuelt (▲▼)",
   "Automatisch alle 10 s": "Automatisk hvert 10. sek.",
   "Bei langen Seiten.": "Ved lange sider.",
   "Bei Gruppen (2./3. Lig) und langen Seiten.": "Ved grupper (2./3. Lig) og lange sider.",
   "Hintergrund": "Bakgrunn",
   "Schwarz": "Svart",
   "Schwarz (klassisch)": "Svart (klassisk)",
   "Dunkelblau": "Mørkeblå",
   "Durchsichtig (Mix)": "Gjennomsiktig (Mix)",
   "Hintergrundfarbe.": "Bakgrunnsfarge.",
   "TEXT kurz schaltet Mix auch direkt um.": "TEXT kort bytter også Mix direkte.",
   "Auto-Update": "Automatisk oppdatering",
   "Beim Start prüfen": "Sjekk ved start",
   "Aus": "Av",
   "Sucht beim Start auf GitHub nach einer neuen Version.": "Ser etter ny versjon på GitHub ved start.",
   "Nachrichten (600)": "Nyheter (600)",
   "Türkisch (TRT, AA)": "Tyrkisk (TRT, AA)",
   "Deutsch (tagesschau, kicker)": "Tysk (tagesschau, kicker)",
   "Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.": "Tyrkiske eller tyske overskrifter, verden, sport … på sidene 600 ff.",
   "Starttaste": "Startknapp",
   "TEXT lang": "TEXT lenge",
   "BLAU lang": "BLÅ lenge",
   "Nur Erweiterungen": "Bare utvidelser",
   "Vom Fernsehbild direkt starten, gilt sofort.": "Start rett fra TV-bildet, gjelder straks.",
   "Tor-Ticker im TV (150)": "Målvarsel på TV (150)",
   "Alle Live-Spiele": "Alle livekamper",
   "Nur 1. Liga DE + TR": "Bare toppligaen DE + TR",
   "Nur Lieblingsverein": "Bare favorittlaget",
   "Kleines Tor-Fenster unten links beim Fernsehen.": "Lite målvindu nede til venstre under TV.",
   "Achtelfinale": "Åttedelsfinale",
   "Sechzehntelfinale": "1/16-finale",
   "%s. Runde": "%s. runde",
   "1. Runde": "1. runde",
   "Viertelfinale": "Kvartfinale",
   "Halbfinale": "Semifinale",
   "Endrunde": "Sluttspill",
   "Finale": "Finale",
   "Qualifikation": "Kvalifisering",
   "Gruppenphase": "Gruppespill",
   "Play-offs": "Playoff",
   "Liga %s": "Liga %s",
   "Gruppe %s": "Gruppe %s",
   "EM-Qualifikation": "EM-kvalifisering",
   "WM-Qualifikation": "VM-kvalifisering",
   "Nations League": "Nations League",
   "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.": "Sperret (HTTP 403) – nytt forsøk om 5 min.",
   "Datenquelle sperrt den Zugriff (HTTP 403) – neuer Versuch in 5 Min.": "Kilden sperrer tilgangen (HTTP 403) – nytt forsøk om 5 min.",
   "Liga bei der Datenquelle nicht gefunden": "Ligaen ble ikke funnet hos kilden",
   "Zeitüberschreitung – Internet langsam oder weg": "Tidsavbrudd – internett tregt eller nede",
   "Keine Internetverbindung": "Ingen internettforbindelse",
   "Keine Länderspiele gefunden": "Ingen landskamper funnet",
   "Keine Internetverbindung (DNS)": "Ingen internettforbindelse (DNS)",
   "SSL-Fehler – Uhrzeit des Receivers prüfen": "SSL-feil – sjekk mottakerens klokke",
   "Verl": "Ekst",
   "Elf": "Str",
   "Kurs": "Kurs",
   "EZB": "ESB",
   "Android-App laden: Downloader-Code ": "Android-app: Downloader-kode ",
   "NACHRICHTEN": "NYHETER (TYSK)",
   "TEXT lang → Details": "TEXT lenge → detaljer",
   "1. Bundesliga": "Bundesliga",
   "Championship (2. Liga)": "Championship",
   "Champions League": "Champions League",
   "Europa League": "Europa League",
   "Conference League": "Conference League"
  },
  "nations": {
   "Germany": "Tyskland",
   "Türkiye": "Tyrkia",
   "Turkey": "Tyrkia",
   "Netherlands": "Nederland",
   "Greece": "Hellas",
   "France": "Frankrike",
   "Italy": "Italia",
   "Belgium": "Belgia",
   "Spain": "Spania",
   "Croatia": "Kroatia",
   "Czechia": "Tsjekkia",
   "Czech Republic": "Tsjekkia",
   "Denmark": "Danmark",
   "Norway": "Norge",
   "Scotland": "Skottland",
   "Switzerland": "Sveits",
   "North Macedonia": "Nord-Makedonia",
   "Hungary": "Ungarn",
   "Northern Ireland": "Nord-Irland",
   "Ukraine": "Ukraina",
   "Austria": "Østerrike",
   "Ireland": "Irland",
   "Republic of Ireland": "Irland",
   "Bosnia and Herzegovina": "Bosnia-Hercegovina",
   "Poland": "Polen",
   "Sweden": "Sverige",
   "Cyprus": "Kypros",
   "Faroe Islands": "Færøyene",
   "Kazakhstan": "Kasakhstan",
   "Estonia": "Estland",
   "Iceland": "Island",
   "Lithuania": "Litauen",
   "Azerbaijan": "Aserbajdsjan",
   "Russia": "Russland",
   "Brazil": "Brasil",
   "United States": "USA",
   "Morocco": "Marokko",
   "Algeria": "Algerie",
   "Cameroon": "Kamerun",
   "Ivory Coast": "Elfenbenskysten",
   "Côte d'Ivoire": "Elfenbenskysten",
   "South Africa": "Sør-Afrika",
   "DR Congo": "DR Kongo",
   "Congo DR": "DR Kongo",
   "Cape Verde": "Kapp Verde",
   "Cabo Verde": "Kapp Verde",
   "South Korea": "Sør-Korea",
   "Korea Republic": "Sør-Korea",
   "IR Iran": "Iran",
   "Saudi Arabia": "Saudi-Arabia",
   "Iraq": "Irak",
   "Uzbekistan": "Usbekistan",
   "United Arab Emirates": "De forente arabiske emirater",
   "China PR": "Kina",
   "China": "Kina"
  }
 }
};
