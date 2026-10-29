package de.aze.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class VodCategory(val id: String, val name: String)

/** Ein Film oder eine Serie in der Übersicht. */
class VodItem(
    val id: String,
    val name: String,
    val poster: String?,
    val rating: String?,
    val year: String?,
    /** Dateiendung für Filme (mp4, mkv …) */
    val ext: String,
)

class VodDetails(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val duration: String?,
    val backdrop: String?,
    val year: String?,
    val rating: String?,
    /** Originaltitel (für die IMDb-Suche) */
    val originalName: String? = null,
)

class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
)

class SeriesDetails(val info: VodDetails, val seasons: Map<Int, List<Episode>>)

/** Quelle für Filme & Serien – Xtream-Schnittstelle oder Einträge aus einer M3U-Liste. */
interface VodProvider {
    suspend fun movieCategories(): List<VodCategory>
    suspend fun seriesCategories(): List<VodCategory>
    suspend fun movies(categoryId: String): List<VodItem>
    suspend fun series(categoryId: String): List<VodItem>
    /** Anzahl je Kategorie-id */
    suspend fun counts(series: Boolean): Map<String, Int>
    /** Alle Filme bzw. Serien (für die Suche) */
    suspend fun all(series: Boolean): List<VodItem>
    suspend fun movieDetails(id: String): VodDetails
    suspend fun seriesDetails(id: String): SeriesDetails
    fun movieUrl(item: VodItem): String
    fun episodeUrl(e: Episode): String
    /** Woher die Filme/Serien kommen (für Hinweise, wenn nichts gefunden wird) */
    fun sourceInfo(): String = ""
}

/**
 * Filme und Serien über die Xtream-Schnittstelle des Anbieters (player_api.php).
 * Geladen wird immer nur eine Kategorie bzw. ein Titel – so bleibt es auch auf schwachen Sticks flüssig.
 */
class XtreamVod(private val src: PlaylistSource) : VodProvider {

