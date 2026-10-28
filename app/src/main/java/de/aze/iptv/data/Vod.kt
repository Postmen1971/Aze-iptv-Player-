package de.aze.iptv.data

import kotlinx.coroutines.Dispatchers
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

/**
 * Filme und Serien über die Xtream-Schnittstelle des Anbieters (player_api.php).
 * Geladen wird immer nur eine Kategorie bzw. ein Titel – so bleibt es auch auf schwachen Sticks flüssig.
 */
class XtreamVod(private val src: PlaylistSource) {

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

    suspend fun movieCategories(): List<VodCategory> = withContext(Dispatchers.IO) { cats("get_vod_categories") }
    suspend fun seriesCategories(): List<VodCategory> = withContext(Dispatchers.IO) { cats("get_series_categories") }

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
    suspend fun counts(series: Boolean): Map<String, Int> = withContext(Dispatchers.IO) {
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

    suspend fun movies(categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val a = arr("get_vod_streams", "&category_id=${enc(categoryId)}")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            VodItem(
                id = o.optString("stream_id"),
                name = o.optString("name"),
                poster = o.str("stream_icon"),
                rating = o.str("rating"),
                year = o.str("year") ?: o.str("releaseDate")?.take(4),
                ext = o.str("container_extension") ?: "mp4",
            )
        }
    }

    suspend fun series(categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val a = arr("get_series", "&category_id=${enc(categoryId)}")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            VodItem(
                id = o.optString("series_id"),
                name = o.optString("name"),
                poster = o.str("cover"),
                rating = o.str("rating"),
                year = o.str("year") ?: o.str("releaseDate")?.take(4),
                ext = "",
            )
        }
    }

    suspend fun movieDetails(id: String): VodDetails = withContext(Dispatchers.IO) {
        val info = obj("get_vod_info", "&vod_id=${enc(id)}").optJSONObject("info") ?: JSONObject()
        details(info)
    }

    suspend fun seriesDetails(id: String): SeriesDetails = withContext(Dispatchers.IO) {
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
            rating = i.str("rating"),
        )
    }

    fun movieUrl(item: VodItem) = "$base/movie/${src.user.trim()}/${src.pass}/${item.id}.${item.ext}"
    fun episodeUrl(e: Episode) = "$base/series/${src.user.trim()}/${src.pass}/${e.id}.${e.ext}"
}
