package de.aze.iptv.data

import java.util.Locale

/** Ein Fußballspiel aus der Programmzeitschrift mit allen Sendern, die es zeigen. */
class FootballMatch(val programme: Programme, val channels: List<NumberedChannel>)

/**
 * „Welches Spiel läuft wo?“ – sucht in der Programmzeitschrift (EPG) der eigenen Sender nach Fußball.
 * Es wird nur die eigene Senderliste durchsucht, also findet man genau die Spiele, die man auch schauen kann.
 */
object Football {

    private val keywords = listOf(
        "fußball", "fussball", "football", "futbol", "soccer",
        "bundesliga", "2. liga", "3. liga", "dfb-pokal", "dfb pokal", "supercup",
        "süper lig", "super lig", "1. lig", "trendyol", "türkiye kupası", "ziraat",
        "champions league", "şampiyonlar ligi", "europa league", "avrupa ligi", "conference league", "konferans ligi",
        "uefa", "fifa", "nations league", "uluslar ligi", "wm-qualifikation", "em-qualifikation",
        "dünya kupası", "avrupa şampiyonası", "weltmeisterschaft", "europameisterschaft",
        "premier league", "laliga", "la liga", "serie a", "ligue 1", "eredivisie", "primeira liga",
        "copa del rey", "fa cup", "coppa italia", "maçı", "canlı maç",
    )

    // Kein Fußball, auch wenn ein Stichwort passt
    private val excluded = listOf("basketbol", "basketball", "voleybol", "volleyball", "handball", "hentbol", "american football", "nfl", "futsal")

    fun isFootball(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        if (excluded.any { t.contains(it) }) return false
        return keywords.any { t.contains(it) }
    }

    /** Laufende und kommende Spiele, nach Anstoß sortiert; gleiche Sendung auf mehreren Sendern zusammengefasst. */
    fun find(epg: Epg, lineup: Lineup, now: Long = System.currentTimeMillis()): List<FootballMatch> {
        val map = LinkedHashMap<String, Pair<Programme, MutableList<NumberedChannel>>>()
        lineup.all.forEach { nc ->
            epg.programmes(nc.channel)?.forEach { p ->
                if (p.stop > now && isFootball(p.title)) {
                    val k = p.title.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() } + "|" + p.start / 60_000
                    val entry = map.getOrPut(k) { p to ArrayList() }
                    if (entry.second.none { it.channel.key == nc.channel.key }) entry.second.add(nc)
                }
            }
        }
        return map.values.map { FootballMatch(it.first, it.second) }.sortedBy { it.programme.start }
    }
}
