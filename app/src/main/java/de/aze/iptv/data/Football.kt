package de.aze.iptv.data

import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Ein Spiel des Tages (Quelle wie bei SkorText: LiveScore). */
class TodayMatch(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    /** Anstoß in Millisekunden */
    val kickoff: Long,
    /** "" = noch nicht begonnen, sonst z. B. "67'", "HZ", "Ende" */
    val status: String,
    val score: String?,
    val live: Boolean,
)

/** Ein Sender, der laut Programmzeitschrift das Spiel zeigt. */
class MatchChannel(
    val channel: NumberedChannel,
    /** Sendung aus der Programmzeitschrift (null = laut TV-Plan) */
    val programme: Programme?,
    val sure: Boolean,
    /** Name des Senders laut TV-Plan, falls daher */
    val listed: String? = null,
)

/**
 * „Welches Spiel läuft wo?“
 *  1. Die Spiele des Tages kommen von derselben Quelle wie bei SkorText (LiveScore), nur die Ligen von SkorText.
 *  2. Für jedes Spiel wird in der Programmzeitschrift (EPG) der eigenen Sender gesucht, wer es zeigt:
 *     beide Mannschaften im Titel = sicher; eine Mannschaft + Fußball + passende Uhrzeit = wahrscheinlich.
 */
object Football {

    private val hosts = listOf("https://prod-public-api.livescore.com", "https://prod-cdn-public-api.livescore.com")

    /** Ligen wie in SkorText: Land, Wettbewerb (Anfang des Codes; leer = alle), Anzeigename */
    private val leagues = listOf(
        Triple("germany", "bundesliga", "Bundesliga"),
        Triple("germany", "2-bundesliga", "2. Bundesliga"),
        Triple("germany", "3-liga", "3. Liga"),
        Triple("germany", "dfb-cup", "DFB-Pokal"),
        Triple("turkey", "super-lig", "Süper Lig"),
        Triple("turkey", "1st-lig", "1. Lig"),
        Triple("turkey", "turkiye-cup", "Türkiye Kupası"),
        Triple("england", "premier-league", "Premier League"),
        Triple("spain", "laliga", "LaLiga"),
        Triple("italy", "serie-a", "Serie A"),
        Triple("france", "ligue-1", "Ligue 1"),
        Triple("champions-league", "", "Champions League"),
        Triple("europa-league", "", "Europa League"),
        Triple("conference-league", "", "Conference League"),
        Triple("holland", "eredivisie", "Eredivisie"),
        Triple("portugal", "primeira-liga", "Liga Portugal"),
    )

    private fun leagueOf(ccd: String, scd: String): String? {
        if (scd.contains("women")) return null
        return leagues.firstOrNull { (c, s, _) -> c == ccd && (s.isEmpty() || scd.startsWith(s)) }?.third
            ?: if (ccd.contains("nations-league") || (ccd.contains("qualification") && ccd.contains("uefa"))) "Länderspiel" else null
    }

    /** Spiele von heute (lokales Datum). Läuft im Hintergrund. */
    fun today(): List<TodayMatch> {
        val key = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Calendar.getInstance().time)
        val path = "/v1/api/app/date/soccer/$key/0?MD=1"
        var lastError: Exception? = null
        for (h in hosts) {
            try {
                return parse(JSONObject(Http.text(h + path)))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Keine Daten")
    }

    private val esd = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parse(o: JSONObject): List<TodayMatch> {
        val out = ArrayList<TodayMatch>()
        val stages = o.optJSONArray("Stages") ?: return out
        for (i in 0 until stages.length()) {
            val st = stages.getJSONObject(i)
            val league = leagueOf(st.optString("Ccd"), st.optString("Scd")) ?: continue
            val events = st.optJSONArray("Events") ?: continue
            for (k in 0 until events.length()) {
                val e = events.getJSONObject(k)
                val home = e.optJSONArray("T1")?.optJSONObject(0)?.optString("Nm").orEmpty()
                val away = e.optJSONArray("T2")?.optJSONObject(0)?.optString("Nm").orEmpty()
                if (home.isEmpty() || away.isEmpty()) continue
                val start = runCatching { esd.parse(e.optString("Esd").padEnd(14, '0'))?.time }.getOrNull() ?: continue
                val eps = e.optString("Eps").trim()
                val (status, live) = when {
                    eps == "NS" || eps.isEmpty() -> "" to false
                    eps == "HT" -> "HZ" to true
                    eps in listOf("FT", "AET", "AP", "Pen.", "FT Pen", "After Pen.") -> "Ende" to false
                    eps in listOf("Postp.", "Canc.", "Aband.", "Postponed", "Cancelled") -> "abgesagt" to false
                    else -> eps to true
                }
                val s1 = e.optString("Tr1"); val s2 = e.optString("Tr2")
                out.add(
                    TodayMatch(
                        id = e.optString("Eid"), league = league, home = fix(home), away = fix(away),
                        kickoff = start, status = status, score = if (s1.isNotEmpty() && s2.isNotEmpty()) "$s1:$s2" else null, live = live,
                    ),
                )
            }
        }
        return out.sortedWith(compareBy({ it.kickoff }, { it.league }))
    }

    // Häufige englische Schreibweisen → so, wie sie im deutschen/türkischen Programm stehen
    private val nameFix = listOf(
        "Bayern Munich" to "Bayern München", "FC Cologne" to "1. FC Köln", "Cologne" to "Köln", "Nuremberg" to "Nürnberg",
        "Monchengladbach" to "Mönchengladbach", "Dusseldorf" to "Düsseldorf", "Besiktas" to "Beşiktaş", "Fenerbahce" to "Fenerbahçe",
        "Basaksehir" to "Başakşehir", "Kasimpasa" to "Kasımpaşa", "Goztepe" to "Göztepe", "Caykur" to "Çaykur", "Eyupspor" to "Eyüpspor",
        "Genclerbirligi" to "Gençlerbirliği", "Karagumruk" to "Karagümrük", "Atletico Madrid" to "Atlético Madrid",
    )

    private fun fix(n: String): String {
        var s = n
        nameFix.forEach { (a, b) -> if (s.contains(a)) s = s.replace(a, b) }
        return s
    }

    // ---- Zuordnung zur Programmzeitschrift ----

    private val generic = setOf(
        "fc", "sc", "sv", "afc", "cf", "ac", "as", "ss", "ssc", "us", "rc", "rcd", "cd", "ud", "sd", "fk", "sk", "bk", "if", "aik",
        "club", "spor", "kulubu", "calcio", "united", "city", "real", "sporting", "athletic", "1", "04", "05", "09", "96", "98",
        "tsg", "vfb", "vfl", "rb", "fsv", "sv", "ksc", "1899", "1846", "1860", "1907", "de", "la", "le",
    )

    private val marks = Regex("\\p{Mn}+")

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT).replace('ı', 'i').replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(marks, "")

