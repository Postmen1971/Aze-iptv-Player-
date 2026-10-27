/* SkorText für Android TV / NVIDIA Shield – Portierung des Enigma2-Plugins */
(function () {
"use strict";
var VERSION = "3.10";

/* ================= Texte ================= */
var TXT = {
 de: {overview:"Übersicht",results:"Ergebnisse",table:"Tabelle",liveTable:"Live-Tabelle",md:"%d. Spieltag",live:"Live",
  allLive:"Alle Live-Spiele",help:"Bedienung",settings:"Einstellungen",noLive:"Zurzeit keine Live-Spiele",ht:"HZ",ft:"Ende",
  pp:"Abg.",goal:"TOR!",club:"Verein",pl:"Pl",sp:"Sp",w:"S",d:"U",l:"N",goals:"Tore",diff:"Diff",pts:"Pkt",
  fast:["Deutschland","Türkei","Europa","LIVE"],days:["So","Mo","Di","Mi","Do","Fr","Sa"],notfound:"Seite %d nicht vorhanden",
  stand:"Stand",cDE:"DEUTSCHLAND",cTR:"TÜRKEI",cEU:"EUROPA",cNEWS:"HABERLER",openHint:"▲▼◄► wählen · OK öffnen · ZURÜCK beenden",
  home:"ZURÜCK = Übersicht",exitAgain:"Übersicht · ZURÜCK nochmal = beenden",setHint:"▲▼ wählen · ◄► ändern · ZURÜCK zurück",
  lselRow:"Ligen auswählen",lselOpen:"öffnen (%d eingeblendet)",lselInfo:"Was auf der Hauptseite stehen soll – mit OK Haken setzen oder entfernen.",
  lselHead:"Haken = auf der Hauptseite eingeblendet",lselHint:"▲▼ wählen · OK Haken an/aus · ZURÜCK",t_lselHint:"Antippen = Haken an/aus · ▲▼ antippen = blättern",
  lselMore:"weitere",lselOn:"Eingeblendet",lselOff:"Ausgeblendet",cEUx:"Weitere Ligen Europas",
  loading:"Lade Daten …",noData:"Keine Daten erreichbar.",noData2:"Internetverbindung prüfen.",noGames:"Keine Spiele gefunden.",
  noTable:"Keine Tabelle verfügbar.",source:"Daten: LiveScore, ESPN, FuPa, TFF",mdNow:"(aktuell)",mdBrowse:"Spieltag",
  noMore:"Kein weiterer Spieltag",newsHint:"▲▼ wählen · OK lesen · ◄► Rubrik",articleHint:"▲▼ / ◄► nächste Meldung · OK / ZURÜCK",
  resHint:"◄► Liga · OK Tabelle",tabHint:"◄► Liga · OK Ergebnisse",
  cNAT:"LÄNDERSPIELE",cRANK:"RANGLISTEN",cMORE:"WEITERES",more:"▼ mehr: Länderspiele, Ranglisten",t_more:"wischen ▲ = mehr: Länderspiele, Ranglisten",
  fav:"Mein Verein",today:"Heute",yesterday:"Gestern",tomorrow:"Morgen",dayHint:"◄► Tag wechseln",t_dayHint:"wischen ◄► = Tag wechseln",
  last:"Letzte Spiele",next:"Nächste Spiele",noFav:"Kein Lieblingsverein gewählt.",noFav2:"Unter Einstellungen (900) auswählen.",place:"Platz",
  scorers:"Torschützen",detHint:"OK = Tabelle · ▲▼ blättern",t_detHint:"Titel antippen = Tabelle · wischen ▲▼",pen:"E",og:"ET",
  rkFifa:"FIFA-Weltrangliste",rkUefa:"UEFA-Länderwertung",rkClub:"UEFA-Klubwertung",points:"Punkte",season:"Saison",nation:"Land",asOf:"Stand",
  noMatches:"Keine Spiele an diesem Tag.",nlHint:"◄► ▲▼ nächste Gruppe",natEU:"Länderspiele Europa",t_nlHint:"wischen = nächste Gruppe",helpOpen:"OK = öffnen",t_helpOpen:"antippen = öffnen",
  helpInfo:"Alle Tasten und Seiten auf einen Blick.",updRow:"Update suchen",updOpen:"OK = jetzt prüfen",t_updOpen:"antippen = jetzt prüfen",
  updInfo:"Neue Version von GitHub laden. Installiert:",updNone:"SkorText ist aktuell",updLoad:"Update %s wird geladen …",updPerm:"Bitte „Unbekannte Apps installieren“ erlauben, dann nochmal",
  updErr:"Update fehlgeschlagen",updAvail:"Neue Version %s – Einstellungen → Update",
  t_openHint:"Antippen = öffnen · ZURÜCK beenden",t_home:"◄ Übersicht (hier tippen)",t_resHint:"wischen ◄► Liga · Titel antippen = Tabelle",
  t_tabHint:"wischen ◄► Liga · Titel antippen = Ergebnisse",t_setHint:"Antippen = ändern · ZURÜCK zurück",
  t_newsHint:"Antippen = lesen · wischen ◄► Rubrik",t_articleHint:"wischen = nächste Meldung · Titel antippen = zurück",t_mdArrows:"wischen ▲▼",
  helpRows:[["◄ ►","Liga wechseln"],["OK","Ergebnisse ⇄ Tabelle"],["▲ ▼","Ergebnisse: Spieltag vor/zurück"],["▲ ▼","Tabelle: Unterseite"],
   ["ZURÜCK","Übersicht, nochmal = App beenden"],["Übersicht","▲▼◄► wählen, OK öffnet"],["440–460","Champions / Europa / Conference League"],["295 / 305","DFB-Pokal / Türkiye Kupası"],["510","Heute (◄► Tag)"],["110","Mein Verein"],["470","Länderspiele Europa: NL / EM- / WM-Quali"],["480–484","Ranglisten FIFA / UEFA"],["500","Alle Live-Spiele"],["600","Haberler (Nachrichten)"],
   ["900","Einstellungen (Stil, Sprache …)"],["Farbtasten","falls vorhanden: DE / TR / Europa / Live"],["0–9","falls vorhanden: Seitennummer"],["Tablet","antippen = öffnen · Titel = Tabelle · oben = Übersicht"],["Wischen","◄► Liga wechseln · ▲▼ Spieltag / Unterseite"]]},
 tr: {overview:"Genel Bakış",results:"Sonuçlar",table:"Puan Durumu",liveTable:"Canlı Puan Durumu",md:"%d. Hafta",live:"Canlı",
  allLive:"Tüm Canlı Maçlar",help:"Kullanım",settings:"Ayarlar",noLive:"Şu an canlı maç yok",ht:"İY",ft:"MS",
  pp:"Ert.",goal:"GOL!",club:"Takım",pl:"S",sp:"O",w:"G",d:"B",l:"M",goals:"Gol",diff:"Av",pts:"P",
  fast:["Almanya","Türkiye","Avrupa","CANLI"],days:["Paz","Pzt","Sal","Çar","Per","Cum","Cmt"],notfound:"Sayfa %d yok",
  stand:"Güncel",cDE:"ALMANYA",cTR:"TÜRKİYE",cEU:"AVRUPA",cNEWS:"HABERLER",openHint:"▲▼◄► seç · OK aç · GERİ çık",
  home:"GERİ = Genel Bakış",exitAgain:"Genel Bakış · tekrar GERİ = çık",setHint:"▲▼ seç · ◄► değiştir · GERİ",
  lselRow:"Ligleri seç",lselOpen:"aç (%d görünür)",lselInfo:"Ana sayfada ne görünsün – OK ile işaretle veya kaldır.",
  lselHead:"İşaretli = ana sayfada görünür",lselHint:"▲▼ seç · OK işaret aç/kapa · GERİ",t_lselHint:"Dokun = işaret aç/kapa · ▲▼ dokun = kaydır",
  lselMore:"devamı",lselOn:"Gösteriliyor",lselOff:"Gizlendi",cEUx:"Diğer Avrupa ligleri",
  loading:"Veriler yükleniyor …",noData:"Veri alınamadı.",noData2:"İnternet bağlantısını kontrol et.",noGames:"Maç bulunamadı.",
  noTable:"Puan durumu yok.",source:"Veri: LiveScore, ESPN, FuPa, TFF",mdNow:"(güncel)",mdBrowse:"Hafta",
  noMore:"Başka hafta yok",newsHint:"▲▼ seç · OK oku · ◄► kategori",articleHint:"▲▼ / ◄► sonraki haber · OK / GERİ",
  resHint:"◄► Lig · OK Puan",tabHint:"◄► Lig · OK Sonuçlar",
  cNAT:"MİLLİ MAÇLAR",cRANK:"SIRALAMALAR",cMORE:"DİĞER",more:"▼ devamı: Milli maçlar, sıralamalar",t_more:"kaydır ▲ = devamı: Milli maçlar, sıralamalar",
  fav:"Takımım",today:"Bugün",yesterday:"Dün",tomorrow:"Yarın",dayHint:"◄► gün değiştir",t_dayHint:"kaydır ◄► = gün değiştir",
  last:"Son maçlar",next:"Sıradaki maçlar",noFav:"Favori takım seçilmedi.",noFav2:"Ayarlar (900) altından seç.",place:"Sıra",
  scorers:"Goller",detHint:"OK = Puan durumu · ▲▼ sayfa",t_detHint:"başlığa dokun = Puan · kaydır ▲▼",pen:"P",og:"KK",
  rkFifa:"FIFA Dünya Sıralaması",rkUefa:"UEFA Ülke Puanı",rkClub:"UEFA Kulüp Puanı",points:"Puan",season:"Sezon",nation:"Ülke",asOf:"Güncel",
  noMatches:"Bu gün maç yok.",nlHint:"◄► ▲▼ sonraki grup",natEU:"Avrupa milli maçları",t_nlHint:"kaydır = sonraki grup",helpOpen:"OK = aç",t_helpOpen:"dokun = aç",
  helpInfo:"Tüm tuşlar ve sayfalar.",updRow:"Güncelleme ara",updOpen:"OK = şimdi kontrol et",t_updOpen:"dokun = şimdi kontrol et",
  updInfo:"GitHub'dan yeni sürüm yükle. Kurulu:",updNone:"SkorText güncel",updLoad:"Güncelleme %s yükleniyor …",updPerm:"Lütfen „Bilinmeyen uygulamalar“ iznini ver, sonra tekrar",
  updErr:"Güncelleme başarısız",updAvail:"Yeni sürüm %s – Ayarlar → Güncelleme",
  t_openHint:"Dokun = aç · GERİ çık",t_home:"◄ Genel Bakış (dokun)",t_resHint:"kaydır ◄► Lig · başlığa dokun = Puan",
  t_tabHint:"kaydır ◄► Lig · başlığa dokun = Sonuçlar",t_setHint:"Dokun = değiştir · GERİ",
  t_newsHint:"Dokun = oku · kaydır ◄► kategori",t_articleHint:"kaydır = sonraki haber · başlığa dokun = geri",t_mdArrows:"kaydır ▲▼",
  helpRows:[["◄ ►","Lig değiştir"],["OK","Sonuçlar ⇄ Puan Durumu"],["▲ ▼","Sonuçlar: önceki/sonraki hafta"],["▲ ▼","Puan durumu: alt sayfa"],
   ["GERİ","Genel Bakış, tekrar = uygulamadan çık"],["Genel Bakış","▲▼◄► seç, OK aç"],["440–460","Şampiyonlar / Avrupa / Konferans Ligi"],["295 / 305","Almanya Kupası / Türkiye Kupası"],["510","Bugün (◄► gün)"],["110","Takımım"],["470","Avrupa milli maçlar: UL / EURO / DK eleme"],["480–484","Sıralamalar FIFA / UEFA"],["500","Tüm canlı maçlar"],["600","Haberler"],
   ["900","Ayarlar (görünüm, dil …)"],["Renk tuşları","varsa: DE / TR / Avrupa / Canlı"],["0–9","varsa: sayfa numarası"],["Tablet","dokun = aç · başlık = Puan · üst = Genel Bakış"],["Kaydır","◄► lig değiştir · ▲▼ hafta / alt sayfa"]]}
};
var SETTINGS = [
 ["style",["Stil / Optik","Görünüm"],[["tt","Videotext (klassisch)","Teletext (klasik)"],["modern","Modern (wie Webseite)","Modern (web sitesi gibi)"]],["Wird sofort umgeschaltet.","Hemen değişir."]],
 ["lang",["Sprache / Dil","Dil / Sprache"],[["de","Deutsch","Deutsch"],["tr","Türkçe","Türkçe"]],["Sprache der Seiten.","Sayfa dili."]],
 ["fav",["Lieblingsverein","Favori takım"],[["","– keiner –","– yok –"],["VfB Stuttgart","VfB Stuttgart","VfB Stuttgart"],["Stuttgarter Kickers","Stuttgarter Kickers","Stuttgarter Kickers"],
   ["Großaspach","SG Sonnenhof Großaspach","SG Sonnenhof Großaspach"],["Göppinger","1. Göppinger SV","1. Göppinger SV"],["Bayern München","FC Bayern München","FC Bayern München"],
   ["Dortmund","Borussia Dortmund","Borussia Dortmund"],["Galatasaray","Galatasaray","Galatasaray"],["Fenerbahçe","Fenerbahçe","Fenerbahçe"],
   ["Beşiktaş","Beşiktaş","Beşiktaş"],["Trabzonspor","Trabzonspor","Trabzonspor"],["Başakşehir","Başakşehir","Başakşehir"]],["Wird in Ergebnissen und Tabellen markiert.","Sonuç ve tabloda işaretlenir."]],
 ["startpage",["Startseite","Açılış sayfası"],[["100","100 Übersicht","100 Genel Bakış"],["500","500 Live","500 Canlı"],["200","200 1. Bundesliga","200 Bundesliga"],["300","300 Süper Lig","300 Süper Lig"]],["Seite nach dem Start.","Başlatınca açılan sayfa."]],
 ["refresh",["Aktualisierung","Güncelleme"],[["60","alle 60 s","60 sn'de bir"],["30","alle 30 s","30 sn'de bir"],["120","alle 120 s","120 sn'de bir"]],["Wie oft neue Daten geholt werden.","Verilerin çekilme sıklığı."]],
 ["rotate",["Unterseiten blättern","Alt sayfa çevirme"],[["man","Manuell (▲▼)","Manuel (▲▼)"],["10","Automatisch alle 10 s","10 sn'de bir otomatik"]],["Bei langen Seiten.","Uzun sayfalarda."]],
 ["bg",["Hintergrund","Arka plan"],[["black","Schwarz","Siyah"],["navy","Dunkelblau","Lacivert"]],["Hintergrundfarbe.","Arka plan rengi."]],
 ["autoupd",["Auto-Update","Otomatik güncelleme"],[["on","Beim Start prüfen","Açılışta kontrol et"],["off","Aus","Kapalı"]],["Sucht beim Start auf GitHub nach einer neuen Version.","Açılışta GitHub'da yeni sürüm arar."]],
 ["news",["Nachrichten (600)","Haberler (600)"],[["tr","Türkisch (TRT, AA)","Türkçe (TRT, AA)"],["de","Deutsch (tagesschau, kicker)","Almanca (tagesschau, kicker)"]],["Türkische oder deutsche Schlagzeilen, Welt, Sport … auf den Seiten 600 ff.","600 ve sonrası: Türkçe veya Almanca manşet, dünya, spor …"]]
];
var DL_CODE = "5694507";  // Downloader-Code für die Android-App (aftv.news/5694507)

/* ================= Einstellungen (dauerhaft) ================= */
var CFG = {};
function prefGet(k, d) {
  try { if (window.SkorBridge && SkorBridge.pref) return SkorBridge.pref(k, d); } catch (e) {}
  try { var v = localStorage.getItem("skortext." + k); return v === null ? d : v; } catch (e) { return d; }
}
function prefSet(k, v) {
  try { if (window.SkorBridge && SkorBridge.setPref) { SkorBridge.setPref(k, v); return; } } catch (e) {}
  try { localStorage.setItem("skortext." + k, v); } catch (e) {}
}
SETTINGS.forEach(function (s) { CFG[s[0]] = prefGet(s[0], s[2][0][0]); });
function cfg(k) { return CFG[k]; }
function setCfg(k, v) { CFG[k] = v; prefSet(k, v); if (k === "news" && typeof setNews === "function") setNews(v); }
var TOUCH = false;
function T(k) { var t = TXT[cfg("lang")]; return (TOUCH && t["t_" + k] !== undefined) ? t["t_" + k] : t[k]; }
function fmt(s, n) { return s.replace("%d", n); }

/* ================= HTTP ================= */
var pending = {}, reqId = 0;
window.__skorRes = function (id, ok, status, text) {
  var p = pending[id]; delete pending[id]; if (!p) return;
  if (ok && status >= 200 && status < 300) p.res(text); else p.rej(new Error(ok ? "HTTP " + status : String(text)));
};
function httpGet(url) {
  return new Promise(function (res, rej) {
    if (window.SkorBridge && SkorBridge.get) { var id = ++reqId; pending[id] = {res: res, rej: rej}; SkorBridge.get(id, url); }
    else fetch(url).then(function (r) { return r.ok ? r.text() : Promise.reject(new Error("HTTP " + r.status)); }).then(res, rej);
  });
}
/* LiveScore hat zwei Server. Sperrt einer (HTTP 403), wird der andere genommen;
   sperren beide, macht SkorText 5 Minuten Pause statt weiter anzufragen. */
var LS_HOSTS = ["https://prod-public-api.livescore.com", "https://prod-cdn-public-api.livescore.com"], LS_I = 0, LS_PAUSE = 0;
function rawJSON(url) { return httpGet(url).then(function (t) { return JSON.parse(t); }); }
function getJSON(url) {
  var m = /^https:\/\/prod-(?:cdn-)?public-api\.livescore\.com(\/.*)$/.exec(url);
  if (!m) return rawJSON(url);
  if (Date.now() < LS_PAUSE) return Promise.reject(new Error("HTTP 403 Pause"));
  var path = m[1], first = LS_I;
  return rawJSON(LS_HOSTS[first] + path).catch(function (e) {
    if (String(e && e.message).indexOf("403") < 0) throw e;
    var other = (first + 1) % LS_HOSTS.length;
    return rawJSON(LS_HOSTS[other] + path).then(function (d) { LS_I = other; return d; }, function (e2) {
      if (String(e2 && e2.message).indexOf("403") >= 0) LS_PAUSE = Date.now() + 5 * 60000;
      throw e2;
    });
  });
}
var BASE = "https://prod-public-api.livescore.com/v1/api/app";
function niceError(e) {
  var s = String(e && e.message || e);
  if (s.indexOf("403") >= 0) return cfg("lang") === "tr" ? "Engellendi (HTTP 403) – 5 dk sonra tekrar" : "Gesperrt (HTTP 403) – neuer Versuch in 5 Min.";
  if (s.indexOf("404") >= 0 || s.indexOf("410") >= 0) return "Liga bei der Datenquelle nicht gefunden";
  if (/timeout|timed out/i.test(s)) return "Zeitüberschreitung – Internet langsam oder weg";
  if (/unknownhost|unable to resolve|dns/i.test(s)) return "Keine Internetverbindung";
  return s.slice(0, 80);
}

/* ================= Daten (LiveScore) ================= */
// Seite, Land, LiveScore-Land, LiveScore-Liga, Name deutsch, Name türkisch, Kürzel
var LEAGUES_DEF = [
 [200,"de","germany","bundesliga","1. Bundesliga","Bundesliga",""],[210,"de","germany","2-bundesliga","2. Bundesliga","2. Bundesliga",""],
 [220,"de","germany","3-liga","3. Liga","3. Lig (Almanya)",""],
 [230,"de","germany","regionalliga-sudwest","Regionalliga Südwest","Regionalliga Güneybatı",""],[240,"de","germany","regionalliga-west","Regionalliga West","Regionalliga Batı",""],
 [250,"de","germany","regionalliga-bayern","Regionalliga Bayern","Regionalliga Bavyera",""],[260,"de","germany","regionalliga-nord","Regionalliga Nord","Regionalliga Kuzey",""],
 [270,"de","germany","regionalliga-nordost","Regionalliga Nordost","Regionalliga Kuzeydoğu",""],
 [280,"de","germany","oberliga-baden-wurttemberg","Oberliga Baden-Württemberg","Oberliga Baden-Württemberg",""],
 [290,"de","fupa","verbandsliga-wuerttemberg","Verbandsliga Württemberg","Verbandsliga Württemberg",""],
 [295,"de","cup:germany","dfb-cup","DFB-Pokal","Almanya Kupası",""],
 [300,"tr","turkey","super-lig","Trendyol Süper Lig","Trendyol Süper Lig",""],[305,"tr","cup:turkey","turkiye-cup","Türkiye Kupası","Ziraat Türkiye Kupası",""],[310,"tr","turkey","1st-lig","Trendyol 1. Lig (2. Liga)","Trendyol 1. Lig",""],
 [320,"tr","turkey","2nd-lig-white-group","2. Lig (3. Liga) · Beyaz Grup","Nesine 2. Lig · Beyaz Grup",""],
 [322,"tr","turkey","2nd-lig-red-group","2. Lig (3. Liga) · Kırmızı Grup","Nesine 2. Lig · Kırmızı Grup",""],
 [400,"eu","england","premier-league","Premier League","Premier Lig","ENG"],[410,"eu","spain","laliga","LaLiga","LaLiga","ESP"],
 [420,"eu","italy","serie-a","Serie A","Serie A","ITA"],[430,"eu","france","ligue-1","Ligue 1","Ligue 1","FRA"],
 [440,"eu","champions-league","league-stage","Champions League","Şampiyonlar Ligi",""],
 [450,"eu","europa-league","league-stage","Europa League","Avrupa Ligi",""],
 [460,"eu","conference-league","league-stage","Conference League","Konferans Ligi",""],
 // weitere Ligen Europas – erst nach Haken in den Einstellungen (Seite 910) sichtbar
 [700,"eu","holland","eredivisie","Eredivisie","Eredivisie","NED"],[710,"eu","portugal","primeira-liga","Liga Portugal","Portekiz Ligi","POR"],
 [720,"eu","belgium","belgian-pro-league-2025","Pro League","Belçika Ligi","BEL"],[730,"eu","scotland","scotland-premiership","Premiership","İskoçya Ligi","SCO"],
 [740,"eu","austria","bundesliga","Bundesliga","Avusturya Ligi","AUT"],[750,"eu","switzerland","super-league","Super League","İsviçre Ligi","SUI"],
 [760,"eu","greece","super-league","Super League","Yunanistan Ligi","GRE"],[770,"eu","england","championship","Championship (2. Liga)","Championship","ENG"],
 [780,"eu","spain","laliga-2","LaLiga 2","LaLiga 2","ESP"],[790,"eu","italy","serie-b","Serie B","Serie B","ITA"],
 [800,"eu","france","ligue-2","Ligue 2","Ligue 2","FRA"],[810,"eu","denmark","superliga","Superliga","Danimarka Ligi","DEN"],
];
var EXTRA_PAGES = [700, 710, 720, 730, 740, 750, 760, 770, 780, 790, 800, 810];
/* Ligen-Auswahl: gespeichert werden nur Seiten, die vom Standard abweichen */
function lgToggled() { var s = {}; String(prefGet("lgsel", "") || "").split(",").forEach(function (x) { if (x && !isNaN(+x)) s[+x] = true; }); return s; }
function pageOn(p) { var t = lgToggled(); return EXTRA_PAGES.indexOf(p) >= 0 ? !!t[p] : !t[p]; }
function togglePage(p) { var t = lgToggled(); if (t[p]) delete t[p]; else t[p] = true; prefSet("lgsel", Object.keys(t).map(Number).sort(function (a, b) { return a - b; }).join(",")); }

var NAME_FIX = [["FC Cologne","1. FC Köln"],["Cologne","Köln"],["Bayern Munich","Bayern München"],["Munich 1860","TSV 1860 München"],["Nuremberg","Nürnberg"],["Monchengladbach","Mönchengladbach"],["Dusseldorf","Düsseldorf"],["Furth","Fürth"],["Munster","Münster"],["Osnabruck","Osnabrück"],["Saarbrucken","Saarbrücken"],["Wurzburg","Würzburg"],["Gottingen","Göttingen"],["Goppingen","Göppingen"],["Lubeck","Lübeck"],["Grossaspach","Großaspach"],["Koln","Köln"],["Gutersloh","Gütersloh"],["Rodinghausen","Rödinghausen"],["Wiedenbruck","Wiedenbrück"],["Gmund","Gmünd"],["Nottingen","Nöttingen"],["Turkgucu","Türkgücü"],["Turkspor","Türkspor"],["Preussen","Preußen"],["Muenster","Münster"],["1860 Munich","TSV 1860 München"],["Luneburger","Lüneburger"],["Teutonia St. Toenis","Teutonia St. Tönis"],["Zweibrucken","Zweibrücken"],["Volklingen","Völklingen"],["Bodoe/Glimt","Bodø/Glimt"],["Slavia Prague","SK Slavia Prag"],["Sparta Prague","AC Sparta Prag"],["Atletico Madrid","Atlético Madrid"],["Ferencvaros","Ferencváros"],["Viktoria Plzen","Viktoria Pilsen"],["Lech Poznan","Lech Posen"],["Lillestroem","Lillestrøm"],["Mjaellby","Mjällby"],["FC Nordsjaelland","FC Nordsjælland"],["FC Copenhagen","FC Kopenhagen"],["FC Salzburg","RB Salzburg"],["Union St.Gilloise","Union Saint-Gilloise"],["Turkiye","Türkiye"],["Besiktas","Beşiktaş"],["Fenerbahce","Fenerbahçe"],["Basaksehir","Başakşehir"],["Kasimpasa","Kasımpaşa"],["Eyupspor","Eyüpspor"],["Goztepe","Göztepe"],["Caykur","Çaykur"],["Genclerbirligi","Gençlerbirliği"],["Karagumruk","Karagümrük"],["Igdir","Iğdır"],["Corum","Çorum"],["Bandirmaspor","Bandırmaspor"],["Umraniyespor","Ümraniyespor"],["Keciorengucu","Keçiörengücü"],["Istanbulspor","İstanbulspor"],["Sariyer","Sarıyer"],["Sanliurfaspor","Şanlıurfaspor"],["Arnavutkoy","Arnavutköy"],["Mus Spor","Muşspor"],["Musspor","Muşspor"],["Elazigspor","Elazığspor"],["Corluspor","Çorluspor"],["Genclik","Gençlik"],["Kirklarelispor","Kırklarelispor"],["Inegolspor","İnegölspor"],["Altinordu","Altınordu"],["Usakspor","Uşakspor"],["Kahramanmaras","Kahramanmaraş"],["Istiklalspor","İstiklalspor"],["Yildirimspor","Yıldırımspor"],["Aliaga","Aliağa"],["Karsiyaka","Karşıyaka"],["Mugla","Muğla"],["Duzce","Düzce"],["Guzide","Güzide"],["Kucukcekmece","Küçükçekmece"],["Beyoglu","Beyoğlu"],["Carsi","Çarşı"],["Iskenderun","İskenderun"],["Sile","Şile"],["Bursa Yildirim","Bursa Yıldırım"],["Igdir FK","Iğdır FK"],["Eskisehir","Eskişehir"],["Kutahya","Kütahya"],["Balikesir","Balıkesir"],["Canakkale","Çanakkale"],["Diyarbakir","Diyarbakır"],["Kirsehir","Kırşehir"],["Nevsehir","Nevşehir"],["Nigde","Niğde"],["Kirikkale","Kırıkkale"],["Inegol","İnegöl"],["gucu","gücü"],["Ayvalik","Ayvalık"],["Kulubu","Kulübü"],["Yesilyurt","Yeşilyurt"],["Sumerbey","Sümerbey"],["Mazidagi","Mazıdağı"],["Bingol","Bingöl"],["Universitesi","Üniversitesi"],["Dernegi","Derneği"],["Agri ","Ağrı "],["Akcaabat","Akçaabat"],["Carsamba","Çarşamba"]];
function fixName(n) {
  n = String(n || "").replace(/i\u0307/g, "i").trim();
  for (var i = 0; i < NAME_FIX.length; i++) { var a = NAME_FIX[i][0], b = NAME_FIX[i][1]; if (n.indexOf(a) >= 0 && n.indexOf(b) < 0) n = n.split(a).join(b); }
  return n;
}
var NATIONS = {"Germany":["Deutschland","Almanya"],"Türkiye":["Türkei","Türkiye"],"Turkey":["Türkei","Türkiye"],"Netherlands":["Niederlande","Hollanda"],"Greece":["Griechenland","Yunanistan"],"Serbia":["Serbien","Sırbistan"],"France":["Frankreich","Fransa"],"Italy":["Italien","İtalya"],"Belgium":["Belgien","Belçika"],"Spain":["Spanien","İspanya"],"England":["England","İngiltere"],"Portugal":["Portugal","Portekiz"],"Croatia":["Kroatien","Hırvatistan"],"Czechia":["Tschechien","Çekya"],"Czech Republic":["Tschechien","Çekya"],"Denmark":["Dänemark","Danimarka"],"Norway":["Norwegen","Norveç"],"Wales":["Wales","Galler"],"Scotland":["Schottland","İskoçya"],"Switzerland":["Schweiz","İsviçre"],"Slovenia":["Slowenien","Slovenya"],"North Macedonia":["Nordmazedonien","Kuzey Makedonya"],"Georgia":["Georgien","Gürcistan"],"Hungary":["Ungarn","Macaristan"],"Northern Ireland":["Nordirland","Kuzey İrlanda"],"Ukraine":["Ukraine","Ukrayna"],"Austria":["Österreich","Avusturya"],"Ireland":["Irland","İrlanda"],"Republic of Ireland":["Irland","İrlanda"],"Israel":["Israel","İsrail"],"Kosovo":["Kosovo","Kosova"],"Bosnia and Herzegovina":["Bosnien-Herzegowina","Bosna-Hersek"],"Poland":["Polen","Polonya"],"Romania":["Rumänien","Romanya"],"Sweden":["Schweden","İsveç"],"Albania":["Albanien","Arnavutluk"],"Belarus":["Belarus","Belarus"],"Finland":["Finnland","Finlandiya"],"San Marino":["San Marino","San Marino"],"Armenia":["Armenien","Ermenistan"],"Cyprus":["Zypern","Kıbrıs Rum Kesimi"],"Latvia":["Lettland","Letonya"],"Montenegro":["Montenegro","Karadağ"],"Faroe Islands":["Färöer","Faroe Adaları"],"Kazakhstan":["Kasachstan","Kazakistan"],"Moldova":["Moldau","Moldova"],"Slovakia":["Slowakei","Slovakya"],"Bulgaria":["Bulgarien","Bulgaristan"],"Estonia":["Estland","Estonya"],"Iceland":["Island","İzlanda"],"Luxembourg":["Luxemburg","Lüksemburg"],"Lithuania":["Litauen","Litvanya"],"Azerbaijan":["Aserbaidschan","Azerbaycan"],"Malta":["Malta","Malta"],"Andorra":["Andorra","Andorra"],"Liechtenstein":["Liechtenstein","Lihtenştayn"],"Gibraltar":["Gibraltar","Cebelitarık"],"Russia":["Russland","Rusya"],"Argentina":["Argentinien","Arjantin"],"Brazil":["Brasilien","Brezilya"],"Uruguay":["Uruguay","Uruguay"],"Colombia":["Kolumbien","Kolombiya"],"Ecuador":["Ecuador","Ekvador"],"Paraguay":["Paraguay","Paraguay"],"Peru":["Peru","Peru"],"Chile":["Chile","Şili"],"Venezuela":["Venezuela","Venezuela"],"Bolivia":["Bolivien","Bolivya"],"USA":["USA","ABD"],"United States":["USA","ABD"],"Mexico":["Mexiko","Meksika"],"Canada":["Kanada","Kanada"],"Panama":["Panama","Panama"],"Costa Rica":["Costa Rica","Kosta Rika"],"Jamaica":["Jamaika","Jamaika"],"Morocco":["Marokko","Fas"],"Senegal":["Senegal","Senegal"],"Egypt":["Ägypten","Mısır"],"Algeria":["Algerien","Cezayir"],"Tunisia":["Tunesien","Tunus"],"Nigeria":["Nigeria","Nijerya"],"Cameroon":["Kamerun","Kamerun"],"Ghana":["Ghana","Gana"],"Ivory Coast":["Elfenbeinküste","Fildişi Sahili"],"Côte d'Ivoire":["Elfenbeinküste","Fildişi Sahili"],"Mali":["Mali","Mali"],"South Africa":["Südafrika","Güney Afrika"],"DR Congo":["DR Kongo","DK Kongo"],"Congo DR":["DR Kongo","DK Kongo"],"Burkina Faso":["Burkina Faso","Burkina Faso"],"Cape Verde":["Kap Verde","Yeşil Burun Adaları"],"Cabo Verde":["Kap Verde","Yeşil Burun Adaları"],"Japan":["Japan","Japonya"],"South Korea":["Südkorea","Güney Kore"],"Korea Republic":["Südkorea","Güney Kore"],"IR Iran":["Iran","İran"],"Iran":["Iran","İran"],"Australia":["Australien","Avustralya"],"Saudi Arabia":["Saudi-Arabien","Suudi Arabistan"],"Qatar":["Katar","Katar"],"Iraq":["Irak","Irak"],"Uzbekistan":["Usbekistan","Özbekistan"],"Jordan":["Jordanien","Ürdün"],"United Arab Emirates":["VAE","BAE"],"China PR":["China","Çin"],"China":["China","Çin"],"New Zealand":["Neuseeland","Yeni Zelanda"]};
function dn(name) { var t = NATIONS[name]; return t ? t[cfg("lang") === "tr" ? 1 : 0] : name; }
var FAV_LEAGUES = {"VfB Stuttgart": [200, 295, 440, 450, 460], "Bayern München": [200, 295, 440, 450, 460], "Dortmund": [200, 295, 440, 450, 460],
  "Stuttgarter Kickers": [230, 280, 295], "Großaspach": [230, 280, 295], "Göppinger": [290],
  "Galatasaray": [300, 305, 440, 450, 460], "Fenerbahçe": [300, 305, 440, 450, 460], "Beşiktaş": [300, 305, 440, 450, 460],
  "Trabzonspor": [300, 305, 440, 450, 460], "Başakşehir": [300, 305, 440, 450, 460]};
var FAV_CODES = {200: "BL", 230: "RL", 280: "OL", 290: "VL", 295: "DFB", 300: "SL", 305: "TK", 440: "CL", 450: "EL", 460: "ECL"};
function seasonStart() { var d = new Date(), y = d.getMonth() >= 6 ? d.getFullYear() : d.getFullYear() - 1; return Date.UTC(y, 6, 1) / 1000; }
function esdTs(v) {
  var s = String(v || ""); if (s.length < 12) return 0;
  return Date.UTC(+s.slice(0, 4), +s.slice(4, 6) - 1, +s.slice(6, 8), +s.slice(8, 10), +s.slice(10, 12), +(s.slice(12, 14) || 0)) / 1000;
}
function toInt(v) { var n = parseInt(v, 10); return isNaN(n) ? null : n; }
function parseEvent(e, key) {
  var eps = String(e.Eps || "").trim(), minute = "", state;
  if (eps === "NS" || eps === "") state = "pre";
  else if (["FT", "AET", "AP", "Pen.", "FT Pen", "After Pen.", "Awarded"].indexOf(eps) >= 0) state = "post";
  else if (eps === "HT") state = "ht";
  else if (["Postp.", "Canc.", "Aband.", "Int.", "Susp.", "Delayed", "Postponed", "Cancelled"].indexOf(eps) >= 0) state = "pp";
  else {
    state = "in";
    if (eps.slice(-1) === "'") minute = eps.slice(0, -1);
    else if (["ET", "BET"].indexOf(eps) >= 0) minute = "Verl";
    else if (eps.indexOf("Pen") >= 0) minute = "Elf";
    else minute = eps.slice(0, 5);
  }
  var t1 = (e.T1 || [{}])[0] || {}, t2 = (e.T2 || [{}])[0] || {};
  var hs = toInt(e.Tr1), as = toInt(e.Tr2);
  if (state === "pre") hs = as = null;
  return {id: String(e.Eid), h: fixName(t1.Nm || "?"), hsn: t1.Abr || "", a: fixName(t2.Nm || "?"), asn: t2.Abr || "",
    hs: hs, as: as, state: state, min: minute, ts: esdTs(e.Esd), round: toInt(e.ErnInf), group: "", ut: key, goal_at: 0, goal_by: null};
}
function parseTable(stage) {
  var lt = stage.LeagueTable || {}, tabs = ((lt.L || [{}])[0] || {}).Tables || [], tab = null;
  tabs.forEach(function (t) { if (!tab && t.LTT === 1) tab = t; });
  if (!tab && tabs.length) tab = tabs[0];
  if (!tab) return [];
  var teams = tab.team || [], n = teams.length;
  var rows = teams.map(function (r) {
    return {pos: r.rnk || 0, tm: fixName(r.Tnm || "?"), short: "", p: r.pld || 0, w: r.win || 0, d: r.drw || 0, l: r.lst || 0,
      gf: r.gf || 0, ga: r.ga || 0, pts: r.pts || 0, phr: (r.phr || [null])[0], zone: null};
  });
  var top = [], bottom = [];
  rows.forEach(function (r) {
    if (r.phr === null || r.phr === undefined) return;
    if (r.pos <= n / 2) { if (top.indexOf(r.phr) < 0) top.push(r.phr); }
    else if (bottom.indexOf(r.phr) < 0) bottom.push(r.phr);
  });
  rows.forEach(function (r) {
    if (r.phr === null || r.phr === undefined) return;
    if (top.indexOf(r.phr) >= 0) r.zone = ["g", "c", "m", "c"][Math.min(top.indexOf(r.phr), 3)];
    else r.zone = ["r", "y", "y"][Math.min(bottom.length - 1 - bottom.indexOf(r.phr), 2)];
  });
  return [["", rows]];
}
function inferRounds(evs) {
  if (!evs.length || evs.every(function (e) { return e.round !== null; })) return;
  var buckets = [], last = null;
  evs.forEach(function (e) { if (last === null || e.ts - last > 60 * 3600) buckets.push([]); buckets[buckets.length - 1].push(e); last = e.ts; });
  var prev = 0;
  buckets.forEach(function (b) {
    var cnt = {}, best = null;
    b.forEach(function (e) { if (e.round !== null) { cnt[e.round] = (cnt[e.round] || 0) + 1; if (best === null || cnt[e.round] > cnt[best]) best = e.round; } });
    var r = best !== null ? best : prev + 1;
    b.forEach(function (e) { if (e.round === null) e.round = r; });
    prev = Math.max(prev, r);
  });
}

function League(d) {
  this.page = d[0]; this.c = d[1]; this.ccd = d[2]; this.scd = d[3]; this.nameDe = d[4]; this.nameTr = d[5]; this.tag = d[6];
  this.ut = this.ccd + "/" + this.scd;
  this.cup = this.ccd.indexOf("cup:") === 0; this.rnames = {};
  if (this.cup) { this.country = this.ccd.slice(4); this.prefix = this.scd; this.ut = "cup/" + this.country + "/" + this.prefix; }
  this.season = true; this.round = null; this.maxRound = null; this.allEvents = [];
  this.events = []; this.prevEvents = []; this.prevRound = null; this.rcache = {}; this.rloading = {};
  this.tables = []; this.tLoad = 0; this.loading = false; this.error = null; this.gdefs = [];
}
League.prototype.name = function (lang) { return lang === "tr" ? this.nameTr : this.nameDe; };
League.prototype.views = function () { return this.c === "nlg" ? [] : [[this.page, null]]; };
League.prototype.groups = function () { return [""]; };
League.prototype.groupIndex = function () { return null; };
League.prototype.inGroup = function () { return true; };
League.prototype.pageOfGroup = function () { return this.page; };
League.prototype.eventsOfRound = function (n) {
  if (n === null || n === undefined) return [];
  var evs = this.allEvents.filter(function (e) { return e.round === n; });
  if (!evs.length && this.source === "espn" && this.espnCal && !(n in this.rcache)) return null;  // beim Blättern nachladen
  return evs;
};
League.prototype.liveCount = function () { return this.events.filter(function (e) { return e.state === "in" || e.state === "ht"; }).length; };
/* Ersatzquelle ESPN, wenn LiveScore sperrt (HTTP 403) */
var ESPN = "https://site.api.espn.com/apis/site/v2/sports/soccer", ESPN_TAB = "https://site.api.espn.com/apis/v2/sports/soccer";
var ESPN_CODES = {200: "ger.1", 210: "ger.2", 295: "ger.dfb_pokal", 300: "tur.1", 400: "eng.1", 410: "esp.1", 420: "ita.1", 430: "fra.1",
  440: "uefa.champions", 450: "uefa.europa", 460: "uefa.europa.conf",
  700: "ned.1", 710: "por.1", 720: "bel.1", 730: "sco.1", 740: "aut.1", 750: "sui.1", 760: "gre.1", 770: "eng.2", 780: "esp.2", 790: "ita.2", 800: "fra.2", 810: "den.1"};
var ESPN_NAMES = {"Bayern Munich": "Bayern München", "1. FC Union Berlin": "Union Berlin", "Borussia Monchengladbach": "Borussia M'gladbach",
  "1. FC Cologne": "1. FC Köln", "FC Cologne": "1. FC Köln", "FC Augsburg": "Augsburg", "VfL Wolfsburg": "Wolfsburg", "1. FC Heidenheim 1846": "FC Heidenheim",
  "TSG Hoffenheim": "Hoffenheim", "SC Freiburg": "Freiburg"};
function espnName(n) { n = n || "?"; return ESPN_NAMES[n] || fixName(n); }
function parseEspnEvent(ev, key) {
  var st = (ev.status || {}).type || {}, state = {pre: "pre", "in": "in", post: "post"}[st.state] || "pre", nm = st.name || "", minute = "";
  if (nm.indexOf("HALFTIME") >= 0) state = "ht";
  else if (/POSTPONED|CANCELED|SUSPENDED/.test(nm)) state = "pp";
  else if (state === "in") minute = String((ev.status || {}).displayClock || "").split("'")[0];
  var comp = (ev.competitions || [{}])[0], home = {}, away = {};
  (comp.competitors || []).forEach(function (c) { if (c.homeAway === "home") home = c; else away = c; });
  var hs = toInt(home.score), as = toInt(away.score); if (state === "pre") hs = as = null;
  var ht = home.team || {}, at = away.team || {};
  return {id: "espn" + ev.id, h: espnName(ht.displayName), hsn: ht.shortDisplayName || "", a: espnName(at.displayName), asn: at.shortDisplayName || "",
    hs: hs, as: as, state: state, min: minute, ts: isoTs(ev.date), round: null, group: "", ut: key, goal_at: 0, goal_by: null};
}
function espnZone(note) {
  var d = String((note || {}).description || "").toLowerCase();
  if (!d) return null;
  if (d.indexOf("relegation") >= 0 && /play|qualif/.test(d)) return "y";
  if (d.indexOf("relegation") >= 0) return "r";
  if (/champions league|round of 16|promotion/.test(d)) return "g";
  if (/europa league|play/.test(d)) return "c";
  if (d.indexOf("conference") >= 0) return "m";
  return "c";
}
League.prototype.loadEspn = function () {
  var self = this, code = ESPN_CODES[self.page], now = Date.now();
  var old = {}; if (self.source === "espn") self.allEvents.forEach(function (e) { old[e.id] = e; });
  var full = !Object.keys(old).length || now - (self.tEspnFull || 0) > 1800000, days = [];
  if (full) for (var d = -6; d <= 6; d++) days.push(d); else days = [0];
  var evs = old;
  return Promise.all(days.map(function (dd) {
    var dt = new Date(now + dd * 86400000), key = dt.getFullYear() + two(dt.getMonth() + 1) + two(dt.getDate());
    return rawJSON(ESPN + "/" + code + "/scoreboard?dates=" + key).then(function (j) {
      var lg0 = (j.leagues || [])[0] || {};
      if (!self.espnCal && lg0.calendarType === "day" && lg0.calendar) self.espnCal = espnCalRounds(lg0.calendar.filter(function (x) { return typeof x === "string"; }).map(isoTs));
      (j.events || []).forEach(function (ev) { var pe = parseEspnEvent(ev, self.ut); evs[pe.id] = pe; });
    }, function () {});
  })).then(function () {
    if (!Object.keys(evs).length) throw new Error("HTTP 403 (LiveScore gesperrt, ESPN ohne Daten)");
    return rawJSON(ESPN_TAB + "/" + code + "/standings").then(function (t) {
      var rows = [];
      (t.children || [t]).forEach(function (ch) {
        ((ch.standings || {}).entries || []).forEach(function (en) {
          var st = {}; (en.stats || []).forEach(function (x) { st[x.name] = x.value; });
          var tm = en.team || {};
          rows.push({pos: +st.rank || 0, tm: espnName(tm.displayName), short: tm.shortDisplayName || "", p: +st.gamesPlayed || 0, w: +st.wins || 0, d: +st.ties || 0,
            l: +st.losses || 0, gf: +st.pointsFor || 0, ga: +st.pointsAgainst || 0, pts: +st.points || 0, zone: espnZone(en.note)});
        });
      });
      return rows;
    }, function () { return self.tables.length ? self.tables[0][1] : []; });
  }).then(function (rows) {
    rows.sort(function (a, b) { return a.pos - b.pos; });
    var lst = Object.keys(evs).map(function (k) { return evs[k]; }).sort(function (a, b) { return a.ts - b.ts; });
    var cal = self.espnCal, expected = rows.length > 2 ? 2 * (rows.length - 1) : null, nowS = now / 1000;
    if (cal && self.espnOff === undefined) {
      if (expected && cal.length === expected) self.espnOff = 0;
      else {
        var win = lst.filter(function (e) { return Math.abs(e.ts - nowS) < 8 * 86400; }).map(function (e) { return Object.assign({}, e); });
        espnGuessRounds(win, rows);
        var cnt = {}, best = null;
        win.forEach(function (e) { var bi = espnCalIndex(cal, e.ts); if (bi !== null) { var o = e.round - (bi + 1); cnt[o] = (cnt[o] || 0) + 1; if (best === null || cnt[o] > cnt[best]) best = o; } });
        self.espnOff = best === null ? null : +best;
      }
    }
    if (cal && self.espnOff !== null && self.espnOff !== undefined) {
      lst.forEach(function (e) { var bi = espnCalIndex(cal, e.ts); e.round = bi !== null ? Math.max(1, bi + 1 + self.espnOff) : (e.round || 1); });
    } else espnGuessRounds(lst, rows);
    self.rnames = {};
    self.setEvents(lst, true);
    if (cal && self.espnOff !== null && self.espnOff !== undefined) self.maxRound = Math.max(self.maxRound || 0, cal.length + self.espnOff);
    else if (expected) self.maxRound = Math.max(self.maxRound || 0, expected);
    self.tables = rows.length ? [["", rows]] : [];
    self.source = "espn"; if (full) self.tEspnFull = now;
    self.tLoad = Date.now() / 1000; self.error = null;
  });
};
function espnCalRounds(days) {
  var out = [];
  days.filter(function (x) { return x; }).sort(function (a, b) { return a - b; }).forEach(function (t) {
    var b = out[out.length - 1];
    if (b && t === b[b.length - 1]) return;
    if (b && t - b[b.length - 1] <= 30 * 3600) b.push(t); else out.push([t]);
  });
  return out;
}
function espnCalIndex(cal, ts) {
  var day = new Date((ts - 7 * 3600) * 1000).toISOString().slice(0, 10);
  for (var i = 0; i < cal.length; i++) for (var j = 0; j < cal[i].length; j++) if (new Date(cal[i][j] * 1000).toISOString().slice(0, 10) === day) return i;
  return null;
}
function espnGuessRounds(lst, rows) {
  var buckets = [], last = null;
  lst.slice().sort(function (a, b) { return a.ts - b.ts; }).forEach(function (e) { if (last === null || e.ts - last > 60 * 3600) buckets.push([]); buckets[buckets.length - 1].push(e); last = e.ts; });
  var played = Math.max.apply(null, [0].concat(rows.map(function (r) { return r.p; }))), idx = 0;
  buckets.forEach(function (b, i) { if (b.some(function (e) { return e.state === "post"; })) idx = i; });
  var base = (played || (idx + 1)) - idx;
  buckets.forEach(function (b, i) { b.forEach(function (e) { e.round = Math.max(1, base + i); }); });
}
League.prototype.loadRoundEspn = function (n) {
  var self = this, cal = self.espnCal, off = self.espnOff;
  if (!cal || off === null || off === undefined || n - 1 - off < 0 || n - 1 - off >= cal.length) { self.rcache[n] = []; return Promise.resolve(n); }
  var got = {};
  return Promise.all(cal[n - 1 - off].map(function (t) {
    var key = new Date(t * 1000).toISOString().slice(0, 10).replace(/-/g, "");
    return rawJSON(ESPN + "/" + ESPN_CODES[self.page] + "/scoreboard?dates=" + key).then(function (j) {
      (j.events || []).forEach(function (ev) { var pe = parseEspnEvent(ev, self.ut); pe.round = n; got[pe.id] = pe; });
    });
  })).then(function () {
    self.allEvents = self.allEvents.filter(function (e) { return !(e.id in got); }).concat(Object.keys(got).map(function (k) { return got[k]; })).sort(function (a, b) { return a.ts - b.ts; });
    self.rcache[n] = true;
    return n;
  });
};
League.prototype.load = function () {
  var self = this;
  if (self.ccd === "fupa") return self.loadFupa();
  var p = self.cup ? self.loadCup() : self.loadLs();
  return p.then(function (r) { self.source = "ls"; return r; }, function (e) {
    var err = String(e && e.message || e);
    if (self.c === "nlg" || /404|410/.test(err)) throw e;
    var chain = [];
    if (ESPN_CODES[self.page]) chain.push(["espn", function () { return self.loadEspn(); }]);
    if (FUPA_SLUGS[self.page]) chain.push(["fupa", function () { return self.loadFupa(FUPA_SLUGS[self.page]); }]);
    if (TFF_PAGES[self.page]) chain.push(["tff", function () { return self.loadTff(); }]);
    var tryNext = function (i) {
      if (i >= chain.length) throw e;
      return chain[i][1]().then(function (r) { self.source = chain[i][0]; return r; }, function () { return tryNext(i + 1); });
    };
    return tryNext(0);
  });
};
League.prototype.loadLs = function () {
  var self = this;
  return getJSON(BASE + "/stage/soccer/" + self.ccd + "/" + self.scd + "/0?MD=1").then(function (d) {
    var st = (d.Stages || [])[0];
    if (!st) throw new Error("404 keine Daten");
    var evs = (st.Events || []).map(function (e) { return parseEvent(e, self.ut); });
    self.setEvents(evs);
    self.tables = parseTable(st);
    if (self.scd === "league-stage") self.tables.forEach(function (t) {
      if (t[1].every(function (r) { return !r.zone; })) t[1].forEach(function (r) { r.zone = r.pos <= 8 ? "g" : (r.pos <= 24 ? "c" : null); });
    });
    self.tLoad = Date.now() / 1000; self.error = null;
  });
};
League.prototype.matchesStage = function (ccd, scd) { return this.cup && ccd === this.country && String(scd || "").indexOf(this.prefix) === 0 && String(scd).indexOf("women") < 0; };
League.prototype.roundLabel = function (n, lang) { var k = this.rnames[n]; return k ? roundText(k, lang) : null; };
var ROUND_NAMES = [[/^round of 16/, ["Achtelfinale", "Son 16 Turu"]], [/^round of 32/, ["Sechzehntelfinale", "Son 32 Turu"]], [/^round (\d+)/, ["%s. Runde", "%s. Tur"]],
  [/^1st round/, ["1. Runde", "1. Tur"]], [/^quarter/, ["Viertelfinale", "Çeyrek Final"]], [/^semi/, ["Halbfinale", "Yarı Final"]], [/^final stage/, ["Endrunde", "Final Aşaması"]],
  [/^final/, ["Finale", "Final"]], [/^qualification/, ["Qualifikation", "Ön Eleme"]], [/^group/, ["Gruppenphase", "Grup Aşaması"]], [/^play/, ["Play-offs", "Play-off"]]];
function roundText(key, lang) {
  var k = key.toLowerCase().trim(), sub = "";
  if (k.indexOf("|") >= 0) { sub = k.split("|")[1]; k = k.split("|")[0]; }
  for (var i = 0; i < ROUND_NAMES.length; i++) {
    var m = ROUND_NAMES[i][0].exec(k);
    if (m) { var t = ROUND_NAMES[i][1][lang === "tr" ? 1 : 0]; if (t.indexOf("%s") >= 0) t = t.replace("%s", m[1]); if (sub) t += " · " + (lang === "tr" ? sub + ". Tur" : sub + ". Runde"); return t; }
  }
  return key;
}
League.prototype.loadCup = function () {
  var self = this, start = seasonStart();
  return getJSON(BASE + "/category/soccer/" + self.country + "/0?MD=1").then(function (cat) {
    var cands = (cat.Stages || []).filter(function (st) { return self.matchesStage(st.Ccd, st.Scd) && (st.Events || []).some(function (e) { return esdTs(e.Esd) >= start; }); });
    return Promise.all(cands.map(function (c) {
      return getJSON(BASE + "/stage/soccer/" + self.country + "/" + c.Scd + "/0?MD=1").then(function (d) { return (d.Stages || [c])[0]; }, function () { return c; });
    }));
  }).then(function (stages) {
    var groups = {}, tables = [];
    stages.forEach(function (st) {
      var evs = (st.Events || []).map(function (e) { return parseEvent(e, self.ut); }).filter(function (e) { return e.ts >= start; });
      if (!evs.length) return;
      var raw = String(st.Snm || "").replace(/;/g, ":"), name = raw.split(":").pop().trim() || raw;
      if (/^group/i.test(name)) { parseTable(st).forEach(function (tb) { tables.push(["Gruppe " + name.split(" ").pop(), tb[1]]); }); name = "Group"; }
      var nums = {}; evs.forEach(function (e) { if (e.round !== null) nums[e.round] = 1; });
      evs.forEach(function (e) {
        var k = name;
        if (/^qualification/i.test(name) && Object.keys(nums).length > 1 && e.round !== null) k = name + "|" + e.round;
        (groups[k] = groups[k] || []).push(e);
      });
    });
    var order = Object.keys(groups).sort(function (a, b) {
      return Math.min.apply(null, groups[a].map(function (e) { return e.ts; })) - Math.min.apply(null, groups[b].map(function (e) { return e.ts; }));
    });
    var all = []; self.rnames = {};
    order.forEach(function (k, i) { self.rnames[i + 1] = k; groups[k].forEach(function (e) { e.round = i + 1; all.push(e); }); });
    self.setEvents(all, true);
    self.tables = tables; self.tLoad = Date.now() / 1000; self.error = null;
  });
};
League.prototype.setEvents = function (evs, noInfer) {
  var self = this;
    evs.sort(function (x, y) { return x.ts - y.ts; });
    if (!noInfer) inferRounds(evs);
    self.allEvents = evs;
    var rounds = []; evs.forEach(function (e) { if (e.round !== null && rounds.indexOf(e.round) < 0) rounds.push(e.round); });
    rounds.sort(function (x, y) { return x - y; });
    self.maxRound = rounds.length ? rounds[rounds.length - 1] : null;
    var cur = null;
    for (var i = 0; i < evs.length; i++) if (evs[i].round !== null && ["pre", "in", "ht"].indexOf(evs[i].state) >= 0) { cur = evs[i].round; break; }
    if (cur === null && rounds.length) cur = rounds[rounds.length - 1];
    self.round = cur;
    self.events = cur ? self.eventsOfRound(cur) : evs.slice(-10);
    self.prevRound = cur && cur > 1 ? cur - 1 : null;
    self.prevEvents = (self.prevRound ? self.eventsOfRound(self.prevRound) : []) || [];
};

/* Amateurligen von FuPa (Verbandsliga Württemberg mit dem 1. Göppinger SV) */
var FUPA = "https://api.fupa.net/v1";
function isoTs(v) { var t = Date.parse(v || ""); return isNaN(t) ? 0 : t / 1000; }
function fupaSeason() { var d = new Date(), y = d.getMonth() >= 6 ? d.getFullYear() : d.getFullYear() - 1; return y + "-" + ("0" + ((y + 1) % 100)).slice(-2); }
function fupaName(t) { var n = (t || {}).name || {}; return [String(n.full || n.middle || "?").trim(), String(n.short || "").trim()]; }
function ymd(d) { return d.getFullYear() + "-" + ("0" + (d.getMonth() + 1)).slice(-2) + "-" + ("0" + d.getDate()).slice(-2); }
function parseFupaMatch(m, key) {
  var sec = String(m.section || "").toUpperCase(), hs = m.homeGoal, as = m.awayGoal, state, minute = "";
  if (hs === undefined) hs = null; if (as === undefined) as = null;
  if (sec === "PRE") { state = "pre"; hs = as = null; }
  else if (sec === "POST") state = hs !== null ? "post" : "pp";
  else { state = "in"; minute = "live"; }
  var h = fupaName(m.homeTeam), a = fupaName(m.awayTeam);
  return {id: "fupa" + m.id, h: h[0], hsn: h[1], a: a[0], asn: a[1], hs: hs, as: as, state: state, min: minute,
    ts: isoTs(m.kickoff), round: (m.round || {}).number || null, group: "", ut: key, goal_at: 0, goal_by: null};
}
League.prototype.loadFupa = function (slug) {
  slug = slug || this.scd;
  var self = this, season = fupaSeason(), base = FUPA + "/competitions/" + slug + "/seasons/" + season + "/matches", now = Date.now();
  var p;
  if (!self.allEvents.length || now - (self.tFull || 0) > 3 * 3600 * 1000 || (self.source && self.source !== "fupa")) {
    var raw = [];
    var page = function (n) {
      return getJSON(base + "?limit=100&page=" + n).then(function (part) {
        part = part || []; raw = raw.concat(part);
        if (part.length >= 100 && n < 5) return page(n + 1);
      });
    };
    p = page(1).then(function () { self.tFull = now; return raw.map(function (m) { return parseFupaMatch(m, self.ut); }); });
  } else {
    var f = ymd(new Date(now - 4 * 86400000)), t = ymd(new Date(now + 8 * 86400000));
    p = getJSON(base + "?from=" + f + "&to=" + t).then(function (list) {
      var fresh = {}; (list || []).forEach(function (m) { var e = parseFupaMatch(m, self.ut); fresh[e.id] = e; });
      var out = self.allEvents.map(function (e) { var n = fresh[e.id]; delete fresh[e.id]; return n || e; });
      for (var k in fresh) out.push(fresh[k]);
      return out;
    });
  }
  return p.then(function (evs) {
    self.setEvents(evs);
    return getJSON(FUPA + "/standings?competition=" + slug + "&season=" + season);
  }).then(function (d) {
    var marks = {up1: "g", up2: "c", down1: "y", down2: "r"};
    var rows = ((d || {}).standings || []).map(function (r) {
      var nm = fupaName(r.team), mk = r.mark || "", z = marks[mk] || (mk.indexOf("up") === 0 ? "c" : (mk.indexOf("down") === 0 ? "r" : null));
      return {pos: r.rank || 0, tm: nm[0], short: nm[1], p: r.matches || 0, w: r.wins || 0, d: r.draws || 0, l: r.defeats || 0,
        gf: r.ownGoals || 0, ga: r.againstGoals || 0, pts: r.points || 0, zone: z};
    });
    self.tables = rows.length ? [["", rows]] : [];
    self.rnames = {};
    self.tLoad = Date.now() / 1000; self.error = null;
  });
};
/* Ersatzquelle TFF (türkische Ligen): Tabelle und Spielplan von tff.org */
var TFF_PAGES = {300: [198, null], 310: [142, null], 320: [976, "Beyaz"], 322: [976, "Kırmızı"], 305: [598, "cup"]};
var TR_MONTHS = {"Ocak": 1, "Şubat": 2, "Mart": 3, "Nisan": 4, "Mayıs": 5, "Haziran": 6, "Temmuz": 7, "Ağustos": 8, "Eylül": 9, "Ekim": 10, "Kasım": 11, "Aralık": 12};
function tffRoundKey(name) {
  var n = String(name || "").replace("Maçları", "").trim(), m = /(\d+)\s*\.?\s*Tur/.exec(n);
  if (m) return "Round " + m[1];
  var low = n.toLowerCase(), map = [["son 16", "Round of 16"], ["son 32", "Round of 32"], ["çeyrek", "Quarter-finals"], ["yarı", "Semi-finals"],
    ["final", "Final"], ["grup", "Group"], ["eleme", "Qualification"], ["play", "Play-offs"]];
  for (var i = 0; i < map.length; i++) if (low.indexOf(map[i][0]) >= 0) return map[i][1];
  return n || "Round";
}
function parseTffCup(html, key) {
  var rows = {}, order = [], m, rx = /kupaMaclari_ctl(\d+)_(lblGrup|lblTarih|lblTakim1|lblSkor|lblTakim2)"[^>]*>([^<]*)</g, evs = [], cur = "";
  while ((m = rx.exec(html))) { if (!rows[m[1]]) { rows[m[1]] = {}; order.push(m[1]); } rows[m[1]][m[2]] = m[3].trim(); }
  order.forEach(function (n) {
    var r = rows[n]; if (r.lblGrup) cur = r.lblGrup; if (!r.lblTakim1) return;
    var ts = 0, dm = /(\d+)\s+(\S+)\s+(\d{4})\s*(\d+)?:?(\d+)?/.exec(r.lblTarih || "");
    if (dm && TR_MONTHS[dm[2]]) ts = Date.UTC(+dm[3], TR_MONTHS[dm[2]] - 1, +dm[1], +(dm[4] || 0), +(dm[5] || 0)) / 1000 - 3 * 3600;
    var sc = /^\s*(\d+)\s*-\s*(\d+)/.exec(r.lblSkor || "");
    evs.push({id: "tffk" + n + r.lblTakim1.slice(0, 6), h: tffName(r.lblTakim1), hsn: "", a: tffName(r.lblTakim2 || "?"), asn: "",
      hs: sc ? +sc[1] : null, as: sc ? +sc[2] : null, state: sc ? "post" : "pre", min: "", ts: ts, nodate: !ts, round: null, rkey: tffRoundKey(cur),
      group: "", ut: key, goal_at: 0, goal_by: null});
  });
  return evs;
}
var FUPA_SLUGS = {200: "bundesliga", 210: "2-bundesliga", 220: "3-liga", 230: "regionalliga-suedwest", 240: "regionalliga-west",
  250: "regionalliga-bayern", 260: "regionalliga-nord", 270: "regionalliga-nordost", 280: "oberliga-baden-wuerttemberg"};
function trTitle(w) {
  return w.split(/\s+/).map(function (word) {
    if (/^\d+$/.test(word) || ["FK", "SK", "SMS", "GMG", "AŞ", "BB", "TFF"].indexOf(word) >= 0) return word;
    return word.charAt(0) + word.slice(1).split("").map(function (c) { return c === "I" ? "ı" : (c === "İ" ? "i" : c.toLowerCase()); }).join("");
  }).join(" ");
}
function tffName(n) {
  n = String(n || "").replace(/\s+/g, " ").trim();
  n = n.replace(" FUTBOL KULÜBÜ", " FK").replace(" FUTBOL SK", " SK").replace(" FUTBOL A.Ş.", "");
  n = n.replace(/\s+(A\.Ş\.|SPOR KULÜBÜ|KULÜBÜ)$/, "");
  return trTitle(n);
}
function parseTff(html, key, group) {
  var rows = [], m, rx = /lnkTakim"[^>]*>(\d+)\.([^<]+)<\/a>([\s\S]*?)<\/tr>/g;
  while ((m = rx.exec(html))) {
    var nums = (m[3].match(/<span[^>]*>(-?\d+)<\/span>/g) || []).map(function (x) { return +x.replace(/<[^>]+>/g, ""); });
    if (nums.length >= 8) rows.push({pos: +m[1], tm: tffName(m[2]), short: "", p: nums[0], w: nums[1], d: nums[2], l: nums[3], gf: nums[4], ga: nums[5], pts: nums[7], zone: null});
  }
  var dates = {}, rd = /lblTarih">([\d.]+)<\/span>(?:&nbsp;|\s)*<span[^>]*lbl?Saat">([\d:]*)<\/span>[\s\S]*?macId=(\d+)/g;
  while ((m = rd.exec(html))) {
    var dmy = m[1].split("."), hm = (m[2] || "0:0").split(":");
    dates[m[3]] = Date.UTC(+dmy[2], +dmy[1] - 1, +dmy[0], +hm[0] || 0, +hm[1] || 0) / 1000 - 3 * 3600;
  }
  var start = html.indexOf("Fikstür Listesi"), end = html.length;
  if (group) { var g = html.indexOf("<br/>" + group + " Grup<br/>"); if (g >= 0) start = g; var nx = html.indexOf("Panel1", start + 200); if (nx > 0) end = nx; }
  var sec = start >= 0 ? html.slice(start, end) : "", evs = [], weeks = [], rw = /(\d+)\.Hafta<\/td>/g;
  while ((m = rw.exec(sec))) weeks.push([+m[1], rw.lastIndex]);
  weeks.forEach(function (w, i) {
    var blk = sec.slice(w[1], i + 1 < weeks.length ? weeks[i + 1][1] : sec.length), r2 = /kulupId=\d+">([^<]+)<\/a><\/td>\s*<td[^>]*><a href="[^"]*macId=(\d+)">([^<]*)<\/a><\/td>\s*<td[^>]*><a href="[^"]*kulupId=\d+">([^<]+)<\/a>/g, x;
    while ((x = r2.exec(blk))) {
      var sc = /^\s*(\d+)\s*-\s*(\d+)\s*$/.exec(x[3]), ts = dates[x[2]] || 0;
      evs.push({id: "tff" + x[2], h: tffName(x[1]), hsn: "", a: tffName(x[4]), asn: "", hs: sc ? +sc[1] : null, as: sc ? +sc[2] : null,
        state: sc ? "post" : "pre", min: "", ts: ts, nodate: !ts, round: w[0], group: "", ut: key, goal_at: 0, goal_by: null});
    }
  });
  return [evs, rows];
}
League.prototype.loadTff = function () {
  var self = this, cfgT = TFF_PAGES[self.page], url = "https://www.tff.org/Default.aspx?pageID=" + cfgT[0];
  if (cfgT[1] === "cup") return httpGet(url).then(function (html) {
    var evs = parseTffCup(html, self.ut);
    if (!evs.length) throw new Error("TFF-Pokal: keine Daten");
    var keys = [];
    evs.slice().sort(function (a, b) { return (a.ts || 9e9) - (b.ts || 9e9); }).forEach(function (e) { if (keys.indexOf(e.rkey) < 0) keys.push(e.rkey); });
    self.rnames = {}; keys.forEach(function (k, i) { self.rnames[i + 1] = k; });
    var now = Date.now() / 1000;
    evs.forEach(function (e) { e.round = keys.indexOf(e.rkey) + 1; if (e.state === "pre" && e.ts && e.ts < now - 3 * 3600) e.state = "pp"; });
    self.setEvents(evs, true); self.tables = []; self.tLoad = Date.now() / 1000; self.error = null;
  });
  return httpGet(url).then(function (html) {
    if (!cfgT[1]) return html;
    var m = new RegExp('grupID=(\\d+)#hmac"[^>]*>\\s*' + cfgT[1]).exec(html);
    return m ? httpGet(url + "&grupID=" + m[1]) : html;
  }).then(function (html) {
    var r = parseTff(html, self.ut, cfgT[1]), evs = r[0], rows = r[1];
    if (!evs.length && !rows.length) throw new Error("TFF: keine Daten");
    var am = /haftaNoActive><a href=[^>]*hafta=(\d+)/.exec(html), active = am ? +am[1] : null;
    if (!active) { var dated = evs.filter(function (e) { return e.ts; }).map(function (e) { return e.round; }); active = dated.length ? Math.min.apply(null, dated) : null; }
    if (active) evs.forEach(function (e) { if (e.state === "pre" && e.round < active) e.state = "pp"; });
    var cur = null; evs.forEach(function (e) { if (e.state === "pre") cur = cur === null ? e.round : Math.min(cur, e.round); });
    var now = Date.now() / 1000;
    evs.forEach(function (e) { if (!e.ts) e.ts = now + (e.round - (cur || e.round)) * 7 * 86400 - (e.state === "post" ? 3600 : 0); });
    self.rnames = {};
    self.setEvents(evs, true);
    self.tables = rows.length ? [["", rows]] : [];
    self.tLoad = Date.now() / 1000; self.error = null;
  });
};
League.prototype.loadRound = function (n) { return this.source === "espn" ? this.loadRoundEspn(n) : Promise.resolve(n); };

var STORE = {leagues: LEAGUES_DEF.map(function (d) { return new League(d); }), live: {}, tLive: 0, liveError: null, goals: [], loadingLive: false};
STORE.byUt = {}; STORE.leagues.forEach(function (l) { STORE.byUt[l.ut] = l; });
STORE.allViews = function () { var out = []; this.leagues.forEach(function (lg) { lg.views().forEach(function (v) { out.push([lg, v[0], v[1]]); }); }); return out; };
STORE.view = function (p) { var v = this.allViews(); for (var i = 0; i < v.length; i++) { if (p === v[i][1]) return [v[i][0], v[i][2], "res"]; if (p === v[i][1] + 1) return [v[i][0], v[i][2], "tab"]; } return null; };
STORE.nlGroups = function () { return this.leagues.filter(function (l) { return l.c === "nlg"; }); };
STORE.setNat = function (groups) {
  this.leagues = this.leagues.filter(function (l) { return l.c !== "nlg"; }).concat(groups);
  var by = {}; this.leagues.forEach(function (l) { by[l.ut] = l; }); this.byUt = by;
};
/* Europäische Länderspiele (Seite 470): automatisch EM-Quali, WM-Quali (UEFA) oder Nations League */
var NAT_TITLES = {euro: ["EM-Qualifikation", "EURO Elemeleri"], wc: ["WM-Qualifikation", "Dünya Kupası Elemeleri"], nl: ["Nations League", "Uluslar Ligi"]};
function natStageName(snm, lang) {
  var out = [];
  String(snm || "").replace(/;/g, ":").split(":").map(function (x) { return x.trim(); }).filter(function (x) { return x; }).forEach(function (p) {
    var m = /^league ([a-d])$/i.exec(p);
    if (m) { out.push(lang === "tr" ? m[1].toUpperCase() + " Ligi" : "Liga " + m[1].toUpperCase()); return; }
    m = /^group ([a-z0-9]+)/i.exec(p);
    if (m) { var g = m[1].toUpperCase(); out.push(lang === "tr" ? (/^\d+$/.test(g) ? g + ". Grup" : "Grup " + g) : "Gruppe " + g); return; }
    if (/play-?off/i.test(p)) out.push("Play-off");
    else if (/final/i.test(p)) out.push(lang === "tr" ? "Final" : "Finale");
    else if (/semi/i.test(p)) out.push(lang === "tr" ? "Yarı Final" : "Halbfinale");
    else if (/quarter/i.test(p)) out.push(lang === "tr" ? "Çeyrek Final" : "Viertelfinale");
  });
  return out.join(" · ") || snm;
}
var NAT = {kind: "nl", groups: [], tDisc: 0, tLoad: 0, loading: false, error: null,
  title: function (lang) { return NAT_TITLES[this.kind][lang === "tr" ? 1 : 0]; }};
function natCandidates() {
  var y = new Date().getFullYear(), out = [];
  for (var yy = y; yy < y + 6; yy++) if (yy % 4 === 0) out.push(["euro", "euro-" + yy + "-qualification"]);
  return out.concat([["wc", "world-cup-qualification"], ["nl", "uefa-nations-league"]]);
}
function natDiscover() {
  var now = Date.now() / 1000, D = 86400;
  return Promise.all(natCandidates().map(function (c) {
    return getJSON(BASE + "/category/soccer/" + c[1] + "/0?MD=1").then(function (cat) {
      var stages = [];
      (cat.Stages || []).forEach(function (st) {
        var scd = st.Scd || ""; if (c[0] === "wc" && scd.indexOf("uefa") < 0) return;
        var ts = (st.Events || []).map(function (e) { return esdTs(e.Esd); }).filter(function (t) { return Math.abs(t - now) < 270 * D; });
        if (ts.length) stages.push([scd, st.Snm || scd, Math.min.apply(null, ts), Math.max.apply(null, ts)]);
      });
      if (!stages.length) return null;
      var near = Math.min.apply(null, stages.map(function (x) { return Math.min(Math.abs(x[2] - now), Math.abs(x[3] - now)); }));
      var active = stages.some(function (x) { return (x[2] - 21 * D <= now && now <= x[3] + 120 * D) || (now <= x[2] && x[2] <= now + 120 * D); });
      return [active ? 0 : 1, c[0], c[1], stages, near];
    }, function () { return null; });
  })).then(function (found) {
    found = found.filter(function (f) { return f; });
    if (!found.length) return false;
    var prio = {euro: 0, wc: 1, nl: 2};
    found.sort(function (a, b) { return a[0] - b[0] || (a[0] === 0 ? prio[a[1]] - prio[b[1]] : a[4] - b[4]); });
    var f = found[0];
    f[3].sort(function (a, b) { var ga = a[0].indexOf("group") < 0 ? 1 : 0, gb = b[0].indexOf("group") < 0 ? 1 : 0; return ga - gb || (a[0] < b[0] ? -1 : 1); });
    NAT.kind = f[1];
    NAT.groups = f[3].map(function (x) {
      var lg = new League([470, "nlg", f[2], x[0], "", "", ""]); lg.snm = x[1]; lg.kind = f[1];
      lg.name = function (lang) { return NAT_TITLES[this.kind][lang === "tr" ? 1 : 0] + " · " + natStageName(this.snm, lang); };
      return lg;
    });
    NAT.tDisc = Date.now() / 1000;
    STORE.setNat(NAT.groups);
    return true;
  });
}
/* Ersatzquelle UEFA, wenn LiveScore gesperrt ist */
var UEFA_MATCH = "https://match.uefa.com/v5/matches", UEFA_STAND = "https://standings.uefa.com/v1/standings";
var UEFA_STATE = {FINISHED: "post", UPCOMING: "pre", LIVE: "in", POSTPONED: "pp", CANCELLED: "pp", ABANDONED: "pp"};
function uefaDay(ts) { return new Date(ts * 1000).toISOString().slice(0, 10); }
function uefaMatches(cid, season, q) { return rawJSON(UEFA_MATCH + "?competitionId=" + cid + "&seasonYear=" + season + "&" + q).then(function (j) { return j || []; }); }
function uefaStageName(grp) {
  var gn = ((grp || {}).metaData || {}).groupName || "", lg = (((grp || {}).league || {}).metaData || {}).leagueName || "";
  var m = /^group ([a-d])(\d+)$/i.exec(gn);
  if (lg && m) return lg + ": Group " + m[2];
  return lg ? lg + ": " + gn : gn;
}
function parseUefaMatch(m) {
  var state = UEFA_STATE[m.status] || "pre";
  if (state === "in" && /HALF_TIME/i.test(m.phase || "")) state = "ht";
  var sc = (m.score || {}).total || {}, hs = sc.home, as = sc.away;
  if (state === "pre" || hs === undefined) { hs = null; as = null; }
  var ht = m.homeTeam || {}, at = m.awayTeam || {};
  return {id: "uefa" + m.id, h: fixName(ht.internationalName || "?"), hs_name: "", a: fixName(at.internationalName || "?"), as_name: "",
    hs: state === "pre" ? null : hs, as: state === "pre" ? null : as, state: state, min: state === "in" ? String(((m.minute || {}).normal) || "") : "",
    ts: isoTs((m.kickOffTime || {}).dateTime), round: toInt((m.matchday || {}).sequenceNumber), group: "", ut: "", gid: String((m.group || {}).id || "")};
}
function natCandidatesUefa() {
  var y = new Date().getFullYear(), out = [];
  for (var yy = y; yy < y + 6; yy++) if (yy % 4 === 0) out.push(["euro", 3, yy]);
  for (yy = y; yy < y + 5; yy++) if (yy % 4 === 2) out.push(["wc", 17, yy]);
  return out.concat([["nl", 2014, y + 1], ["nl", 2014, y]]);
}
function natDiscoverUefa() {
  var now = Date.now() / 1000, D = 86400, cands = natCandidatesUefa();
  function tryAt(i) {
    if (i >= cands.length) return Promise.resolve(null);
    var c = cands[i];
    return uefaMatches(c[1], c[2], "fromDate=" + uefaDay(now - 21 * D) + "&toDate=" + uefaDay(now + 120 * D) + "&limit=1&offset=0&order=ASC").then(function (ms) {
      return ms.some(function (m) { return (m.group || {}).id; }) ? c : tryAt(i + 1);
    }, function () { return tryAt(i + 1); });
  }
  return tryAt(0).then(function (c) {
    if (!c) return false;
    return rawJSON(UEFA_STAND + "?competitionId=" + c[1] + "&seasonYear=" + c[2]).then(function (st) {
      var groups = [];
      (st || []).forEach(function (g) {
        var grp = g.group || {};
        if (!grp.id || !(g.items || []).length) return;
        var lg = new League([470, "nlg", "uefa-" + c[1], "g" + grp.id, "", "", ""]);
        lg.snm = uefaStageName(grp); lg.kind = c[0]; lg.source = "uefa"; lg.gid = String(grp.id);
        lg.order = [toInt((grp.league || {}).order) || 0, toInt(grp.order) || 0];
        lg.name = function (lang) { return NAT_TITLES[this.kind][lang === "tr" ? 1 : 0] + " · " + natStageName(this.snm, lang); };
        lg.load = function () { return natLoadUefa(); };
        groups.push(lg);
      });
      if (!groups.length) return false;
      groups.sort(function (a, b) { return a.order[0] - b.order[0] || a.order[1] - b.order[1]; });
      NAT.kind = c[0]; NAT.uefa = [c[1], c[2]]; NAT.groups = groups; NAT.tDisc = now; NAT.tUefaFull = 0; NAT.uefaStand = st;
      STORE.setNat(NAT.groups);
      return true;
    });
  });
}
function natLoadUefa() {
  var cid = NAT.uefa[0], season = NAT.uefa[1], now = Date.now() / 1000, full = now - (NAT.tUefaFull || 0) > 1800;
  var byg = {}; NAT.groups.forEach(function (lg) { byg[lg.gid] = lg; });
  var p = full ? Promise.all([
      uefaMatches(cid, season, "toDate=" + uefaDay(now) + "&limit=80&offset=0&order=DESC"),
      uefaMatches(cid, season, "fromDate=" + uefaDay(now + 86400) + "&limit=80&offset=0&order=ASC"),
      now - NAT.tDisc > 60 ? rawJSON(UEFA_STAND + "?competitionId=" + cid + "&seasonYear=" + season).then(null, function () { return null; }) : Promise.resolve(null)
    ]).then(function (r) { if (r[2]) NAT.uefaStand = r[2]; return r[0].concat(r[1]); })
    : uefaMatches(cid, season, "fromDate=" + uefaDay(now) + "&toDate=" + uefaDay(now) + "&limit=80&offset=0&order=ASC");
  return p.then(function (ms) {
    var per = {}, tabs = {};
    ms.forEach(function (m) { var e = parseUefaMatch(m); if (byg[e.gid]) { e.ut = byg[e.gid].ut; (per[e.gid] = per[e.gid] || {})[e.id] = e; } });
    (NAT.uefaStand || []).forEach(function (g) {
      var gid = String((g.group || {}).id || "");
      tabs[gid] = (g.items || []).map(function (it) {
        var tm = it.team || {};
        return {pos: toInt(it.rank) || 0, tm: fixName(tm.internationalName || "?"), short: "", p: toInt(it.played) || 0, w: toInt(it.won) || 0,
          d: toInt(it.drawn) || 0, l: toInt(it.lost) || 0, gf: toInt(it.goalsFor) || 0, ga: toInt(it.goalsAgainst) || 0, pts: toInt(it.points) || 0, phr: null, zone: null};
      }).sort(function (a, b) { return a.pos - b.pos; });
    });
    Object.keys(byg).forEach(function (gid) {
      var lg = byg[gid], old = {};
      if (!full) lg.allEvents.forEach(function (e) { old[e.id] = e; });
      var nw = per[gid] || {}; Object.keys(nw).forEach(function (k) { old[k] = nw[k]; });
      lg.rnames = {};
      lg.setEvents(Object.keys(old).map(function (k) { return old[k]; }), true);
      if (tabs[gid] && tabs[gid].length) lg.tables = [["", tabs[gid]]];
      lg.tLoad = now; lg.error = null;
    });
    if (full) NAT.tUefaFull = now;
  });
}
function natLoad() {
  var p = (!NAT.groups.length || Date.now() / 1000 - NAT.tDisc > 6 * 3600)
    ? natDiscover().then(function (ok) { return ok || natDiscoverUefa(); }) : Promise.resolve(true);
  return p.then(function (ok) {
    if (!ok && !NAT.groups.length) throw new Error("Keine Länderspiele gefunden");
    if (NAT.groups.length && NAT.groups[0].source === "uefa") return natLoadUefa();
    return Promise.all(NAT.groups.map(function (lg) { return lg.load().then(function () {}, function (e) { lg.error = niceError(e); }); })).then(function () {
      if (NAT.groups.length && NAT.groups.every(function (lg) { return lg.error && !lg.allEvents.length; }))
        return natDiscoverUefa().then(function (ok2) { if (ok2) return natLoadUefa(); });
    });
  }).then(function () { NAT.tLoad = Date.now() / 1000; NAT.error = null; });
}
STORE.leagueByPage = function (p) { var v = this.view(p); return v ? v[0] : null; };
STORE.liveCount = function (lg) { var n = 0; for (var k in this.live) if (!lg || this.live[k].ut === lg.ut) n++; return n; };
STORE.liveCountView = function (lg) { return this.liveCount(lg); };
STORE.leagueOfStage = function (ccd, scd) {
  var lg = this.byUt[ccd + "/" + scd];
  if (!lg) for (var i = 0; i < this.leagues.length; i++) if (this.leagues[i].matchesStage(ccd, scd)) { lg = this.leagues[i]; break; }
  if (lg && lg.c !== "nlg" && !pageOn(lg.page)) return null;  // in den Einstellungen ausgeblendet
  return lg || null;
};
function espnDay(key, onlyLive) {
  return Promise.all(Object.keys(ESPN_CODES).map(function (pg) {
    var lg = STORE.leagueByPage(+pg); if (!lg || !pageOn(+pg)) return null;
    return rawJSON(ESPN + "/" + ESPN_CODES[pg] + "/scoreboard?dates=" + key).then(function (j) {
      var evs = (j.events || []).map(function (ev) { return parseEspnEvent(ev, lg.ut); });
      if (onlyLive) evs = evs.filter(function (e) { return e.state === "in" || e.state === "ht"; });
      return evs.length ? [lg, evs] : null;
    }, function () { return null; });
  })).then(function (a) { return a.filter(function (x) { return x; }); });
}
function fupaDay(key) {
  var day = key.slice(0, 4) + "-" + key.slice(4, 6) + "-" + key.slice(6, 8);
  return Promise.all(Object.keys(FUPA_SLUGS).filter(function (pg) { return !ESPN_CODES[pg]; }).map(function (pg) {
    var lg = STORE.leagueByPage(+pg); if (!lg || !pageOn(+pg)) return null;
    return rawJSON(FUPA + "/competitions/" + FUPA_SLUGS[pg] + "/seasons/" + fupaSeason() + "/matches?from=" + day + "&to=" + day).then(function (lst) {
      var evs = (lst || []).map(function (m) { return parseFupaMatch(m, lg.ut); });
      return evs.length ? [lg, evs] : null;
    }, function () { return null; });
  })).then(function (a) { return a.filter(function (x) { return x; }); });
}
STORE.fetchLive = function () {
  return STORE.fetchLiveLs().catch(function (e) {
    if (/404|410/.test(String(e && e.message))) throw e;
    var d = new Date(), key = d.getFullYear() + two(d.getMonth() + 1) + two(d.getDate());
    return espnDay(key, true).then(function (list) {
      var out = {};
      list.forEach(function (x) { x[1].forEach(function (pe) { out[pe.id] = pe; }); });
      STORE.leagues.forEach(function (lg) { lg.events.forEach(function (pe) { if ((pe.state === "in" || pe.state === "ht") && !out[pe.id]) out[pe.id] = pe; }); });
      return out;
    });
  });
};
STORE.fetchLiveLs = function () {
  return getJSON(BASE + "/live/soccer/0?MD=1").then(function (d) {
    var out = {};
    (d.Stages || []).forEach(function (st) {
      var lg = STORE.leagueOfStage(st.Ccd, st.Scd); if (!lg) return;
      (st.Events || []).forEach(function (e) { var pe = parseEvent(e, lg.ut); if (pe.state === "in" || pe.state === "ht") out[pe.id] = pe; });
    });
    return out;
  });
};
STORE.days = {};
STORE.fetchDay = function (key) {
  return getJSON(BASE + "/date/soccer/" + key + "/0?MD=1").catch(function (e) {
    if (/404|410/.test(String(e && e.message))) throw e;
    return Promise.all([espnDay(key, false), fupaDay(key)]).then(function (r) { return {Stages: [], __fb: r[0].concat(r[1])}; });
  }).then(function (d) {
    var by = {};
    (d.__fb || []).forEach(function (x) { by[x[0].page] = x[1]; });
    (d.Stages || []).forEach(function (st) {
      var lg = STORE.leagueOfStage(st.Ccd, st.Scd); if (!lg) return;
      (st.Events || []).forEach(function (e) { (by[lg.page] = by[lg.page] || []).push(parseEvent(e, lg.ut)); });
    });
    var t0 = new Date(+key.slice(0, 4), +key.slice(4, 6) - 1, +key.slice(6, 8)).getTime() / 1000;
    STORE.leagues.forEach(function (lg) {
      if ((lg.ccd === "fupa" || (lg.source === "tff" && !by[lg.page])) && lg.allEvents.length) {
        var ev = lg.allEvents.filter(function (e) { return e.ts >= t0 && e.ts < t0 + 86400 && !e.nodate; }); if (ev.length) by[lg.page] = ev;
      }
    });
    var out = [];
    STORE.leagues.forEach(function (lg) { if (by[lg.page]) out.push([lg, by[lg.page].sort(function (a, b) { return a.ts - b.ts || (a.h < b.h ? -1 : 1); })]); });
    STORE.days[key] = {t: Date.now() / 1000, list: out};
  });
};
/* Spieldetails: Tore und Platzverweise */
var INC_TYPES = {36: "goal", 37: "pen", 39: "og", 44: "yr", 45: "red"};
var DETAILS = {cache: {}, loading: {}};
function parseIncidents(d) {
  var out = [], incs = (d || {}).Incs || {};
  Object.keys(incs).sort(function (a, b) { return (+a) - (+b); }).forEach(function (per) {
    (incs[per] || []).forEach(function (it) {
      [it].concat(it.Incs || []).forEach(function (x) {
        var typ = INC_TYPES[x.IT]; if (!typ) return;
        var mn = toInt(x.Min) || 0, ex = toInt(x.MinEx) || 0;
        out.push({side: toInt(x.Nm) || 1, min: ex ? mn + "+" + ex : String(mn), sort: mn * 100 + ex, type: typ, name: x.Ln || x.Snm || x.Pn || "?"});
      });
    });
  });
  return out.sort(function (a, b) { return a.sort - b.sort; });
}
function detailsNeeded(e) {
  if (e.state === "pre" || String(e.id).indexOf("fupa") === 0 || DETAILS.loading[e.id]) return false;
  var c = DETAILS.cache[e.id]; if (!c) return true; if (c.final) return false;
  return Date.now() / 1000 - c.t > +cfg("refresh");
}
function loadDetails(e) {
  DETAILS.loading[e.id] = true;
  return getJSON(BASE + "/incidents/soccer/" + e.id + "?locale=en").then(function (d) {
    delete DETAILS.loading[e.id]; DETAILS.cache[e.id] = {t: Date.now() / 1000, list: parseIncidents(d), final: e.state === "post"};
  }, function () { delete DETAILS.loading[e.id]; DETAILS.cache[e.id] = {t: Date.now() / 1000, list: [], final: false}; });
}
/* Ranglisten */
function uefaSeasonYear() { var d = new Date(); return d.getMonth() >= 6 ? d.getFullYear() + 1 : d.getFullYear(); }
var RANKINGS = [{page: 480, kind: "fifa", rows: [], date: "", tLoad: 0, loading: false, error: null},
  {page: 482, kind: "uefa", rows: [], date: "", tLoad: 0, loading: false, error: null},
  {page: 484, kind: "club", rows: [], date: "", tLoad: 0, loading: false, error: null}];
function rankingByPage(p) { for (var i = 0; i < RANKINGS.length; i++) if (RANKINGS[i].page === p) return RANKINGS[i]; return null; }
function loadRanking(rk) {
  if (rk.kind === "fifa") {
    return httpGet("https://inside.fifa.com/fifa-world-ranking/men").then(function (html) {
      var m = /"id":"(FRS_Male_Football_\d+)"/.exec(html); if (!m) throw new Error("FIFA-Datum nicht gefunden");
      var md = /"lastUpdateDate":"(\d{4})-(\d\d)-(\d\d)/.exec(html); rk.date = md ? md[3] + "." + md[2] + "." + md[1] : "";
      return getJSON("https://api.fifa.com/api/v3/fifarankings/rankings/rankingsbyschedule?rankingScheduleId=" + m[1] + "&language=en");
    }).then(function (d) {
      rk.rows = (d.Results || []).map(function (r) {
        return {pos: r.Rank || 0, prev: r.PrevRank || 0, name: ((r.TeamName || [{}])[0] || {}).Description || "?", code: r.IdCountry || "", pts: +r.TotalPoints || 0};
      }).sort(function (a, b) { return a.pos - b.pos; });
      rk.tLoad = Date.now() / 1000; rk.error = null;
    });
  }
  var typ = rk.kind === "uefa" ? "MEN_ASSOCIATION" : "MEN_CLUB", sy = uefaSeasonYear();
  return getJSON("https://comp.uefa.com/v2/coefficients?coefficientRange=OVERALL&coefficientType=" + typ + "&language=EN&page=1&pagesize=" + (rk.kind === "uefa" ? 60 : 80) + "&seasonYear=" + sy).then(function (d) {
    var data = d.data || {}, lu = data.lastUpdateDate || "";
    rk.date = lu.length >= 10 ? lu.slice(8, 10) + "." + lu.slice(5, 7) + "." + lu.slice(0, 4) : "";
    rk.rows = (data.members || []).map(function (m) {
      var mem = m.member || {}, ov = m.overallRanking || {}, cur = (m.seasonRankings || []).filter(function (x) { return x.seasonYear === sy; })[0];
      return {pos: ov.position || 0, prev: 0, name: fixName((rk.kind === "club" ? mem.displayOfficialName : mem.displayName) || mem.countryName || "?"), code: mem.countryCode || "",
        pts: +(ov.totalValue || ov.totalPoints || 0), season: cur ? +(cur.totalValue || cur.totalPoints || 0) : null};
    }).sort(function (a, b) { return a.pos - b.pos; });
    rk.tLoad = Date.now() / 1000; rk.error = null;
  });
}
STORE.applyLive = function (nw) {
  var now = Date.now() / 1000, self = this, k;
  for (k in nw) {
    var e = nw[k], old = self.live[k];
    if (old) {
      var o = (old.hs || 0) + (old.as || 0), n = (e.hs || 0) + (e.as || 0);
      if (n > o) { e.goal_at = now; e.goal_by = (e.hs || 0) > (old.hs || 0) ? "h" : "a"; self.goals.push([now, e]); }
      else { e.goal_at = old.goal_at; e.goal_by = old.goal_by; }
    }
  }
  var ended = Object.keys(self.live).filter(function (id) { return !(id in nw); });
  self.live = nw; self.tLive = now;
  self.goals = self.goals.filter(function (g) { return now - g[0] < 60; });
  self.leagues.forEach(function (lg) {
    var changed = false;
    [lg.allEvents, lg.events].forEach(function (lst) {
      lst.forEach(function (ev, i) {
        if (nw[ev.id]) { var ne = {}; for (var q in nw[ev.id]) ne[q] = nw[ev.id][q]; ne.round = ev.round; lst[i] = ne; }
        else if (ended.indexOf(ev.id) >= 0) changed = true;
      });
    });
    if (changed) lg.tLoad = 0;
  });
};

/* Nachrichten */
var FEED_SETS = {tr: [
 {page: 600, name: "Manşet", source: "TRT Haber", url: "https://www.trthaber.com/manset_articles.rss"},
 {page: 610, name: "Güncel", source: "Anadolu Ajansı", url: "https://www.aa.com.tr/tr/rss/default?cat=guncel"},
 {page: 620, name: "Dünya", source: "Anadolu Ajansı", url: "https://www.aa.com.tr/tr/rss/default?cat=dunya"},
 {page: 630, name: "Ekonomi", source: "Anadolu Ajansı", url: "https://www.aa.com.tr/tr/rss/default?cat=ekonomi"},
 {page: 640, name: "Spor", source: "Anadolu Ajansı", url: "https://www.aa.com.tr/tr/rss/default?cat=spor"}
], de: [
 {page: 600, name: "Schlagzeilen", source: "tagesschau", url: "https://www.tagesschau.de/infoservices/alle-meldungen-100~rss2.xml"},
 {page: 610, name: "Inland", source: "tagesschau", url: "https://www.tagesschau.de/inland/index~rss2.xml"},
 {page: 620, name: "Welt", source: "tagesschau", url: "https://www.tagesschau.de/ausland/index~rss2.xml"},
 {page: 630, name: "Wirtschaft", source: "tagesschau", url: "https://www.tagesschau.de/wirtschaft/index~rss2.xml"},
 {page: 640, name: "Sport", source: "sportschau", url: "https://www.sportschau.de/index~rss2.xml"},
 {page: 650, name: "Fußball", source: "kicker", url: "https://newsfeed.kicker.de/news/aktuell"}
]};
[FEED_SETS.tr, FEED_SETS.de].forEach(function (s) { s.forEach(function (f) { f.items = []; f.tLoad = 0; f.loading = false; f.error = null; }); });
var FEEDS = [], NEWS_LANG = "tr";
function setNews(lang) { NEWS_LANG = lang === "de" ? "de" : "tr"; FEEDS.length = 0; FEED_SETS[NEWS_LANG].forEach(function (f) { FEEDS.push(f); }); }
setNews(cfg("news"));
function newsLabel() { return (NEWS_LANG === "de" ? "Nachrichten" : "Haberler") + " · " + FEEDS.slice(0, 3).map(function (f) { return f.name; }).join(", ") + " …"; }
function feedByPage(p) { for (var i = 0; i < FEEDS.length; i++) if (FEEDS[i].page === p) return FEEDS[i]; return null; }
function cleanText(s) {
  var d = document.createElement("div"); d.innerHTML = String(s || "").replace(/<[^>]+>/g, " ");
  return (d.textContent || "").replace(/\s+/g, " ").trim();
}
function parseFeed(text) {
  text = text.replace(/^﻿/, "");
  var doc = new DOMParser().parseFromString(text, "text/xml"), items = [];
  var nodes = doc.querySelectorAll("item, entry");
  if (!nodes.length) { // Notfall: abgeschnittener Feed
    var m, re = /<item>([\s\S]*?)<\/item>/g;
    while ((m = re.exec(text))) {
      var g = function (tag) { var r = new RegExp("<" + tag + "[^>]*>([\\s\\S]*?)</" + tag + ">").exec(m[1]); return r ? r[1].replace("<![CDATA[", "").replace("]]>", "") : ""; };
      items.push({title: cleanText(g("title")), desc: cleanText(g("description")), ts: Date.parse(g("pubDate")) / 1000 || 0});
    }
    return items;
  }
  Array.prototype.forEach.call(nodes, function (n) {
    var get = function (names) { for (var i = 0; i < names.length; i++) { var x = n.getElementsByTagName(names[i])[0]; if (x) return x.textContent; } return ""; };
    var t = cleanText(get(["title"]));
    if (t) items.push({title: t, desc: cleanText(get(["description", "summary"])), ts: (Date.parse(get(["pubDate", "published", "updated"])) / 1000) || 0});
  });
  return items;
}
function loadFeed(f) {
  return httpGet(f.url).then(function (t) {
    var it = parseFeed(t); it.sort(function (a, b) { return b.ts - a.ts; });
    f.items = it.slice(0, 60); f.tLoad = Date.now() / 1000; f.error = null;
  });
}

/* Wechselkurs */
var RATE = {value: null, date: "", src: "", tLoad: 0, loading: false};
function loadRate() {
  return getJSON("https://api.frankfurter.dev/v1/latest?from=EUR&to=TRY").then(function (d) {
    RATE.value = +d.rates.TRY; RATE.date = d.date || ""; RATE.src = "ezb";
  }, function () {
    return getJSON("https://open.er-api.com/v6/latest/EUR").then(function (d) {
      RATE.value = +d.rates.TRY; var dt = new Date((d.time_last_update_unix || Date.now() / 1000) * 1000);
      RATE.date = dt.getFullYear() + "-" + two(dt.getMonth() + 1) + "-" + two(dt.getDate()); RATE.src = "er";
    });
  }).then(function () { RATE.tLoad = Date.now() / 1000; });
}

/* ================= Darstellung ================= */
var PAL_TT = {w: "#f4f4f4", y: "#ffee33", c: "#33f0f0", g: "#33e36a", r: "#ff3d3d", m: "#ff4fd8", b: "#2643ff", d: "#8a8a8a", k: "#000000", fav: "#6a1260", sel: "#1d2fa8"};
var PAL_MOD = {w: "#eef2f7", y: "#ffd54a", c: "#7cc8ff", g: "#3ddc84", r: "#ff5a5f", m: "#e070ff", b: "#4c8dff", d: "#8c98aa", k: "#0b1016", fav: "#4b2c6e", sel: "#2f6bff",
  bg: "#0f151d", hdr: "#0a0f15", card: "#1b2533", zebra1: "#141c26", zebra2: "#111821"};
var COL = PAL_TT;
var PAGE_BG = {black: "#000000", navy: "#06103a"};
var COUNTRY_BG = {de: ["r", "w"], tr: ["g", "k"], eu: ["y", "k"], nat: ["c", "k"]};
var IDX_ROWS = 20;
var SRC_TAG = {espn: " · ESPN", tff: " · TFF", fupa: " · FuPa"};
function isFavFirst(name) { return isFav(name) && !/\b(II|U ?2\d|U ?19|U ?17)\b/.test(name || ""); }
function modern() { return cfg("style") === "modern"; }
function seg(col, width, text, fg, bg, al, blink) { return [col, width, text, fg || "w", bg || null, al || "l", !!blink]; }
function fit(t, w, short) { t = t || ""; if (t.length <= w) return t; if (short && short.length <= w) return short; return t.slice(0, w); }
function two(n) { return (n < 10 ? "0" : "") + n; }
function isFav(name) { var f = cfg("fav"); return !!f && (name || "").toLowerCase().indexOf(f.toLowerCase()) >= 0; }
function trUpper(s) { return s.replace(/i/g, "İ").replace(/ı/g, "I").toUpperCase(); }
function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return {"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;"}[c]; }); }

