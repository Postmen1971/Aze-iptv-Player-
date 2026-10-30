package de.aze.iptv.data

import java.net.URLEncoder

/** Ein Sender aus der Playlist. Der Stream-Link dient als eindeutiger Schlüssel. */
data class Channel(
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
    /** id der Playlist, aus der der Sender stammt. */
    val source: String = "",
    /** Kennung für die Programmzeitschrift (tvg-id) */
    val tvgId: String? = null,
    /** Name für die Programmzeitschrift (tvg-name), falls anders als der angezeigte Name */
    val tvgName: String? = null,
) {
    val key: String get() = url
}

/** M3U-Link, Server + Login (Xtream) oder Portal + MAC (Stalker). Bei STALKER steht die MAC in [PlaylistSource.user]. */
enum class SourceType { M3U, XTREAM, STALKER }

/**
 * Eine Playlist-Quelle: entweder ein kompletter M3U-Link
 * oder Server + Benutzer + Passwort (Xtream), aus denen der Link zusammengesetzt wird.
 */
data class PlaylistSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val user: String = "",
    val pass: String = "",
) {
    fun m3uUrl(): String = when (type) {
        SourceType.M3U, SourceType.STALKER -> url.trim()
        SourceType.XTREAM -> {
            val raw = url.trim().trimEnd('/')
            val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
            "$base/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")

    /**
     * Ein M3U-Link eines Anbieters (…/get.php?username=…&password=…) wird automatisch zu "Server + Login".
     * Dann kommen Gruppen, EPG sowie Filme & Serien direkt über die Anbieter-Schnittstelle –
     * auch wenn der Link nur "type=m3u" (ohne Gruppen) war.
     */
    fun normalized(): PlaylistSource {
        if (type != SourceType.M3U) return this
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return this
        if (u.path?.endsWith("get.php") != true || u.rawQuery == null) return this
        val q = u.rawQuery.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).lowercase() to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val user = q["username"]?.takeIf { it.isNotBlank() } ?: return this
        val pass = q["password"]?.takeIf { it.isNotBlank() } ?: return this
        val port = if (u.port > 0) ":${u.port}" else ""
        val prefix = u.path.removeSuffix("get.php").trimEnd('/')
        return copy(type = SourceType.XTREAM, url = "${u.scheme}://${u.host}$port$prefix", user = user, pass = pass)
    }
}

/** Ein Sender mit seiner festen Nummer in der aktuellen Reihenfolge. */
data class NumberedChannel(
    val number: Int,
    val channel: Channel,
    val favorite: Boolean,
)

data class ChannelGroup(
    val name: String,
    val isFavorites: Boolean,
    val channels: List<NumberedChannel>,
    val sourceId: String? = null,
    val sourceName: String? = null,
) {
    val range: String
        get() = if (channels.isEmpty()) "–" else "${channels.first().number}–${channels.last().number}"
}

/** Alle Gruppen mit fertiger Nummerierung. */
data class Lineup(
    val groups: List<ChannelGroup>,
    val all: List<NumberedChannel>,
) {
    val byNumber: Map<Int, NumberedChannel> = all.associateBy { it.number }
    val byKey: Map<String, NumberedChannel> = all.associateBy { it.channel.key }

    companion object {
        val EMPTY = Lineup(emptyList(), emptyList())
    }
}
