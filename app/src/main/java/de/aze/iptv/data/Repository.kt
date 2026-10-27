package de.aze.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Lädt Playlists aus dem Netz und hält eine Kopie auf dem Gerät, damit die App sofort startet.
 *
 * Speicherschonend:
 *  - M3U-Listen werden direkt in eine Datei geladen und zeilenweise gelesen (auch 50-MB-Listen laufen auf schwachen Sticks).
 *  - Bei Server + Login (Xtream) werden über die Anbieter-Schnittstelle nur die Live-Sender geholt,
 *    nicht die riesige Gesamtliste mit allen Filmen und Serien. Klappt das nicht, wird die normale M3U-Liste genommen.
 */
class Repository(context: Context) {

    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }

    private fun cacheFile(source: PlaylistSource) = File(dir, "${source.id}.m3u")

    suspend fun loadCached(sources: List<PlaylistSource>): List<Channel> = withContext(Dispatchers.IO) {
        sources.flatMap { src ->
            val f = cacheFile(src)
            if (f.exists()) {
                runCatching {
                    f.bufferedReader().useLines { lines -> M3uParser.parse(lines) }.map { it.copy(source = src.id) }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }

    fun hasCache(sources: List<PlaylistSource>): Boolean = sources.any { cacheFile(it).exists() }

    /** Lädt alle Quellen neu. Gibt Fehlermeldungen pro Quelle zurück (leer = alles gut). */
    suspend fun refresh(sources: List<PlaylistSource>): List<String> = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        sources.forEach { src ->
            val tmp = File(dir, "${src.id}.tmp")
            try {
                val viaApi = src.type == SourceType.XTREAM && runCatching { writeXtreamLive(src, tmp) }.getOrDefault(false)
                if (!viaApi) Http.download(src.m3uUrl(), tmp)
                if (!looksLikeM3u(tmp)) {
                    errors.add("${src.name}: Antwort ist keine M3U-Liste")
                    tmp.delete()
                } else if (!tmp.renameTo(cacheFile(src))) {
                    tmp.copyTo(cacheFile(src), overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                errors.add("${src.name}: ${e.message ?: "Fehler beim Laden"}")
            }
        }
        errors
    }

    private fun looksLikeM3u(f: File): Boolean {
        if (!f.exists() || f.length() == 0L) return false
        val head = f.inputStream().use { input ->
            val buf = ByteArray(4096)
            val n = input.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
        return head.trimStart('﻿', ' ', '\r', '\n', '\t').startsWith("#EXTM3U", ignoreCase = true) || head.contains("#EXTINF")
    }

    /**
     * Xtream-Schnittstelle: Live-Kategorien und Live-Sender holen und als kompakte M3U-Datei ablegen.
     * Gibt false zurück, wenn der Anbieter die Schnittstelle nicht anbietet.
     */
    private fun writeXtreamLive(src: PlaylistSource, target: File): Boolean {
        val raw = src.url.trim().trimEnd('/')
        val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val user = java.net.URLEncoder.encode(src.user.trim(), "UTF-8")
        val pass = java.net.URLEncoder.encode(src.pass, "UTF-8")
        val api = "$base/player_api.php?username=$user&password=$pass"

        val cats = JSONArray(Http.text("$api&action=get_live_categories"))
        val catNames = HashMap<String, String>(cats.length() * 2)
        val catOrder = ArrayList<String>()
        for (i in 0 until cats.length()) {
            val c = cats.getJSONObject(i)
            val id = c.optString("category_id")
            catNames[id] = c.optString("category_name")
            catOrder.add(id)
        }

        val streams = JSONArray(Http.text("$api&action=get_live_streams"))
        if (streams.length() == 0) return false
        // Pfad-Teile wie beim Anbieter: /live/benutzer/passwort/id.ts
        val pathUser = src.user.trim()
        val pathPass = src.pass

        // Nach Kategorie-Reihenfolge des Anbieters sortieren, innerhalb nach "num".
        val byCat = LinkedHashMap<String, MutableList<org.json.JSONObject>>()
        catOrder.forEach { byCat[it] = ArrayList() }
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            byCat.getOrPut(s.optString("category_id")) { ArrayList() }.add(s)
        }
        target.bufferedWriter().use { w ->
            w.write("#EXTM3U\n")
            byCat.forEach { (catId, list) ->
                list.sortBy { it.optInt("num", Int.MAX_VALUE) }
                val group = clean(catNames[catId] ?: "Ohne Gruppe")
                list.forEach { s ->
                    val name = clean(s.optString("name"))
                    val logo = clean(s.optString("stream_icon"))
                    val id = s.optString("stream_id")
                    if (id.isNotEmpty()) {
                        w.write("#EXTINF:-1 tvg-logo=\"$logo\" group-title=\"$group\",$name\n")
                        w.write("$base/live/$pathUser/$pathPass/$id.ts\n")
                    }
                }
            }
        }
        return true
    }

    private fun clean(s: String) = s.replace("\"", "'").replace("\n", " ").replace("\r", " ").trim()

    fun delete(source: PlaylistSource) {
        cacheFile(source).delete()
    }
}
