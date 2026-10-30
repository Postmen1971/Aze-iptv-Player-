package de.aze.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * YouTube-Livesender. Abgespielt wird mit dem offiziellen eingebetteten YouTube-Player –
 * es werden keine Stream-Adressen ausgelesen.
 *  - Kanal: spielt den aktuellen Livestream des Kanals (embed/live_stream?channel=UC…)
 *  - Video: spielt genau dieses Video bzw. diesen Livestream
 */
data class YtEntry(
    val id: String,
    val name: String,
    val isChannel: Boolean,
    /** Kanal-ID (UC…) oder Video-ID */
    val ref: String,
) {
    fun toChannel(): Channel = Channel(
        name = name,
        group = YouTube.GROUP,
        logo = null,
        url = if (isChannel) "youtube:channel:$ref" else "youtube:video:$ref",
        source = YouTube.SOURCE,
    )
}

object YouTube {
    const val GROUP = "YouTube Live"
    const val SOURCE = "youtube"

    private val channelId = Regex("""(UC[A-Za-z0-9_-]{22})""")
    private val videoId = Regex("""(?:v=|youtu\.be/|/live/|/embed/|/shorts/)([A-Za-z0-9_-]{11})""")

    fun isYouTube(url: String) = url.startsWith("youtube:")

    /**
     * Vorschläge: offizielle YouTube-Kanäle von Sendern, die selbst kostenlos live auf YouTube übertragen.
     * Werden nur hinzugefügt, wenn man sie in den Einstellungen antippt.
     */
    val suggestions: List<Pair<String, String>> = listOf(
        "TRT Haber" to "UCBgTP2LOFVPmq15W-RH-WXA",
        "NOW TV" to "https://www.youtube.com/live/AS8Cbp-MQ4Q",
        "TV100" to "https://www.youtube.com/live/4WSvLRk83-c",
        "Sözcü TV" to "https://www.youtube.com/live/ztmY_cCtUl0",
        "Star TV" to "https://www.youtube.com/live/82O6yOy_XwE",
        "NTV" to "https://www.youtube.com/live/pqq5c6k70kk",
        "Show TV" to "https://www.youtube.com/live/cbgKyiqOljQ",
        "Haber Global" to "https://www.youtube.com/live/EqoCJ8BPxtE",
        "beIN Haber" to "https://www.youtube.com/live/i7UpPgxfZZ8",
        "Kanal D" to "https://www.youtube.com/live/b94j0fwsPGQ",
        "Kanal 7" to "https://www.youtube.com/live/2exv1i3jgIY",
        "TRT Spor" to "https://www.youtube.com/live/2ZT8u2dwwNE",
        "A Spor" to "https://www.youtube.com/live/nol9os2B8zE",
        "CNN Türk" to "https://www.youtube.com/live/XBt21-w85WA",
        "TVNET" to "https://www.youtube.com/live/Z5bqy4gwEmc",
        "KRT" to "https://www.youtube.com/live/yRBufi6xue8",
        "Kardeş Payı" to "https://www.youtube.com/live/eYeu4VUkhYc",
        "Yeşilçam" to "https://www.youtube.com/live/dXb2dcygmpk",
    )

    /** Normale YouTube-Adresse (zum Öffnen in der YouTube-App). */
    fun watchUrl(url: String): String = when {
        url.startsWith("youtube:channel:") -> "https://www.youtube.com/channel/${url.removePrefix("youtube:channel:")}/live"
        else -> "https://www.youtube.com/watch?v=${url.removePrefix("youtube:video:")}"
    }

    /** Adresse für den eingebetteten Player. */
    fun embedUrl(url: String, origin: String): String {
        // vq = gewünschte Qualität (höchste); der Player schaltet zusätzlich nach dem Start hoch
        val params = "autoplay=1&controls=0&rel=0&playsinline=1&enablejsapi=1&vq=hd1080&origin=$origin"
        return when {
            url.startsWith("youtube:channel:") ->
                "https://www.youtube.com/embed/live_stream?channel=${url.removePrefix("youtube:channel:")}&$params"
            else -> "https://www.youtube.com/embed/${url.removePrefix("youtube:video:")}?$params"
        }
    }

    /**
     * Wandelt einen Link in einen Eintrag um. Erkennt Kanal-IDs, Video-Links und @Namen.
     * Für @Namen wird die öffentliche Kanalseite einmal gelesen, um die Kanal-ID zu finden.
     */
    suspend fun resolve(link: String, name: String): YtEntry? = withContext(Dispatchers.IO) {
        val l = link.trim()
        val title = name.trim()
        channelId.find(l)?.let { return@withContext YtEntry(UUID.randomUUID().toString(), title.ifEmpty { "YouTube" }, true, it.groupValues[1]) }
        videoId.find(l)?.let { return@withContext YtEntry(UUID.randomUUID().toString(), title.ifEmpty { "YouTube" }, false, it.groupValues[1]) }

        // @name oder Kanal-Adresse: Kanal-ID von der Kanalseite holen.
        val page = when {
            l.startsWith("@") -> "https://www.youtube.com/$l"
            l.startsWith("http") -> l
            l.contains("youtube.com") -> "https://$l"
            else -> "https://www.youtube.com/@$l"
        }.substringBefore("?").removeSuffix("/live").removeSuffix("/")
        val html = runCatching {
            val conn = URL(page).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            conn.setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
            // Ohne diese Angabe zeigt YouTube in der EU zuerst eine Zustimmungsseite.
            conn.setRequestProperty("Cookie", "SOCS=CAI")
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { conn.disconnect() }
        }.getOrNull() ?: return@withContext null
        val id = Regex(""""(?:externalId|channelId)":"(UC[A-Za-z0-9_-]{22})"""").find(html)?.groupValues?.get(1)
            ?: Regex("""channel/(UC[A-Za-z0-9_-]{22})""").find(html)?.groupValues?.get(1)
            ?: return@withContext null
        val fallbackName = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)
        YtEntry(UUID.randomUUID().toString(), title.ifEmpty { fallbackName ?: "YouTube" }, true, id)
    }
}
