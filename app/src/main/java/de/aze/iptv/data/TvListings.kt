package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * TV-Übertragungen des Tages aus einer öffentlichen Sport-Datenbank (TheSportsDB, frei nutzbarer Test-Zugang).
 * Liefert je Spiel die Sender, die es laut Plan zeigen – auch solche, die nicht in der eigenen Liste sind.
 * Klappt der Abruf nicht, bleibt die Liste einfach leer (die Programmzeitschrift wird trotzdem durchsucht).
 */
object TvListings {

    class Broadcast(val event: String, val channel: String, val country: String)

    private val keys = listOf("123", "3")

    fun today(): List<Broadcast> {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Calendar.getInstance().time)
        for (k in keys) {
            val list = runCatching {
                parse(Http.text("https://www.thesportsdb.com/api/v1/json/$k/eventstv.php?d=$d&s=Soccer"))
            }.getOrNull()
            if (!list.isNullOrEmpty()) return list
        }
        return emptyList()
    }

    private fun parse(text: String): List<Broadcast> {
        val o = JSONObject(text)
        // Name des Feldes je nach Version unterschiedlich – erstes Array nehmen
        val arr: JSONArray = o.keys().asSequence().mapNotNull { o.optJSONArray(it) }.firstOrNull() ?: return emptyList()
        val out = ArrayList<Broadcast>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val sport = e.optString("strSport")
            if (sport.isNotEmpty() && !sport.equals("Soccer", true)) continue
            val ev = e.optString("strEvent").takeIf { it.isNotBlank() } ?: continue
            val ch = e.optString("strChannel").takeIf { it.isNotBlank() } ?: continue
            out.add(Broadcast(ev, ch.trim(), e.optString("strCountry").trim()))
        }
        return out
    }

    private val countryOrder = listOf("germany", "turkey", "austria", "switzerland")

    /** Sender laut Plan für ein Spiel – deutschsprachige und türkische zuerst. */
    fun forMatch(m: TodayMatch, all: List<Broadcast>): List<Broadcast> {
        val t1 = Football.tokens(m.home)
        val t2 = Football.tokens(m.away)
        return all.filter { b ->
            val t = Football.norm(b.event)
            t1.any { t.contains(it) } && t2.any { t.contains(it) }
        }.distinctBy { it.channel.lowercase() + it.country.lowercase() }
            .sortedBy { b -> countryOrder.indexOf(b.country.lowercase()).let { if (it < 0) 99 else it } }
    }
}
