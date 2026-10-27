package de.aze.iptv.data

/**
 * Nummerierung:
 *  - Favoriten bekommen 1, 2, 3 … in ihrer festgelegten Reihenfolge.
 *  - Danach folgen die Gruppen aller Playlists – in eigener Reihenfolge, falls festgelegt.
 *  - Ausgeblendete Gruppen und Sender bekommen keine Nummer und erscheinen nirgends.
 *  - Ein Favorit taucht nicht ein zweites Mal in seiner Gruppe auf, damit jeder Sender genau eine Nummer hat.
 * Ändert sich etwas, rücken die Nummern dahinter automatisch nach.
 */
object Numbering {

    /** Alle Gruppen in der eingestellten Reihenfolge, inklusive ausgeblendeter (für die Bearbeiten-Ansicht). */
    fun arrange(channels: List<Channel>, sources: List<PlaylistSource>, layout: ListLayout): List<EditGroup> {
        val names = sources.associate { it.id to it.name }
        val unique = LinkedHashMap<String, Channel>()
        channels.forEach { if (!unique.containsKey(it.key)) unique[it.key] = it }

        val byGroup = LinkedHashMap<String, MutableList<Channel>>()
        val info = HashMap<String, Pair<String, String>>() // key -> (sourceId, Gruppenname)
        unique.values.forEach { ch ->
            val k = ListLayout.groupKey(ch.source, ch.group)
            byGroup.getOrPut(k) { ArrayList() }.add(ch)
            info.getOrPut(k) { ch.source to ch.group }
        }

        val orderedKeys = layout.groupOrder.filter { byGroup.containsKey(it) } +
            byGroup.keys.filter { it !in layout.groupOrder }

        return orderedKeys.map { k ->
            val list = byGroup.getValue(k)
            val custom = layout.channelOrder[k]
            val ordered = if (custom == null) {
                list
            } else {
                val pos = custom.withIndex().associate { it.value to it.index }
                list.withIndex().sortedBy { pos[it.value.key] ?: (custom.size + it.index) }.map { it.value }
            }
            val (sourceId, original) = info.getValue(k)
            EditGroup(
                key = k,
                name = layout.groupNames[k] ?: original,
                originalName = original,
                sourceName = names[sourceId],
                channels = ordered,
                hidden = k in layout.hiddenGroups,
            )
        }
    }

    fun build(
        channels: List<Channel>,
        favoriteKeys: List<String>,
        sources: List<PlaylistSource> = emptyList(),
        layout: ListLayout = ListLayout(),
    ): Lineup {
        val unique = HashMap<String, Channel>()
        channels.forEach { if (!unique.containsKey(it.key)) unique[it.key] = it }

        var number = 1
        val all = ArrayList<NumberedChannel>(unique.size)
        val groups = ArrayList<ChannelGroup>()

        val favorites = favoriteKeys.mapNotNull { unique[it] }
        val favoriteSet = favorites.map { it.key }.toHashSet()
        val favList = favorites.map { NumberedChannel(number++, it, true) }
        all.addAll(favList)
        groups.add(ChannelGroup("Favoriten", isFavorites = true, channels = favList))

        arrange(channels, sources, layout).forEach { g ->
            if (g.hidden) return@forEach
            val visible = g.channels.filter { it.key !in favoriteSet && it.key !in layout.hiddenChannels }
            if (visible.isEmpty()) return@forEach
            val numbered = visible.map { NumberedChannel(number++, it, false) }
            all.addAll(numbered)
            val first = g.channels.first()
            groups.add(
                ChannelGroup(
                    name = g.name,
                    isFavorites = false,
                    channels = numbered,
                    sourceId = first.source,
                    sourceName = g.sourceName,
                )
            )
        }
        return Lineup(groups, all)
    }
}
