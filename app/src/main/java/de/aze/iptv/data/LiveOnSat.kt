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

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'")
        .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    fun parse(html: String): List<Fixture> {
        val text = html.replace(scripts, " ").replace(blockTags, "\n").replace(anyTag, " ")
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
            // Sat-Angaben (Frequenzen, Positionen) überspringen, nur Sendernamen behalten
            if (Regex("\\d{4,5}\\s*[HV]\\b").containsMatchIn(l) || Regex("\\d+(\\.\\d)?°").containsMatchIn(l)) continue
            chans.add(l)
            if (chans.size > 60) flush()
        }
        flush()
        return out
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
