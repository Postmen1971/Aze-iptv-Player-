package de.aze.iptv.data

import org.json.JSONObject
import java.net.URLEncoder

/** IMDb-Bewertung über omdbapi.com (braucht einen kostenlosen eigenen Schlüssel). */
object Omdb {

    class Rating(val imdb: String, val votes: String?)

    private val cache = HashMap<String, Rating?>()

    // "TR: Titel", "[DE] Titel", "4K-TR | Titel" → "Titel"; "(2017)" und Jahreszahl am Ende weg
    private val prefix = Regex("""^(\[[^\]]{1,8}\]|[A-Z0-9]{2,5}(-[A-Z]{2})?\s*[:|\-])\s*""")
    private val year = Regex("""\s*[\(\[]?(19|20)\d{2}[\)\]]?\s*$""")

    fun clean(title: String): String = title.trim().replace(prefix, "").replace(year, "").trim()

    /** Läuft im Hintergrund. null = nicht gefunden. */
    fun rating(key: String, title: String, original: String?, yearHint: String?): Rating? {
        val names = listOfNotNull(original?.let { clean(it) }, clean(title)).filter { it.isNotBlank() }.distinct()
        val y = yearHint?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val cacheKey = names.joinToString("|") + "|" + y
        synchronized(cache) { if (cache.containsKey(cacheKey)) return cache[cacheKey] }
        var result: Rating? = null
        loop@ for (n in names) {
            for (withYear in listOf(true, false)) {
                if (withYear && y == null) continue
                val url = "https://www.omdbapi.com/?apikey=${URLEncoder.encode(key, "UTF-8")}&t=${URLEncoder.encode(n, "UTF-8")}" +
                    (if (withYear) "&y=$y" else "")
                val o = runCatching { JSONObject(Http.text(url)) }.getOrNull() ?: continue
                if (o.optString("Response") != "True") continue
                val r = o.optString("imdbRating").takeIf { it.isNotBlank() && it != "N/A" } ?: continue
                result = Rating(r, o.optString("imdbVotes").takeIf { it.isNotBlank() && it != "N/A" })
                break@loop
            }
        }
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }
}
