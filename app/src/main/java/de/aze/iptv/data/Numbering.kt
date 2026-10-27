package de.aze.iptv.data

/**
 * Nummerierung:
 *  - Favoriten bekommen 1, 2, 3 … in ihrer festgelegten Reihenfolge.
 *  - Danach folgen alle Playlists nacheinander (in der Reihenfolge aus den Einstellungen),
 *    jeweils mit ihren Gruppen in Playlist-Reihenfolge.
 *  - Ein Favorit taucht nicht ein zweites Mal in seiner Gruppe auf, damit jeder Sender genau eine Nummer hat.
 * Kommt ein Favorit dazu oder fällt weg, rücken die Nummern dahinter automatisch nach.
 */
object Numbering {

    fun build(channels: List<Channel>, favoriteKeys: List<String>, sources: List<PlaylistSource> = emptyList()): Lineup {
        val names = sources.associate { it.id to it.name }
        val unique = LinkedHashMap<String, Channel>()
        channels.forEach { if (!unique.containsKey(it.key)) unique[it.key] = it }

        var number = 1
        val all = ArrayList<NumberedChannel>(unique.size)
        val groups = ArrayList<ChannelGroup>()

        val favorites = favoriteKeys.mapNotNull { unique[it] }
        val favoriteSet = favorites.map { it.key }.toHashSet()
        val favList = favorites.map { NumberedChannel(number++, it, true) }
        all.addAll(favList)
        groups.add(ChannelGroup("Favoriten", isFavorites = true, channels = favList))

        // Gruppen je Playlist getrennt halten (gleiche Gruppennamen aus zwei Playlists werden nicht vermischt).
        val byGroup = LinkedHashMap<Pair<String, String>, MutableList<Channel>>()
        unique.values.forEach { ch ->
            if (ch.key !in favoriteSet) byGroup.getOrPut(ch.source to ch.group) { ArrayList() }.add(ch)
        }
        byGroup.forEach { (k, list) ->
            val numbered = list.map { NumberedChannel(number++, it, false) }
            all.addAll(numbered)
            groups.add(
                ChannelGroup(
                    name = k.second,
                    isFavorites = false,
                    channels = numbered,
                    sourceId = k.first,
                    sourceName = names[k.first],
                )
            )
        }
        return Lineup(groups, all)
    }
}
