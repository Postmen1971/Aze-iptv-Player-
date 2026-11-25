package de.aze.iptv.data

import java.text.Normalizer

/**
 * Eigene Suche in Filmen bzw. Serien (Aziz 25.11.): sucht nur dort – keine Sender, keine globale Suche –, in allen
 * Kategorien, aber nicht in ausgeblendeten Kategorien und nicht in Titeln, die der Erwachsenen-Filter verbirgt.
 *
 * Damit man auf der Fernbedienung nicht genau tippen muss:
 *  – Groß/klein, Akzente und Sonderbuchstaben egal: „kahramanmaras“ findet „Kahramanmaraş“, „madchen“/„maedchen“
 *    findet „Mädchen“, „istanbul“ findet „İstanbul“
 *  – mehrere Wörter in beliebiger Reihenfolge: „matrix 1999“, „1999 matrix“
 *  – Reihenfolge: genau der Titel, dann Titel, die so anfangen, dann Wörter, die so anfangen, dann der Rest.
 *    Kürzel wie „DE - “, „TR | “, „[4K] “ vor dem Titel zählen dabei nicht.
 * Reine Rechenregel ohne Android (Unit-Test VodSearchTest).
 */
object VodSearch {
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    /** Kürzel vor dem eigentlichen Titel: „DE - “, „TR | “, „EN: “, „[DE] “, „(4K) “, „4K - “ … */
    private val PREFIX = Regex("^(\\s*([\\[(][^\\])]{1,8}[\\])]|[A-Za-z0-9+]{2,4}\\s*[-|:•]))\\s*")

    /** Vergleichsform: klein, ohne Akzente, ä/ae, ö/oe, ü/ue, ß/ss, ı/i gleich, nur Buchstaben/Ziffern mit Leerzeichen */
    fun normalize(s: String): String {
        val lower = s.lowercase(java.util.Locale.ROOT)
            .replace('ı', 'i')
            .replace("ß", "ss")
        val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
            .replace("ae", "a").replace("oe", "o").replace("ue", "u")
        return plain.replace(NON_ALNUM, " ").trim()
    }

    /** Titel ohne vorangestellte Kürzel (für die Reihenfolge) */
    fun clean(name: String): String {
        var s = name
        repeat(3) { s = s.replace(PREFIX, "") }
        return s.ifBlank { name }
    }

    /**
     * Treffer in [items], sortiert wie oben beschrieben (bei Gleichstand Reihenfolge des Anbieters).
     * [visible] = nicht ausgeblendet (Kategorie sichtbar, nicht vom Erwachsenen-Filter verborgen).
     */
    fun <T> search(items: List<T>, query: String, name: (T) -> String, visible: (T) -> Boolean): List<T> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = words.joinToString(" ")
        val hits = ArrayList<Pair<Int, Int>>() // Rang, Index
        items.forEachIndexed { i, it ->
            val full = normalize(name(it))
            if (words.any { w -> w !in full }) return@forEachIndexed
            if (!visible(it)) return@forEachIndexed
            val title = normalize(clean(name(it)))
            val tokens = title.split(' ')
            val rank = when {
                title == q -> 0
                title.startsWith(q) -> 1
                words.all { w -> tokens.any { t -> t.startsWith(w) } } -> 2
                else -> 3
            }
            hits.add(rank to i)
        }
        hits.sortWith(compareBy({ it.first }, { it.second }))
        return hits.map { items[it.second] }
    }
}