var S = {page: 100, sub: 0, entry: "", entryAt: 0, msg: "", msgUntil: 0, idx: [0, 1], setSel: 0, newsSel: 0, newsOpen: null, md: null, det: false, dayOff: 0,
  blinkOn: true, lastRot: Date.now(), openedAt: Date.now() / 1000};
var G = {rowH: 20, fs: 16, cw: 9.6, pw: 800, cols: 80};
var scr = document.getElementById("scr"), rateEl = document.getElementById("rate");
var rowEls = [];
for (var ri = 0; ri < 24; ri++) { var d = document.createElement("div"); d.className = "row"; scr.appendChild(d); rowEls.push(d); }

function layout() {
  var dw = window.innerWidth || document.documentElement.clientWidth || 0, dh = window.innerHeight || document.documentElement.clientHeight || 0;
  G.lw = dw; G.lh = dh;
  if (dw < 50 || dh < 50) return false;  // Fenster hat noch keine Größe (Shield beim Start) – später nochmal
  var mx = Math.floor(dw * 0.03), my = Math.floor(dh * 0.035);
  G.rowH = Math.floor((dh - 2 * my) / 24); G.fs = Math.floor(G.rowH * 0.84); G.cw = G.fs * 0.602;
  G.pw = dw - 2 * mx; G.cols = Math.floor(G.pw / G.cw);
  scr.style.left = mx + "px"; scr.style.top = my + "px"; scr.style.width = G.pw + "px"; scr.style.height = (G.rowH * 24) + "px";
  rowEls.forEach(function (el, i) { el.style.top = (i * G.rowH) + "px"; el.style.height = G.rowH + "px"; });
  rateEl.style.left = (mx + G.cw) + "px"; rateEl.style.top = (my + G.rowH * 20) + "px";
  rateEl.style.width = Math.floor(G.pw * 0.47) + "px"; rateEl.style.height = (G.rowH * 2) + "px";
  G.ok = true;
  return true;
}
// Lebenszeichen für die Android-Hülle: neu ausmessen und zeichnen; "ok" = Seite läuft
window.skorAlive = function () {
  try { if (!G.ok || G.lw !== window.innerWidth || G.lh !== window.innerHeight) layout(); draw(); return G.ok ? "ok" : "nolayout"; }
  catch (e) { return "err"; }
};
window.addEventListener("resize", function () { layout(); draw(); });

