package de.aze.iptv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import de.aze.iptv.data.Channel
import de.aze.iptv.data.EditGroup
import de.aze.iptv.data.Epg
import de.aze.iptv.data.EpgRepository
import de.aze.iptv.data.Lineup
import de.aze.iptv.data.ListLayout
import de.aze.iptv.data.Network
import de.aze.iptv.data.ProxySettings
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.BufferLevel
import de.aze.iptv.player.PlayerEngine
import de.aze.iptv.ui.Palette
import de.aze.iptv.ui.Schemes
import androidx.compose.runtime.mutableStateListOf
import de.aze.iptv.data.NumberedChannel
import de.aze.iptv.data.Numbering
import de.aze.iptv.data.PlaylistSource
import de.aze.iptv.data.Repository
import de.aze.iptv.data.SourceType
import de.aze.iptv.data.StalkerClient
import de.aze.iptv.data.Store
import de.aze.iptv.data.YouTube
import de.aze.iptv.data.YtEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Zentraler Zustand der App: Quellen, Senderliste, Favoriten, aktueller Sender. */
class AppController(context: Context, private val scope: CoroutineScope) {

    private val store = Store(context)
    private val repo = Repository(context)
    private val epgRepo = EpgRepository(context)

    /** Programmzeitschrift; Zuordnung zu den Sendern über tvg-id oder Namen. */
    var epg by mutableStateOf(Epg.EMPTY, referentialEqualityPolicy())
        private set
    var epgLoading by mutableStateOf(false)
        private set
    var epgUrl by mutableStateOf(store.epgUrl)
        private set

    fun updateEpgUrl(url: String) {
        epgUrl = url.trim()
        store.epgUrl = epgUrl
    }

    fun refreshEpg() {
        if (epgLoading) return
        val urls = repo.epgUrls(sources) + listOfNotNull(epgUrl.takeIf { it.startsWith("http") })
        if (urls.isEmpty()) {
            message = "Keine EPG-Quelle gefunden. Bei M3U-Listen ohne Programmdaten eine EPG-Adresse eintragen."
            return
        }
        scope.launch {
            epgLoading = true
            val result = epgRepo.refresh(urls, channels)
            epgLoading = false
            result.onSuccess {
                epg = it
                message = "Programmzeitschrift geladen: ${it.channelCount} Sender mit Programm."
            }.onFailure {
                message = "Programmzeitschrift konnte nicht geladen werden."
            }
        }
    }

    // Anbieter-M3U-Links werden automatisch zu Server + Login (siehe PlaylistSource.normalized)
    var sources by mutableStateOf(store.sources().map { it.normalized() })
        private set
    var favorites by mutableStateOf(store.favorites())
        private set
    // referentialEqualityPolicy: große Listen nicht bei jeder Zuweisung Element für Element vergleichen
    var lineup by mutableStateOf(Lineup.EMPTY, referentialEqualityPolicy())
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
    var editGroups by mutableStateOf<List<EditGroup>>(emptyList(), referentialEqualityPolicy())
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

    /** Farbschema (0 Mitternacht, 1 TiviMate Grau, 2 TiviMate Blau, 3 Petrol). */
    var schemeIndex by mutableStateOf(store.scheme.coerceIn(0, Schemes.lastIndex))
        private set

    init {
        Palette.scheme = Schemes[schemeIndex]
    }

    fun updateScheme(i: Int) {
        schemeIndex = i.coerceIn(0, Schemes.lastIndex)
        store.scheme = schemeIndex
        Palette.scheme = Schemes[schemeIndex]
    }

    /** Senderliste im Enigma2-Stil (Bouquets) statt der modernen Ansicht. */
    var enigmaStyle by mutableStateOf(store.listStyle == "ENIGMA")
        private set

    fun updateListStyle(enigma: Boolean) {
        enigmaStyle = enigma
        store.listStyle = if (enigma) "ENIGMA" else "MODERN"
    }

    var playerEngine by mutableStateOf(PlayerEngine.of(store.playerEngine))
        private set

    fun updateEngine(e: PlayerEngine) {
        playerEngine = e
        store.playerEngine = e.name
    }

    /** Sender, die in dieser Sitzung mit VLC laufen (weil Media3 sie nicht abspielen konnte). */
    val vlcFallback = mutableStateListOf<String>()

    fun markVlc(key: String) {
        if (key !in vlcFallback) vlcFallback.add(key)
    }

    fun toggleVlc(key: String) {
        if (key in vlcFallback) vlcFallback.remove(key) else vlcFallback.add(key)
    }

    // ---- Filme & Serien: Weiterschauen ----
    private val resume = store.resumePositions()
    var resumeVersion by mutableStateOf(0)
        private set

    fun resumeOf(key: String): Long {
        @Suppress("UNUSED_VARIABLE") val v = resumeVersion
        return resume[key] ?: 0L
    }

    /** Stelle merken – nicht ganz am Anfang und nicht kurz vor Schluss (dann gilt es als gesehen). */
    fun saveResume(key: String, posMs: Long, durMs: Long) {
        if (posMs < 60_000 || (durMs > 0 && posMs > durMs - 180_000)) resume.remove(key) else resume[key] = posMs
        store.saveResumePositions(resume)
        resumeVersion++
    }

    /** Bildqualität: sofort die beste Stufe statt langsam hochzuschalten. */
    var forceHighest by mutableStateOf(store.forceHighest)
        private set
    /** Tunneling: Bild direkt an den Video-Chip (kann Farben/Bewegung verbessern, nicht auf jedem Gerät stabil). */
    var tunneling by mutableStateOf(store.tunneling)
        private set

