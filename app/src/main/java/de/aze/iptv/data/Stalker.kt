package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Stalker-/Ministra-Portal ("Portal + MAC").
 * Die App meldet sich wie eine MAG-Box mit der freigeschalteten MAC-Adresse an:
 *  1. Handshake → Zugangsschlüssel (Token)
 *  2. Profil abrufen
 *  3. Gruppen (Genres) und Sender laden
 *  4. Beim Abspielen für jeden Sender einen gültigen Stream-Link anfordern (create_link)
 */
class StalkerClient(portalUrl: String, mac: String) {

    private val mac: String = mac.trim().uppercase()
    private val portal: String = portalUrl.trim().let { if (it.startsWith("http")) it else "http://$it" }.trimEnd('/')

    /** Wurzel ohne "/c" am Ende, z. B. http://server:8080 oder http://server/stalker_portal */
    private val root: String = portal.removeSuffix("/c").removeSuffix("/c/").trimEnd('/')
    private val hostRoot: String = runCatching {
        val u = URL(portal)
        "${u.protocol}://${u.host}${if (u.port > 0) ":${u.port}" else ""}"
    }.getOrDefault(root)

    @Volatile private var endpoint: String? = null
    @Volatile private var token: String? = null

    private val sn = hash("MD5", this.mac).uppercase().take(13)
    private val deviceId = hash("SHA-256", this.mac).uppercase()
    private val signature = hash("SHA-256", sn + this.mac).uppercase()

    private fun hash(algo: String, s: String): String =
        MessageDigest.getInstance(algo).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Übliche Adressen der Portal-Schnittstelle, in dieser Reihenfolge ausprobiert. */
    private fun candidates(): List<String> = listOf(
        "$root/portal.php",
        "$root/server/load.php",
        "$hostRoot/portal.php",
        "$hostRoot/stalker_portal/server/load.php",
        "$hostRoot/stalker_portal/portal.php",
        "$hostRoot/server/load.php",
        "$hostRoot/load.php",
    ).distinct()

    private fun get(base: String, query: String): String {
        val conn = URL("$base?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
            )
            conn.setRequestProperty("X-User-Agent", "Model: MAG254; Link: Ethernet")
            conn.setRequestProperty("Referer", "$root/c/")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty(
                "Cookie",
                "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=${URLEncoder.encode(java.util.TimeZone.getDefault().id, "UTF-8")}",
            )
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Portal antwortet mit Fehler $code")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    private fun json(text: String): JSONObject {
        val t = text.trim()
        if (!t.startsWith("{")) {
            if (t.contains("Authorization failed", ignoreCase = true)) throw AuthException()
            throw IOException("Portal liefert keine gültige Antwort")
        }
        return JSONObject(t)
    }

    private class AuthException : IOException("Anmeldung am Portal fehlgeschlagen – MAC freigeschaltet?")

