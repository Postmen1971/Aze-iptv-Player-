package de.aze.iptv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.aze.iptv.data.Channel
import de.aze.iptv.data.Lineup
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

    private var channels: List<Channel> = emptyList()

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
        lineup = Numbering.build(channels, favorites)
        current = restoreKey?.let { lineup.byKey[it] } ?: current?.let { lineup.byKey[it.channel.key] } ?: lineup.all.firstOrNull()
    }

    fun play(channel: NumberedChannel) {
        current = channel
        store.lastChannelKey = channel.channel.key
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

    fun removeSource(source: PlaylistSource) {
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
