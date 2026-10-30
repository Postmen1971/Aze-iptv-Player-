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
        // Die #EXTINF-Zeile wird erst ausgewertet, wenn feststeht, dass es ein Live-Sender ist –
        // Listen mit zehntausenden Filmen/Serien laden so viel schneller.
        var extinf: String? = null
        var extGroup: String? = null

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    extinf = line
                    extGroup = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(':').trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val inf = extinf
                    if (inf != null && !isVod(line)) {
                        val attrs = attrRegex.findAll(inf).associate {
                            it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] }
                        }
                        val group = (attrs["group-title"] ?: attrs["tvg-group"] ?: attrs["group"]).orEmpty().trim()
                        val name = titleOf(inf).ifBlank { attrs["tvg-name"].orEmpty() }
                        val g = group.ifBlank { extGroup.orEmpty() }.ifBlank { "Ohne Gruppe" }
                        result.add(
                            Channel(
                                name = name.trim(), group = g,
                                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                                url = line,
                                tvgId = attrs["tvg-id"]?.trim()?.takeIf { it.isNotEmpty() },
                                tvgName = attrs["tvg-name"]?.trim()?.takeIf { it.isNotEmpty() && it != name.trim() },
                            ),
                        )
                    }
                    extinf = null
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
