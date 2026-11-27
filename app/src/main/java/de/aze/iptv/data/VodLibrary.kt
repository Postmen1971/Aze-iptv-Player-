package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Eigene Listen bei Filmen & Serien (Aziz 25.11.):
 *  – „Weiterschauen“: angefangene Filme und Folgen (die Stelle selbst steht wie bisher in den Resume-Positionen)
 *  – „Merkliste“: mit „Vormerken“ gemerkte Filme/Serien
 *  – „Neu hinzugefügt“: die zuletzt vom Anbieter hochgeladenen (Feld „added“ bzw. „last_modified“)
 * Die Einträge sind Kopien von Titel, Cover usw. – so stehen sie sofort da, ohne den Anbieter zu fragen.
 * Reine Regeln ohne Android (Unit-Test VodLibraryTest).
 */
data class VodRef(
    val sourceId: String,
    val series: Boolean,
    val id: String,
    val name: String,
    val poster: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val ext: String = "",
    val cat: String? = null,
) {
    fun toItem() = VodItem(id, name, poster, rating, year, ext, cat)

    fun toJson(): JSONObject = JSONObject().apply {
        put("src", sourceId); put("ser", series); put("id", id); put("name", name)
        poster?.let { put("poster", it) }; rating?.let { put("rating", it) }; year?.let { put("year", it) }
        put("ext", ext); cat?.let { put("cat", it) }
    }

    companion object {
        fun of(sourceId: String, series: Boolean, item: VodItem) =
            VodRef(sourceId, series, item.id, item.name, item.poster, item.rating, item.year, item.ext, item.cat)

        fun fromJson(o: JSONObject): VodRef? {
            val id = o.optString("id").ifBlank { return null }
            fun s(k: String) = o.optString(k).ifBlank { null }
            return VodRef(o.optString("src"), o.optBoolean("ser"), id, o.optString("name"), s("poster"), s("rating"), s("year"), o.optString("ext"), s("cat"))
        }
    }
}

/** Folge einer Serie (für „Weiterschauen S1 E3“ direkt aus den Serien-Details) */
data class EpisodeRef(val id: String, val title: String, val season: Int, val number: Int, val ext: String) {
    fun toEpisode() = Episode(id, title, season, number, ext, null, null, null)
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("s", season).put("n", number).put("ext", ext)

    companion object {
        fun of(e: Episode) = EpisodeRef(e.id, e.title, e.season, e.number, e.ext)
        fun fromJson(o: JSONObject?): EpisodeRef? = o?.let {
            EpisodeRef(it.optString("id").ifBlank { return null }, it.optString("title"), it.optInt("s"), it.optInt("n"), it.optString("ext"))
        }
    }
}

/** Ein angefangener Film bzw. die zuletzt angefangene Folge einer Serie */
data class ContinueEntry(val ref: VodRef, val resumeKey: String, val episode: EpisodeRef? = null, val at: Long = 0) {
    fun toJson(): JSONObject = JSONObject().put("ref", ref.toJson()).put("key", resumeKey).put("at", at).apply {
        episode?.let { put("ep", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject): ContinueEntry? {
            val ref = o.optJSONObject("ref")?.let { VodRef.fromJson(it) } ?: return null
            return ContinueEntry(ref, o.optString("key").ifBlank { return null }, EpisodeRef.fromJson(o.optJSONObject("ep")), o.optLong("at"))
        }
    }
}

object VodLibrary {
    /** so viele angefangene Titel bzw. gemerkte Titel höchstens behalten */
    const val MAX_CONTINUE = 100
    const val MAX_WATCH = 500
    /** „Neu hinzugefügt“: so viele neueste */
    const val NEW_COUNT = 200

    /**
     * Neuer Stand der Weiterschauen-Liste, nachdem [entry] gespielt wurde. [stillOpen] = es ist noch eine Stelle
     * gemerkt (nicht zu Ende geschaut). Eine Serie steht nur einmal drin (mit der zuletzt angefangenen Folge).
     */
    fun afterPlay(list: List<ContinueEntry>, entry: ContinueEntry, stillOpen: Boolean): List<ContinueEntry> {
        val rest = list.filterNot { it.resumeKey == entry.resumeKey || it.sameTitle(entry) }
        return (if (stillOpen) listOf(entry) + rest else rest).take(MAX_CONTINUE)
    }

    private fun ContinueEntry.sameTitle(o: ContinueEntry) = ref.sourceId == o.ref.sourceId && ref.series == o.ref.series && ref.id == o.ref.id

    /** Weiterschauen dieser Playlist (Filme bzw. Serien), neueste zuerst – nur, wo noch eine Stelle gemerkt ist */
    fun continueFor(list: List<ContinueEntry>, sourceId: String, series: Boolean, resumeOf: (String) -> Long): List<ContinueEntry> =
        list.filter { it.ref.sourceId == sourceId && it.ref.series == series && resumeOf(it.resumeKey) > 0 }
            .sortedByDescending { it.at }

    /** Vormerken an/aus; neue Einträge oben */
    fun toggleWatch(list: List<VodRef>, ref: VodRef): List<VodRef> =
        if (list.any { it.same(ref) }) list.filterNot { it.same(ref) } else (listOf(ref) + list).take(MAX_WATCH)

    fun VodRef.same(o: VodRef) = sourceId == o.sourceId && series == o.series && id == o.id

    /** Die neuesten [n] Titel (nur mit Datum vom Anbieter), sichtbare zuerst gefiltert */
    /**
     * Kategorie für Erwachsene – bei „Neu hinzugefügt“ NIE dabei, egal ob der Erwachsenen-Filter an ist (Aziz 27.11.:
     * „ausschließlich normale Filme; alle Kategorien weg, die mit X anfangen“). Auch mit Länder-Vorsatz („DE | XXX“).
     */
    fun adultCategory(name: String): Boolean {
        if (AdultFilter.isAdult(name)) return true
        val n = name.trim()
        val tail = n.substringAfterLast('|').substringAfterLast(':').trim()
        return n.startsWith("X", ignoreCase = true) || tail.startsWith("X", ignoreCase = true)
    }

    fun newest(items: List<VodItem>, visible: (VodItem) -> Boolean, n: Int = NEW_COUNT): List<VodItem> =
        items.asSequence().filter { it.added > 0 && visible(it) }.sortedByDescending { it.added }.take(n).toList()

    fun continueToJson(l: List<ContinueEntry>) = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
    fun continueFromJson(s: String?): List<ContinueEntry> = runCatching {
        val a = JSONArray(s ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> ContinueEntry.fromJson(o) } }
    }.getOrDefault(emptyList())

    fun watchToJson(l: List<VodRef>) = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
    fun watchFromJson(s: String?): List<VodRef> = runCatching {
        val a = JSONArray(s ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> VodRef.fromJson(o) } }
    }.getOrDefault(emptyList())

    /** Trailer-Angabe des Anbieters → YouTube-Video-ID (oder null): „dQw4w9WgXcQ“, ganze Links, Kurzlinks */
    fun trailerId(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t == "null") return null
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return t
        return Regex("""(?:v=|youtu\.be/|/embed/|/shorts/|/v/)([A-Za-z0-9_-]{11})""").find(t)?.groupValues?.get(1)
    }
}
