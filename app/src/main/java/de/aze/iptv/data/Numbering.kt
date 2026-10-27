package de.aze.iptv.data

/**
 * Nummerierung:
 *  - Favoriten bekommen 1, 2, 3 … in ihrer festgelegten Reihenfolge.
 *  - Danach zählen alle anderen Gruppen in Playlist-Reihenfolge weiter.
 *  - Ein Favorit taucht nicht ein zweites Mal in seiner Gruppe auf, damit jeder Sender genau eine Nummer hat.
 * Kommt ein Favorit dazu oder fällt weg, rücken die Nummern dahinter automatisch nach.
 */
object Numbering {

    fun build(channels: List<Channel>, favoriteKeys: List<String>): Lineup {
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

        val byGroup = LinkedHashMap<String, MutableList<Channel>>()
        unique.values.forEach { ch ->
            if (ch.key !in favoriteSet) byGroup.getOrPut(ch.group) { ArrayList() }.add(ch)
        }
        byGroup.forEach { (name, list) ->
            val numbered = list.map { NumberedChannel(number++, it, false) }
            all.addAll(numbered)
            groups.add(ChannelGroup(name, isFavorites = false, channels = numbered))
        }
        return Lineup(groups, all)
    }
}
