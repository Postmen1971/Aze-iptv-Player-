package de.aze.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /** Gespeicherte Kopie der Playlist (für Filme & Serien aus M3U-Listen). */
    fun cacheFileOf(source: PlaylistSource): File = cacheFile(source)

    suspend fun loadCached(sources: List<PlaylistSource>): List<Channel> = withContext(Dispatchers.IO) {
        // Alle Playlists gleichzeitig lesen (schnellerer Start)
        sources.map { src -> async { readCached(src) } }.awaitAll().flatten()
    }

    private fun readCached(src: PlaylistSource): List<Channel> {
        val f = cacheFile(src)
        if (!f.exists()) return emptyList()
        return runCatching {
            val free = FreeLists.isFree(src)
            f.bufferedReader().useLines { lines -> M3uParser.parse(lines) }.map {
                // Freie Sender (iptv-org): alle zusammen in einer eigenen Gruppe
                if (free) it.copy(source = src.id, group = src.name) else it.copy(source = src.id)
            }
        }.getOrDefault(emptyList())
    }

    fun hasCache(sources: List<PlaylistSource>): Boolean = sources.any { cacheFile(it).exists() }

    /** Lädt alle Quellen neu. Gibt je Quelle (id) eine Fehlermeldung zurück; fehlt die id, war alles gut. */
    suspend fun refresh(sources: List<PlaylistSource>): Map<String, String> = withContext(Dispatchers.IO) {
        val errors = LinkedHashMap<String, String>()
        sources.forEach { src ->
            val tmp = File(dir, "${src.id}.tmp")
            try {
                if (src.type == SourceType.STALKER) {
                    // Portal + MAC: Senderliste über die Portal-Schnittstelle
                    val n = StalkerClient.forSource(src).writeLive(src.id, tmp)
                    if (n == 0) throw java.io.IOException("Portal liefert keine Sender – MAC freigeschaltet?")
                } else {
                    val viaApi = src.type == SourceType.XTREAM && runCatching { writeXtreamLive(src, tmp) }.getOrDefault(false)
                    if (!viaApi) Http.download(src.m3uUrl(), tmp)
                }
                if (!looksLikeM3u(tmp)) {
                    errors[src.id] = describeBadAnswer(tmp, src)
                    tmp.delete()
                } else if (!tmp.renameTo(cacheFile(src))) {
                    tmp.copyTo(cacheFile(src), overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                errors[src.id] = when {
                    e is java.net.UnknownHostException -> "Server nicht gefunden – Adresse prüfen"
                    e is java.net.SocketTimeoutException -> "Server antwortet nicht (Zeitüberschreitung)"
                    e.message?.contains("401") == true || e.message?.contains("403") == true -> "Zugriff verweigert (${e.message}) – Login prüfen"
                    else -> e.message ?: "Fehler beim Laden"
                }
            }
        }
        errors
    }

    /** Verständliche Meldung, wenn statt einer Liste etwas anderes zurückkam. */
    private fun describeBadAnswer(f: File, src: PlaylistSource): String {
        if (!f.exists() || f.length() == 0L) return "Leere Antwort vom Server" + if (src.type == SourceType.XTREAM) " – Benutzer/Passwort prüfen" else ""
        val head = runCatching { f.inputStream().use { i -> val b = ByteArray(300); val n = i.read(b); String(b, 0, maxOf(n, 0)) } }.getOrDefault("")
        return when {
            head.contains("\"auth\":0") || head.contains("\"auth\": 0") -> "Benutzer oder Passwort falsch"
            head.trimStart().startsWith("<") -> "Server liefert eine Webseite statt einer Liste – Link prüfen"
            else -> "Antwort ist keine M3U-Liste"
        }
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

        // Senderliste Stück für Stück lesen (nicht die ganze, oft 20–50 MB große Antwort auf einmal in den Speicher)
        val streams = readLiveStreams("$api&action=get_live_streams")
        if (streams.isEmpty()) return false
        // Pfad-Teile wie beim Anbieter: /live/benutzer/passwort/id.ts
        val pathUser = src.user.trim()
        val pathPass = src.pass

        // Nach Kategorie-Reihenfolge des Anbieters sortieren, innerhalb nach "num".
        val byCat = LinkedHashMap<String, MutableList<LiveStream>>()
        catOrder.forEach { byCat[it] = ArrayList() }
        streams.forEach { s -> byCat.getOrPut(s.cat) { ArrayList() }.add(s) }
        target.bufferedWriter().use { w ->
            w.write("#EXTM3U\n")
            byCat.forEach { (catId, list) ->
                list.sortBy { it.num }
                val group = clean(catNames[catId] ?: "Ohne Gruppe")
                list.forEach { s ->
                    val name = clean(s.name)
                    val logo = clean(s.logo)
                    val id = s.id
                    val epgId = clean(s.epg)
                    if (id.isNotEmpty()) {
                        w.write("#EXTINF:-1 tvg-id=\"$epgId\" tvg-logo=\"$logo\" group-title=\"$group\",$name\n")
                        w.write("$base/live/$pathUser/$pathPass/$id.ts\n")
                    }
                }
            }
        }
        return true
    }

    private class LiveStream(val name: String, val logo: String, val id: String, val epg: String, val num: Int, val cat: String)

    private fun readLiveStreams(url: String): List<LiveStream> {
        val out = ArrayList<LiveStream>()
        val conn = Http.open(url)
        try {
            android.util.JsonReader(conn.inputStream.bufferedReader()).use { r ->
                if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return out
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    var name = ""; var logo = ""; var id = ""; var epg = ""; var num = Int.MAX_VALUE; var cat = ""
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> name = str(r)
                            "stream_icon" -> logo = str(r)
                            "stream_id" -> id = str(r)
                            "epg_channel_id" -> epg = str(r)
                            "num" -> num = str(r).toIntOrNull() ?: Int.MAX_VALUE
                            "category_id" -> str(r).takeIf { it.isNotBlank() }?.let { cat = it }
                            // Manche Server liefern statt "category_id" nur eine Liste "category_ids"
                            "category_ids" -> if (r.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                                r.beginArray()
                                var first = true
                                while (r.hasNext()) { val v = str(r); if (first && cat.isEmpty()) cat = v; first = false }
                                r.endArray()
                            } else r.skipValue()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    out.add(LiveStream(name, logo, id, epg, num, cat))
                }
                r.endArray()
            }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /** Beliebigen Wert als Text lesen (Zahl, Text, null, true/false); Listen/Objekte werden übersprungen. */
    private fun str(r: android.util.JsonReader): String = when (r.peek()) {
        android.util.JsonToken.NULL -> { r.nextNull(); "" }
        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> r.nextString().let { if (it == "null") "" else it }
        android.util.JsonToken.BOOLEAN -> r.nextBoolean().toString()
        else -> { r.skipValue(); "" }
    }

    private fun clean(s: String) = s.replace("\"", "'").replace("\n", " ").replace("\r", " ").trim()

    /**
     * EPG-Adressen der Playlists: bei Server + Login die xmltv.php des Anbieters,
     * bei M3U die Angabe url-tvg / x-tvg-url aus der ersten Zeile der Liste.
     */
    fun epgUrls(sources: List<PlaylistSource>): List<String> = sources.flatMap { src ->
        if (src.type == SourceType.XTREAM) {
            val raw = src.url.trim().trimEnd('/')
            val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
            listOf("$base/xmltv.php?username=${java.net.URLEncoder.encode(src.user.trim(), "UTF-8")}&password=${java.net.URLEncoder.encode(src.pass, "UTF-8")}")
        } else {
            val f = cacheFile(src)
            // Kopfzeile der M3U: url-tvg="a.xml.gz,b.xml.gz" – alle Adressen nehmen
            if (!f.exists()) emptyList() else runCatching {
                val head = f.bufferedReader().use { it.readLine() }.orEmpty()
                tvgUrl.findAll(head).flatMap { m -> m.groupValues[1].split(',', ' ').asSequence() }
                    .map { it.trim() }.filter { it.startsWith("http") }.toList()
            }.getOrDefault(emptyList())
        }
    }.distinct()

    private val tvgUrl = Regex("(?:url-tvg|x-tvg-url|tvg-url)\\s*=\\s*\"([^\"]+)\"")

    fun delete(source: PlaylistSource) {
        cacheFile(source).delete()
    }
}
