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
) {
    val key: String get() = url
}

enum class SourceType { M3U, XTREAM }

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
        SourceType.M3U -> url.trim()
        SourceType.XTREAM -> {
            val raw = url.trim().trimEnd('/')
            val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
            "$base/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")
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
