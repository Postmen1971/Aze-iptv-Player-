package de.aze.iptv.data

import android.content.Context
import android.content.SharedPreferences
import de.aze.iptv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Ersatz-Cover von TMDB (The Movie Database), wenn das Cover des Anbieters fehlt, kaputt oder zu langsam ist.
 * Der Schlüssel kommt beim Bauen aus dem GitHub-Secret TMDB_KEY (trägt Aziz selbst ein).
 * Ergebnisse (auch „nichts gefunden“) werden auf dem Gerät gemerkt → jeder Titel wird nur einmal gesucht.
 */
object Tmdb {
    val enabled: Boolean get() = BuildConfig.TMDB_KEY.isNotEmpty()

    private var prefs: SharedPreferences? = null
    private val mem = ConcurrentHashMap<String, String>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("tmdb", Context.MODE_PRIVATE)
    }

    /** Anbieter-Cover, das schon einmal nicht kam → gleich TMDB nehmen */
    fun replacementFor(providerUrl: String): String? = prefs?.getString("u:$providerUrl", null)?.takeIf { it.isNotEmpty() }
    fun rememberReplacement(providerUrl: String, tmdbUrl: String) {
        prefs?.edit()?.putString("u:$providerUrl", tmdbUrl)?.apply()
    }

    /** Titel aufräumen: „DE - Titel (2019) [4K]“ → „Titel“ */
    internal fun clean(name: String): Pair<String, String?> {
        var n = name.trim()
        val year = Regex("""[(\[]?\b(19\d{2}|20\d{2})\b[)\]]?""").findAll(n).lastOrNull()?.groupValues?.get(1)
        n = n.replace(Regex("""^\s*[|\[(]?\s*[A-Z]{2,3}(-[A-Z]{2})?\s*[|\])]?\s*[-:|]\s*"""), "") // „DE - “, „|TR| “, „EN: “
        n = n.replace(Regex("""^\s*[|\[][A-Z]{2,3}[|\]]\s*"""), "")
        n = n.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")                                        // (2019), [4K], (Multi-Sub)
        n = n.replace(Regex("""\b(19|20)\d{2}\b"""), " ")
        n = n.replace(Regex("""(?i)\b(4k|uhd|fhd|hd|sd|hevc|h265|x265|multi[- ]?sub|multi|dual|altyaz[ıi]l[ıi]|t[üu]rk[çc]e dublaj|dublaj)\b"""), " ")
        n = n.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '|')
        return n to year
    }

    /** Poster-Adresse bei TMDB suchen (klein, w185). null = nichts gefunden oder TMDB nicht eingerichtet. */
    suspend fun poster(name: String, series: Boolean): String? {
        if (!enabled) return null
        val (title, year) = clean(name)
        if (title.length < 2) return null
        val key = (if (series) "s:" else "m:") + title.lowercase() + "|" + (year ?: "")
        mem[key]?.let { return it.ifEmpty { null } }
        prefs?.getString(key, null)?.let { mem[key] = it; return it.ifEmpty { null } }
        val found = withContext(Dispatchers.IO) {
            search(title, year, series) ?: if (year != null) search(title, null, series) else null
        }
        val url = found?.let { "https://image.tmdb.org/t/p/w185$it" } ?: ""
        mem[key] = url
        prefs?.edit()?.putString(key, url)?.apply()
        return url.ifEmpty { null }
    }

    /**
     * Weitere Trailer (YouTube-IDs) von TMDB – deutsch zuerst, dann türkisch, dann englisch; Trailer vor Teasern.
     * Aziz 26.11.: der Trailer des Anbieters war „in deinem Land nicht verfügbar“ – deutsche Trailer der Verleiher
     * laufen hier. Erfolgreiche Antworten werden gemerkt (auch „keine“), Netzfehler nicht.
     */
    suspend fun trailers(tmdbId: String?, name: String, series: Boolean): List<String> {
        if (!enabled) return emptyList()
        val kind = if (series) "tv" else "movie"
        val (title, year) = clean(name)
        val key = "t:$kind:" + (tmdbId ?: (title.lowercase() + "|" + (year ?: "")))
        (mem[key] ?: prefs?.getString(key, null))?.let { mem[key] = it; return it.split(',').filter { s -> s.isNotBlank() } }
        if (tmdbId == null && title.length < 2) return emptyList()
        val found = withContext(Dispatchers.IO) {
            val id = tmdbId ?: searchId(title, year, series) ?: (if (year != null) searchId(title, null, series) else null)
                ?: return@withContext emptyList<String>()
            videos(kind, id)
        } ?: return emptyList()
        val s = found.joinToString(",")
        mem[key] = s
        prefs?.edit()?.putString(key, s)?.apply()
        return found
    }

    /** null = Netzfehler (nicht merken) */
    private fun videos(kind: String, id: String): List<String>? = runCatching {
        val url = "https://api.themoviedb.org/3/$kind/$id/videos?api_key=${BuildConfig.TMDB_KEY}&language=de-DE&include_video_language=de,tr,en,null"
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 404) return@use emptyList()
            if (!r.isSuccessful) return@use null
            rankTrailers(JSONObject(r.body?.string() ?: return@use null).optJSONArray("results"))
        }
    }.getOrNull()

    /** YouTube-Videos ordnen: Sprache (de, tr, en, andere), Art (Trailer, Teaser, Rest), offizielle zuerst; höchstens 5 */
    internal fun rankTrailers(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val langRank = mapOf("de" to 0, "tr" to 1, "en" to 2)
        val typeRank = mapOf("Trailer" to 0, "Teaser" to 1)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("site").equals("YouTube", true) && Regex("^[A-Za-z0-9_-]{11}$").matches(it.optString("key")) }
            .filter { it.optString("type") in setOf("Trailer", "Teaser", "Clip", "Featurette") }
            .sortedWith(
                compareBy<JSONObject>(
                    { langRank[it.optString("iso_639_1")] ?: 3 },
                    { typeRank[it.optString("type")] ?: 2 },
                    { if (it.optBoolean("official")) 0 else 1 },
                ),
            )
            .map { it.optString("key") }.distinct().take(5)
    }

    private fun searchId(title: String, year: String?, series: Boolean): String? = runCatching {
        val q = URLEncoder.encode(title, "UTF-8")
        val y = when {
            year == null -> ""
            series -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        val url = "https://api.themoviedb.org/3/search/${if (series) "tv" else "movie"}?api_key=${BuildConfig.TMDB_KEY}&language=de-DE&include_adult=false&query=$q$y"
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use null
            JSONObject(r.body?.string() ?: return@use null).optJSONArray("results")?.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 }?.toString()
        }
    }.getOrNull()

    private fun search(title: String, year: String?, series: Boolean): String? = runCatching {
        val q = URLEncoder.encode(title, "UTF-8")
        val y = when {
            year == null -> ""
            series -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        val url = "https://api.themoviedb.org/3/search/${if (series) "tv" else "movie"}?api_key=${BuildConfig.TMDB_KEY}&language=de-DE&include_adult=false&query=$q$y"
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val arr = JSONObject(r.body?.string() ?: return@use null).optJSONArray("results") ?: return@use null
            (0 until minOf(arr.length(), 5)).asSequence()
                .mapNotNull { arr.optJSONObject(it)?.optString("poster_path")?.takeIf { p -> p.startsWith("/") } }
                .firstOrNull()
        }
    }.getOrNull()
}
