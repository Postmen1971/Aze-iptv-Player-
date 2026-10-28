package de.aze.iptv.data

/**
 * Freie Sender aus der öffentlichen Sammlung iptv-org (frei zugängliche Streams der Sender selbst).
 * Die App enthält keine Senderliste – sie lädt auf Wunsch die aktuelle Liste von iptv-org,
 * wie eine normale Playlist. Alle Sender einer Liste landen zusammen in einer eigenen Gruppe.
 */
object FreeLists {
    class Entry(val code: String, val name: String)

    val entries = listOf(
        Entry("de", "Frei-TV Deutschland"),
        Entry("tr", "Frei-TV Türkei"),
    )

    private const val BASE = "https://iptv-org.github.io/iptv/countries/"

    fun url(code: String) = "$BASE$code.m3u"

    fun isFree(src: PlaylistSource) = src.type == SourceType.M3U && src.url.trim().startsWith(BASE)
}
