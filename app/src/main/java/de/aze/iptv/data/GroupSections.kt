package de.aze.iptv.data

/**
 * Gruppenübersicht wie bei TiviMate (Aziz 23.11.): über den Gruppen steht farbig der Name der Playlist; ein Druck
 * darauf klappt ihre Gruppen zu (nur noch der Name ist zu sehen) bzw. wieder auf.
 *  – eine Playlist: ein Abschnitt mit ihrem Namen, darin Favoriten und alle Gruppen
 *  – mehrere: „Alle Playlists“ mit den Favoriten, darunter je Playlist ein Abschnitt (Reihenfolge wie in der Liste)
 * Reine Rechenregel ohne Android (Unit-Test GroupSectionsTest).
 */
object GroupSections {
    /** Abschnitts-ID für „Alle Playlists“ (Favoriten bei mehreren Playlists) */
    const val ALL = "*"

    data class Section(val id: String, val title: String, val groups: List<Int>)

    sealed interface Row {
        data class Header(val section: Section, val collapsed: Boolean) : Row
        data class Item(val index: Int, val sectionId: String) : Row
    }

    /**
     * [multi] = mehrere Playlists gleichzeitig zu sehen (kein Playlist-Filter). [singleId]/[singleTitle] = die eine
     * Playlist, wenn nicht [multi].
     */
    fun build(groups: List<ChannelGroup>, multi: Boolean, singleId: String?, singleTitle: String): List<Section> {
        if (groups.isEmpty()) return emptyList()
        if (!multi) return listOf(Section(singleId ?: ALL, singleTitle, groups.indices.toList()))
        val all = ArrayList<Int>()
        val bySource = LinkedHashMap<String, MutableList<Int>>()
        val names = HashMap<String, String>()
        groups.forEachIndexed { i, g ->
            val sid = g.sourceId
            if (g.isFavorites || sid == null) all.add(i)
            else {
                bySource.getOrPut(sid) { ArrayList() }.add(i)
                if (g.sourceName != null) names.putIfAbsent(sid, g.sourceName)
            }
        }
        val out = ArrayList<Section>()
        if (all.isNotEmpty()) out.add(Section(ALL, "Alle Playlists", all))
        bySource.forEach { (sid, idx) -> out.add(Section(sid, names[sid] ?: "Playlist", idx)) }
        return out
    }

    /** Zeilen der Liste: je Abschnitt die Kopfzeile, bei aufgeklapptem Abschnitt darunter seine Gruppen */
    fun rows(sections: List<Section>, collapsed: Set<String>): List<Row> = buildList {
        sections.forEach { s ->
            val c = s.id in collapsed
            add(Row.Header(s, c))
            if (!c) s.groups.forEach { add(Row.Item(it, s.id)) }
        }
    }

    /** Abschnitt einer Gruppe */
    fun sectionOf(sections: List<Section>, groupIndex: Int): Section? = sections.firstOrNull { groupIndex in it.groups }

    /**
     * Nächste Gruppe beim Weiterschalten (◀ ▶, Spulen): Gruppen zugeklappter Playlists werden übersprungen – außer es ist
     * alles zugeklappt. [usable] = Gruppe hat Sender.
     */
    fun next(sections: List<Section>, collapsed: Set<String>, count: Int, from: Int, delta: Int, usable: (Int) -> Boolean): Int {
        if (count == 0) return from
        val open = sections.filter { it.id !in collapsed }.flatMap { it.groups }.toHashSet()
        val allowed: (Int) -> Boolean = if (open.any(usable)) { i -> i in open && usable(i) } else usable
        var g = from
        for (t in 0 until count) {
            g = Math.floorMod(g + delta, count)
            if (allowed(g)) return g
        }
        return from
    }
}
