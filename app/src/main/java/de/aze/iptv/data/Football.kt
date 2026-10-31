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

    private val genericFootball = listOf("fussball", "football", "futbol", "canli mac", "live-fussball", "livefussball")

    // Wettbewerbe, wie sie in Sendungstiteln stehen (vereinfacht geschrieben, siehe norm)
    private val leagueAliases = mapOf(
        "Bundesliga" to listOf("bundesliga"),
        "2. Bundesliga" to listOf("2. bundesliga", "2 bundesliga", "zweite liga"),
        "3. Liga" to listOf("3. liga", "3 liga"),
        "DFB-Pokal" to listOf("dfb-pokal", "dfb pokal"),
        "Süper Lig" to listOf("super lig", "superlig"),
        "1. Lig" to listOf("1. lig", "1 lig"),
        "Türkiye Kupası" to listOf("turkiye kupasi", "ziraat"),
        "Premier League" to listOf("premier league"),
        "LaLiga" to listOf("laliga", "la liga", "primera division"),
        "Serie A" to listOf("serie a"),
        "Ligue 1" to listOf("ligue 1"),
        "Champions League" to listOf("champions league"),
        "Europa League" to listOf("europa league"),
        "Conference League" to listOf("conference league"),
        "Eredivisie" to listOf("eredivisie"),
        "Liga Portugal" to listOf("liga portugal", "primeira liga"),
    )

    /**
     * Nennt der Sendungstitel einen ANDEREN Wettbewerb als den des Spiels?
     * ("Fußball: Premier League" ist nicht Fenerbahçe – Göztepe aus der Süper Lig.)
     */
    fun namesOtherLeague(title: String, league: String): Boolean {
        val own = leagueAliases[league].orEmpty()
        val others = leagueAliases.filterKeys { it != league }.values.flatten()
        // "2. bundesliga" enthält "bundesliga": der längere Name zählt
        val ownHit = own.any { a -> title.contains(a) && others.none { o -> o.length > a.length && o.contains(a) && title.contains(o) } }
        val otherHit = others.any { o -> title.contains(o) && own.none { a -> a.length > o.length && a.contains(o) && title.contains(a) } }
        return otherHit && !ownHit
    }

    private val footballWords = listOf("fussball", "football", "futbol", "liga", "lig ", "league", "cup", "kupa", "pokal", "mac", "canli")

    // ---- Sendernamen aus dem TV-Plan den eigenen Sendern zuordnen ----

    // Wörter, die beim Vergleich der Sendernamen nicht zählen (Qualität, Länder, liveonsat-Zusätze wie "($/geo/R)")
    private val nameNoise = setOf(
        "hd", "fhd", "uhd", "4k", "sd", "tv", "tr", "de", "at", "ch", "live", "geo", "r", "online", "bar", "streaming",
        "turkiye", "turkey", "germany", "deutschland", "deutsch", "austria", "osterreich", "schweiz", "swiss",
        "italia", "espana", "france", "belgium", "hungary", "slovenija", "bih", "srbija", "hrvatska", "romania", "ro", "polska",
        "baltic", "bulgaria", "suomi", "portugal", "nederland", "netherlands", "greece", "hellas", "cyprus", "uk", "ireland",
        "danmark", "denmark", "sverige", "norge", "czech", "cz", "slovakia", "sk", "albania", "kosovo", "macedonia", "mk",
    )

    private fun nameTokens(s: String): List<String> =
        norm(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in nameNoise }

    /** Eigene Sender mit vorbereiteten Namen und Land (einmal je Fenster). */
    class NameIndex(val items: List<Pair<NumberedChannel, String>>, val countries: List<String?> = emptyList())

    fun nameIndex(lineup: Lineup): NameIndex =
        NameIndex(
            lineup.all.map { it to " " + nameTokens(it.channel.name).joinToString(" ") + " " },
            lineup.all.map { Epg.countryOf(it.channel) },
        )

    // Länder in Sendernamen von liveonsat (z. B. "Disney+ Norge", "Sky Sport Deutschland", "beIN Sports Türkiye")
    private val listedCountryWords = mapOf(
        "norge" to "no", "norway" to "no", "deutschland" to "de", "germany" to "de", "deutsch" to "de",
        "turkiye" to "tr", "turkey" to "tr", "austria" to "at", "osterreich" to "at", "schweiz" to "ch", "swiss" to "ch",
        "uk" to "uk", "ireland" to "uk", "italia" to "it", "espana" to "es", "france" to "fr", "belgium" to "be",
        "polska" to "pl", "portugal" to "pt", "nederland" to "nl", "netherlands" to "nl", "danmark" to "dk", "denmark" to "dk",
        "sverige" to "se", "suomi" to "fi", "bih" to "ba", "srbija" to "rs", "hrvatska" to "hr", "slovenija" to "si",
        "hungary" to "hu", "romania" to "ro", "bulgaria" to "bg", "greece" to "gr", "hellas" to "gr", "cyprus" to "cy",
        "czech" to "cz", "slovakia" to "sk", "albania" to "al", "kosovo" to "xk", "macedonia" to "mk", "baltic" to "baltic",
    )

    /** Land eines Senders laut liveonsat-Namen (null = nicht angegeben). */
    fun listedCountry(listed: String): String? =
        norm(listed).split(Regex("[^a-z0-9]+")).firstNotNullOfOrNull { listedCountryWords[it] }

    /** Eigene Listen sind meist deutsch/türkisch: Sender ohne erkennbares Land gelten nur für diese Länder. */
    private val homeCountries = setOf("de", "tr", "at", "ch")

    /**
     * Eigene Sender, deren Name zum Sender aus dem TV-Plan passt (z. B. "beIN Sports 1" → "TR: BEIN SPORTS 1 HD").
     * Das Land muss passen: "Disney+ Norge" ist NICHT der türkische oder deutsche Disney-Sender.
     */
    fun matchByName(listed: String, idx: NameIndex): List<NumberedChannel> {
        val t = nameTokens(listed)
        if (t.isEmpty()) return emptyList()
        val lc = listedCountry(listed)
        return idx.items.indices.filter { i ->
            val n = idx.items[i].second
            if (!t.all { n.contains(" $it ") }) return@filter false
            val own = idx.countries.getOrNull(i)
            lc == null || own == lc || (own == null && lc in homeCountries)
        }.map { idx.items[it].first }.take(4)
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
        val possible = ArrayList<MatchChannel>()
        val leagueKey = norm(m.league)
        idx.items.forEach { (nc, progs) ->
            for ((p, t) in progs) {
                // Sendung muss den Anstoß abdecken (Vorberichte zählen mit)
                if (p.stop < m.kickoff + 20 * 60_000L || p.start > m.kickoff + 30 * 60_000L) continue
                val h1 = t1.any { t.contains(it) }
                val h2 = t2.any { t.contains(it) }
                val sure = h1 && h2
                val other = namesOtherLeague(t, m.league)
                val likely = (h1 || h2) && footballWords.any { t.contains(it) } && !other
                if (sure || likely) {
                    out.add(MatchChannel(nc, p, sure))
                    break
                }
                // Notlösung: zur Anstoßzeit läuft dort Fußball aus diesem Wettbewerb (ohne Mannschaftsnamen im Titel)
                if (!other && (t.contains(leagueKey) || genericFootball.any { t.contains(it) })) {
                    possible.add(MatchChannel(nc, p, false))
                    break
                }
            }
        }
        val sorted = out.sortedWith(compareBy({ !it.sure }, { it.channel.number }))
        return if (sorted.isNotEmpty()) sorted else possible.sortedBy { it.channel.number }.take(10)
    }
}