    /** Anmelden (Handshake + Profil). Probiert die bekannten Portal-Adressen durch. */
    @Synchronized
    fun connect(force: Boolean = false) {
        if (!force && token != null && endpoint != null) return
        token = null
        var lastError: Exception? = null
        val list = endpoint?.let { listOf(it) + candidates() } ?: candidates()
        for (ep in list.distinct()) {
            try {
                val hs = json(get(ep, "type=stb&action=handshake&token=&prehash=0&JsHttpRequest=1-xml"))
                val t = hs.optJSONObject("js")?.optString("token").orEmpty()
                if (t.isEmpty()) continue
                token = t
                endpoint = ep
                val profile = "type=stb&action=get_profile&hd=1&num_banks=2&sn=$sn&stb_type=MAG254&client_type=STB" +
                    "&image_version=218&video_out=hdmi&device_id=$deviceId&device_id2=$deviceId&signature=$signature" +
                    "&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&api_signature=262&prehash=0&JsHttpRequest=1-xml"
                runCatching { json(get(ep, profile)) }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError as? IOException ?: IOException("Portal nicht erreichbar – Adresse prüfen")
    }

    /** Anfrage mit Anmeldung; bei abgelaufenem Schlüssel einmal neu anmelden. */
    private fun call(query: String): JSONObject {
        connect()
        return try {
            json(get(endpoint!!, "$query&JsHttpRequest=1-xml"))
        } catch (e: AuthException) {
            connect(force = true)
            json(get(endpoint!!, "$query&JsHttpRequest=1-xml"))
        }
    }

    fun genres(): Map<String, String> {
        val js = call("type=itv&action=get_genres").opt("js")
        val arr = js as? JSONArray ?: return emptyMap()
        return (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.optString("id") to o.optString("title")
        }
    }

    fun channels(): JSONArray {
        val js = call("type=itv&action=get_all_channels").opt("js")
        return when (js) {
            is JSONObject -> js.optJSONArray("data") ?: JSONArray()
            is JSONArray -> js
            else -> JSONArray()
        }
    }

    /** Gültigen Stream-Link für einen Sender holen. */
    fun createLink(cmd: String): String {
        val q = "type=itv&action=create_link&cmd=${URLEncoder.encode(cmd, "UTF-8")}&series=&forced_storage=undefined&disable_ad=0&download=0"
        val js = call(q).optJSONObject("js")
        val link = js?.optString("cmd").orEmpty().ifEmpty { cmd }
        return cleanCmd(link)
    }

    /** Liefert ein Portal einen festen Link mit, kann der ohne Rückfrage genutzt werden. */
    fun logoUrl(logo: String): String? = when {
        logo.isBlank() -> null
        logo.startsWith("http") -> logo
        else -> "$hostRoot/stalker_portal/misc/logos/320/$logo"
    }

    /** Senderliste als kompakte M3U-Datei ablegen (Stream-Links werden erst beim Abspielen angefordert). */
    fun writeLive(sourceId: String, target: File): Int {
        val genres = genres()
        val list = channels()
        if (list.length() == 0) return 0
        // nach Genre-Reihenfolge des Portals, innerhalb nach Sendernummer
        val byGenre = LinkedHashMap<String, MutableList<JSONObject>>()
        genres.keys.forEach { byGenre[it] = ArrayList() }
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            byGenre.getOrPut(o.optString("tv_genre_id")) { ArrayList() }.add(o)
        }
        var n = 0
        target.bufferedWriter().use { w ->
            w.write("#EXTM3U\n")
            byGenre.forEach { (gid, chans) ->
                chans.sortBy { it.optString("number").toIntOrNull() ?: Int.MAX_VALUE }
                val group = clean(genres[gid] ?: "Ohne Gruppe")
                chans.forEach { o ->
                    val cmd = o.optString("cmd")
                    if (cmd.isBlank()) return@forEach
                    val name = clean(o.optString("name"))
                    val logo = clean(logoUrl(o.optString("logo")).orEmpty())
                    val tvg = clean(o.optString("xmltv_id").takeIf { it != "null" }.orEmpty())
                    w.write("#EXTINF:-1 tvg-id=\"$tvg\" tvg-logo=\"$logo\" group-title=\"$group\",$name\n")
                    w.write("${PREFIX}$sourceId/${URLEncoder.encode(cmd, "UTF-8")}\n")
                    n++
                }
            }
        }
        return n
    }

    private fun clean(s: String) = s.replace("\"", "'").replace("\n", " ").replace("\r", " ").trim()

    companion object {
        /** Sender-Links der Portale: stalker://<playlist-id>/<cmd> */
        const val PREFIX = "stalker://"

        fun cleanCmd(cmd: String): String =
            cmd.trim().removePrefix("ffmpeg ").removePrefix("auto ").removePrefix("ffrt ").trim()

        fun isStalker(url: String) = url.startsWith(PREFIX)

        /** Zerlegt stalker://id/cmd in (id, cmd). */
        fun parse(url: String): Pair<String, String>? {
            val rest = url.removePrefix(PREFIX)
            val i = rest.indexOf('/')
            if (i <= 0) return null
            return rest.substring(0, i) to URLDecoder.decode(rest.substring(i + 1), "UTF-8")
        }

        private val clients = HashMap<String, StalkerClient>()

        /** Ein Anmelde-Zustand je Playlist (Token wird wiederverwendet). */
        @Synchronized
        fun forSource(src: PlaylistSource): StalkerClient =
            clients.getOrPut(src.id + "|" + src.url + "|" + src.user) { StalkerClient(src.url, src.user) }
    }
}