    private val base: String = src.url.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }
    private val api = "$base/player_api.php?username=${enc(src.user.trim())}&password=${enc(src.pass)}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun arr(action: String, extra: String = ""): JSONArray {
        val t = Http.text("$api&action=$action$extra").trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    private fun obj(action: String, extra: String): JSONObject = JSONObject(Http.text("$api&action=$action$extra"))

    private fun JSONObject.str(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    override suspend fun movieCategories(): List<VodCategory> = withContext(Dispatchers.IO) { cats("get_vod_categories") }
    override suspend fun seriesCategories(): List<VodCategory> = withContext(Dispatchers.IO) { cats("get_series_categories") }

    private fun cats(action: String): List<VodCategory> {
        val a = arr(action)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            VodCategory(o.optString("category_id"), o.optString("category_name"))
        }
    }

    /**
     * Anzahl der Filme bzw. Serien je Kategorie. Liest die Gesamtliste Stück für Stück,
     * damit auch sehr große Anbieter-Listen wenig Speicher brauchen.
     */
    override suspend fun counts(series: Boolean): Map<String, Int> = withContext(Dispatchers.IO) {
        val conn = Http.open("$api&action=${if (series) "get_series" else "get_vod_streams"}")
        try {
            android.util.JsonReader(conn.inputStream.bufferedReader()).use { r ->
                val m = HashMap<String, Int>()
                if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return@use m
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    r.beginObject()
                    var cat: String? = null
                    while (r.hasNext()) {
                        val n = r.nextName()
                        val t = r.peek()
                        if (n == "category_id" && (t == android.util.JsonToken.STRING || t == android.util.JsonToken.NUMBER)) cat = r.nextString()
                        else r.skipValue()
                    }
                    r.endObject()
                    cat?.let { m[it] = (m[it] ?: 0) + 1 }
                }
                m
            }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun movies(categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        streamItems("get_vod_streams", "&category_id=${enc(categoryId)}", series = false)
    }

    override suspend fun series(categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        streamItems("get_series", "&category_id=${enc(categoryId)}", series = true)
    }

    private val allCache = HashMap<Boolean, List<VodItem>>()

    override suspend fun all(series: Boolean): List<VodItem> = withContext(Dispatchers.IO) {
        synchronized(allCache) { allCache[series] } ?: streamItems(if (series) "get_series" else "get_vod_streams", "", series)
            .also { synchronized(allCache) { allCache[series] = it } }
    }

    /** Liste Stück für Stück lesen (große Anbieter liefern zehntausende Einträge). */
    private fun streamItems(action: String, extra: String, series: Boolean): List<VodItem> {
        val conn = Http.open("$api&action=$action$extra")
        try {
            android.util.JsonReader(conn.inputStream.bufferedReader()).use { r ->
                val out = ArrayList<VodItem>()
                if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return out
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    r.beginObject()
                    val f = HashMap<String, String>()
                    while (r.hasNext()) {
                        val n = r.nextName()
                        val t = r.peek()
                        if (t == android.util.JsonToken.STRING || t == android.util.JsonToken.NUMBER) {
                            if (n in WANTED) f[n] = r.nextString() else r.skipValue()
                        } else r.skipValue()
                    }
                    r.endObject()
                    fun g(k: String) = f[k]?.takeIf { it.isNotBlank() && it != "null" }
                    val id = g(if (series) "series_id" else "stream_id") ?: continue
                    out.add(
                        VodItem(
                            id = id,
                            name = f["name"].orEmpty(),
                            poster = g(if (series) "cover" else "stream_icon"),
                            // Bewertung von 0–10; manche Anbieter liefern nur die 5er-Skala
                            rating = g("rating")?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0 }
                                ?: g("rating_5based")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.ROOT, it * 2) },
                            year = g("year") ?: g("releaseDate")?.take(4),
                            ext = if (series) "" else g("container_extension") ?: "mp4",
                        ),
                    )
                }
                return out
            }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        val WANTED = setOf("stream_id", "series_id", "name", "stream_icon", "cover", "rating", "rating_5based", "year", "releaseDate", "container_extension")
    }

    override suspend fun movieDetails(id: String): VodDetails = withContext(Dispatchers.IO) {
        val info = obj("get_vod_info", "&vod_id=${enc(id)}").optJSONObject("info") ?: JSONObject()
        details(info)
    }

    override suspend fun seriesDetails(id: String): SeriesDetails = withContext(Dispatchers.IO) {
        val o = obj("get_series_info", "&series_id=${enc(id)}")
        val info = details(o.optJSONObject("info") ?: JSONObject())
        val seasons = sortedMapOf<Int, List<Episode>>()
        val eps = o.optJSONObject("episodes")
        eps?.keys()?.forEach { key ->
            val list = eps.optJSONArray(key) ?: return@forEach
            val s = key.toIntOrNull() ?: 0
            seasons[s] = (0 until list.length()).map { i ->
                val e = list.getJSONObject(i)
                val ei = e.optJSONObject("info") ?: JSONObject()
                Episode(
                    id = e.optString("id"),
                    title = e.optString("title"),
                    season = s,
                    number = e.optInt("episode_num", i + 1),
                    ext = e.str("container_extension") ?: "mp4",
                    plot = ei.str("plot"),
                    image = ei.str("movie_image"),
                    duration = ei.str("duration"),
                )
            }.sortedBy { it.number }
        }
        SeriesDetails(info, seasons)
    }

    private fun details(i: JSONObject): VodDetails {
        val backdrop = i.optJSONArray("backdrop_path")?.let { if (it.length() > 0) it.optString(0) else null }
            ?: i.str("backdrop_path")
        return VodDetails(
            plot = i.str("plot") ?: i.str("description"),
            genre = i.str("genre"),
            cast = i.str("cast") ?: i.str("actors"),
            director = i.str("director"),
            duration = i.str("duration"),
            backdrop = backdrop?.takeIf { it.startsWith("http") },
            year = i.str("releasedate")?.take(4) ?: i.str("releaseDate")?.take(4) ?: i.str("year"),
            rating = i.str("rating")?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0 }
                ?: i.str("rating_5based")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.ROOT, it * 2) },
            originalName = i.str("o_name") ?: i.str("original_name"),
        )
    }

    override fun sourceInfo() = "Anbieter-Schnittstelle (Server + Login)"

    override fun movieUrl(item: VodItem) = "$base/movie/${src.user.trim()}/${src.pass}/${item.id}.${item.ext}"
    override fun episodeUrl(e: Episode) = "$base/series/${src.user.trim()}/${src.pass}/${e.id}.${e.ext}"
}


/**
 * Filme & Serien direkt aus einer M3U-Liste (Einträge mit /movie/, /series/ oder Video-Dateien).
 * Kategorien = Gruppen der Liste; Serienfolgen werden über "S01 E02" im Namen zusammengefasst.
 */
class M3uVod(private val file: java.io.File) : VodProvider {