function pageType(p) {
  if (p === 100) return "idx"; if (p === 190) return "help"; if (p === 500) return "live"; if (p === 900) return "set"; if (p === 910) return "lsel";
  if (p === 110) return "fav"; if (p === 470) return "nl"; if (p === 510) return "day"; if (rankingByPage(p)) return "rank";
  if (feedByPage(p)) return "news";
  var v = STORE.view(p); return v ? v[2] : null;
}
function allPages() {
  var pages = [100, 110, 190, 470]; STORE.allViews().forEach(function (v) { pages.push(v[1], v[1] + 1); });
  return pages.concat([500, 510]).concat(RANKINGS.map(function (r) { return r.page; })).concat(FEEDS.map(function (f) { return f.page; })).concat([900, 910]).sort(function (a, b) { return a - b; });
}
function up(s, country) {
  if (country === "tr" || cfg("lang") === "tr") { var u = trUpper(s); if (cfg("lang") !== "tr") u = u.replace(/LİGA/g, "LIGA"); return u; }
  return s.toUpperCase();
}
function message(s, secs) { S.msg = s; S.msgUntil = Date.now() + (secs || 2.2) * 1000; }

/* ---- Laden ---- */
function needLeague(lg) {
  if (lg.loading) return;
  var age = Date.now() / 1000 - lg.tLoad, limit = (STORE.liveCount(lg) || lg.liveCount()) ? +cfg("refresh") : 600;
  if (lg.tLoad && age <= limit) return;
  lg.loading = true;
  lg.load().then(function () { lg.loading = false; draw(); }, function (e) {
    lg.loading = false; lg.error = niceError(e); lg.tLoad = Date.now() / 1000 - 600 + 20; draw();
  });
}
function needRound(lg, n) {
  if (lg.eventsOfRound(n) !== null || lg.rloading[n] || !lg.season) return;
  lg.rloading[n] = true;
  lg.loadRound(n).then(function () { delete lg.rloading[n]; draw(); }, function () { delete lg.rloading[n]; lg.rcache[n] = []; draw(); });
}
function favLeagues() { return (FAV_LEAGUES[cfg("fav")] || []).map(function (p) { return STORE.leagueByPage(p); }).filter(function (l) { return l; }); }
function needDetails(lg) {
  var rnd = lg.round ? shownRound(lg) : null, evs = rnd ? lg.eventsOfRound(rnd) : lg.events, busy = Object.keys(DETAILS.loading).length;
  (evs || []).forEach(function (e) { if (busy < 4 && detailsNeeded(e)) { busy++; loadDetails(e).then(draw); } });
}
function dayKey() { var d = new Date(Date.now() + S.dayOff * 86400000); return d.getFullYear() + two(d.getMonth() + 1) + two(d.getDate()); }
function needDay() {
  var key = dayKey(), d = STORE.days[key], now = Date.now() / 1000;
  if (d && d.loading) return;
  if (d && d.list) { var live = d.list.some(function (x) { return x[1].some(function (e) { return e.state === "in" || e.state === "ht"; }); }); if (now - d.t < (live ? +cfg("refresh") : 300)) return; }
  if (d && d.error && now - d.t < 30) return;
  STORE.days[key] = STORE.days[key] || {t: 0}; STORE.days[key].loading = true;
  STORE.fetchDay(key).then(function () { STORE.days[key].loading = false; draw(); },
    function (e) { var x = STORE.days[key]; x.loading = false; x.t = Date.now() / 1000; x.error = niceError(e); draw(); });
}
function needNat() {
  if (NAT.loading) return;
  var now = Date.now() / 1000, live = NAT.groups.some(function (lg) { return lg.events.some(function (e) { return e.state === "in" || e.state === "ht"; }); });
  if (NAT.tLoad && now - NAT.tLoad < (live ? +cfg("refresh") : 600)) return;
  if (NAT.error && now - NAT.tLoad < 30) return;
  NAT.loading = true;
  natLoad().then(function () { NAT.loading = false; draw(); }, function (e) { NAT.loading = false; NAT.error = niceError(e); NAT.tLoad = Date.now() / 1000; draw(); });
}
function needRank(rk) {
  if (!rk || rk.loading) return;
  var now = Date.now() / 1000;
  if (rk.tLoad && now - rk.tLoad < 6 * 3600 && !rk.error) return;
  if (rk.error && now - rk.tLoad < 60) return;
  rk.loading = true;
  loadRanking(rk).then(function () { rk.loading = false; draw(); }, function (e) { rk.loading = false; rk.error = niceError(e); rk.tLoad = Date.now() / 1000; draw(); });
}
function needFeed(f) {
  if (f.loading || (f.tLoad && Date.now() / 1000 - f.tLoad < 600)) return;
  f.loading = true;
  loadFeed(f).then(function () { f.loading = false; draw(); }, function (e) { f.loading = false; f.error = niceError(e); f.tLoad = Date.now() / 1000 - 600 + 30; draw(); });
}
function needRate() {
  if (RATE.loading || (RATE.tLoad && Date.now() / 1000 - RATE.tLoad < 1800)) return;
  RATE.loading = true;
  loadRate().then(function () { RATE.loading = false; draw(); }, function () { RATE.loading = false; RATE.tLoad = Date.now() / 1000 - 1800 + 120; });
}
function fetchLive() {
  STORE.loadingLive = true;
  STORE.fetchLive().then(function (nw) { STORE.loadingLive = false; STORE.liveError = null; STORE.applyLive(nw); draw(); },
    function (e) { STORE.loadingLive = false; STORE.tLive = Date.now() / 1000; STORE.liveError = niceError(e); });
}

