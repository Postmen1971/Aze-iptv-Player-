package de.aze.iptv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.aze.iptv.data.Channel
import de.aze.iptv.data.EditGroup
import de.aze.iptv.data.Lineup
import de.aze.iptv.data.ListLayout
import de.aze.iptv.data.Network
import de.aze.iptv.data.ProxySettings
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.BufferLevel
import de.aze.iptv.data.NumberedChannel
import de.aze.iptv.data.Numbering
import de.aze.iptv.data.PlaylistSource
import de.aze.iptv.data.Repository
import de.aze.iptv.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Zentraler Zustand der App: Quellen, Senderliste, Favoriten, aktueller Sender. */
class AppController(context: Context, private val scope: CoroutineScope) {

    private val store = Store(context)
    private val repo = Repository(context)

    var sources by mutableStateOf(store.sources())
        private set
    var favorites by mutableStateOf(store.favorites())
        private set
    var lineup by mutableStateOf(Lineup.EMPTY)
        private set
    var current by mutableStateOf<NumberedChannel?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var refreshHours by mutableStateOf(store.refreshHours)
        private set
    var lastRefresh by mutableStateOf(store.lastRefresh)
        private set

    var bufferLevel by mutableStateOf(BufferLevel.of(store.bufferLevel))
        private set
    var aspectMode by mutableStateOf(AspectMode.of(store.aspectMode))
        private set
    var audioLanguage by mutableStateOf(store.audioLanguage)
        private set
    var subtitlesOn by mutableStateOf(store.subtitlesOn)
        private set
    var proxy by mutableStateOf(store.proxy)
        private set

    private var channels: List<Channel> = emptyList()

    init {
        Network.applyProxy(proxy)
    }

    // ---- Senderliste bearbeiten (wie Bouquet-Bearbeitung bei Enigma2) ----

    private var layout: ListLayout = store.layout

    /** Alle Gruppen inkl. ausgeblendeter, in der eingestellten Reihenfolge. */
    var editGroups by mutableStateOf<List<EditGroup>>(emptyList())
        private set

    val hiddenChannels: Set<String> get() = layout.hiddenChannels

    private fun saveLayout(l: ListLayout) {
        layout = l
        store.layout = l
        rebuild(restoreKey = current?.channel?.key)
    }