    private class SeriesAcc(val id: String, val name: String, val poster: String?) {
        val episodes = ArrayList<Episode>()
    }

    private class Data(
        val movieCats: List<VodCategory>,
        val movies: Map<String, List<VodItem>>,
        val seriesCats: List<VodCategory>,
        val series: Map<String, List<VodItem>>,
        val seriesById: Map<String, SeriesAcc>,
        val urls: Map<String, String>,
    )

    @Volatile private var data: Data? = null

    private fun load(): Data = data ?: synchronized(this) {
        data ?: parse().also { data = it }
    }

    private val attr = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val episodeRx = Regex("""^(.*?)[\s._\-|:]*[Ss](\d{1,2})\s*[Ee](\d{1,3})""")
    private val videoExt = Regex("""\.(mp4|mkv|avi|mov|m4v|wmv|flv|mpg|mpeg)(\?.*)?$""", RegexOption.IGNORE_CASE)

    private fun parse(): Data {
        val movieCats = LinkedHashMap<String, MutableList<VodItem>>()
        val seriesCats = LinkedHashMap<String, LinkedHashMap<String, SeriesAcc>>()
        val seriesById = HashMap<String, SeriesAcc>()
        val urls = HashMap<String, String>()
        var name: String? = null
        var group = ""
        var logo: String? = null
        var n = 0
        if (file.exists()) file.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF", true) -> {
                        val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] } }
                        group = (a["group-title"] ?: "").trim().ifEmpty { "Ohne Kategorie" }
                        logo = a["tvg-logo"]?.takeIf { it.isNotBlank() }
                        name = titleOf(line).ifBlank { a["tvg-name"].orEmpty() }.trim()
                    }
                    line.isEmpty() || line.startsWith("#") -> Unit
                    else -> {
                        val nm = name
                        name = null
                        if (nm == null) return@forEach
                        val isSeriesUrl = line.contains("/series/")
                        val isMovieUrl = line.contains("/movie/")
                        val isFile = videoExt.containsMatchIn(line) && !line.contains("/live/")
                        if (!isSeriesUrl && !isMovieUrl && !isFile) return@forEach
                        n++
                        val ep = episodeRx.find(nm)
                        if (isSeriesUrl || (ep != null && !isMovieUrl)) {
                            val sName = ep?.groupValues?.get(1)?.trim()?.trimEnd('-', ':', '|')?.trim()?.ifEmpty { null } ?: nm
                            val cat = seriesCats.getOrPut(group) { LinkedHashMap() }
                            val acc = cat.getOrPut(sName) {
                                SeriesAcc("s$n", sName, logo).also { seriesById[it.id] = it }
                            }
                            val eid = "e$n"
                            urls[eid] = line
                            acc.episodes.add(
                                Episode(
                                    id = eid,
                                    title = nm,
                                    season = ep?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                                    number = ep?.groupValues?.get(3)?.toIntOrNull() ?: (acc.episodes.size + 1),
                                    ext = "",
                                    plot = null, image = logo, duration = null,
                                ),
                            )
                        } else {
                            val id = "m$n"
                            urls[id] = line
                            movieCats.getOrPut(group) { ArrayList() }.add(VodItem(id, nm, logo, null, null, ""))
                        }
                    }
                }
            }
        }
        val mc = movieCats.keys.mapIndexed { i, g -> VodCategory("$i", g) }
        val sc = seriesCats.keys.mapIndexed { i, g -> VodCategory("$i", g) }
        return Data(
            movieCats = mc,
            movies = mc.associate { it.id to movieCats[it.name].orEmpty() },
            seriesCats = sc,
            series = sc.associate { c -> c.id to seriesCats[c.name].orEmpty().values.map { VodItem(it.id, it.name, it.poster, null, null, "") } },
            seriesById = seriesById,
            urls = urls,
        )
    }

    private fun titleOf(line: String): String {
        var q = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') q = !q
            if (c == ',' && !q) return line.substring(i + 1)
        }
        return ""
    }

    override suspend fun movieCategories() = withContext(Dispatchers.IO) { load().movieCats }
    override suspend fun seriesCategories() = withContext(Dispatchers.IO) { load().seriesCats }
    override suspend fun movies(categoryId: String) = withContext(Dispatchers.IO) { load().movies[categoryId].orEmpty() }
    override suspend fun series(categoryId: String) = withContext(Dispatchers.IO) { load().series[categoryId].orEmpty() }
    override suspend fun counts(series: Boolean) = withContext(Dispatchers.IO) {
        val d = load()
        (if (series) d.series else d.movies).mapValues { it.value.size }
    }
    override suspend fun all(series: Boolean) = withContext(Dispatchers.IO) {
        val d = load()
        (if (series) d.series else d.movies).values.flatten()
    }

    private val empty = VodDetails(null, null, null, null, null, null, null, null)
    override suspend fun movieDetails(id: String) = empty
    override suspend fun seriesDetails(id: String) = withContext(Dispatchers.IO) {
        val acc = load().seriesById[id]
        val seasons = sortedMapOf<Int, List<Episode>>()
        acc?.episodes?.groupBy { it.season }?.forEach { (s, l) -> seasons[s] = l.sortedBy { it.number } }
        SeriesDetails(empty, seasons)
    }

    override fun sourceInfo(): String {
        val d = data ?: return "Einträge aus der M3U-Liste"
        return "Einträge aus der M3U-Liste: ${d.movies.values.sumOf { it.size }} Filme, ${d.seriesById.size} Serien"
    }

    override fun movieUrl(item: VodItem) = load().urls[item.id].orEmpty()
    override fun episodeUrl(e: Episode) = load().urls[e.id].orEmpty()
}

