package de.aze.iptv.data

/**
 * Was die Senderlisten-Verwaltung bearbeitet: die eigene Liste (AppController) oder bei der Fernhilfe die Liste
 * einer anderen Box (RemoteListModel). Die Bedienung ist in beiden Fällen dieselbe.
 */
interface ListEditTarget {
    val editGroups: List<EditGroup>
    val hiddenChannels: Set<String>
    /** Zusatzzeile unter einem Sender, z. B. „Nr. 5 · Favorit“ (leer = nichts) */
    fun channelNote(channel: Channel): String
    fun moveGroup(key: String, delta: Int)
    fun toggleGroupHidden(key: String)
    fun renameGroup(key: String, name: String)
    fun moveChannel(groupKey: String, channelKey: String, delta: Int)
    fun toggleChannelHidden(channelKey: String)
    fun resetGroup(groupKey: String)
}

/** Was die Playlist-Verwaltung bearbeitet: die eigenen Playlists oder (Fernhilfe) die einer anderen Box. */
interface PlaylistTarget {
    val sources: List<PlaylistSource>
    val sourceStatus: Map<String, String>
    fun addSource(source: PlaylistSource)
    /** Link, Login oder Name korrigieren (gleiche id) */
    fun updateSource(source: PlaylistSource)
    fun setSourceEnabled(source: PlaylistSource, on: Boolean)
    fun renameSource(source: PlaylistSource, name: String)
    fun moveSource(source: PlaylistSource, delta: Int)
    fun removeSource(source: PlaylistSource)
}