/* ---- Navigation ---- */
function go(p) {
  if (pageType(p) === null) { message(fmt(T("notfound"), p)); return; }
  var a = STORE.leagueByPage(S.page), b = STORE.leagueByPage(p);
  if (!a || a !== b) S.md = null;
  var old = S.page;
  if (p === 510 && old !== 510) S.dayOff = 0;
  S.det = false;
  S.page = p; S.sub = 0; S.lastRot = Date.now(); S.newsSel = 0; S.newsOpen = null;
  var f = feedByPage(p); if (f) needFeed(f);
  if (p === 100) idxMark(old);
  var lg = STORE.leagueByPage(p); if (lg) needLeague(lg);
  var t = pageType(p);
  if (t === "fav") favLeagues().forEach(needLeague);
  else if (t === "nl") { needNat(); if (old !== 470) S.nlPick = true; }
  else if (t === "day") needDay();
  else if (t === "rank") needRank(rankingByPage(p));
}
function shownRound(lg) {
  if (S.md) return S.md;
  var started = lg.events.some(function (e) { return e.state !== "pre"; });
  if (!started && lg.prevRound) return lg.prevRound;
  return lg.round;
}
function keyNumber(n) {
  if (!S.entry && n === 0) { go(100); return; }
  S.entry += String(n); S.entryAt = Date.now();
  if (S.entry.length === 3) { var p = +S.entry; S.entry = ""; go(p); }
}
function key(k) {
  if (/^[0-9]$/.test(k)) { keyNumber(+k); draw(); return; }
  S.entry = "";
  var t = pageType(S.page);
  if (t === "news" && ["back", "ok", "up", "down", "left", "right"].indexOf(k) >= 0) { newsKey(k); draw(); return; }
  if (k === "back" && t === "lsel") { go(900); draw(); return; }
  if (k === "back") {
    if (S.page !== 100) { go(100); message(T("exitAgain"), 1.8); }
    else { draw(); return "exit"; }
  } else if (k === "red" || k === "green" || k === "yellow" || k === "blue") go({red: 200, green: 300, yellow: 400, blue: 500}[k]);
  else if (k === "menu") { go(900); S.setSel = 0; }
  else if (k === "info") go(190);
  else if (k === "chup" || k === "chdn") { var pages = allPages(), i = Math.max(0, pages.indexOf(S.page)); go(pages[(i + (k === "chup" ? 1 : -1) + pages.length) % pages.length]); }
  else if (k === "left" || k === "right") {
    var dd = k === "right" ? 1 : -1;
    if (t === "idx") moveIdx(dd, 0);
    else if (t === "set") changeSetting(dd);
    else if (t === "res" || t === "tab") {
      var views = STORE.allViews(), base = S.page - (t === "tab" ? 1 : 0), bases = views.map(function (v) { return v[1]; });
      var j = Math.max(0, bases.indexOf(base)), det = S.det; go(bases[(j + dd + bases.length) % bases.length] + (t === "tab" ? 1 : 0));
      var nl = STORE.leagueByPage(S.page); S.det = det && t === "res" && nl && nl.ccd !== "fupa";
    } else if (t === "day") { S.dayOff = Math.max(-7, Math.min(7, S.dayOff + dd)); S.sub = 0; needDay(); }
    else if (t === "rank") { var rp = RANKINGS.map(function (r) { return r.page; }); go(rp[(rp.indexOf(S.page) + dd + rp.length) % rp.length]); }
    else if (t === "lsel") lselToggle();
    else if (t === "live" || t === "help" || t === "fav" || t === "nl") { var n = subpages().length; S.sub = (S.sub + dd + n) % Math.max(1, n); }
  } else if (k === "up" || k === "down") {
    var d2 = k === "down" ? 1 : -1;
    if (t === "idx") moveIdx(0, d2);
    else if (t === "set") S.setSel = (S.setSel + d2 + SETTINGS.length + 3) % (SETTINGS.length + 3);
    else if (t === "lsel") { var its = lselItems().filter(function (x) { return x[0] !== null; }).map(function (x) { return x[0]; }), ci = Math.max(0, its.indexOf(S.lsSel)); S.lsSel = its[(ci + d2 + its.length) % its.length]; }
    else if (t === "res" && STORE.leagueByPage(S.page).round && !S.det) {
      var lg = STORE.leagueByPage(S.page), nr = shownRound(lg) + (d2 > 0 ? -1 : 1), top = lg.maxRound || (lg.round + 40);
      if (nr >= 1 && nr <= top) { S.md = nr; S.sub = 0; needRound(lg, nr); } else message(T("noMore"), 1.5);
    } else { var ns = subpages().length; S.sub = (S.sub + d2 + ns) % Math.max(1, ns); S.lastRot = Date.now(); }
  } else if (k === "ok") {
    if (t === "idx") { var it = idxCols()[S.idx[0]][S.idx[1]]; if (it && it.page) go(it.page); }
    else if (t === "res") {
      go(S.page + 1);
    }
    else if (t === "tab") { go(S.page - 1); }
    else if (t === "set") changeSetting(1);
    else if (t === "lsel") lselToggle();
  }
  draw();
}
function changeSetting(d) {
  if (S.setSel === SETTINGS.length) { go(910); return; }
  if (S.setSel === SETTINGS.length + 1) { go(190); return; }
  if (S.setSel > SETTINGS.length + 1) { checkUpdate(true); return; }
  var s = SETTINGS[S.setSel], vals = s[2].map(function (o) { return o[0]; }), i = Math.max(0, vals.indexOf(cfg(s[0])));
  setCfg(s[0], vals[(i + d + vals.length) % vals.length]);
}
function newsKey(k) {
  var f = feedByPage(S.page), n = f.items.length;
  if (k === "back") { if (S.newsOpen !== null) S.newsOpen = null; else { go(100); message(T("exitAgain"), 1.8); } }
  else if (k === "ok") { S.newsOpen = (S.newsOpen === null && n) ? S.newsSel : null; }
  else if ((k === "up" || k === "down") && n) {
    var d = k === "down" ? 1 : -1;
    if (S.newsOpen !== null) { S.newsOpen = (S.newsOpen + d + n) % n; S.newsSel = S.newsOpen; } else S.newsSel = (S.newsSel + d + n) % n;
  } else if (k === "left" || k === "right") {
    var dd = k === "right" ? 1 : -1;
    if (S.newsOpen !== null && n) { S.newsOpen = (S.newsOpen + dd + n) % n; S.newsSel = S.newsOpen; }
    else { var i = FEEDS.indexOf(f); go(FEEDS[(i + dd + FEEDS.length) % FEEDS.length].page); }
  }
}
/* ---- Ligen-Auswahl (Seite 910) ---- */
function lselItems() {
  var lang = cfg("lang"), out = [];
  [["de", "cDE"], ["tr", "cTR"], ["eu", "cEU"]].forEach(function (cs) {
    out.push([null, T(cs[1]), cs[0]]);
    STORE.leagues.forEach(function (l) { if (l.c === cs[0] && EXTRA_PAGES.indexOf(l.page) < 0) out.push([l.page, (l.tag ? l.tag + " " : "") + l.name(lang), cs[0]]); });
    if (cs[0] === "eu") {
      out.push([null, T("cEUx"), "eu"]);
      STORE.leagues.forEach(function (l) { if (EXTRA_PAGES.indexOf(l.page) >= 0) out.push([l.page, (l.tag ? l.tag + " " : "") + l.name(lang), "eu"]); });
    }
  });
  out.push([null, T("cNAT"), "nat"], [470, NAT.groups.length ? NAT.title(lang) : T("natEU"), "nat"]);
  out.push([null, T("cRANK"), "rk"], [480, T("rkFifa"), "rk"], [482, T("rkUefa"), "rk"], [484, T("rkClub"), "rk"]);
  out.push([null, T("cMORE"), "more"], [110, T("fav"), "more"], [510, T("today"), "more"], [500, T("allLive"), "more"], [600, "Nachrichten / Haberler", "more"]);
  return out;
}
function lselToggle() { if (S.lsSel === null || S.lsSel === undefined) return; togglePage(S.lsSel); message(pageOn(S.lsSel) ? T("lselOn") : T("lselOff"), 1.2); }
function lselSubpages() {
  var C = G.cols, its = lselItems(), at = 1, H = 17;
  if (S.lsSel === undefined) S.lsSel = 200;
  its.forEach(function (x, i) { if (x[0] === S.lsSel) at = i; });
  var top = Math.max(0, Math.min(at - Math.floor(H / 2), its.length - H));
  var hb = {de: ["r", "w"], tr: ["g", "k"], eu: ["y", "k"], nat: ["c", "k"], rk: ["b", "w"], more: ["m", "k"]};
  var lines = [text(top > 0 ? "▲ " + T("lselMore") : "", "d")];
  S.lselVis = [];
  its.slice(top, top + H).forEach(function (x) {
    S.lselVis.push(x[0]);
    if (x[0] === null) { var c = hb[x[2]] || ["k", "w"]; lines.push([null, [seg(1, C - 2, " " + up(x[1]), c[1], c[0])]]); return; }
    var sel = x[0] === S.lsSel, on = pageOn(x[0]);
    lines.push([sel ? COL.sel : null, [seg(1, 5, on ? "[✓]" : "[ ]", on ? "g" : "d"), seg(6, 5, String(x[0]), sel ? "y" : "w"),
      seg(11, C - 12, (sel ? "► " : "") + x[1], sel ? "y" : (on ? "w" : "d"))]]);
  });
  lines.push(text(top + H < its.length ? "▼ " + T("lselMore") : "", "d"));
  lines.push(text(T("lselHint"), "c"));
  return [{ban: ["910 " + up(T("lselRow")), "SkorText"], bg: ["b", "w"], head: text(T("lselHead"), "c"), lines: lines}];
}
function idxMark(old) {
  // in der Übersicht dort stehen bleiben, wo man zuletzt hineingegangen ist
  try {
    var lg = STORE.leagueByPage(old), target = lg ? lg.page : (old >= 600 && old < 700 ? 600 : old), cols = idxCols();
    for (var c = 0; c < cols.length; c++) for (var r = 0; r < cols[c].length; r++) {
      var it = cols[c][r]; if (it && it.page === target) { S.idx = [c, r]; return; }
    }
    var cur = cols[S.idx[0]] && cols[S.idx[0]][S.idx[1]];
    if (!cur || !cur.page) S.idx = [0, 1];
  } catch (e) { S.idx = [0, 1]; }
}
function idxCols() {
  var lang = cfg("lang");
  function views(c) {
    var out = [];
    STORE.leagues.filter(function (l) { return l.c === c && pageOn(l.page); }).forEach(function (l) {
      l.views().forEach(function (v) {
        out.push({page: v[0], lbl: (l.tag ? l.tag + " " : "") + l.name(lang) + (v[1] !== null ? " · " + l.gdefs[v[1]][0] : ""), lg: l, gi: v[1]});
      });
    });
    return out;
  }
  var fav = cfg("fav");
  function sec(hdr, items) { items = items.filter(function (it) { return it.page === 900 || pageOn(it.page); }); return items.length ? [hdr].concat(items) : []; }
  var more = sec({hdr: T("cMORE"), bg: "m", fg: "k"}, [
    {page: 110, lbl: T("fav") + (fav ? " · " + fav : "")}, {page: 510, lbl: T("today")}, {page: 500, lbl: T("allLive"), live: true},
    {page: 600, lbl: newsLabel()}, {page: 900, lbl: T("settings")}]);
  var dep = sec({hdr: T("cDE"), bg: "r", fg: "w"}, views("de"));
  var left = dep.concat(dep.length ? [null] : []).concat(more);
  var right = sec({hdr: T("cTR"), bg: "g", fg: "k"}, views("tr")).concat(sec({hdr: T("cEU"), bg: "y", fg: "k"}, views("eu")))
    .concat(sec({hdr: T("cNAT"), bg: "c", fg: "k"}, [{page: 470, lbl: NAT.groups.length ? NAT.title(lang) : T("natEU")}]))
    .concat(sec({hdr: T("cRANK"), bg: "b", fg: "w"}, [{page: 480, lbl: T("rkFifa")}, {page: 482, lbl: T("rkUefa")}, {page: 484, lbl: T("rkClub")}]));
  // rechts zu lang (viele Ligen eingeblendet) -> Blöcke vom Ende nach links, solange Platz ist
  while (right.length > IDX_ROWS && left.length < IDX_ROWS) {
    var cut = -1; right.forEach(function (it, i) { if (it && it.hdr) cut = i; });
    if (cut <= 0 || left.length + 1 + right.length - cut > IDX_ROWS) break;
    left = left.concat([null]).concat(right.slice(cut)); right = right.slice(0, cut);
  }
  var n = Math.max(left.length, right.length, IDX_ROWS);
  while (left.length < n) left.push(null);
  while (right.length < n) right.push(null);
  return [left, right];
}
function moveIdx(dc, dr) {
  var cols = idxCols(), c = S.idx[0], r = S.idx[1], ok = function (it) { return it && it.page; }, k = 0;
  if (dc) { c = (c + dc + 2) % 2; r = Math.min(r, cols[c].length - 1); while (!ok(cols[c][r]) && k++ < 40) r = (r + 1) % cols[c].length; }
  if (dr) { do { r = (r + dr + cols[c].length) % cols[c].length; } while (!ok(cols[c][r]) && k++ < 40); }
  S.idx = [c, r];
}

