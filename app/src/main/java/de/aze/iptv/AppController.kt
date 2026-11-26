package de.aze.iptv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import de.aze.iptv.data.Channel
import de.aze.iptv.data.EditGroup
import de.aze.iptv.data.Epg
import de.aze.iptv.data.EpgChannel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Zentraler Zustand der App: Quellen, Senderliste, Favoriten, aktueller Sender. */
class AppController(context: Context, outerScope: CoroutineScope) : de.aze.iptv.data.ListEditTarget, de.aze.iptv.data.PlaylistTarget {

    /**
     * Eigener Bereich für Hintergrundarbeiten: ein Fehler in EINER Aufgabe (Laden, EPG, Portal …)
     * wird gemeldet, beendet aber weder die anderen Aufgaben noch die App.
     */
    private val scope: CoroutineScope = CoroutineScope(
        outerScope.coroutineContext + kotlinx.coroutines.SupervisorJob(outerScope.coroutineContext[kotlinx.coroutines.Job]) +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                android.util.Log.e("AzeIPTV", "Hintergrundfehler", e)
                message = "Fehler: ${e.message ?: e.javaClass.simpleName}"
            },
    )

    private val store = Store(context).also {
        de.aze.iptv.data.LiveOnSat.debugFile = java.io.File(context.cacheDir, "liveonsat.html")
    }
    private val repo = Repository(context)
    private val epgRepo = EpgRepository(context)

    /** Programmzeitschrift; Zuordnung zu den Sendern über tvg-id oder Namen. */
    var epg by mutableStateOf(Epg.EMPTY, referentialEqualityPolicy())
        private set
    var epgLoading by mutableStateOf(false)
        private set
    /** Eigene (externe) EPG-Quellen, eine Adresse je Zeile gespeichert */
    var epgExtra by mutableStateOf(store.epgUrl.lines().map { it.trim() }.filter { it.startsWith("http") })
        private set

    /** EPG-Adressen aus den Playlists (Anbieter / url-tvg in der M3U) */
    fun epgFromSources(): List<String> = runCatching { repo.epgUrls(activeSources) }.getOrDefault(emptyList())

    fun addEpgUrl(url: String) {
        val u = url.trim()
        if (!u.startsWith("http")) { message = "Bitte eine Adresse mit http:// oder https:// eingeben."; return }
        if (u in epgExtra) return
        epgExtra = epgExtra + u
        store.epgUrl = epgExtra.joinToString("\n")
        refreshEpg()
    }

    fun removeEpgUrl(url: String) {
        epgExtra = epgExtra - url
        store.epgUrl = epgExtra.joinToString("\n")
    }

    /** Zeitverschiebung je EPG-Quelle in Stunden */
    var epgShifts by mutableStateOf(store.epgShifts())
        private set

    fun shiftEpg(url: String, hours: Int) {
        val h = hours.coerceIn(-12, 12)
        epgShifts = if (h == 0) epgShifts - url else epgShifts + (url to h)
        store.saveEpgShifts(epgShifts)
        refreshEpg(quiet = true)
    }

    var epgReport by mutableStateOf("")
        private set

    /** Eigene Zuordnung Sender → EPG-Kennung */
    var epgMap by mutableStateOf(store.epgMap())
        private set

    fun setEpgMapping(ch: Channel, epgId: String?) {
        epgMap = if (epgId == null) epgMap - ch.key else epgMap + (ch.key to epgId)
        store.saveEpgMap(epgMap)
        epg = epg.withManual(epgMap)
        // Programm für den neu zugeordneten Sender holen
        if (epgId != null && epg.programmes(ch) == null) refreshEpg(quiet = true)
    }

    suspend fun epgCatalog(): List<EpgChannel> = epgRepo.catalog()

    private var epgAgain = false

    var epgEnabled by mutableStateOf(store.epgEnabled)
        private set

    fun updateEpgEnabled(on: Boolean) {
        epgEnabled = on
        store.epgEnabled = on
        if (on) {
            scope.launch { epg = runCatching { epgRepo.load() }.getOrDefault(Epg.EMPTY).withManual(epgMap); refreshEpg(quiet = true) }
        } else {
            epg = Epg.EMPTY
        }
    }

    /**
     * [retries]: bei Fehler so oft nach je 1 min still erneut versuchen – nach dem Aufwachen aus dem Standby ist das
     * Netz bei Fernsehern oft noch nicht da (die Programmzeitschrift blieb dann bis zum Neuladen von Hand leer).
     */
    fun refreshEpg(quiet: Boolean = false, retries: Int = 0) {
        // Läuft schon: danach noch einmal laden (z. B. nach geänderter Zeitverschiebung)
        if (!epgEnabled) return
        // ein neuer Versuch ersetzt einen noch wartenden (sonst liefen nach mehreren Aktualisierungen mehrere Ketten)
        epgRetryJob?.cancel()
        if (epgLoading) { epgAgain = true; return }
        val urls = (epgFromSources() + epgExtra).distinct()
        if (urls.isEmpty()) {
            if (!quiet) message = "Keine EPG-Quelle gefunden. Bei M3U-Listen ohne Programmdaten unter Einstellungen → Programm (EPG) eine Quelle hinzufügen."
            return
        }
        scope.launch {
            epgLoading = true
            val result = epgRepo.refresh(urls, channels, epgMap, epgShifts)
            epgLoading = false
            epgReport = epgRepo.lastReport
            if (epgAgain) { epgAgain = false; refreshEpg(quiet = true) }
            result.onSuccess {
                epg = it
                if (!quiet) message = "Programmzeitschrift geladen: ${it.channelCount} Sender mit Programm (${epgRepo.lastReport})."
            }.onFailure {
                if (retries > 0) {
                    de.aze.iptv.ui.qaLog("EPG_AUTO fehlgeschlagen – neuer Versuch in 1 min (noch $retries)")
                    epgRetryJob = scope.launch { kotlinx.coroutines.delay(60_000); refreshEpg(quiet = true, retries = retries - 1) }
                } else if (!quiet) message = "Programmzeitschrift konnte nicht geladen werden."
            }
        }
    }

    /**
     * Programm älter als so viele Stunden → beim Start und beim Zurückkehren in die App neu laden. Die gespeicherten
     * Daten reichen nur 24 h voraus – bisher wurde nur beim Kaltstart nach 12 h neu geladen. Android TV / Google TV
     * beenden die App aber fast nie (Home-Taste, Standby): am nächsten Tag war das Programm abgelaufen und die gelbe
     * Zeile in der Senderliste leer, bis man in den Einstellungen „Programm jetzt laden“ drückte (Aziz 25.11.).
     */
    private val epgAutoHours = 8
    private var epgRetryJob: kotlinx.coroutines.Job? = null

    /** start() ist durch – erst danach prüft onForeground (sonst doppelt beim App-Start) */
    private var started = false

    /**
     * App ist (wieder) im Vordergrund – und danach alle 30 min, solange sie zu sehen ist (MainActivity): fällige
     * Senderliste, Programmzeitschrift und Zugangsdaten nachladen.
     */
    fun onForeground() {
        if (!started || loading || activeSources.isEmpty()) return
        if (needsRefresh()) {
            de.aze.iptv.ui.qaLog("AUTO_AKTUALISIEREN Senderliste fällig")
            refreshNow()
            return
        }
        if (epgEnabled && !epgLoading && epgRepo.isStale(epgAutoHours)) {
            de.aze.iptv.ui.qaLog("EPG_AUTO Programm älter als $epgAutoHours h – lädt neu")
            refreshEpg(quiet = true, retries = 3)
        }
        refreshAccountInfo(6 * 3_600_000L)
    }

    // ---- Ton-Bild-Synchronisation ----
    /** Versatz für alle Sender (ms, positiv = Ton später) */
    var audioDelay by mutableStateOf(store.audioDelay)
        private set
    /** Fußball: selbst bestätigte Zuordnung liveonsat-Sender → eigener Sender (wird beim nächsten Spiel zuerst genommen) */
    var losLearned by mutableStateOf(store.losLearned())
        private set

    private var losLearnedNames = store.losLearnedNames()

    fun learnLos(listed: String, nc: de.aze.iptv.data.NumberedChannel) {
        val k = listed.lowercase()
        losLearned = losLearned + (k to nc.channel.key)
        store.saveLosLearned(losLearned)
        // Originalschreibweise von liveonsat als Schlüssel fürs Teilen, Wert = eigener Sendername
        losLearnedNames = losLearnedNames.filterKeys { it.lowercase() != k } + (listed to nc.channel.name)
        store.saveLosLearnedNames(losLearnedNames)
    }

    fun forgetLos() {
        losLearned = emptyMap()
        losLearnedNames = emptyMap()
        store.saveLosLearned(emptyMap())
        store.saveLosLearnedNames(emptyMap())
    }

    /** Gelernte Zuordnungen als Text zum Teilen: liveonsat-Sender → eigener Sendername */
    fun losShareText(): String {
        val byKey = lineup.all.associateBy { it.channel.key }
        val names = losLearned.mapNotNull { (listed, key) ->
            val orig = losLearnedNames.keys.firstOrNull { it.lowercase() == listed } ?: listed
            val name = losLearnedNames[orig] ?: byKey[key]?.channel?.name ?: return@mapNotNull null
            orig to name
        }.toMap()
        return org.json.JSONObject(mapOf("app" to "Aze-IPTV-Player ${de.aze.iptv.BuildConfig.VERSION_NAME}", "map" to org.json.JSONObject(names as Map<*, *>)) as Map<*, *>).toString(2)
    }

    /** Gemeinschaftsliste: liveonsat-Sender (klein) → passende Sendernamen anderer Geräte */
    var community by mutableStateOf(de.aze.iptv.data.Community.parse(store.communityJson))
        private set

    /** Höchstens alle 12 h neu laden; ohne Internet bleibt die zuletzt geladene Liste. Nur im Hintergrund aufrufen. */
    fun refreshCommunity() {
        if (System.currentTimeMillis() - store.communityAt < 12 * 3_600_000L && community.isNotEmpty()) return
        runCatching {
            val json = de.aze.iptv.data.Community.fetch()
            val parsed = de.aze.iptv.data.Community.parse(json)
            store.communityJson = json
            store.communityAt = System.currentTimeMillis()
            community = parsed
        }
    }

    /** Zusätzlicher Versatz je Sender */
    var audioDelays by mutableStateOf(store.audioDelays())
        private set

    fun updateAudioDelay(ms: Int) {
        audioDelay = ms.coerceIn(-2000, 2000)
        store.audioDelay = audioDelay
    }

    fun channelDelay(key: String?): Int = key?.let { audioDelays[it] } ?: 0

    fun updateChannelDelay(key: String, ms: Int) {
        val v = ms.coerceIn(-2000, 2000)
        audioDelays = if (v == 0) audioDelays - key else audioDelays + (key to v)
        store.saveAudioDelays(audioDelays)
    }

    fun clearChannelDelays() {
        audioDelays = emptyMap()
        store.saveAudioDelays(audioDelays)
    }

    /** Wirksamer Versatz für einen Sender: allgemein + Sender */
    fun effectiveDelay(key: String?): Int = (audioDelay + channelDelay(key)).coerceIn(-3000, 3000)

    // Anbieter-M3U-Links werden automatisch zu Server + Login (siehe PlaylistSource.normalized)
    final override var sources by mutableStateOf(store.sources().map { it.normalized() })
        private set

    /** Nur die eingeschalteten Playlists – pausierte werden weder geladen noch angezeigt. */
    val activeSources: List<PlaylistSource> get() = sources.filter { it.enabled }

    /**
     * Playlist pausieren bzw. wieder einschalten. Beim Pausieren bleiben Zugangsdaten und die zuletzt geladene
     * Liste auf dem Gerät – beim Einschalten ist sie sofort wieder da (und wird nur neu geladen, wenn nötig).
     */
    override fun setSourceEnabled(source: PlaylistSource, on: Boolean) {
        sources = sources.map { if (it.id == source.id) it.copy(enabled = on) else it }
        store.saveSources(sources)
        if (!on && playlistFilter == source.id) playlistFilter = null
        scope.launch {
            channels = repo.loadCached(activeSources)
            updateStatus(emptyMap())
            rebuild(restoreKey = current?.channel?.key).join()
            if (!on) {
                // lief gerade ein Sender dieser Playlist: auf den ersten Sender wechseln
                if (current?.channel?.source == source.id) lineup.all.firstOrNull()?.let { play(it) }
                message = "„${source.name}“ pausiert – wird nicht mehr geladen. Einschalten unter Einstellungen → Playlists."
            } else {
                message = "„${source.name}“ ist wieder eingeschaltet."
                if (!repo.hasCache(listOf(source))) refreshNow()
            }
        }
    }
    var favorites by mutableStateOf(store.favorites())
        private set
    // referentialEqualityPolicy: große Listen nicht bei jeder Zuweisung Element für Element vergleichen
    var lineup by mutableStateOf(Lineup.EMPTY, referentialEqualityPolicy())
        private set
    var current by mutableStateOf<NumberedChannel?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Der letzte Sender wurde beim Start sofort gestartet (Senderliste kam erst danach) */
    var instantStart by mutableStateOf(false)
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
    final override var editGroups by mutableStateOf<List<EditGroup>>(emptyList(), referentialEqualityPolicy())
        private set

    override val hiddenChannels: Set<String> get() = layout.hiddenChannels

    override fun channelNote(channel: Channel): String {
        if (channel.key in layout.hiddenChannels) return "ausgeblendet"
        val nr = lineup.byKey[channel.key]?.number ?: return ""
        return if (isFavorite(channel)) "Nr. $nr · Favorit" else "Nr. $nr"
    }

    private fun saveLayout(l: ListLayout) {
        layout = l
        store.layout = l
        rebuild(restoreKey = current?.channel?.key)
    }

    /** Neue Anordnung aus der Web-Oberfläche (Handy/PC) übernehmen. */
    fun applyWebEdit(edits: List<de.aze.iptv.data.WebGroupEdit>, snapshot: List<EditGroup>) {
        val groupKeys = snapshot.map { it.key }.toSet()
        val channelKeys = snapshot.flatMap { g -> g.channels.map { it.key } }.toSet()
        val originals = snapshot.associate { it.key to it.originalName }
        val snapOrder = snapshot.associate { g -> g.key to g.channels.map { it.key } }
        var names = layout.groupNames
        edits.forEach { e ->
            val t = e.name.trim()
            names = if (t.isEmpty() || t == originals[e.key]) names - e.key else names + (e.key to t)
        }
        saveLayout(
            layout.copy(
                groupOrder = mergeGroupOrder(layout.groupOrder, edits.map { it.key }),
                hiddenGroups = (layout.hiddenGroups - groupKeys) + edits.filter { it.hidden }.map { it.key },
                groupNames = names,
                // nur Gruppen ablegen, deren Reihenfolge wirklich geändert wurde (sonst wuchsen die Einstellungen bei
                // großen Listen um mehrere MB und wurden bei jeder Änderung komplett neu geschrieben)
                channelOrder = layout.channelOrder + edits
                    .filter { e -> e.channels.map { it.first } != snapOrder[e.key] }
                    .associate { e -> e.key to e.channels.map { it.first } },
                hiddenChannels = (layout.hiddenChannels - channelKeys) +
                    edits.flatMap { e -> e.channels.filter { it.second }.map { it.first } },
            )
        )
        message = "Senderliste vom Handy übernommen."
    }

    override fun moveGroup(key: String, delta: Int) {
        val keys = editGroups.map { it.key }.toMutableList()
        val i = keys.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in keys.indices) return
        keys[i] = keys[j].also { keys[j] = keys[i] }
        saveLayout(layout.copy(groupOrder = mergeGroupOrder(layout.groupOrder, keys)))
    }

    /**
     * Neue Reihenfolge der sichtbaren Gruppen übernehmen, ohne die Plätze der gerade nicht gezeigten Gruppen
     * (pausierte Playlists) zu verlieren – die rutschten sonst beim Wiedereinschalten ans Ende.
     */
    private fun mergeGroupOrder(old: List<String>, shownNew: List<String>): List<String> {
        val shown = shownNew.toHashSet()
        val next = ArrayDeque(shownNew)
        val out = ArrayList<String>(old.size + shownNew.size)
        old.forEach { k -> if (k in shown) next.removeFirstOrNull()?.let { out += it } else out += k }
        out.addAll(next)
        return out
    }

    /**
     * Ausgeblendete Gruppen auf einmal setzen (Dialog „Gruppen ausblenden“). Gruppen, die gerade nicht in der Liste
     * sind (z. B. aus pausierten Playlists), behalten ihre Einstellung.
     */
    fun setHiddenGroups(hidden: Set<String>) {
        val shown = editGroups.map { it.key }.toSet()
        saveLayout(layout.copy(hiddenGroups = (layout.hiddenGroups - shown) + hidden.intersect(shown)))
    }

    // ---- Filme & Serien: Kategorien ausblenden ----
    var vodHidden by mutableStateOf(store.vodHidden)
        private set

    private fun vodKey(sourceId: String, series: Boolean, catId: String) = "$sourceId|${if (series) "s" else "m"}|$catId"

    fun isVodHidden(sourceId: String, series: Boolean, catId: String): Boolean = vodKey(sourceId, series, catId) in vodHidden

    /** Ausgeblendete Kategorien einer Playlist (Filme ODER Serien) auf einmal setzen. */
    fun setVodHidden(sourceId: String, series: Boolean, allIds: Collection<String>, hiddenIds: Set<String>) {
        val all = allIds.map { vodKey(sourceId, series, it) }.toSet()
        vodHidden = (vodHidden - all) + hiddenIds.map { vodKey(sourceId, series, it) }
        store.vodHidden = vodHidden
    }

    override fun toggleGroupHidden(key: String) {
        val h = layout.hiddenGroups
        saveLayout(layout.copy(hiddenGroups = if (key in h) h - key else h + key))
    }

    override fun renameGroup(key: String, name: String) {
        val trimmed = name.trim()
        val original = editGroups.firstOrNull { it.key == key }?.originalName
        val names = if (trimmed.isEmpty() || trimmed == original) layout.groupNames - key else layout.groupNames + (key to trimmed)
        saveLayout(layout.copy(groupNames = names))
    }

    override fun moveChannel(groupKey: String, channelKey: String, delta: Int) {
        val group = editGroups.firstOrNull { it.key == groupKey } ?: return
        val keys = group.channels.map { it.key }.toMutableList()
        val i = keys.indexOf(channelKey)
        val j = i + delta
        if (i < 0 || j !in keys.indices) return
        keys[i] = keys[j].also { keys[j] = keys[i] }
        saveLayout(layout.copy(channelOrder = layout.channelOrder + (groupKey to keys)))
    }

    override fun toggleChannelHidden(channelKey: String) {
        val h = layout.hiddenChannels
        saveLayout(layout.copy(hiddenChannels = if (channelKey in h) h - channelKey else h + channelKey))
    }

    /** Eigene Anordnung einer Gruppe verwerfen. */
    override fun resetGroup(groupKey: String) {
        saveLayout(
            layout.copy(
                channelOrder = layout.channelOrder - groupKey,
                hiddenChannels = layout.hiddenChannels - editGroups.firstOrNull { it.key == groupKey }?.channels?.map { it.key }.orEmpty().toSet(),
                groupNames = layout.groupNames - groupKey,
            )
        )
    }

    /** Farbschema (Index in [de.aze.iptv.ui.Schemes]). Neues Design „Aurora“ einmalig einstellen. */
    var schemeIndex by mutableStateOf(
        (if (!store.auroraApplied) {
            store.auroraApplied = true
            de.aze.iptv.ui.AuroraIndex.also { store.scheme = it }
        } else store.scheme).coerceIn(0, Schemes.lastIndex),
    )
        private set

    var textColorIndex by mutableStateOf(store.textColor.coerceIn(0, de.aze.iptv.ui.TextColors.lastIndex))
        private set

    /** Stil der Oberfläche (Aze / Kino / Glas) */
    var appStyle by mutableStateOf(de.aze.iptv.ui.AppStyle.of(store.appStyle))
        private set

    init {
        Palette.scheme = Schemes[schemeIndex]
        Palette.style = appStyle
        Palette.textOverride = de.aze.iptv.ui.TextColors[textColorIndex].second
    }

    /** Geräteart: 0 = automatisch, 1 = Fernseher, 2 = Tablet, 3 = Handy (siehe ui/Device.kt) */
    var deviceMode by mutableStateOf(store.deviceMode)
        private set

    fun updateDeviceMode(mode: Int) {
        deviceMode = mode
        store.deviceMode = mode
        de.aze.iptv.ui.Device.apply(mode)
    }

    /** Stil wählen – stellt die passenden Farben mit ein (danach frei änderbar). */
    fun updateStyle(st: de.aze.iptv.ui.AppStyle) {
        appStyle = st
        store.appStyle = st.ordinal
        Palette.style = st
        Schemes.indexOfFirst { it.name == st.schemeName }.takeIf { it >= 0 }?.let { updateScheme(it) }
    }

    fun updateTextColor(i: Int) {
        textColorIndex = i.coerceIn(0, de.aze.iptv.ui.TextColors.lastIndex)
        store.textColor = textColorIndex
        Palette.textOverride = de.aze.iptv.ui.TextColors[textColorIndex].second
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
        resume.remove(key) // neu einsortieren: zuletzt Gesehenes steht hinten und bleibt beim Kürzen erhalten
        if (!(posMs < 60_000 || (durMs > 0 && posMs > durMs - 180_000))) resume[key] = posMs
        store.saveResumePositions(resume)
        resumeVersion++
    }

    // ---- Weiterschauen-Reihe und Merkliste (siehe VodLibrary) ----
    var vodContinue by mutableStateOf(de.aze.iptv.data.VodLibrary.continueFromJson(store.vodContinue))
        private set
    var vodWatch by mutableStateOf(de.aze.iptv.data.VodLibrary.watchFromJson(store.vodWatch))
        private set

    /** Nach dem Abspielen (Stelle schon gespeichert): in die Weiterschauen-Reihe – oder heraus, wenn zu Ende geschaut */
    fun noteContinue(entry: de.aze.iptv.data.ContinueEntry) {
        val open = resumeOf(entry.resumeKey) > 0
        vodContinue = de.aze.iptv.data.VodLibrary.afterPlay(vodContinue, entry.copy(at = System.currentTimeMillis()), open)
        store.vodContinue = de.aze.iptv.data.VodLibrary.continueToJson(vodContinue)
        de.aze.iptv.ui.qaLog(if (open) "WEITERSCHAUEN_NEU ${entry.ref.name}" else "WEITERSCHAUEN_FERTIG ${entry.ref.name}")
    }

    fun continueFor(sourceId: String, series: Boolean): List<de.aze.iptv.data.ContinueEntry> {
        @Suppress("UNUSED_VARIABLE") val v = resumeVersion
        return de.aze.iptv.data.VodLibrary.continueFor(vodContinue, sourceId, series) { resume[it] ?: 0L }
    }

    /** Aus der Weiterschauen-Reihe entfernen (Menütaste / Knopf): Eintrag und gemerkte Stelle(n) weg */
    fun removeContinue(sourceId: String, series: Boolean, id: String) {
        val gone = vodContinue.filter { it.ref.sourceId == sourceId && it.ref.series == series && it.ref.id == id }
        // auch Stellen von vor der Weiterschauen-Reihe (stehen nicht in der Liste)
        val hadResume = !series && resume.remove("m:$sourceId:$id") != null
        gone.forEach { resume.remove(it.resumeKey) }
        if (gone.isEmpty() && !hadResume) return
        store.saveResumePositions(resume)
        resumeVersion++
        vodContinue = vodContinue - gone.toSet()
        store.vodContinue = de.aze.iptv.data.VodLibrary.continueToJson(vodContinue)
        de.aze.iptv.ui.qaLog("WEITERSCHAUEN_ENTFERNT ${gone.firstOrNull()?.ref?.name ?: id}")
    }

    /** Ganze Weiterschauen-Reihe leeren (Einstellungen → Filme & Serien) */
    fun clearContinue() {
        vodContinue.forEach { resume.remove(it.resumeKey) }
        resume.keys.filter { it.startsWith("m:") || it.startsWith("e:") }.forEach { resume.remove(it) }
        store.saveResumePositions(resume)
        resumeVersion++
        vodContinue = emptyList()
        store.vodContinue = "[]"
    }

    fun isWatched(sourceId: String, series: Boolean, id: String) =
        vodWatch.any { it.sourceId == sourceId && it.series == series && it.id == id }

    fun watchFor(sourceId: String, series: Boolean) = vodWatch.filter { it.sourceId == sourceId && it.series == series }

    /** „Vormerken“ an/aus */
    fun toggleWatch(ref: de.aze.iptv.data.VodRef) {
        val was = isWatched(ref.sourceId, ref.series, ref.id)
        vodWatch = de.aze.iptv.data.VodLibrary.toggleWatch(vodWatch, ref)
        store.vodWatch = de.aze.iptv.data.VodLibrary.watchToJson(vodWatch)
        de.aze.iptv.ui.qaLog(if (was) "MERKLISTE_ENTFERNT ${ref.name}" else "MERKLISTE_NEU ${ref.name}")
    }

    fun clearWatch() {
        vodWatch = emptyList()
        store.vodWatch = "[]"
    }

    /** Filme & Serien: zuletzt gewählte Sprachen (beim nächsten Film wieder genommen, wenn vorhanden) */
    var vodAudioLang by mutableStateOf(store.vodAudioLang)
        private set
    var vodSubLang by mutableStateOf(store.vodSubLang)
        private set

    fun rememberVodAudio(lang: String?) {
        if (lang.isNullOrBlank() || lang == vodAudioLang) return
        vodAudioLang = lang; store.vodAudioLang = lang
        de.aze.iptv.ui.qaLog("SPRACHE_TON $lang")
    }

    /** [lang] = Sprache der Untertitel, „aus“ = keine */
    fun rememberVodSub(lang: String?) {
        if (lang.isNullOrBlank() || lang == vodSubLang) return
        vodSubLang = lang; store.vodSubLang = lang
        de.aze.iptv.ui.qaLog("SPRACHE_UNTERTITEL $lang")
    }

    /** Bild-im-Bild (Handy/Tablet) */
    var pip by mutableStateOf(store.pip)
        private set
    fun updatePip(on: Boolean) { pip = on; store.pip = on }
    /** gerade im kleinen Fenster (Bild-im-Bild): nur das Bild zeigen, keine Leisten/Listen */
    var inPip by mutableStateOf(false)
    /** Live-Fernsehen ist der sichtbare Bildschirm (MainActivity) bzw. ein Film/eine Folge läuft (VodPlayer) */
    var pipLive = false
    var vodPlaying = false
    /** der laufende Film kommt vom Anbieter (nicht vom Gerät) – Downloads warten bzw. bremsen dann */
    @Volatile var vodRemote = false

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

    /** Bildwiederholrate anpassen (AFR): Bildschirm auf 50 Hz bei 25/50-Bilder-Sendern usw. */
    var afr by mutableStateOf(store.afr)
        private set

    var afr50 by mutableStateOf(store.afr50)
        private set
    var afrDelay by mutableStateOf(store.afrDelay)
        private set
    var showDiag by mutableStateOf(store.showDiag)
        private set

    /** Zum Ausprobieren (Einstellungen → System): schnelles Umschalten / schnellerer Wächter bei stehendem Bild */
    var zapFast by mutableStateOf(store.zapFast)
        private set
    var freezeFast by mutableStateOf(store.freezeFast)
        private set

    fun updateZapFast(on: Boolean) { zapFast = on; store.zapFast = on }
    var holdFrame by mutableStateOf(store.holdFrame)
        private set
    fun updateHoldFrame(on: Boolean) { holdFrame = on; store.holdFrame = on }
    fun updateFreezeFast(on: Boolean) { freezeFast = on; store.freezeFast = on }

    /** Bis wann der Bildschirm noch mit dem Umschalten beschäftigt ist (Wiedergabe so lange zurückhalten). */
    @Volatile var displaySettleUntil = 0L

    fun updateAfr50(on: Boolean) {
        afr50 = on
        store.afr50 = on
        if (on) {
            afr = false; store.afr = false
            tunneling = false; store.tunneling = false
        }
    }

    fun updateAfrDelay(s: Int) {
        afrDelay = s
        store.afrDelay = s
    }

    var coverProxy by mutableStateOf(store.coverProxy)
        private set

    fun updateCoverProxy(on: Boolean) {
        coverProxy = on
        store.coverProxy = on
        de.aze.iptv.ui.CoverUrls.proxy = on
    }

    /** Cover-Quelle: TMDB zuerst (Anbieter nur als Ersatz) statt Anbieter zuerst */
    var coverTmdbFirst by mutableStateOf(store.coverTmdbFirst)
        private set

    fun updateCoverTmdbFirst(on: Boolean) {
        coverTmdbFirst = on
        store.coverTmdbFirst = on
        de.aze.iptv.ui.CoverUrls.tmdbFirst = on
    }

    var vodLang by mutableStateOf(store.vodLang)
        private set

    fun updateVodLang(v: Int) {
        vodLang = v
        store.vodLang = v
    }

    var vodFast by mutableStateOf(store.vodFast)
        private set

    fun updateVodFast(on: Boolean) {
        vodFast = on
        store.vodFast = on
    }

    var multiViewOn by mutableStateOf(store.multiViewOn)
        private set

    fun updateMultiViewOn(on: Boolean) {
        multiViewOn = on
        store.multiViewOn = on
    }

    fun updateShowDiag(on: Boolean) {
        showDiag = on
        store.showDiag = on
    }

    fun updateAfr(on: Boolean) {
        afr = on
        store.afr = on
        if (on) { afr50 = false; store.afr50 = false }
        // Beides zusammen lässt das Bild auf manchen Geräten einfrieren
        if (on && tunneling) {
            tunneling = false
            store.tunneling = false
        }
    }

    fun updateTunneling(on: Boolean) {
        tunneling = on
        store.tunneling = on
        if (on && afr) {
            afr = false
            store.afr = false
        }
        if (on && afr50) {
            afr50 = false
            store.afr50 = false
        }
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

    /** Bildformat je Sender – gilt nur für diesen Sender (Schnellmenü „Bild“). Ohne Eintrag gilt das allgemeine Format. */
    var channelAspects by mutableStateOf(runCatching { store.channelAspects() }.getOrDefault(emptyMap()))
        private set

    fun aspectFor(key: String?): AspectMode = key?.let { channelAspects[it] }?.let { AspectMode.of(it) } ?: aspectMode

    fun updateChannelAspect(key: String, mode: AspectMode) {
        // gleich dem allgemeinen Format → Eintrag entfernen (Liste bleibt klein)
        channelAspects = if (mode == aspectMode) channelAspects - key else channelAspects + (key to mode.name)
        store.saveChannelAspects(channelAspects)
    }

    /** Dolby-Ton in der App umwandeln – gilt nur für diese Sender (Schnellmenü „Dolby-Ton umwandeln“). */
    var dolbyChannels by mutableStateOf(runCatching { store.dolbyChannels() }.getOrDefault(emptySet()))
        private set

    fun toggleDolby(key: String) {
        dolbyChannels = if (key in dolbyChannels) dolbyChannels - key else dolbyChannels + key
        store.saveDolbyChannels(dolbyChannels)
    }

    fun clearChannelAspects() {
        channelAspects = emptyMap()
        store.saveChannelAspects(channelAspects)
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
            // Sofortstart: den zuletzt geschauten Sender gleich abspielen – die Senderliste (bei großen Anbietern
            // zehntausende Einträge) wird währenddessen gelesen. Danach übernimmt der Eintrag aus der Liste
            // (gleiche Adresse → die Wiedergabe läuft ohne Unterbrechung weiter).
            if (current == null) runCatching { store.lastChannel() }.getOrNull()?.let { last ->
                val c = last.channel
                val known = activeSources.any { it.id == c.source }
                val blocked = adultFilter && (de.aze.iptv.data.AdultFilter.isAdult(c.group) || de.aze.iptv.data.AdultFilter.isAdult(c.name))
                if (known && !blocked) { current = last; instantStart = true }
            }
            try {
                channels = repo.loadCached(activeSources)
                rebuild(restoreKey = store.lastChannelKey).join()
                updateStatus(emptyMap())
            } finally {
                loading = false
            }
            if (epgEnabled) epg = runCatching { epgRepo.load() }.getOrDefault(Epg.EMPTY).withManual(epgMap)
            val refresh = needsRefresh() || refreshPending
            val epgStale = !refresh && activeSources.isNotEmpty() && epgRepo.isStale(epgAutoHours)
            // (Versuch 1.0.307, die Liste erst nach dem ersten Bild zu lesen und Downloads 8 s zu verschieben, machte das
            // Umschalten in den ersten Minuten langsamer – Emulator 81 → 115 ms – zurückgenommen)
            if (refresh) {
                refreshPending = false
                refreshNow()
            }
            else if (epgStale) refreshEpg(retries = 3)
            refreshAccountInfo(6 * 3_600_000L)
            started = true
        }
    }

    /** Beim Start: Liste neu laden, wenn das gewählte Intervall abgelaufen ist. */
    private fun needsRefresh(): Boolean {
        val active = activeSources
        if (active.isEmpty()) return false
        if (!repo.hasCache(active)) return true
        if (refreshHours <= 0) return false
        return System.currentTimeMillis() - lastRefresh >= refreshHours * 3_600_000L
    }

    /** Je Playlist: wie viele Sender geladen wurden oder was schiefging (für die Einstellungen). */
    final override var sourceStatus by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private fun updateStatus(errors: Map<String, String>) {
        val counts = channels.groupingBy { it.source }.eachCount()
        sourceStatus = sources.associate { src ->
            val n = counts[src.id] ?: 0
            src.id to when {
                !src.enabled -> "Pausiert – wird nicht geladen (Zugangsdaten und letzte Liste bleiben gespeichert)"
                errors[src.id] != null -> "Fehler: ${errors[src.id]}" + if (n > 0) " (alte Liste mit $n Sendern bleibt)" else ""
                n == 0 -> "Liste geladen, aber keine Live-Sender darin gefunden"
                else -> "$n Sender geladen"
            }
        }
    }

    /** Wurde während einer laufenden Aktualisierung eine neue angefordert (z. B. neue Playlist), danach nachholen. */
    private var refreshPending = false

    fun refreshNow() {
        if (loading) {
            refreshPending = true
            return
        }
        scope.launch {
            loading = true
            message = "Senderliste wird aktualisiert …"
            val errors = try {
                val active = activeSources
                val errs = repo.refresh(active)
                repo.removeOrphans(sources.map { it.id }.toSet())
                channels = repo.loadCached(activeSources)
                val now = System.currentTimeMillis()
                if (errs.size < active.size) {
                    lastRefresh = now
                    store.lastRefresh = now
                }
                updateStatus(errs)
                rebuild(restoreKey = current?.channel?.key).join()
                errs
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Nie in "wird aktualisiert" hängen bleiben
                mapOf("" to (e.message ?: "Fehler beim Aktualisieren"))
            } finally {
                loading = false
            }
            message = if (errors.isEmpty()) "Senderliste aktualisiert: ${lineup.all.size} Sender"
            else errors.entries.joinToString("\n") { (id, e) -> "${sources.firstOrNull { it.id == id }?.name ?: "Playlist"}: $e" }
            // Programmzeitschrift gleich mit aktualisieren (Netz evtl. noch nicht da: still erneut versuchen)
            refreshEpg(retries = 3)
            refreshAccountInfo(0)
            if (refreshPending) {
                refreshPending = false
                refreshNow()
            }
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

    /** Erwachsenen-Filter: Gruppen, Sender und Film-Kategorien mit Erwachsenen-Inhalten ausblenden. */
    var adultFilter by mutableStateOf(store.adultFilter)
        private set

    fun updateAdultFilter(on: Boolean) {
        adultFilter = on
        store.adultFilter = on
        rebuild(restoreKey = current?.channel?.key)
    }

    // ---- Bedienung ----
    var keyMap by mutableStateOf(de.aze.iptv.data.KeyMap.parse(store.keyMap))
        private set

    fun keyAction(k: de.aze.iptv.data.RemoteKey): de.aze.iptv.data.KeyAction = keyMap[k] ?: k.default

    fun setKeyAction(k: de.aze.iptv.data.RemoteKey, a: de.aze.iptv.data.KeyAction) {
        keyMap = keyMap + (k to a)
        store.keyMap = de.aze.iptv.data.KeyMap.toJson(keyMap)
    }

    fun resetKeyMap() {
        keyMap = de.aze.iptv.data.KeyMap.parse("{}")
        store.keyMap = "{}"
    }

    var zapInGroup by mutableStateOf(store.zapInGroup)
        private set

    fun updateZapInGroup(on: Boolean) {
        zapInGroup = on
        store.zapInGroup = on
    }

    var nameDisplay by mutableStateOf(store.nameDisplay)
        private set

    fun updateNameDisplay(v: Int) {
        nameDisplay = v
        store.nameDisplay = v
    }

    /** wie lange die Infoleiste bzw. der Sendername stehen bleibt (1–7 s) */
    var infoSeconds by mutableStateOf(store.infoSeconds)
        private set

    fun updateInfoSeconds(v: Int) {
        infoSeconds = v.coerceIn(1, 7)
        store.infoSeconds = infoSeconds
    }

    var expertMode by mutableStateOf(store.expertMode)
        private set

    fun updateExpertMode(on: Boolean) {
        expertMode = on
        store.expertMode = on
    }

    var pinEnabled by mutableStateOf(store.pinEnabled)
        private set

    fun updatePinEnabled(on: Boolean) {
        pinEnabled = on
        store.pinEnabled = on
    }

    var omdbKey by mutableStateOf(store.omdbKey)
        private set

    fun updateOmdbKey(k: String) {
        omdbKey = k.trim()
        store.omdbKey = omdbKey
    }

    var youtubeExternal by mutableStateOf(store.youtubeExternal)
        private set

    fun updateYoutubeExternal(on: Boolean) {
        youtubeExternal = on
        store.youtubeExternal = on
    }

    // ---- Rückkehr nach der YouTube-App (Aziz 26.11.: nach dem Trailer landete er im Live-TV statt beim Film) ----
    // Solange YouTube vorn ist, kann Android unsere App beenden (Speicher). Dann startet sie neu – bisher im Live-TV.
    // Deshalb merken wir vor dem Öffnen von YouTube, bei welchem Film man war, und kehren beim Neustart dorthin zurück
    // (höchstens 30 Minuten danach, nur einmal).
    private val returnPrefs = context.getSharedPreferences("aze_return", Context.MODE_PRIVATE)

    fun markVodReturn(ref: de.aze.iptv.data.VodRef) {
        returnPrefs.edit().putString("ref", ref.toJson().toString()).putLong("at", System.currentTimeMillis()).apply()
    }

    fun clearVodReturn() { if (returnPrefs.contains("ref")) returnPrefs.edit().clear().apply() }

    /** gemerkter Film (und gleich vergessen) – null, wenn keiner oder älter als 30 Minuten */
    fun takeVodReturn(): de.aze.iptv.data.VodRef? {
        val s = returnPrefs.getString("ref", null) ?: return null
        val at = returnPrefs.getLong("at", 0)
        clearVodReturn()
        if (System.currentTimeMillis() - at > 30 * 60_000L) return null
        return runCatching { de.aze.iptv.data.VodRef.fromJson(org.json.JSONObject(s)) }.getOrNull()
    }

    /** für VodScreen: beim Neustart gleich diese Film-Details öffnen */
    var pendingVodReturn: de.aze.iptv.data.VodRef? = null

    /** Trailer gleich in der YouTube-App öffnen (Standard); ohne YouTube-App läuft er im Player der App */
    var trailerExternal by mutableStateOf(store.trailerExternal)
        private set

    fun updateTrailerExternal(on: Boolean) {
        trailerExternal = on
        store.trailerExternal = on
    }

    /** Kanäle aus einer Export-Datei übernehmen (vorhandene bleiben, doppelte werden übersprungen). */
    fun importYoutube(entries: List<YtEntry>): Int {
        val known = youtube.map { it.ref }.toHashSet()
        val fresh = entries.filter { known.add(it.ref) }.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        if (fresh.isEmpty()) return 0
        youtube = youtube + fresh
        store.youtube = youtube
        rebuild(restoreKey = current?.channel?.key)
        return fresh.size
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
        val raw = youtube.map { it.toChannel() } + channels
        val filterAdult = adultFilter
        val named = sources + PlaylistSource(YouTube.SOURCE, "YouTube", SourceType.M3U, "")
        val favs = favorites
        val lay = layout
        rebuildJob?.cancel()
        return scope.launch {
            val (lu, eg) = withContext(Dispatchers.Default) {
                // Filter läuft im Hintergrund (bei zehntausenden Sendern sonst spürbares Stocken)
                // Gruppen-Prüfung nur einmal je Gruppe (sonst je Sender – bei 50 000 Sendern spürbar)
                val adultGroup = HashMap<String, Boolean>()
                val all = if (filterAdult) raw.filterNot {
                    adultGroup.getOrPut(it.group) { de.aze.iptv.data.AdultFilter.isAdult(it.group) } || de.aze.iptv.data.AdultFilter.isAdult(it.name)
                } else raw
                val arranged = Numbering.arrange(all, named, lay)
                Numbering.build(all, favs, named, lay, arranged) to arranged
            }
            lineup = lu
            editGroups = eg
            // Zuerst der Sender, der JETZT läuft: wer während des Neuaufbaus (große Listen brauchen spürbar) umschaltet,
            // wurde sonst auf den alten Sender zurückgeworfen.
            current = current?.let { lu.byKey[it.channel.key] } ?: restoreKey?.let { lu.byKey[it] } ?: lu.all.firstOrNull()
        }.also { rebuildJob = it }
    }

    // je Playlist nur EIN Anbieter-Objekt (alte nach dem Aktualisieren verwerfen, sonst bleiben ganze Filmlisten im Speicher)
    private val vodProviders = HashMap<String, Pair<String, de.aze.iptv.data.VodProvider>>()

    /** Playlists, die Filme & Serien haben können (Server + Login und M3U). */
    val vodSources: List<PlaylistSource>
        get() = activeSources.filter { it.type == SourceType.XTREAM || it.type == SourceType.M3U }

    /** Filme & Serien einer Playlist. Bei M3U wird erkannt, ob ein Xtream-Anbieter dahintersteckt. */
    fun vodProvider(src: PlaylistSource): de.aze.iptv.data.VodProvider {
        val file = repo.cacheFileOf(src)
        val key = "${src.id}|${src.url}|${src.user}|${file.lastModified()}"
        vodProviders[src.id]?.let { (k, p) -> if (k == key) return p }
        val p = if (src.type == SourceType.XTREAM) de.aze.iptv.data.XtreamVod(src)
        else de.aze.iptv.data.HybridVod(file, src.name)
        vodProviders[src.id] = key to p
        return p
    }

    // ---- Herunterladen (Filme & Folgen aufs Gerät, siehe Downloader) ----
    init {
        de.aze.iptv.data.Downloader.init(context)
        // Adresse erst beim Laden bauen (gespeichert werden keine Zugangsdaten)
        de.aze.iptv.data.Downloader.resolver = { e ->
            vodSources.firstOrNull { it.id == e.ref.sourceId }?.let { src ->
                val api = vodProvider(src)
                val ep = e.episode
                if (ep != null) api.episodeUrl(ep.toEpisode()) else api.movieUrl(e.ref.toItem())
            }
        }
        // Fernsehen hat Vorrang: gebremst oder warten (Anbieter mit nur einer Verbindung)
        de.aze.iptv.data.Downloader.policy = { e ->
            val watching = (pipLive && current != null) || vodRemote
            de.aze.iptv.data.DownloadRules.mode(watching, accountInfo[e.ref.sourceId]?.maxCons)
        }
        scope.launch {
            kotlinx.coroutines.delay(4_000) // erst die Senderliste, dann weiterladen
            de.aze.iptv.data.Downloader.start()
        }
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

    /**
     * Gleichzeitige Verbindungen laut Anbieter (nur Server + Login): (erlaubt, gerade belegt) oder null.
     * Für Multi-View: jedes Bild ist eine eigene Verbindung.
     */
    suspend fun connectionInfo(ch: Channel?): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val src = ch?.let { c -> sources.firstOrNull { it.id == c.source } } ?: return@withContext null
        if (src.type != SourceType.XTREAM) return@withContext null
        val info = org.json.JSONObject(de.aze.iptv.data.Http.text(xtreamApi(src))).optJSONObject("user_info") ?: return@withContext null
        val max = info.optString("max_connections").toIntOrNull() ?: return@withContext null
        val used = info.optString("active_cons").toIntOrNull() ?: 0
        max to used
    }

    private fun xtreamApi(src: PlaylistSource): String {
        val raw = src.url.trim().trimEnd('/')
        val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        return "$base/player_api.php?username=${java.net.URLEncoder.encode(src.user.trim(), "UTF-8")}&password=${java.net.URLEncoder.encode(src.pass, "UTF-8")}"
    }

    // ---- Zugang laut Anbieter (wie TiviMate / IBO Player): bis wann gültig, Verbindungen ----

    final override var accountInfo by mutableStateOf(runCatching { store.accountInfo() }.getOrDefault(emptyMap()))
        private set
    private var accountJob: kotlinx.coroutines.Job? = null

    /** Server + Login und Portal beim Anbieter abfragen (M3U-Listen haben keine solche Angabe). */
    final override fun refreshAccountInfo(maxAgeMs: Long) {
        if (accountJob?.isActive == true) return
        accountJob = scope.launch {
            val now = System.currentTimeMillis()
            val fresh = withContext(Dispatchers.IO) {
                sources.filter { it.enabled && it.type != SourceType.M3U }
                    .filter { s -> accountInfo[s.id]?.let { now - it.checkedAt >= maxAgeMs } ?: true }
                    .mapNotNull { src ->
                        val info = runCatching {
                            when (src.type) {
                                SourceType.XTREAM -> de.aze.iptv.data.AccountInfo.parseXtream(de.aze.iptv.data.Http.text(xtreamApi(src)), now)
                                SourceType.STALKER -> StalkerClient.forSource(src).accountEnd()
                                    ?.let { de.aze.iptv.data.AccountInfo.parsePortalDate(it) }
                                    ?.let { de.aze.iptv.data.AccountInfo(it, checkedAt = now) }
                                SourceType.M3U -> null
                            }
                        }.getOrNull()
                        info?.let { src.id to it }
                    }
            }
            if (fresh.isEmpty()) return@launch
            val ids = sources.map { it.id }.toSet()
            accountInfo = (accountInfo + fresh).filterKeys { it in ids }
            store.saveAccountInfo(accountInfo)
            de.aze.iptv.ui.qaLog("ZUGANG " + fresh.joinToString(" | ") { (id, a) -> "${sources.firstOrNull { it.id == id }?.name}: ${a.describe(now)}" })
            warnExpiry(now)
        }
    }

    /** Läuft ein Zugang in weniger als 7 Tagen ab (oder ist abgelaufen): einmal am Tag darauf hinweisen. */
    private fun warnExpiry(now: Long) {
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(now)
        if (store.accountWarnDay == day) return
        val bad = activeSources.mapNotNull { s -> accountInfo[s.id]?.takeIf { it.level(now) == de.aze.iptv.data.AccountInfo.Level.BAD }?.let { s to it } }
        if (bad.isEmpty()) return
        store.accountWarnDay = day
        message = bad.joinToString("\n") { (s, a) -> "Zugang „${s.name}“: ${a.describe(now)}" }
    }

    /** Nur Test-Build: Multi-View beim Start öffnen. */
    var testMultiView = false
    /** Test-Zugang: gleich „Senderliste verwalten“ öffnen (nur im QA-Build) */
    var testListEditor = false
    /** Test-Zugang: gleich Filme öffnen (nur im QA-Build) */
    var testOpenVod = false
    /** Test-Zugang: dabei gleich die Serien */
    var testOpenSeries = false
    /** Test: gleich mit den Einstellungen starten (ohne PIN) */
    var testOpenSettings = false
    /** „Fußball heute“ aus dem Menü der Filme gewählt → im Fernsehen öffnen */
    var pendingFootball by mutableStateOf(false)
    /** SkorText öffnen, sobald das Live-Bild wieder da ist (Seitenmenü in Filme/Serien) */
    var pendingSkor by mutableStateOf(false)
    /** Test-Zugang: SkorText gleich öffnen (nur im QA-Build) */
    var testSkorText = false

    /** Der zuletzt geschaute Sender (für OK = zurückspringen). */
    private var previousKey: String? = null

    private var saveLastJob: Job? = null

    fun play(channel: NumberedChannel) {
        val before = current?.channel?.key
        if (before != null && before != channel.channel.key) previousKey = before
        current = channel
        // Speichern (2× verschlüsseln + Datei) erst, wenn man auf dem Sender bleibt – nicht bei jedem Durchzappen
        saveLastJob?.cancel()
        saveLastJob = scope.launch { delay(2_500); store.saveLast(channel) }
    }

    /** Beim Verlassen der App sofort speichern, was gerade läuft */
    fun flushLast() {
        if (saveLastJob?.isActive == true) { saveLastJob?.cancel(); current?.let { store.saveLast(it) } }
    }

    /** Wiedergabe läuft (Bild + Ton) – Startarbeiten im Hintergrund warten darauf */
    @Volatile var firstPlayingAt = 0L
        private set
    fun notePlaying() { if (firstPlayingAt == 0L) firstPlayingAt = android.os.SystemClock.elapsedRealtime() }


    /** Springt zwischen aktuellem und vorherigem Sender hin und her. */
    fun playPrevious(): Boolean {
        val target = previousKey?.let { lineup.byKey[it] } ?: return false
        play(target)
        return true
    }

    fun step(delta: Int) {
        // Auf Wunsch nur innerhalb der Gruppe des laufenden Senders zappen
        if (zapInGroup) {
            val cur = current
            val group = cur?.let { c ->
                lineup.groups.firstOrNull { g -> !g.isFavorites && g.channels.any { it.channel.key == c.channel.key } }
                    ?: lineup.groups.firstOrNull { g -> g.channels.any { it.channel.key == c.channel.key } }
            }
            if (group != null && group.channels.isNotEmpty()) {
                val i = group.channels.indexOfFirst { it.channel.key == cur.channel.key }
                play(group.channels[Math.floorMod(i + delta, group.channels.size)])
                return
            }
        }
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
        val adding = channel.key !in favorites
        favorites = if (adding) favorites + channel.key else favorites - channel.key
        message = if (adding) "★ „${channel.name}“ zu den Favoriten hinzugefügt" else "„${channel.name}“ aus den Favoriten entfernt"
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

    override fun addSource(source: PlaylistSource) {
        sources = sources + source.normalized()
        store.saveSources(sources)
        refreshNow()
    }

    /** Link, Login oder Name einer Playlist korrigieren. Geänderter Zugang → neu laden. */
    override fun updateSource(source: PlaylistSource) {
        val old = sources.firstOrNull { it.id == source.id } ?: return
        val n = source.normalized().copy(name = source.name.trim().ifEmpty { old.name })
        sources = sources.map { if (it.id == n.id) n else it }
        store.saveSources(sources)
        if (old.type != n.type || old.url != n.url || old.user != n.user || old.pass != n.pass) {
            repo.delete(old)
            refreshNow()
        } else rebuild(restoreKey = current?.channel?.key)
    }

    /** Welche Playlist die Senderliste zeigt: null = alle untereinander. */
    var playlistFilter by mutableStateOf<String?>(null)
        private set

    /** Schaltet die Senderliste weiter: Alle → Playlist 1 → Playlist 2 → … → Alle. */
    fun nextPlaylistFilter() {
        val ids = listOf<String?>(null) + activeSources.map { it.id }
        playlistFilter = ids[(ids.indexOf(playlistFilter) + 1) % ids.size]
    }

    /** Gruppenübersicht: zugeklappte Playlists (nur ihr Name ist zu sehen, siehe GroupSections) */
    var collapsedPlaylists by mutableStateOf(runCatching { store.collapsedPlaylists() }.getOrDefault(emptySet()))
        private set

    fun togglePlaylistCollapsed(id: String) {
        collapsedPlaylists = if (id in collapsedPlaylists) collapsedPlaylists - id else collapsedPlaylists + id
        store.saveCollapsedPlaylists(collapsedPlaylists)
    }

    fun playlistName(id: String?): String =
        if (id == null) "Alle" else sources.firstOrNull { it.id == id }?.name ?: "Playlist"

    override fun renameSource(source: PlaylistSource, name: String) {
        if (name.isBlank()) return
        sources = sources.map { if (it.id == source.id) it.copy(name = name.trim()) else it }
        store.saveSources(sources)
        rebuild(restoreKey = current?.channel?.key)
    }

    /** Reihenfolge der Playlists ändern (bestimmt auch die Nummerierung). */
    override fun moveSource(source: PlaylistSource, delta: Int) {
        val list = sources.toMutableList()
        val i = list.indexOfFirst { it.id == source.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        sources = list
        store.saveSources(list)
        scope.launch {
            channels = repo.loadCached(activeSources)
            rebuild(restoreKey = current?.channel?.key)
        }
    }

    /** Freie Sender (iptv-org) ein- oder ausschalten. */
    fun toggleFreeList(e: de.aze.iptv.data.FreeLists.Entry) {
        val url = de.aze.iptv.data.FreeLists.url(e.code)
        val existing = sources.firstOrNull { it.url.trim() == url }
        if (existing != null) {
            removeSource(existing)
        } else {
            addSource(PlaylistSource(java.util.UUID.randomUUID().toString(), e.name, SourceType.M3U, url))
        }
    }

    override fun removeSource(source: PlaylistSource) {
        if (playlistFilter == source.id) playlistFilter = null
        sources = sources.filterNot { it.id == source.id }
        store.saveSources(sources)
        repo.delete(source)
        scope.launch {
            channels = repo.loadCached(activeSources)
            rebuild(restoreKey = current?.channel?.key)
        }
    }

    /** Fernhilfe: diese Box lässt sich von einer anderen Box aus helfen (Code, 10 min + 1 min je Aktion) */
    val remoteHost: RemoteHelpHost by lazy { RemoteHelpHost(this, scope) }

    /** Briefkasten der Fernhilfe (nur der automatische Test stellt hier seinen eigenen Server ein) */
    var remoteRelay: String = de.aze.iptv.data.Relay.DEFAULT
        set(v) { field = v; remoteHost.relayBase = v }

    /** Fernhilfe geben: mit dem Code einer anderen Box verbinden */
    fun remoteGuest(code: String): RemoteHelpGuest = RemoteHelpGuest(code, scope, remoteRelay).also { it.start() }

    fun updateRefreshHours(hours: Int) {
        refreshHours = hours
        store.refreshHours = hours
    }
}
