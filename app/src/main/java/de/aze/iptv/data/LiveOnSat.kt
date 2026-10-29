package de.aze.iptv.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Sender je Spiel von liveonsat.com (dieselbe Quelle, die das Enigma2-Plugin FootOnSat nutzt).
 * Die Seite wird wie im Browser geladen, in Textzeilen zerlegt und so gelesen:
 *   „Mannschaft A v Mannschaft B“  → neues Spiel
 *   folgende kurze Zeilen          → Sender dieses Spiels
 * Ändert liveonsat das Seitenformat, bleibt die Liste leer (Programmzeitschrift und TV-Plan gelten weiter).
 */
object LiveOnSat {

    class Fixture(val home: String, val away: String, val channels: List<String>)

    private val urls = listOf("https://liveonsat.com/2day.php", "https://www.liveonsat.com/2day.php")

    /** Zuletzt geladene Seite und Ergebnis – zur Fehlersuche (abrufbar über die Web-Bearbeitung: /liveonsat). */
    @Volatile var lastHtml: String? = null
    @Volatile var lastStatus: String = "noch nicht geladen"

    /**
     * liveonsat rechnet in britischer Zeit: Zwischen Mitternacht (bei uns) und 1 Uhr zeigt „2day“ noch den Vortag.
     * Deshalb zusätzlich die Seite mit unserem heutigen Datum laden und beides zusammenführen.
     */
    fun today(): List<Fixture> {
        val cal = java.util.Calendar.getInstance()
        val dd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val mm = cal.get(java.util.Calendar.MONTH) + 1
        val yy = cal.get(java.util.Calendar.YEAR)
        val dated = "?start_dd=$dd&start_mm=$mm&start_yyyy=$yy&end_dd=$dd&end_mm=$mm&end_yyyy=$yy"
        val candidates = listOf(urls[0] + dated, urls[0], urls[1] + dated, urls[1])
        val errors = ArrayList<String>()
        val all = LinkedHashMap<String, Fixture>()
        var bytes = 0
        var pages = 0
        for (u in candidates) {
            if (pages >= 2) break
            val html = try {
                fetch(u)
            } catch (e: Exception) {
                errors.add(e.message ?: e.javaClass.simpleName)
                continue
            }
            pages++
            bytes += html.length
            if (lastHtml == null || u.contains("start_dd")) lastHtml = html
            parse(html).forEach { f -> all.putIfAbsent(Football.norm(f.home) + "|" + Football.norm(f.away), f) }
        }
        lastStatus = if (pages == 0) "nicht erreichbar (" + errors.joinToString(", ") + ")"
        else "${bytes / 1024} KB geladen, ${all.size} Spiele mit Sendern erkannt"
        return all.values.toList()
    }