/* ---- Zeilen ---- */
function text(s, fg) { return [null, [seg(0, G.cols, s, fg || "w")]]; }
function blank() { return [null, []]; }
function chunk(lines, n) { n = n || 20; var out = []; for (var i = 0; i < lines.length; i += n) out.push(lines.slice(i, i + n)); return out.length ? out : [[]]; }
function dayLabel(ts) { var d = new Date(ts * 1000); return T("days")[d.getDay()] + " " + two(d.getDate()) + "." + two(d.getMonth() + 1) + "."; }
function statusSegs(e) {
  if (e.state === "pre" && e.nodate) return [seg(0, 6, "", "w")];
  if (e.state === "pre") { var d = new Date(e.ts * 1000); return [seg(0, 6, two(d.getHours()) + ":" + two(d.getMinutes()), "w")]; }
  if (e.state === "post") return [seg(0, 6, T("ft"), "d")];
  if (e.state === "pp") return [seg(0, 6, T("pp"), "r")];
  if (e.state === "ht") return [seg(0, 6, T("ht"), "g")];
  return [seg(0, 6, (e.min || "") + (/^\d/.test(e.min || "") ? "'" : ""), "g")];
}
function resRow(e) {
  var C = G.cols, W = Math.floor((C - 17) / 2), fresh = Date.now() / 1000 - (e.goal_at || 0) < 30;
  var s = statusSegs(e);
  s.push(seg(7, W, fit(dn(e.h), W, e.hsn), fresh && e.goal_by === "h" ? "y" : "w", null, "r"));
  if (e.state === "pre" || e.hs === null) s.push(seg(8 + W, 5, "-:-", "d", null, "c"));
  else if (fresh) s.push(seg(8 + W, 5, e.hs + ":" + e.as, "k", "y", "c", true));
  else s.push(seg(8 + W, 5, e.hs + ":" + e.as, e.state === "post" ? "w" : "y", null, "c"));
  s.push(seg(14 + W, W, fit(dn(e.a), W, e.asn), fresh && e.goal_by === "a" ? "y" : "w"));
  if (e.state === "in" || e.state === "ht") s.push(seg(15 + 2 * W, 2, "●", "g", null, "l", true));
  return [(isFav(e.h) || isFav(e.a)) ? COL.fav : null, s];
}
function zebra(row, i) {
  // Videotext: jede zweite Zeile türkis statt weiß – besser lesbar
  if (i % 2 === 0) return row;
  return [row[0], row[1].map(function (s) { var c = s.slice(); if (c[3] === "w") c[3] = "c"; return c; })];
}
function resultLines(events) {
  var out = [], last = null, n = 0;
  events.slice().sort(function (a, b) {
    var la = (a.state === "in" || a.state === "ht") ? 0 : 1, lb = (b.state === "in" || b.state === "ht") ? 0 : 1;
    return la - lb || a.ts - b.ts || (a.h < b.h ? -1 : 1);
  }).forEach(function (e) {
    var live = e.state === "in" || e.state === "ht", dl = live ? "● " + up(T("live")) : (e.nodate ? "" : dayLabel(e.ts));
    if (dl !== last) { out.push(text(dl, live ? "g" : "c")); last = dl; }
    out.push(zebra(resRow(e), n++));
  });
  return out;
}
function tableLines(lg, rows0, gi) {
  var C = G.cols, N = C - 40, changed = false;
  var live = lg.events.filter(function (e) { return (e.state === "in" || e.state === "ht") && e.hs !== null && lg.inGroup(e.group, gi); });
  var rows = rows0.map(function (r) { var o = {}; for (var k in r) o[k] = r[k]; return o; });
  if (live.length) {
    var by = {}; rows.forEach(function (r) { by[r.tm] = r; });
    live.forEach(function (e) {
      var H = by[e.h], A = by[e.a]; if (!H || !A) return;
      [[H, e.hs, e.as], [A, e.as, e.hs]].forEach(function (x) {
        var X = x[0], gf = x[1], ga = x[2]; X.live = true;
        if (lg.round && X.p >= lg.round) return;
        X.p++; X.gf += gf; X.ga += ga; X.changed = true;
        if (gf > ga) { X.w++; X.pts += 3; } else if (gf === ga) { X.d++; X.pts += 1; } else X.l++;
      });
    });
    changed = rows.some(function (r) { return r.changed; });
    if (changed) rows.sort(function (a, b) { return b.pts - a.pts || (b.gf - b.ga) - (a.gf - a.ga) || b.gf - a.gf || (a.tm < b.tm ? -1 : 1); });
  }
  return rows.map(function (r, i) {
    var rank = changed ? i + 1 : r.pos, trend = changed ? r.pos - rank : 0, z = r.zone;
    var s = [seg(0, 3, rank + ".", z ? "k" : "w", z, "c")];
    if (trend > 0) s.push(seg(3, 1, "▲", "g")); else if (trend < 0) s.push(seg(3, 1, "▼", "r"));
    var name = fit(dn(r.tm), N - (r.live ? 2 : 0), r.short);
    s.push(seg(5, N, name, "w"));
    if (r.live) s.push(seg(5 + name.length + 1, 1, "•", "g", null, "l", true));
    var gd = r.gf - r.ga;
    s.push(seg(C - 34, 3, String(r.p), "w", null, "r"), seg(C - 30, 3, String(r.w), "w", null, "r"), seg(C - 26, 3, String(r.d), "w", null, "r"),
      seg(C - 22, 3, String(r.l), "w", null, "r"), seg(C - 18, 7, r.gf + ":" + r.ga, "w", null, "r"),
      seg(C - 10, 5, gd > 0 ? "+" + gd : String(gd), "w", null, "r"), seg(C - 4, 4, String(r.pts), "y", null, "r"));
    return zebra([isFav(r.tm) ? COL.fav : null, s], i);
  });
}
function tableHead() {
  var C = G.cols;
  return [null, [seg(0, 3, T("pl"), "c", null, "c"), seg(5, C - 40, T("club"), "c"), seg(C - 34, 3, T("sp"), "c", null, "r"), seg(C - 30, 3, T("w"), "c", null, "r"),
    seg(C - 26, 3, T("d"), "c", null, "r"), seg(C - 22, 3, T("l"), "c", null, "r"), seg(C - 18, 7, T("goals"), "c", null, "r"),
    seg(C - 10, 5, T("diff"), "c", null, "r"), seg(C - 4, 4, T("pts"), "c", null, "r")]];
}
function infoHead(left) {
  var C = G.cols, d = new Date((STORE.tLive || Date.now() / 1000) * 1000);
  return [null, [seg(0, C - 18, left, "c"), seg(C - 17, 17, T("stand") + " " + two(d.getHours()) + ":" + two(d.getMinutes()) + ":" + two(d.getSeconds()), "c", null, "r")]];
}
function statusLines(lg) {
  if (lg.loading && !lg.tLoad) return [blank(), text(T("loading"), "y")];
  if (lg.error && !lg.events.length && !lg.tables.length) return [blank(), text(T("noData"), "r"), text(T("noData2"), "c"), blank(), text(fit(lg.error, G.cols), "d")];
  return null;
}
function subpages() {
  var lang = cfg("lang"), t = pageType(S.page), subs = [], C = G.cols, lines;
  if (t === "idx") {
    var cols = idxCols(), L = cols[0], R = cols[1], half = Math.floor(C / 2), mod = modern();
    lines = [];
    var win = Math.floor(S.idx[1] / IDX_ROWS), nwin = Math.ceil(Math.max(L.length, R.length) / IDX_ROWS);
    for (var i = win * IDX_ROWS; i < Math.min(Math.max(L.length, R.length), (win + 1) * IDX_ROWS); i++) {
      var s = [];
      [L, R].forEach(function (col, ci) {
        var it = col[i], base = ci * half; if (!it) return;
        if (it.hdr) { s.push(seg(base, half - 1, " " + it.hdr, it.fg, it.bg)); return; }
        var sel = S.idx[0] === ci && S.idx[1] === i, bg, fgp, fgn, fgl;
        if (mod) { bg = sel ? "sel" : null; fgp = sel ? "y" : "c"; fgn = sel ? "w" : (it.live ? "g" : "w"); fgl = sel ? "w" : "g"; }
        else { bg = sel ? "c" : null; fgp = sel ? "k" : "y"; fgn = sel ? "k" : (it.live ? "g" : "w"); fgl = sel ? "k" : "g"; }
        s.push(seg(base, half - 1, "", "k", bg));
        if (sel && mod) s.push(seg(base, 0.45, "", "k", "y"));
        s.push(seg(base + (mod ? 0.6 : 0), 4, String(it.page), fgp, bg));
        s.push(seg(base + (mod ? 5 : 4), half - 12, fit(it.lbl, half - 12), fgn, bg));
        var n = it.lg ? STORE.liveCountView(it.lg, it.gi) : (it.live ? STORE.liveCount() : 0);
        if (n) s.push(seg(base + half - 7, 5, "●" + n, fgl, bg, "r"));
      });
      lines.push([null, s]);
    }
    while (lines.length < IDX_ROWS) lines.push(blank());
    lines.push(text(T("openHint"), "c"));
    subs = [{ban: [up(T("overview")), "SkorText " + VERSION + (nwin > 1 ? "  " + (win + 1) + "/" + nwin : "")], bg: ["b", "w"], head: lines[0], lines: lines.slice(1)}];
  } else if (t === "res" || t === "tab") {
    var v = STORE.view(S.page), lg = v[0], gi = v[1], bgc = COUNTRY_BG[lg.c], title = up(lg.name(lang), lg.c), stl = statusLines(lg);
    var parts;
    if (gi !== null) parts = [[gi, title + " · " + up(lg.gdefs[gi][0], lg.c), null]];
    else if (lg.tables.length > 1) parts = lg.tables.map(function (tb) { return [null, title + (tb[0] ? " · " + up(tb[0], lg.c) : ""), tb[0]]; });
    else parts = [[null, title, null]];
    for (var pi = 0; pi < parts.length; pi++) {
      var pgi = parts[pi][0], gt = parts[pi][1], pname = parts[pi][2];
      var match = pname !== null ? function (name) { return name === pname; } : function (name) { return lg.inGroup(name, pgi); };
      if (t === "res") {
        var rnd = lg.round ? shownRound(lg) : null;
        if (stl) lines = stl;
        else {
          var evs = rnd ? lg.eventsOfRound(rnd) : lg.events;
          if (evs === null) { needRound(lg, rnd); lines = [blank(), text(T("loading"), "y")]; }
          else { lines = resultLines(evs.filter(function (e) { return match(e.group); })); if (!lines.length) lines = [blank(), text(T("noGames"), "c")]; }
        }
        var lbl = rnd ? (lg.roundLabel(rnd, lang) || fmt(T("md"), rnd)) : "";
        if (S.det && !stl) {
          var devs = (rnd ? lg.eventsOfRound(rnd) : lg.events) || [];
          var dh = infoHead(lbl + "   " + T("detHint"));
          chunkBlocks(detBlocks(devs.filter(function (e) { return match(e.group); }))).forEach(function (ch) { subs.push({ban: [gt, up(T("scorers"))], bg: bgc, head: dh, lines: ch}); });
          continue;
        }
        var hint = rnd ? lbl + (rnd === lg.round ? "  " + T("mdNow") : "") + "   " + (TOUCH ? T("mdArrows") : "▲▼") + " " + T("mdBrowse") + " · " + T("resHint") : T("resHint");
        chunk(lines).forEach(function (ch) { subs.push({ban: [gt, up(T("results")) + (SRC_TAG[lg.source] || "")], bg: bgc, head: infoHead(hint), lines: ch}); });
      } else {
        if (stl) lines = stl;
        else {
          var tabs = lg.tables.filter(function (x) { return match(x[0]); });
          lines = tabs.length ? tableLines(lg, tabs[0][1], pgi) : [blank(), text(T("noTable"), "c")];
        }
        chunk(lines).forEach(function (ch) { subs.push({ban: [gt, up(STORE.liveCountView(lg, pgi) ? T("liveTable") : T("table")) + (SRC_TAG[lg.source] || "")], bg: bgc, head: tableHead(), lines: ch}); });
      }
      if (stl) break;
    }
  } else if (t === "live") {
    lines = [];
    STORE.leagues.forEach(function (lg) {
      var evs = Object.keys(STORE.live).map(function (k) { return STORE.live[k]; }).filter(function (e) { return e.ut === lg.ut; });
      if (!evs.length) return;
      var groups = []; evs.forEach(function (e) { if (groups.indexOf(e.group) < 0) groups.push(e.group); }); groups.sort();
      groups.forEach(function (g) {
        lines.push(text((lg.tag ? lg.tag + " " : "") + lg.name(lang) + (g ? " · " + g : "") + "   (" + lg.pageOfGroup(g) + ")", "y"));
        evs.filter(function (e) { return e.group === g; }).sort(function (a, b) { return a.ts - b.ts; }).forEach(function (e, zi) { lines.push(zebra(resRow(e), zi)); });
      });
    });
    if (!lines.length) lines = STORE.liveError && !Object.keys(STORE.live).length ? [blank(), text(T("noData"), "r"), text(T("noData2"), "c")] : [blank(), text(T("noLive"), "c")];
    chunk(lines).forEach(function (ch) { subs.push({ban: ["● " + up(T("allLive")), ""], bg: ["b", "w"], head: infoHead(dayLabel(Date.now() / 1000)), lines: ch}); });
  } else if (t === "help") {
    lines = [blank()];
    T("helpRows").forEach(function (r) { lines.push([null, [seg(1, 14, r[0], "y"), seg(16, C - 16, r[1])]]); });
    lines.push(blank()); lines.push(text("SkorText TV " + VERSION + " · " + T("source"), "d"));
    subs = chunk(lines).map(function (ch) { return {ban: [up(T("help")), "SkorText"], bg: ["b", "w"], head: blank(), lines: ch}; });
  } else if (t === "fav") subs = favSubpages();
  else if (t === "day") subs = daySubpages();
  else if (t === "nl") subs = nlSubpages();
  else if (t === "rank") subs = rankSubpages();
  else if (t === "set") {
    lines = [blank()]; var half2 = Math.floor(C / 2);
    SETTINGS.forEach(function (st, i) {
      var sel = i === S.setSel, cur = st[2].filter(function (o) { return o[0] === cfg(st[0]); })[0] || st[2][0], val = lang === "tr" ? cur[2] : cur[1];
      lines.push([sel ? COL.sel : null, [seg(1, half2 - 2, lang === "tr" ? st[1][1] : st[1][0], sel ? "y" : "w"), seg(half2, half2, (sel ? "◄ " : "  ") + val + (sel ? " ►" : ""), sel ? "y" : "c")]]);
    });
    var ls = S.setSel === SETTINGS.length, hs = S.setSel === SETTINGS.length + 1, us = S.setSel > SETTINGS.length + 1;
    var non = lselItems().filter(function (x) { return x[0] !== null && pageOn(x[0]); }).length;
    lines.push([ls ? COL.sel : null, [seg(1, half2 - 2, T("lselRow"), ls ? "y" : "w"), seg(half2, half2, (ls ? "► " : "  ") + fmt(T("lselOpen"), non), ls ? "y" : "c")]]);
    lines.push([hs ? COL.sel : null, [seg(1, half2 - 2, T("help"), hs ? "y" : "w"), seg(half2, half2, (hs ? "► " : "  ") + T("helpOpen"), hs ? "y" : "c")]]);
    lines.push([us ? COL.sel : null, [seg(1, half2 - 2, T("updRow"), us ? "y" : "w"), seg(half2, half2, (us ? "► " : "  ") + (UPD.msg || T("updOpen")), us ? "y" : "c")]]);
    lines.push(blank()); lines.push(text(" " + (ls ? T("lselInfo") : hs ? T("helpInfo") : (us ? T("updInfo") + " " + VERSION : SETTINGS[S.setSel][3][lang === "tr" ? 1 : 0])), "g"));
    lines.push(blank()); lines.push(text(" " + (lang === "tr" ? "Android uygulaması: Downloader kodu " : "Android-App laden: Downloader-Code ") + DL_CODE + "  (aftv.news/" + DL_CODE + ")", "y"));
    while (lines.length < 19) lines.push(blank());
    lines.push(text(T("setHint"), "c"));
    subs = [{ban: [up(T("settings")), "SkorText"], bg: ["b", "w"], head: text("MENU", "d"), lines: lines}];
  } else if (t === "lsel") subs = lselSubpages();
  else if (t === "news") subs = newsSubpages();
  return subs.length ? subs : [{ban: ["", ""], bg: ["b", "w"], head: blank(), lines: []}];
}
function detItems(inc, side) {
  var out = [];
  inc.forEach(function (x) {
    if (x.side !== side) return;
    var m = x.min + ".";
    if (x.type === "goal" || x.type === "pen" || x.type === "og") out.push([m + " " + x.name + (x.type === "pen" ? " (" + T("pen") + ")" : (x.type === "og" ? " (" + T("og") + ")" : "")), "c"]);
    else if (x.type === "red") out.push(["■ " + m + " " + x.name, "r"]);
    else if (x.type === "yr") out.push(["■ " + m + " " + x.name, "y"]);
  });
  return out;
}
function packItems(items, width) {
  var lines = [], cur = [], ln = 0;
  items.forEach(function (it) {
    var txt = fit(it[0], width), add = txt.length + (cur.length ? 2 : 0);
    if (cur.length && ln + add > width) { lines.push(cur); cur = []; ln = 0; add = txt.length; }
    cur.push([txt, it[1]]); ln += add;
  });
  if (cur.length) lines.push(cur);
  return lines;
}
function detBlocks(events) {
  var C = G.cols, W = Math.floor((C - 17) / 2), blocks = [];
  events.slice().sort(function (a, b) {
    var la = (a.state === "in" || a.state === "ht") ? 0 : 1, lb = (b.state === "in" || b.state === "ht") ? 0 : 1;
    return la - lb || a.ts - b.ts || (a.h < b.h ? -1 : 1);
  }).forEach(function (e) {
    var bl = [resRow(e)];
    if (e.state !== "pre") {
      var c = DETAILS.cache[e.id];
      if (!c) bl.push([null, [seg(7, W, "…", "d", null, "r")]]);
      else {
        var hl = packItems(detItems(c.list, 1), W), al = packItems(detItems(c.list, 2), W);
        for (var i = 0; i < Math.max(hl.length, al.length); i++) {
          var s = [];
          if (i < hl.length) {
            var tot = hl[i].reduce(function (a, x) { return a + x[0].length; }, 0) + 2 * (hl[i].length - 1), x0 = 7 + W - tot;
            hl[i].forEach(function (x, j) { var last = j === hl[i].length - 1; s.push(seg(x0, x[0].length + (last ? 0 : 1), x[0] + (last ? "" : ","), x[1])); x0 += x[0].length + 2; });
          }
          if (i < al.length) {
            var x1 = 14 + W;
            al[i].forEach(function (x, j) { var last = j === al[i].length - 1; s.push(seg(x1, x[0].length + 1, x[0] + (last ? "" : ","), x[1])); x1 += x[0].length + 2; });
          }
          bl.push([null, s]);
        }
      }
    }
    blocks.push(bl);
  });
  return blocks.length ? blocks : [[blank(), text(T("noGames"), "c")]];
}
function chunkBlocks(blocks, n) {
  n = n || 20; var pages = [], cur = [];
  blocks.forEach(function (bl) { if (cur.length && cur.length + bl.length > n) { pages.push(cur); cur = []; } cur = cur.concat(bl.slice(0, n)); });
  if (cur.length) pages.push(cur);
  return pages.length ? pages : [[]];
}
function favRow(e, lg) {
  var C = G.cols, W = Math.floor((C - 22) / 2), d = new Date(e.ts * 1000), live = e.state === "in" || e.state === "ht";
  var s = live ? statusSegs(e) : [seg(0, 6, two(d.getDate()) + "." + two(d.getMonth() + 1) + ".", "d")];
  s.push(seg(7, W, fit(dn(e.h), W, e.hsn), isFav(e.h) ? "y" : "w", null, "r"));
  if (e.state === "pre") s.push(seg(8 + W, 5, two(d.getHours()) + ":" + two(d.getMinutes()), "c", null, "c"));
  else if (e.hs === null) s.push(seg(8 + W, 5, e.state === "pp" ? T("pp") : "-:-", e.state === "pp" ? "r" : "d", null, "c"));
  else s.push(seg(8 + W, 5, e.hs + ":" + e.as, live ? "y" : "w", null, "c"));
  s.push(seg(14 + W, W, fit(dn(e.a), W, e.asn), isFav(e.a) ? "y" : "w"));
  s.push(seg(C - 4, 4, FAV_CODES[lg.page] || "", "d", null, "r"));
  return [null, s];
}
function favSubpages() {
  var C = G.cols, lang = cfg("lang"), fav = cfg("fav"), ban = [up(T("fav")) + (fav ? " · " + up(fav) : ""), ""], head = infoHead("");
  if (!fav) return [{ban: ban, bg: ["m", "k"], head: head, lines: [blank(), text(T("noFav"), "y"), text(T("noFav2"), "c")]}];
  var lgs = favLeagues(), lines = [], seen = {}, evs = [];
  lgs.forEach(function (lg) {
    lg.allEvents.forEach(function (e) { if (!seen[e.id] && (isFavFirst(e.h) || isFavFirst(e.a))) { seen[e.id] = 1; evs.push([e, lg]); } });
    lg.tables.forEach(function (tb) {
      tb[1].forEach(function (r) {
        if (isFavFirst(r.tm)) lines.push([null, [seg(1, C - 30, fit(lg.name(lang) + (tb[0] ? " · " + tb[0] : ""), C - 31), "c"),
          seg(C - 29, 29, T("place") + " " + r.pos + " · " + r.p + " " + T("sp") + " · " + r.pts + " " + T("pts"), "y", null, "r")]]);
      });
    });
  });
  if (!evs.length && lgs.some(function (lg) { return lg.loading || !lg.tLoad; })) return [{ban: ban, bg: ["m", "k"], head: head, lines: [blank(), text(T("loading"), "y")]}];
  var now = Date.now() / 1000;
  var live = evs.filter(function (x) { return x[0].state === "in" || x[0].state === "ht"; });
  var past = evs.filter(function (x) { return (x[0].state === "post" || x[0].state === "pp") && x[0].ts <= now; }).sort(function (a, b) { return a[0].ts - b[0].ts; }).slice(-5);
  var nxt = evs.filter(function (x) { return x[0].state === "pre" || (x[0].state === "pp" && x[0].ts > now); }).sort(function (a, b) { return a[0].ts - b[0].ts; }).slice(0, 5);
  if (live.length) { lines.push(blank()); lines.push(text("● " + up(T("live")), "g")); live.forEach(function (x) { lines.push(favRow(x[0], x[1])); }); }
  lines.push(blank()); lines.push(text(up(T("last")), "c"));
  if (past.length) past.forEach(function (x, i) { lines.push(zebra(favRow(x[0], x[1]), i)); }); else lines.push(text("  –", "d"));
  lines.push(blank()); lines.push(text(up(T("next")), "c"));
  if (nxt.length) nxt.forEach(function (x, i) { lines.push(zebra(favRow(x[0], x[1]), i)); }); else lines.push(text("  –", "d"));
  return chunk(lines).map(function (ch) { return {ban: ban, bg: ["m", "k"], head: head, lines: ch}; });
}
function nlSubpages() {
  var lang = cfg("lang"), subs = [];
  var grs = NAT.groups.filter(function (lg) { return !(lg.error && !lg.allEvents.length); });
  if (S.nlPick && grs.length && grs.every(function (lg) { return lg.tLoad || lg.error; })) {
    S.nlPick = false;
    grs.forEach(function (l, i) { if (l.tables.some(function (tb) { return tb[1].some(function (r) { return r.tm === "Germany"; }); })) S.sub = i; });
  }
  grs.forEach(function (lg) {
    var lines = [];
    if (!lg.allEvents.length && !lg.tables.length) lines = [blank(), text(T("loading"), "y")];
    else {
      var lastR = 0; lg.allEvents.forEach(function (e) { if (e.state !== "pre" && e.state !== "pp" && e.round && e.round > lastR) lastR = e.round; });
      var last = lastR ? lg.allEvents.filter(function (e) { return e.round === lastR && e.state !== "pre"; }) : [];
      var pre = lg.allEvents.filter(function (e) { return e.state === "pre"; }), nextR = 0;
      pre.forEach(function (e) { if (e.round && (!nextR || e.round < nextR)) nextR = e.round; });
      var nxt = nextR ? pre.filter(function (e) { return e.round === nextR; }) : pre.slice(0, 2);
      var byT = function (a, b) { return a.ts - b.ts || (a.h < b.h ? -1 : 1); };
      if (last.length) { lines.push(text(up(fmt(T("md"), lastR)), "c")); last.sort(byT).forEach(function (e, i) { lines.push(zebra(resRow(e), i)); }); lines.push(blank()); }
      if (lg.tables.length) { lines.push(tableHead()); lines = lines.concat(tableLines(lg, lg.tables[0][1], null)); lines.push(blank()); }
      if (nxt.length) { lines.push(text(up(T("next")) + (nextR ? " · " + fmt(T("md"), nextR) : ""), "c")); nxt.sort(byT).forEach(function (e, i) { lines.push(zebra(favRow(e, lg), i)); }); }
    }
    subs.push({ban: [up(lg.name(lang)), ""], bg: ["c", "k"], head: infoHead(T("nlHint")), lines: lines.slice(0, 20)});
  });
  if (subs.length) return subs;
  var msg = NAT.error && !NAT.loading ? [blank(), text(T("noData"), "r"), text(fit(NAT.error, G.cols), "d")] : [blank(), text(T("loading"), "y")];
  return [{ban: [up(NAT.title(lang)), ""], bg: ["c", "k"], head: blank(), lines: msg}];
}
function daySubpages() {
  var lang = cfg("lang"), key = dayKey(), t0 = Date.now() / 1000 + S.dayOff * 86400;
  var nm = {"0": T("today"), "-1": T("yesterday"), "1": T("tomorrow")}[String(S.dayOff)];
  var title = up(nm ? nm + " · " + dayLabel(t0) : dayLabel(t0)), d = STORE.days[key] || {}, head = infoHead(T("dayHint"));
  if (!d.list) {
    var l = d.error ? [blank(), text(T("noData"), "r"), text(T("noData2"), "c"), blank(), text(fit(d.error, G.cols), "d")] : [blank(), text(T("loading"), "y")];
    return [{ban: [title, ""], bg: ["b", "w"], head: head, lines: l}];
  }
  var blocks = d.list.map(function (x) {
    var lg = x[0], bl = [text((lg.tag ? lg.tag + " " : "") + lg.name(lang) + "   (" + lg.page + ")", "y")];
    x[1].forEach(function (e, i) { bl.push(zebra(resRow(STORE.live[e.id] || e), i)); });
    return bl;
  });
  if (!blocks.length) blocks = [[blank(), text(T("noMatches"), "c")]];
  return chunkBlocks(blocks).map(function (ch) { return {ban: [title, ""], bg: ["b", "w"], head: head, lines: ch}; });
}
function rankSubpages() {
  var C = G.cols, rk = rankingByPage(S.page), title = up({fifa: T("rkFifa"), uefa: T("rkUefa"), club: T("rkClub")}[rk.kind]);
  var hd = [null, [seg(0, 4, T("pl"), "c", null, "r"), seg(6, C - 30, rk.kind === "club" ? T("club") : T("nation"), "c"),
    seg(C - 23, 10, T("points"), "c", null, "r"), seg(C - 11, 10, rk.kind === "fifa" ? "+/-" : (rk.kind === "uefa" ? T("season") : T("nation")), "c", null, "r")]];
  if (!rk.rows.length) return [{ban: [title, ""], bg: ["b", "w"], head: hd, lines: rk.error ? [blank(), text(T("noData"), "r"), text(fit(rk.error, C), "d")] : [blank(), text(T("loading"), "y")]}];
  var lines = rk.rows.slice(0, rk.kind === "club" ? 80 : 60).map(function (r, i) {
    var mine = rk.kind === "club" ? isFav(r.name) : (r.code === "GER" || r.code === "TUR");
    var s = [seg(0, 4, r.pos + ".", "w", null, "r"), seg(6, C - 30, fit(dn(r.name), C - 31), "w"), seg(C - 23, 10, r.pts.toFixed(rk.kind === "fifa" ? 2 : 3), "y", null, "r")];
    if (rk.kind === "fifa") { var dl = r.prev ? r.prev - r.pos : 0; s.push(seg(C - 11, 10, dl > 0 ? "▲" + dl : (dl < 0 ? "▼" + (-dl) : "="), dl > 0 ? "g" : (dl < 0 ? "r" : "d"), null, "r")); }
    else if (rk.kind === "uefa") s.push(seg(C - 11, 10, r.season !== null ? r.season.toFixed(3) : "", "w", null, "r"));
    else s.push(seg(C - 11, 10, r.code, (r.code === "GER" || r.code === "TUR") ? "y" : "w", null, "r"));
    return zebra([mine ? COL.fav : null, s], i);
  });
  return chunk(lines).map(function (ch) { return {ban: [title, rk.date ? T("asOf") + " " + rk.date : ""], bg: ["b", "w"], head: hd, lines: ch}; });
}
function wrap(s, w) { var out = [], line = ""; (s || "").split(/\s+/).forEach(function (word) { if (!line) line = word; else if ((line + " " + word).length <= w) line += " " + word; else { out.push(line); line = word; } }); if (line) out.push(line); return out; }
function newsSubpages() {
  var C = G.cols, f = feedByPage(S.page), title = NEWS_LANG === "de" ? ("NACHRICHTEN · " + f.name).toUpperCase() : trUpper(T("cNEWS") + " · " + f.name), lt = new Date((f.tLoad || Date.now() / 1000) * 1000);
  var head = [null, [seg(0, C - 18, f.source, "c"), seg(C - 17, 17, T("stand") + " " + two(lt.getHours()) + ":" + two(lt.getMinutes()), "c", null, "r")]];
  var lines;
  if (!f.items.length) {
    lines = (f.loading || !f.tLoad) ? [blank(), text(T("loading"), "y")] : [blank(), text(T("noData"), "r"), text(T("noData2"), "c"), blank(), text(fit(f.error || "", C), "d")];
    return [{ban: [title, ""], bg: ["m", "k"], head: head, lines: lines}];
  }
  var n = f.items.length;
  if (S.newsOpen !== null) {
    var it = f.items[S.newsOpen]; lines = [blank()];
    wrap(it.title, C - 2).slice(0, 4).forEach(function (l) { lines.push(text(" " + l, "y")); });
    if (it.ts) { var d = new Date(it.ts * 1000); lines.push(text(" " + dayLabel(it.ts) + " " + two(d.getHours()) + ":" + two(d.getMinutes()), "d")); }
    lines.push(blank());
    var room = 19 - lines.length, body = wrap(it.desc, C - 2);
    if (body.length > room) { body = body.slice(0, room); body[room - 1] = body[room - 1].slice(0, C - 4) + " …"; }
    body.forEach(function (l) { lines.push(text(" " + l, "w")); });
    while (lines.length < 19) lines.push(blank());
    lines.push(text(T("articleHint"), "c"));
    return [{ban: [title, (S.newsOpen + 1) + "/" + n], bg: ["m", "k"], head: head, lines: lines}];
  }
  var per = 18, subs = []; S.sub = Math.floor(S.newsSel / per);
  for (var st = 0; st < n; st += per) {
    lines = [];
    for (var i = st; i < Math.min(n, st + per); i++) {
      var x = f.items[i], sel = i === S.newsSel, tm = "";
      if (x.ts) { var dt = new Date(x.ts * 1000); tm = two(dt.getHours()) + ":" + two(dt.getMinutes()); }
      lines.push([sel ? COL.sel : null, [seg(0, 5, tm, sel ? "y" : "d"), seg(6, C - 6, fit(x.title, C - 6), sel ? "y" : "w")]]);
    }
    while (lines.length < 19) lines.push(blank());
    lines.push(text(T("newsHint"), "c"));
    subs.push({ban: [title, ""], bg: ["m", "k"], head: head, lines: lines});
  }
  return subs;
}
function buildRows() {
  var C = G.cols, subs = subpages(); if (S.sub >= subs.length) S.sub = 0;
  var sp = subs[S.sub], now = new Date(), rows = [];
  var pg = S.entry ? "P" + (S.entry + "---").slice(0, 3) : "P" + S.page;
  var date = T("days")[now.getDay()] + " " + two(now.getDate()) + "." + two(now.getMonth() + 1) + ".  " + two(now.getHours()) + ":" + two(now.getMinutes()) + ":" + two(now.getSeconds());
  var h = [seg(0, 5, pg, S.entry ? "y" : "w", null, "l", !!S.entry), seg(6, 9, "SkorText", "y")];
  if (S.page !== 100) h.push(seg(17, C - 40, T("home"), "c"));
  h.push(seg(C - 22, 22, date, "y", null, "r"));
  rows.push([null, h]);
  var goal = null, ns = Date.now() / 1000;
  for (var i = STORE.goals.length - 1; i >= 0; i--) if (ns - STORE.goals[i][0] < 8 && STORE.goals[i][0] > S.openedAt) { goal = STORE.goals[i][1]; break; }
  if (Date.now() < S.msgUntil) rows.push([COL.w, [seg(1, C - 2, S.msg, "k")]]);
  else if (goal) {
    var lg = STORE.byUt[goal.ut], m = goal.min + (/^\d/.test(goal.min || "") ? "'" : "");
    var txt = T("goal") + "  " + m + "  " + goal.h + " " + (goal.hs || 0) + ":" + (goal.as || 0) + " " + goal.a;
    rows.push([S.blinkOn ? COL.y : COL.k, [seg(1, C - 22, fit(txt, C - 22), S.blinkOn ? "k" : "y"), seg(C - 21, 20, fit(lg ? lg.name(cfg("lang")) : "", 20), S.blinkOn ? "k" : "y", null, "r")]]);
  } else {
    var bg = sp.bg[0], fg = sp.bg[1];
    if (modern()) { fg = bg; bg = "card"; }
    var right = sp.ban[1] + (subs.length > 1 ? "  " + (S.sub + 1) + "/" + subs.length : "");
    rows.push([COL[bg], [seg(1, C - 2 - right.length - 1, fit(sp.ban[0], C - 3 - right.length), fg), seg(C - 1 - right.length, right.length, right, fg, null, "r")]]);
  }
  rows.push(sp.head || blank());
  var body = sp.lines.slice(0, 20); while (body.length < 20) body.push(blank());
  rows = rows.concat(body);
  var F = T("fast"), q = Math.floor(C / 4);
  rows.push([null, [seg(0, q - 1, F[0], "w", "r", "c"), seg(q, q - 1, F[1], "k", "g", "c"), seg(2 * q, q - 1, F[2], "k", "y", "c"), seg(3 * q, C - 3 * q, F[3], "w", "b", "c")]]);
  return rows;
}
function draw() {
  COL = modern() ? PAL_MOD : PAL_TT;
  var rows;
  try { rows = buildRows(); } catch (e) { rows = [[null, [seg(0, G.cols, "SkorText: " + e, "r")]]]; }
  var mod = modern(), pbg = mod && cfg("bg") !== "navy" ? COL.bg : (PAGE_BG[cfg("bg")] || "#000");
  document.body.style.background = pbg;
  var font = mod ? "SkorSans" : "SkorMono";
  scr.style.fontFamily = '"' + font + '", "DejaVu Sans Mono", monospace';
  scr.style.fontSize = G.fs + "px";
  for (var i = 0; i < 24; i++) {
    var r = rows[i] || [null, []], rbg = r[0], segs = r[1], bgc = rbg !== null ? rbg : pbg, rounded = false;
    if (mod) {
      if (rbg === null && i === 0) bgc = COL.hdr;
      else if (rbg === null && i >= 3 && i <= 22 && segs.some(function (s) { return s[2]; })) { bgc = i % 2 ? COL.zebra1 : COL.zebra2; rounded = true; }
      else if (rbg !== null) rounded = true;
    }
    var el = rowEls[i];
    el.style.background = bgc; el.style.borderRadius = rounded ? Math.max(3, Math.floor(G.rowH / 4)) + "px" : "0";
    el.style.boxShadow = (mod && rbg === COL.sel) ? "inset " + Math.max(4, Math.floor(G.cw * 0.45)) + "px 0 0 " + COL.y : "none";
    var html = "";
    segs.forEach(function (s) {
      var x = Math.floor(s[0] * G.cw), w = Math.floor(s[1] * G.cw) + 2, txt = (s[6] && !S.blinkOn) ? "" : s[2];
      if (x >= G.pw) return; w = Math.min(w, G.pw - x);
      html += '<div class="sg ' + (s[5] === "r" ? "r" : s[5] === "c" ? "c" : "") + '" style="left:' + x + "px;width:" + w + "px;color:" + COL[s[3]] +
        (s[4] ? ";background:" + COL[s[4]] : "") + '">' + esc(txt) + "</div>";
    });
    el.innerHTML = html;
  }
  // Wechselkurs auf der Uebersicht
  if (false) {
    rateEl.style.display = "flex"; rateEl.style.fontFamily = scr.style.fontFamily;
    rateEl.firstChild.style.fontSize = Math.floor(G.fs * 1.3) + "px"; rateEl.lastChild.style.fontSize = G.fs + "px";
    rateEl.firstChild.style.color = COL.y; rateEl.lastChild.style.color = COL.d;
    if (RATE.value === null) { rateEl.firstChild.textContent = "1 € = … ₺"; rateEl.lastChild.textContent = ""; }
    else {
      rateEl.firstChild.textContent = "1 € = " + RATE.value.toFixed(2).replace(".", ",") + " ₺";
      var dd = RATE.date.length >= 10 ? RATE.date.slice(8, 10) + "." + RATE.date.slice(5, 7) + "." : "";
      rateEl.lastChild.textContent = (RATE.src === "ezb" ? (cfg("lang") === "tr" ? "AMB" : "EZB") : "Kurs") + " " + dd;
    }
  } else rateEl.style.display = "none";
}