    fun moveGroup(key: String, delta: Int) {
        val keys = editGroups.map { it.key }.toMutableList()
        val i = keys.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in keys.indices) return
        keys[i] = keys[j].also { keys[j] = keys[i] }
        saveLayout(layout.copy(groupOrder = keys))
    }

    fun toggleGroupHidden(key: String) {
        val h = layout.hiddenGroups
        saveLayout(layout.copy(hiddenGroups = if (key in h) h - key else h + key))
    }

    fun renameGroup(key: String, name: String) {
        val trimmed = name.trim()
        val original = editGroups.firstOrNull { it.key == key }?.originalName
        val names = if (trimmed.isEmpty() || trimmed == original) layout.groupNames - key else layout.groupNames + (key to trimmed)
        saveLayout(layout.copy(groupNames = names))
    }

    fun moveChannel(groupKey: String, channelKey: String, delta: Int) {
        val group = editGroups.firstOrNull { it.key == groupKey } ?: return
        val keys = group.channels.map { it.key }.toMutableList()
        val i = keys.indexOf(channelKey)
        val j = i + delta
        if (i < 0 || j !in keys.indices) return
        keys[i] = keys[j].also { keys[j] = keys[i] }
        saveLayout(layout.copy(channelOrder = layout.channelOrder + (groupKey to keys)))
    }

    fun toggleChannelHidden(channelKey: String) {
        val h = layout.hiddenChannels
        saveLayout(layout.copy(hiddenChannels = if (channelKey in h) h - channelKey else h + channelKey))
    }

    /** Eigene Anordnung einer Gruppe verwerfen. */
    fun resetGroup(groupKey: String) {
        saveLayout(
            layout.copy(
                channelOrder = layout.channelOrder - groupKey,
                hiddenChannels = layout.hiddenChannels - editGroups.firstOrNull { it.key == groupKey }?.channels?.map { it.key }.orEmpty().toSet(),
                groupNames = layout.groupNames - groupKey,
            )
        )
    }

    /** Senderliste im Enigma2-Stil (Bouquets) statt der modernen Ansicht. */
    var enigmaStyle by mutableStateOf(store.listStyle == "ENIGMA")
        private set

    fun updateListStyle(enigma: Boolean) {
        enigmaStyle = enigma
        store.listStyle = if (enigma) "ENIGMA" else "MODERN"
    }

    fun checkPin(pin: String): Boolean = store.checkPin(pin)
    fun changePin(pin: String) = store.setPin(pin)
    val pinIsDefault: Boolean get() = store.pinIsDefault

    fun updateBuffer(level: BufferLevel) {
        bufferLevel = level
        store.bufferLevel = level.name
    }

    fun updateAspect(mode: AspectMode) {
        aspectMode = mode
        store.aspectMode = mode.name
    }

    fun updateAudioLanguage(lang: String?) {
        audioLanguage = lang
        store.audioLanguage = lang
    }

    fun updateSubtitles(on: Boolean) {
        subtitlesOn = on
        store.subtitlesOn = on
    }

    fun updateProxy(settings: ProxySettings) {
        proxy = settings
        store.proxy = settings
        Network.applyProxy(settings)
    }

    fun start() {
        scope.launch {
            loading = true
            channels = repo.loadCached(sources)
            rebuild(restoreKey = store.lastChannelKey)
            loading = false
            if (needsRefresh()) refreshNow()
        }
    }

    /** Beim Start: Liste neu laden, wenn das gewählte Intervall abgelaufen ist. */
    private fun needsRefresh(): Boolean {
        if (sources.isEmpty()) return false
        if (!repo.hasCache(sources)) return true
        if (refreshHours <= 0) return false
        return System.currentTimeMillis() - lastRefresh >= refreshHours * 3_600_000L
    }

    fun refreshNow() {
        if (loading) return
        scope.launch {
            loading = true
            message = "Senderliste wird aktualisiert …"
            val errors = repo.refresh(sources)
            channels = repo.loadCached(sources)
            val now = System.currentTimeMillis()
            if (errors.size < sources.size) {
                lastRefresh = now
                store.lastRefresh = now
            }
            rebuild(restoreKey = current?.channel?.key)
            loading = false
            message = if (errors.isEmpty()) "Senderliste aktualisiert: ${lineup.all.size} Sender" else errors.joinToString("\n")
        }
    }

    private fun rebuild(restoreKey: String?) {
        lineup = Numbering.build(channels, favorites, sources, layout)
        editGroups = Numbering.arrange(channels, sources, layout)
        current = restoreKey?.let { lineup.byKey[it] } ?: current?.let { lineup.byKey[it.channel.key] } ?: lineup.all.firstOrNull()
    }

    /** Der zuletzt geschaute Sender (für OK = zurückspringen). */
    private var previousKey: String? = null

    fun play(channel: NumberedChannel) {
        val before = current?.channel?.key
        if (before != null && before != channel.channel.key) previousKey = before
        current = channel
        store.lastChannelKey = channel.channel.key
    }

    /** Springt zwischen aktuellem und vorherigem Sender hin und her. */
    fun playPrevious(): Boolean {
        val target = previousKey?.let { lineup.byKey[it] } ?: return false
        play(target)
        return true
    }

    fun step(delta: Int) {
        val all = lineup.all
        if (all.isEmpty()) return
        val idx = current?.let { c -> all.indexOfFirst { it.channel.key == c.channel.key } } ?: -1
        val next = if (idx < 0) 0 else Math.floorMod(idx + delta, all.size)
        play(all[next])
    }

    /** Direktwahl: true, wenn es die Nummer gibt. */
    fun playNumber(number: Int): Boolean {
        val target = lineup.byNumber[number] ?: return false
        play(target)
        return true
    }

    fun isFavorite(channel: Channel): Boolean = channel.key in favorites

    fun toggleFavorite(channel: Channel) {
        favorites = if (channel.key in favorites) favorites - channel.key else favorites + channel.key
        store.saveFavorites(favorites)
        rebuild(restoreKey = current?.channel?.key)
    }

    fun moveFavorite(channel: Channel, delta: Int) {
        val list = favorites.toMutableList()
        val i = list.indexOf(channel.key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        favorites = list
        store.saveFavorites(list)
        rebuild(restoreKey = current?.channel?.key)
    }

    fun addSource(source: PlaylistSource) {
        sources = sources + source
        store.saveSources(sources)
        refreshNow()
    }

    /** Welche Playlist die Senderliste zeigt: null = alle untereinander. */
    var playlistFilter by mutableStateOf<String?>(null)
        private set

    /** Schaltet die Senderliste weiter: Alle → Playlist 1 → Playlist 2 → … → Alle. */
    fun nextPlaylistFilter() {
        val ids = listOf<String?>(null) + sources.map { it.id }
        playlistFilter = ids[(ids.indexOf(playlistFilter) + 1) % ids.size]
    }

    fun playlistName(id: String?): String =
        if (id == null) "Alle" else sources.firstOrNull { it.id == id }?.name ?: "Playlist"

    fun renameSource(source: PlaylistSource, name: String) {
        if (name.isBlank()) return
        sources = sources.map { if (it.id == source.id) it.copy(name = name.trim()) else it }
        store.saveSources(sources)
        rebuild(restoreKey = current?.channel?.key)
    }

    /** Reihenfolge der Playlists ändern (bestimmt auch die Nummerierung). */
    fun moveSource(source: PlaylistSource, delta: Int) {
        val list = sources.toMutableList()
        val i = list.indexOfFirst { it.id == source.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        sources = list
        store.saveSources(list)
        scope.launch {
            channels = repo.loadCached(sources)
            rebuild(restoreKey = current?.channel?.key)
        }
    }

    fun removeSource(source: PlaylistSource) {
        if (playlistFilter == source.id) playlistFilter = null
        sources = sources.filterNot { it.id == source.id }
        store.saveSources(sources)
        repo.delete(source)
        scope.launch {
            channels = repo.loadCached(sources)
            rebuild(restoreKey = current?.channel?.key)
        }
    }

    fun updateRefreshHours(hours: Int) {
        refreshHours = hours
        store.refreshHours = hours
    }
}