    /** Zur Fehlersuche: Spiele bei liveonsat, in denen eine der beiden Mannschaften vorkommt. */
    fun similar(m: TodayMatch, all: List<Fixture>): List<Fixture> {
        val t = Football.tokens(m.home) + Football.tokens(m.away)
        return all.filter { f ->
            val s = Football.norm(f.home + " " + f.away)
            t.any { s.contains(it) }
        }.take(3)
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    private val blockTags = Regex("(?i)<\\s*(br|/div|/p|/tr|/td|/li|/h\\d|/a|/span|/table)[^>]*>")
    private val anyTag = Regex("<[^>]+>")
    private val scripts = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val matchLine = Regex("^(.{2,60}?)\\s+v\\.?s?\\.?\\s+(.{2,60})$", RegexOption.IGNORE_CASE)
    private val timeLine = Regex("(?i)^(st|ko|kick[- ]?off)?\\s*:?\\s*\\d{1,2}[:.]\\d{2}.*$")
    private val headerWords = Regex("(?i)\\b(week|round|matchday|spieltag|group|stage|qualif|play-?off|final|semi|quarter)\\b")

    private val entity = Regex("&(#\\d+|[a-zA-Z]+);")
    private fun decode(s: String) = s.replace(entity) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "nbsp" -> " "; "quot" -> "\""; "lt" -> "<"; "gt" -> ">"; "deg" -> "°"; "apos" -> "'"
            else -> if (e.startsWith("#")) e.drop(1).toIntOrNull()?.toChar()?.toString() ?: " " else " "
        }
    }

    // Versteckte Satelliten-Infos je Sender (Tooltip: Satellit, Position, Frequenz, Verschlüsselung) – keine Sender
    private val handlerAttrs = Regex("(?is)\\s(on\\w+|title|data-[\\w-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
    private val satNames = Regex(
        "(?i)^(thor|astra|hot ?bird|eutelsat|t[uü]rksat|badr|nilesat|arabsat|hispasat|amos|express|yamal|yahsat|eshail|es'hail|" +
            "intelsat|hellas ?sat|abs|galaxy|ses|telstar|apstar|asiasat|measat|thaicom|nss|bulgariasat|azerspace|belintersat|kazsat|" +
            "sirius|sky ?brasil|chinasat|optus|ch(i)?nasat|\\* ?iptv)\\b",
    )
    private val encryption = setOf(
        "conax", "irdeto", "nagravision", "nagra", "viaccess", "videoguard", "hd videoguard", "powervu", "biss", "biss-1", "biss-2",
        "free", "fta", "mediaguard", "cryptoworks", "verimatrix", "tandberg", "dre-crypt", "drecrypt", "betacrypt", "seca", "hd",
        "satellite", "pos", "freq", "symbol", "encryption", "sat", "frequency", "dvb-s", "dvb-s2", "live", "ku", "c-band",
    )
    private val satPos = Regex("^\\d{1,3}([.,]\\d)?\\s*°?\\s*[ewEW]\\b")
    private val techLine = Regex("^\\d[\\d\\s.,/:-]*([HVLRhvlr]\\b.*)?$")

    private fun isChannel(l: String): Boolean {
        val low = l.lowercase()
        if (low in encryption) return false
        if (l.contains('°') || satPos.containsMatchIn(l) || techLine.matches(l) || satNames.containsMatchIn(l)) return false
        if (Regex("\\d{4,5}\\s*[HV]\\b").containsMatchIn(l)) return false
        if (!l.any { it.isLetter() }) return false
        return true
    }

    fun parse(html: String): List<Fixture> {
        val text = html.replace(scripts, " ").replace(handlerAttrs, " ").replace(blockTags, "\n").replace(anyTag, " ")
        val lines = decode(text).lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Fixture>()
        var home: String? = null
        var away: String? = null
        val chans = ArrayList<String>()
        fun flush() {
            val h = home; val a = away
            if (h != null && a != null && chans.isNotEmpty()) out.add(Fixture(h, a, chans.distinct()))
            chans.clear()
        }
        for (l in lines) {
            val m = matchLine.matchEntire(l)
            if (m != null && !l.contains("http")) {
                flush()
                home = m.groupValues[1].trim()
                away = m.groupValues[2].trim()
                continue
            }
            if (home == null) continue
            if (timeLine.matches(l) || l.length > 50 || l.length < 2 || headerWords.containsMatchIn(l)) continue
            if (!isChannel(l)) continue
            if (chans.size < 400) chans.add(l)
        }
        flush()
        return out
    }

    // Sender außerhalb Europas (an Land/Region im Namen erkennbar) – werden nicht angezeigt
    private val nonEurope = Regex(
        "(?i)\\b(usa|us|canada|mena|arabia|arab|africa|asia|japan|india|malaysia|indonesia|thailand|vietnam|philippines|china|korea|" +
            "brasil|brazil|argentina|mexico|latam|latin|caribbean|persiana|iran|astro|azam|supersport|kenya|nigeria|ghana|australia|" +
            "new zealand|nz|singapore|hong kong|taiwan|peacock|fubo|espn\\+|paramount\\+|sling|kayo|optus|stan sport|bangladesh|pakistan|" +
            "israel|iraq|kurdistan|afghanistan|ethiopia|tanzania|uganda|zambia|mongolia|cambodia|myanmar|nepal|sri lanka|" +
            "chile|colombia|peru|ecuador|venezuela|uruguay|paraguay|bolivia|costa rica|honduras|guatemala|panama|el salvador)\\b",
    )

    fun isEuropean(channel: String): Boolean = !nonEurope.containsMatchIn(channel)

    private val priority = listOf("deutsch", "germany", "german", "türkiye", "turkiye", "turkey", "turkish", "austria", "österreich", "schweiz", "swiss")

    /** Europäische Sender, deutsch/türkisch zuerst. */
    fun europeanSorted(list: List<String>): List<String> =
        list.filter { isEuropean(it) }.sortedBy { c ->
            val low = c.lowercase()
            if (priority.any { low.contains(it) }) 0 else 1
        }

    /** Sender für ein Spiel des Tages (Zuordnung über die Mannschaftsnamen). */
    fun forMatch(m: TodayMatch, all: List<Fixture>): List<String> {
        val t1 = Football.tokens(m.home)
        val t2 = Football.tokens(m.away)
        return all.firstOrNull { f ->
            val h = Football.norm(f.home)
            val a = Football.norm(f.away)
            (t1.any { h.contains(it) } && t2.any { a.contains(it) }) || (t1.any { a.contains(it) } && t2.any { h.contains(it) })
        }?.channels.orEmpty()
    }
}
