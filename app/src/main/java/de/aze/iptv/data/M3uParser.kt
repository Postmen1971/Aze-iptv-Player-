package de.aze.iptv.data

/**
 * Liest M3U/M3U8-Playlists. Übernimmt Name, Gruppe (group-title oder #EXTGRP) und Logo (tvg-logo).
 * Filme und Serien aus Xtream-Listen werden für den Live-Bereich ausgefiltert.
 */
object M3uParser {

    // name="wert" oder name='wert' (manche Listen nutzen einfache Anführungszeichen)
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parse(text: String): List<Channel> = parse(text.lineSequence())

    /** Zeilenweise lesen – so muss auch eine sehr große Liste nie komplett im Speicher liegen. */
    fun parse(lines: Sequence<String>): List<Channel> {
        val result = ArrayList<Channel>()
        var name: String? = null
        var group = ""
        var logo: String? = null
        var tvgId: String? = null
        var extGroup: String? = null

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] }
                    }
                    group = (attrs["group-title"] ?: attrs["tvg-group"] ?: attrs["group"]).orEmpty().trim()
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    tvgId = attrs["tvg-id"]?.trim()?.takeIf { it.isNotEmpty() }
                    val title = titleOf(line)
                    name = title.ifBlank { attrs["tvg-name"].orEmpty() }
                    extGroup = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(':').trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val n = name
                    if (n != null && !isVod(line)) {
                        val g = group.ifBlank { extGroup.orEmpty() }.ifBlank { "Ohne Gruppe" }
                        result.add(Channel(name = n.trim(), group = g, logo = logo, url = line, tvgId = tvgId))
                    }
                    name = null
                    group = ""
                    logo = null
                    tvgId = null
                    extGroup = null
                }
            }
        }
        return result
    }

    /** Der Sendername steht hinter dem ersten Komma, das nicht in Anführungszeichen liegt. */
    private fun titleOf(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ',' && !inQuotes) return line.substring(i + 1)
        }
        return ""
    }

    private fun isVod(url: String): Boolean =
        url.contains("/movie/") || url.contains("/series/")
}