    fun updateForceHighest(on: Boolean) {
        forceHighest = on
        store.forceHighest = on
    }

    fun updateTunneling(on: Boolean) {
        tunneling = on
        store.tunneling = on
    }

    var autostart by mutableStateOf(store.autostart)
        private set

    fun updateAutostart(on: Boolean) {
        autostart = on
        store.autostart = on
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
            rebuild(restoreKey = store.lastChannelKey).join()
            updateStatus(emptyMap())
            loading = false
            epg = epgRepo.load()
            if (needsRefresh()) refreshNow()
            else if (sources.isNotEmpty() && epgRepo.isStale(12)) refreshEpg()
        }
    }

    /** Beim Start: Liste neu laden, wenn das gewählte Intervall abgelaufen ist. */
    private fun needsRefresh(): Boolean {
        if (sources.isEmpty()) return false
        if (!repo.hasCache(sources)) return true
        if (refreshHours <= 0) return false
        return System.currentTimeMillis() - lastRefresh >= refreshHours * 3_600_000L
    }

    /** Je Playlist: wie viele Sender geladen wurden oder was schiefging (für die Einstellungen). */
    var sourceStatus by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private fun updateStatus(errors: Map<String, String>) {
        val counts = channels.groupingBy { it.source }.eachCount()
        sourceStatus = sources.associate { src ->
            val n = counts[src.id] ?: 0
            src.id to when {
                errors[src.id] != null -> "Fehler: ${errors[src.id]}" + if (n > 0) " (alte Liste mit $n Sendern bleibt)" else ""
                n == 0 -> "Liste geladen, aber keine Live-Sender darin gefunden"
                else -> "$n Sender geladen"
            }
        }
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
            updateStatus(errors)
            rebuild(restoreKey = current?.channel?.key).join()
            loading = false
            message = if (errors.isEmpty()) "Senderliste aktualisiert: ${lineup.all.size} Sender"
            else errors.entries.joinToString("\n") { (id, e) -> "${sources.firstOrNull { it.id == id }?.name ?: "Playlist"}: $e" }
            // Programmzeitschrift gleich mit aktualisieren
            refreshEpg()
        }
    }

    // ---- YouTube Live (eigene Gruppe, offizieller eingebetteter Player) ----

    var youtube by mutableStateOf(store.youtube)
        private set

    /** true, wenn es Playlists oder YouTube-Sender gibt. */
    val hasAnySource: Boolean get() = sources.isNotEmpty() || youtube.isNotEmpty()

    fun addYoutube(link: String, name: String) {
        scope.launch {
            message = "YouTube-Kanal wird gesucht …"
            val entry = YouTube.resolve(link, name)
            if (entry == null) {
                message = "Kanal nicht gefunden. Bitte den Link vom Kanal (youtube.com/@name) oder die Kanal-ID (UC…) eingeben."
                return@launch
            }
            youtube = youtube + entry
            store.youtube = youtube
            rebuild(restoreKey = current?.channel?.key)
            message = "„${entry.name}“ zur Gruppe YouTube Live hinzugefügt."
        }
    }

    fun removeYoutube(entry: YtEntry) {
        youtube = youtube.filterNot { it.id == entry.id }
        store.youtube = youtube
        rebuild(restoreKey = current?.channel?.key)
    }

    fun moveYoutube(entry: YtEntry, delta: Int) {
        val list = youtube.toMutableList()
        val i = list.indexOfFirst { it.id == entry.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        youtube = list
        store.youtube = list
        rebuild(restoreKey = current?.channel?.key)
    }

    private var rebuildJob: Job? = null

    /**
     * Nummerierung und Gruppen neu berechnen – im Hintergrund, damit die Oberfläche bei
     * großen Listen (zehntausende Sender) nicht hängt.
     */
    private fun rebuild(restoreKey: String?): Job {
        // YouTube-Gruppe kommt direkt nach den Favoriten (in "Senderliste bearbeiten" verschiebbar).
        val all = youtube.map { it.toChannel() } + channels
        val named = sources + PlaylistSource(YouTube.SOURCE, "YouTube", SourceType.M3U, "")
        val favs = favorites
        val lay = layout
        rebuildJob?.cancel()
        return scope.launch {
            val (lu, eg) = withContext(Dispatchers.Default) {
                Numbering.build(all, favs, named, lay) to Numbering.arrange(all, named, lay)
            }
            lineup = lu
            editGroups = eg
            current = restoreKey?.let { lu.byKey[it] } ?: current?.let { lu.byKey[it.channel.key] } ?: lu.all.firstOrNull()
        }.also { rebuildJob = it }
    }

    /**
     * Abspiel-Adresse für einen Sender. Bei Portal + MAC (Stalker) wird der gültige Link erst jetzt beim Portal angefordert.
     */
    suspend fun resolveStreamUrl(url: String): String {
        if (!StalkerClient.isStalker(url)) return url
        val (sid, cmd) = StalkerClient.parse(url) ?: throw java.io.IOException("Ungültiger Portal-Sender")
        val src = sources.firstOrNull { it.id == sid } ?: throw java.io.IOException("Portal nicht mehr vorhanden")
        return withContext(Dispatchers.IO) {
            runCatching { StalkerClient.forSource(src).createLink(cmd) }.getOrElse { e ->
                // Manche Portale liefern schon einen direkten Link mit
                val direct = StalkerClient.cleanCmd(cmd)
                if (direct.startsWith("http") && !direct.contains("localhost")) direct else throw e
            }
        }
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
        sources = sources + source.normalized()
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