/**
 * Für M3U-Playlists: Steckt hinter der Liste ein Xtream-Anbieter (erkennbar an den Sender-Links),
 * kommen Filme & Serien mit Postern und Beschreibung über dessen Schnittstelle.
 * Sonst werden die Film-/Serien-Einträge aus der M3U-Liste selbst genommen.
 */
class HybridVod(private val file: java.io.File, private val name: String) : VodProvider {
    @Volatile private var chosen: VodProvider? = null
    private val lock = kotlinx.coroutines.sync.Mutex()

    private suspend fun p(): VodProvider = chosen ?: lock.withLock {
        chosen ?: run {
            val derived = withContext(Dispatchers.IO) { detectXtream(file, name) }
            val x = derived?.let { XtreamVod(it) }
            val ok = x != null && runCatching { x.movieCategories().isNotEmpty() || x.seriesCategories().isNotEmpty() }.getOrDefault(false)
            info = when {
                !file.exists() -> "M3U-Liste noch nicht geladen"
                derived == null -> "Kein Anbieter-Login in der M3U erkannt"
                ok -> "Anbieter-Login aus der M3U erkannt"
                else -> "Anbieter-Login erkannt, aber Schnittstelle antwortet nicht"
            }
            (if (ok) x!! else M3uVod(file)).also { chosen = it }
        }
    }

    override suspend fun movieCategories() = p().movieCategories()
    override suspend fun seriesCategories() = p().seriesCategories()
    override suspend fun movies(categoryId: String) = p().movies(categoryId)
    override suspend fun series(categoryId: String) = p().series(categoryId)
    override suspend fun counts(series: Boolean) = p().counts(series)
    override suspend fun all(series: Boolean) = p().all(series)
    override suspend fun movieDetails(id: String) = p().movieDetails(id)
    override suspend fun seriesDetails(id: String) = p().seriesDetails(id)
    @Volatile private var info = ""
    override fun sourceInfo() = info + (chosen?.sourceInfo()?.let { " → $it" } ?: "")

    override fun movieUrl(item: VodItem) = chosen?.movieUrl(item).orEmpty()
    override fun episodeUrl(e: Episode) = chosen?.episodeUrl(e).orEmpty()

    companion object {
        private val xtreamUrl = Regex("""^(https?://[^/\s]+)/(?:live/|movie/|series/)?([^/\s]+)/([^/\s]+)/\d+(?:\.[A-Za-z0-9]+)?$""")

        /** Erkennt Server, Benutzer und Passwort aus den Sender-Links einer M3U-Datei. */
        fun detectXtream(file: java.io.File, name: String): PlaylistSource? {
            if (!file.exists()) return null
            return runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.startsWith("http") }
                        .take(300)
                        .mapNotNull { xtreamUrl.matchEntire(it) }
                        .firstOrNull()
                        ?.let { m ->
                            val (base, user, pass) = m.destructured
                            if (user == "live" || user == "movie" || user == "series") return@let null
                            PlaylistSource(
                                id = "xc-" + file.nameWithoutExtension,
                                name = name,
                                type = SourceType.XTREAM,
                                url = base,
                                user = java.net.URLDecoder.decode(user, "UTF-8"),
                                pass = java.net.URLDecoder.decode(pass, "UTF-8"),
                            )
                        }
                }
            }.getOrNull()
        }
    }
}