    /** Aussagekräftige Wörter eines Vereinsnamens, z. B. "Borussia Dortmund" → [borussia, dortmund]. */
    fun tokens(team: String): List<String> =
        norm(team).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in generic }
            .ifEmpty { listOf(norm(team).replace(Regex("[^a-z0-9]"), "")) }

    private val footballWords = listOf("fussball", "football", "futbol", "liga", "lig ", "league", "cup", "kupa", "pokal", "mac", "canli")

    // ---- Sendernamen aus dem TV-Plan den eigenen Sendern zuordnen ----

    private val nameNoise = setOf("hd", "fhd", "uhd", "4k", "sd", "tv", "tr", "de", "at", "ch", "live", "turkiye", "turkey", "germany", "deutschland")

    private fun nameTokens(s: String): List<String> =
        norm(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in nameNoise }

    /** Eigene Sender mit vorbereiteten Namen (einmal je Fenster). */
    class NameIndex(val items: List<Pair<NumberedChannel, String>>)

    fun nameIndex(lineup: Lineup): NameIndex =
        NameIndex(lineup.all.map { it to " " + nameTokens(it.channel.name).joinToString(" ") + " " })

    /** Eigene Sender, deren Name zum Sender aus dem TV-Plan passt (z. B. "beIN Sports 1" → "TR: BEIN SPORTS 1 HD"). */
    fun matchByName(listed: String, idx: NameIndex): List<NumberedChannel> {
        val t = nameTokens(listed)
        if (t.isEmpty()) return emptyList()
        return idx.items.filter { (_, n) -> t.all { n.contains(" $it ") } }.map { it.first }.take(4)
    }

    /** Vorbereitete Programmzeitschrift: je Sender die Sendungen des Tages mit vereinfachtem Titel. */
    class EpgIndex(val items: List<Pair<NumberedChannel, List<Pair<Programme, String>>>>)

    fun index(epg: Epg, lineup: Lineup, from: Long, to: Long): EpgIndex =
        EpgIndex(
            lineup.all.mapNotNull { nc ->
                val progs = epg.programmes(nc.channel)?.filter { it.stop > from && it.start < to } ?: return@mapNotNull null
                if (progs.isEmpty()) null else nc to progs.map { it to norm(it.title) }
            },
        )

    /** Sender, die das Spiel laut Programmzeitschrift zeigen (sichere zuerst). */
    fun channelsFor(m: TodayMatch, idx: EpgIndex): List<MatchChannel> {
        val t1 = tokens(m.home)
        val t2 = tokens(m.away)
        val out = ArrayList<MatchChannel>()
        idx.items.forEach { (nc, progs) ->
            for ((p, t) in progs) {
                // Sendung muss den Anstoß abdecken (Vorberichte zählen mit)
                if (p.stop < m.kickoff + 20 * 60_000L || p.start > m.kickoff + 30 * 60_000L) continue
                val h1 = t1.any { t.contains(it) }
                val h2 = t2.any { t.contains(it) }
                val sure = h1 && h2
                val likely = (h1 || h2) && footballWords.any { t.contains(it) }
                if (sure || likely) {
                    out.add(MatchChannel(nc, p, sure))
                    break
                }
            }
        }
        return out.sortedWith(compareBy({ !it.sure }, { it.channel.number }))
    }
}