/* ================= Takt ================= */
/* Update über GitHub: skortext.apk (Android) und skortext.ipk (GigaBlue) liegen auf derselben Release-Seite */
var UPD_BASE = "https://github.com/postmen1971/skortext/releases/latest/download/", UPD = {msg: "", busy: false};
function newerVersion(a, b) {
  var x = String(a || "0").split("."), y = String(b || "0").split(".");
  for (var i = 0; i < Math.max(x.length, y.length); i++) { var p = +(x[i] || 0), q = +(y[i] || 0); if (p !== q) return p > q; }
  return false;
}
function checkUpdate(manual) {
  if (UPD.busy) return;
  UPD.busy = true; if (manual) { UPD.msg = "…"; draw(); }
  rawJSON(UPD_BASE + "version.json").then(function (v) {
    UPD.busy = false;
    if (!newerVersion(v.app, VERSION)) { if (manual) { UPD.msg = T("updNone") + " (" + VERSION + ")"; message(UPD.msg, 3); } draw(); return; }
    UPD.msg = T("updLoad").replace("%s", v.app); message(UPD.msg, 4); draw();
    if (window.SkorBridge && SkorBridge.installApk) SkorBridge.installApk(UPD_BASE + "skortext.apk");
  }, function () { UPD.busy = false; if (manual) { UPD.msg = T("updErr"); message(UPD.msg, 3); } draw(); });
}
window.__skorUpd = function (st) {
  if (st === "perm") UPD.msg = T("updPerm");
  else if (String(st).indexOf("error") === 0) UPD.msg = T("updErr");
  else UPD.msg = "";
  if (UPD.msg) message(UPD.msg, 5);
  draw();
};
function tick() {
  S.blinkOn = !S.blinkOn;
  var now = Date.now() / 1000;
  if (S.entry && Date.now() - S.entryAt > 4000) S.entry = "";
  if (!STORE.loadingLive && now - STORE.tLive > +cfg("refresh")) fetchLive();
  var lg = STORE.leagueByPage(S.page); if (lg) { needLeague(lg); if (S.det) needDetails(lg); }
  var tt = pageType(S.page);
  if (tt === "fav") favLeagues().forEach(needLeague);
  else if (tt === "nl") needNat();
  else if (tt === "day") needDay();
  else if (tt === "rank") needRank(rankingByPage(S.page));
  var f = feedByPage(S.page); if (f) needFeed(f);
  if (cfg("rotate") === "10" && !f && !(tt === "res" && !S.det) && Date.now() - S.lastRot > 10000) {
    S.lastRot = Date.now(); var n = subpages().length; if (n > 1) S.sub = (S.sub + 1) % n;
  }
  draw();
}

