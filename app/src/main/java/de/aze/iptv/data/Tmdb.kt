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