/* Fernbedienung: die Android-Huelle ruft skorKey(...) auf; im Browser gehen auch Tasten */
window.skorKey = function (k) { try { return key(k) || ""; } catch (e) { return ""; } };
var KB = {ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right", Enter: "ok", Escape: "back", Backspace: "back",
  r: "red", g: "green", y: "yellow", b: "blue", m: "menu", i: "info", PageUp: "chup", PageDown: "chdn"};
document.addEventListener("keydown", function (e) {
  var k = /^[0-9]$/.test(e.key) ? e.key : KB[e.key]; if (!k) return;
  e.preventDefault(); window.skorKey(k);
});

/* ================= Touch (Tablet) ================= */
function touchTap(x, y) {
  var r = scr.getBoundingClientRect(), row = Math.floor((y - r.top) / G.rowH), col = (x - r.left) / G.cw, t = pageType(S.page), C = G.cols;
  if (row < 0 || row > 23) return;
  if (row === 0) { if (S.page !== 100) key("back"); else draw(); return; }
  if (row === 23) { var q = Math.floor(C / 4); key(["red", "green", "yellow", "blue"][Math.max(0, Math.min(3, Math.floor(col / q)))]); return; }
  var bi = row - 3;
  if (row === 1) {
    if (t === "res" || t === "tab") key("ok");
    else if (t === "news" && S.newsOpen !== null) key("back");
    return;
  }
  if (t === "idx") {
    var cols = idxCols(), ci = col < Math.floor(C / 2) ? 0 : 1, ri = Math.floor(S.idx[1] / IDX_ROWS) * IDX_ROWS + bi, it = bi >= 0 && bi < IDX_ROWS ? cols[ci][ri] : null;
    if (it && it.page) { S.idx = [ci, ri]; draw(); go(it.page); draw(); }
    return;
  }
  if (t === "set") {
    var si = bi - 1;
    if (si === SETTINGS.length) { S.setSel = si; go(910); draw(); return; }
    if (si === SETTINGS.length + 1) { S.setSel = si; go(190); draw(); return; }
    if (si === SETTINGS.length + 2) { S.setSel = si; checkUpdate(true); draw(); return; }
    if (si >= 0 && si < SETTINGS.length) {
      var half = Math.floor(C / 2);
      if (S.setSel !== si) { S.setSel = si; if (col >= half) changeSetting(1); }
      else changeSetting(col >= half && col < half + 3 ? -1 : 1);
      draw();
    }
    return;
  }
  if (t === "lsel") {
    var vis = S.lselVis || [], li = bi - 1;
    if (li >= 0 && li < vis.length && vis[li] !== null) { S.lsSel = vis[li]; lselToggle(); draw(); }
    else if (li === -1) { for (var q1 = 0; q1 < 8; q1++) key("up"); } else if (li === vis.length) { for (var q2 = 0; q2 < 8; q2++) key("down"); }
    return;
  }
  if (t === "news") {
    var f = feedByPage(S.page);
    if (S.newsOpen === null && f && bi >= 0 && bi < 18) {
      var ni = S.sub * 18 + bi;
      if (ni < f.items.length) { S.newsSel = ni; S.newsOpen = ni; draw(); }
    } else if (S.newsOpen !== null) key("right");
    return;
  }
  if (t === "res") key("ok");
}
function touchSwipe(dx, dy) {
  var t = pageType(S.page);
  if (Math.abs(dx) > Math.abs(dy)) { key(dx < 0 ? "right" : "left"); return; }
  // nach unten wischen = zurück (früherer Spieltag / vorige Unterseite), nach oben = weiter
  if (t === "res") key(dy > 0 ? "down" : "up");
  else if (t === "idx") {
    // Übersicht: nach oben wischen = Seite 2 (Länderspiele, Ranglisten), nach unten = zurück zu Seite 1
    var cols = idxCols(), n = Math.max(cols[0].length, cols[1].length), win = Math.floor(S.idx[1] / IDX_ROWS), nw = Math.ceil(n / IDX_ROWS);
    win = Math.max(0, Math.min(nw - 1, win + (dy < 0 ? 1 : -1)));
    for (var r = win * IDX_ROWS; r < Math.min(n, (win + 1) * IDX_ROWS); r++) { var it = cols[0][r] || cols[1][r]; if (it && it.page) { S.idx = [cols[0][r] && cols[0][r].page ? 0 : 1, r]; break; } }
    draw();
  }
  else if (t === "set") return;
  else if (t === "nl") key(Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? "right" : "left") : (dy < 0 ? "down" : "up"));
  else key(dy > 0 ? "up" : "down");
}
(function () {
  var sx = 0, sy = 0, st = 0;
  document.addEventListener("touchstart", function (e) {
    if (!TOUCH) { TOUCH = true; draw(); }
    var p = e.touches[0]; sx = p.clientX; sy = p.clientY; st = Date.now();
  }, {passive: true});
  document.addEventListener("touchend", function (e) {
    var p = e.changedTouches[0], dx = p.clientX - sx, dy = p.clientY - sy, lim = Math.max(40, G.rowH * 2);
    try {
      if (Math.abs(dx) > lim || Math.abs(dy) > lim) touchSwipe(dx, dy);
      else if (Date.now() - st < 800) touchTap(p.clientX, p.clientY);
    } catch (err) {}
    e.preventDefault();
  }, {passive: false});
})();

setInterval(function () {
  // Größe hat sich geändert oder war beim Start noch 0 -> neu ausmessen
  try { if (!G.ok || G.lw !== window.innerWidth || G.lh !== window.innerHeight) { if (layout()) draw(); } } catch (e) {}
  tick();
}, 500);
try {
layout();
var sp0 = cfg("startpage"); S.page = /^\d+$/.test(sp0) ? +sp0 : 100;
go(S.page);
fetchLive();
draw();
} catch (e) { try { console.log("Start: " + e); } catch (e2) {} }

if (cfg("autoupd") !== "off") setTimeout(function () { checkUpdate(false); }, 8000);
try { document.fonts && document.fonts.ready.then(draw); } catch (e) {}
window.__skor = {S: S, STORE: STORE, FEEDS: FEEDS, RATE: RATE, draw: draw, key: key, parseFeed: parseFeed, parseEvent: parseEvent, G: G};
})();
