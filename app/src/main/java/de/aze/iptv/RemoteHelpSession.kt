@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package de.aze.iptv

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import de.aze.iptv.data.Channel
import de.aze.iptv.data.EditGroup
import de.aze.iptv.data.ListEditCodec
import de.aze.iptv.data.ListEditTarget
import de.aze.iptv.data.PlaylistSource
import de.aze.iptv.data.PlaylistTarget
import de.aze.iptv.data.Relay
import de.aze.iptv.data.RemoteCrypto
import de.aze.iptv.data.SourceType
import de.aze.iptv.ui.qaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Zeitregel der Fernhilfe: erst 10 Minuten, danach bleibt sie nach jeder Aktion des Helfers 1 Minute offen. */
internal object RemoteHelpTime {
    const val FIRST_MS = 10 * 60_000L
    const val EXTEND_MS = 60_000L
    fun now() = SystemClock.elapsedRealtime()
}

internal fun sourceToJson(s: PlaylistSource): JSONObject = JSONObject()
    .put("id", s.id).put("name", s.name).put("type", s.type.name)
    .put("url", s.url).put("user", s.user).put("pass", s.pass).put("on", s.enabled)

internal fun sourceFromJson(o: JSONObject): PlaylistSource? = runCatching {
    PlaylistSource(
        id = o.getString("id").take(64),
        name = o.optString("name").take(80),
        type = SourceType.valueOf(o.getString("type")),
        url = o.getString("url").take(2_000),
        user = o.optString("user").take(200),
        pass = o.optString("pass").take(200),
        enabled = o.optBoolean("on", true),
    )
}.getOrNull()

/**
 * Box, die Hilfe bekommt: zeigt den Code, hört auf den Briefkasten, führt die Aufträge des Helfers aus und schickt
 * ihm den aktuellen Stand (Playlists + Senderliste, nur Namen) zurück.
 */
class RemoteHelpHost(private val app: AppController, private val scope: CoroutineScope) {
    var code by mutableStateOf<String?>(null)
        private set
    var expiresAt by mutableStateOf(0L)
        private set
    /** Briefkasten erreichbar */
    var online by mutableStateOf(false)
        private set
    /** Helfer hat sich gemeldet */
    var connected by mutableStateOf(false)
        private set
    var lastAction by mutableStateOf<String?>(null)
        private set
    val active: Boolean get() = code != null

    /** Briefkasten-Adresse (Test: eigener Test-Server) */
    var relayBase: String = Relay.DEFAULT

    private var job: Job? = null
    private var keys: RemoteCrypto.Keys? = null
    private var relay: Relay? = null
    private val sendLock = Mutex()
    private var snapshot: List<EditGroup> = emptyList()
    private var version = 0
    private var statePending: Job? = null

    fun start(fixedCode: String? = null) {
        stop(null)
        val c = fixedCode ?: RemoteCrypto.newCode()
        code = c
        connected = false; online = false; lastAction = null
        expiresAt = RemoteHelpTime.now() + RemoteHelpTime.FIRST_MS
        qaLog("FH_HOST_START")
        job = scope.launch {
            val k = withContext(Dispatchers.Default) { RemoteCrypto.keys(c) }
            val r = Relay(relayBase)
            keys = k; relay = r
            launch(Dispatchers.IO) {
                r.listen(
                    k.toBox,
                    active = { code == c },
                    onState = { on -> scope.launch { if (code == c) online = on } },
                    onMessage = { text ->
                        val o = RemoteCrypto.open(k, text) ?: return@listen
                        scope.launch { if (code == c) handle(o) }
                    },
                )
            }
            // Stand neu schicken, sobald sich Playlists oder Senderliste ändern (z. B. Liste fertig geladen)
            launch {
                snapshotFlow { Triple(app.sources, app.sourceStatus, app.editGroups) }
                    .debounce(1_500)
                    .collect { if (connected) sendState() }
            }
            while (isActive && code == c) {
                delay(1_000)
                if (RemoteHelpTime.now() > expiresAt) { stop("Zeit abgelaufen"); break }
            }
        }
    }

    /** Fernhilfe schließen. [reason] null = still (z. B. Neustart). */
    fun stop(reason: String?) {
        val k = keys; val r = relay
        if (code != null && connected && k != null && r != null) {
            val bye = RemoteCrypto.seal(k, JSONObject().put("op", "bye").put("text", reason ?: "").toString())
            scope.launch(Dispatchers.IO) { runCatching { r.publish(k.toHelper, bye) } }
        }
        val was = code != null
        code = null; connected = false; online = false
        job?.cancel(); job = null
        keys = null; relay = null
        if (was && reason != null) {
            app.message = "Fernhilfe beendet – $reason. Alles bleibt nur auf diesem Gerät."
            qaLog("FH_HOST_STOP $reason")
        }
    }

    private fun touch() {
        expiresAt = maxOf(expiresAt, RemoteHelpTime.now() + RemoteHelpTime.EXTEND_MS)
    }

    private fun did(text: String) {
        lastAction = text
        app.message = "Fernhilfe: $text"
        qaLog("FH_APPLY $text")
    }

    private fun source(o: JSONObject): PlaylistSource? {
        val id = o.optString("id")
        return app.sources.firstOrNull { it.id == id }
    }

    private fun handle(o: JSONObject) {
        touch()
        when (o.optString("op")) {
            "hello" -> {
                connected = true
                did("Helfer ist verbunden")
                sendState()
                return
            }
            "ping" -> return
            "state?" -> { sendState(); return }
            "bye" -> { stop("Helfer hat beendet"); return }
            "add" -> sourceFromJson(o.getJSONObject("s"))?.let { s ->
                if (s.url.isBlank()) return@let
                val n = s.copy(id = java.util.UUID.randomUUID().toString(), enabled = true, name = s.name.ifBlank { "Playlist" })
                app.addSource(n)
                did("Playlist „${n.name}“ hinzugefügt – wird geladen")
            }
            "update" -> sourceFromJson(o.getJSONObject("s"))?.let { s ->
                if (app.sources.none { it.id == s.id } || s.url.isBlank()) return@let
                app.updateSource(s)
                did("Playlist „${s.name}“ geändert")
            }
            "enable" -> source(o)?.let { s ->
                val on = o.optBoolean("on")
                app.setSourceEnabled(s, on)
                did(if (on) "„${s.name}“ eingeschaltet" else "„${s.name}“ pausiert")
            }
            "move" -> source(o)?.let { s -> app.moveSource(s, o.optInt("d").coerceIn(-1, 1)); did("Reihenfolge der Playlists geändert") }
            "remove" -> source(o)?.let { s -> app.removeSource(s); did("Playlist „${s.name}“ gelöscht") }
            "refresh" -> { app.refreshNow(); did("Senderliste wird neu geladen") }
            "layout" -> applyLayout(o)
            else -> return
        }
        scheduleState()
    }

    private fun applyLayout(o: JSONObject) {
        val snap = snapshot
        if (o.optInt("v") != version || snap.isEmpty()) {
            send(JSONObject().put("op", "msg").put("text", "Die Senderliste hat sich inzwischen geändert – bitte noch einmal öffnen und erneut ändern."))
            sendState()
            return
        }
        val edits = runCatching { ListEditCodec.decode(o.getJSONArray("groups"), snap) }.getOrElse {
            send(JSONObject().put("op", "msg").put("text", "Senderliste nicht übernommen: ${it.message}"))
            return
        }
        app.applyWebEdit(edits, snap)
        did("Senderliste geändert (sortiert/ausgeblendet)")
        send(JSONObject().put("op", "msg").put("text", "Senderliste übernommen."))
    }

    private fun scheduleState() {
        statePending?.cancel()
        statePending = scope.launch { delay(1_200); sendState() }
    }

    private fun sendState() {
        snapshot = app.editGroups
        version++
        val sources = JSONArray().also { a -> app.sources.forEach { a.put(sourceToJson(it)) } }
        val status = JSONObject().also { st -> app.sourceStatus.forEach { (k, v) -> st.put(k, v) } }
        val o = JSONObject()
            .put("op", "state").put("v", version)
            .put("left", ((expiresAt - RemoteHelpTime.now()) / 1000).coerceAtLeast(0))
            .put("device", android.os.Build.MODEL ?: "")
            .put("sources", sources).put("status", status)
            .put("groups", ListEditCodec.encode(snapshot, app.hiddenChannels))
        send(o)
    }

    private fun send(o: JSONObject) {
        val k = keys ?: return
        val r = relay ?: return
        scope.launch(Dispatchers.IO) {
            sendLock.withLock {
                runCatching { r.publish(k.toHelper, RemoteCrypto.seal(k, o.toString())) }
                    .onFailure { e -> launch(Dispatchers.Main) { lastAction = "Senden fehlgeschlagen: ${e.message}" } }
            }
        }
    }
}

/**
 * Box, die hilft: verbindet sich über den Code mit der anderen Box und zeigt deren Playlists und Senderliste.
 * Jede Änderung geht als Auftrag an die andere Box; dort wird sie ausgeführt und der neue Stand kommt zurück.
 */
class RemoteHelpGuest(private val rawCode: String, private val scope: CoroutineScope, private val relayBase: String = Relay.DEFAULT) : PlaylistTarget {
    var status by mutableStateOf("Verbinde …")
        private set
    var connected by mutableStateOf(false)
        private set
    var device by mutableStateOf("")
        private set
    var closed by mutableStateOf<String?>(null)
        private set
    var expiresAt by mutableStateOf(0L)
        private set
    final override var sources by mutableStateOf<List<PlaylistSource>>(emptyList())
        private set
    final override var sourceStatus by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Senderliste der anderen Box (beim Bearbeiten nicht ersetzt, damit Änderungen nicht verloren gehen) */
    var list by mutableStateOf<RemoteListModel?>(null, referentialEqualityPolicy())
        private set
    var editing = false
        set(v) { field = v; if (!v) pendingList?.let { list = it; pendingList = null } }

    private var listVersion = 0
    private var pendingList: RemoteListModel? = null
    private var keys: RemoteCrypto.Keys? = null
    private var relay: Relay? = null
    private var job: Job? = null
    private val sendLock = Mutex()
    private var lastPing = 0L
    @Volatile private var running = true

    fun start() {
        job = scope.launch {
            val code = RemoteCrypto.normalize(rawCode)
            if (code == null) { closed = "Der Code muss 8 Zeichen haben (Buchstaben und Zahlen)."; return@launch }
            val k = withContext(Dispatchers.Default) { RemoteCrypto.keys(code) }
            val r = Relay(relayBase)
            keys = k; relay = r
            launch(Dispatchers.IO) {
                r.listen(
                    k.toHelper,
                    active = { running },
                    onState = { on -> scope.launch { if (!connected) status = if (on) "Warte auf die andere Box …" else "Briefkasten nicht erreichbar – neuer Versuch …" } },
                    onMessage = { text ->
                        val o = RemoteCrypto.open(k, text) ?: return@listen
                        scope.launch { if (running) handle(o) }
                    },
                )
            }
            // „Hallo“ wiederholen, bis die andere Box antwortet
            repeat(6) {
                if (connected || !running) return@launch
                sendNow(JSONObject().put("op", "hello"))
                delay(7_000)
            }
            if (!connected && running) status = "Keine Antwort. Stimmt der Code, und ist auf der anderen Box „Fernhilfe erlauben“ noch offen?"
        }
    }

    fun stop() {
        if (!running) return
        if (connected) send(JSONObject().put("op", "bye"))
        running = false
        scope.launch { delay(3_000); job?.cancel() }
    }

    private fun handle(o: JSONObject) {
        when (o.optString("op")) {
            "state" -> {
                connected = true
                device = o.optString("device")
                expiresAt = RemoteHelpTime.now() + o.optLong("left") * 1000
                sources = (0 until (o.optJSONArray("sources")?.length() ?: 0)).mapNotNull { sourceFromJson(o.getJSONArray("sources").getJSONObject(it)) }
                sourceStatus = o.optJSONObject("status")?.let { st -> st.keys().asSequence().associateWith { st.optString(it) } }.orEmpty()
                listVersion = o.optInt("v")
                val model = RemoteListModel(o.optJSONArray("groups") ?: JSONArray(), listVersion, ::activity)
                if (editing) pendingList = model else list = model
                status = "Verbunden – Änderungen gelten sofort auf der anderen Box."
                qaLog("FH_STATE ${sources.size} Playlists, ${model.editGroups.size} Gruppen")
            }
            "msg" -> { status = o.optString("text"); qaLog("FH_MSG ${o.optString("text")}") }
            "bye" -> {
                closed = "Die andere Box hat die Fernhilfe beendet" + o.optString("text").takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() + "."
                running = false
            }
        }
    }

    /** Aktivität des Helfers (auch nur Sortieren): hält die Fernhilfe offen, höchstens alle 20 s eine Nachricht */
    fun activity() {
        expiresAt = maxOf(expiresAt, RemoteHelpTime.now() + RemoteHelpTime.EXTEND_MS)
        val now = RemoteHelpTime.now()
        if (now - lastPing > 20_000) { lastPing = now; send(JSONObject().put("op", "ping")) }
    }

    private fun order(o: JSONObject, text: String) {
        expiresAt = maxOf(expiresAt, RemoteHelpTime.now() + RemoteHelpTime.EXTEND_MS)
        lastPing = RemoteHelpTime.now()
        status = "$text – wird übertragen …"
        send(o)
    }

    override fun addSource(source: PlaylistSource) = order(JSONObject().put("op", "add").put("s", sourceToJson(source)), "Playlist hinzufügen")
    override fun updateSource(source: PlaylistSource) = order(JSONObject().put("op", "update").put("s", sourceToJson(source)), "Playlist ändern")
    override fun setSourceEnabled(source: PlaylistSource, on: Boolean) =
        order(JSONObject().put("op", "enable").put("id", source.id).put("on", on), if (on) "Einschalten" else "Pausieren")
    override fun renameSource(source: PlaylistSource, name: String) {
        if (name.isBlank()) return
        updateSource(source.copy(name = name.trim()))
    }
    override fun moveSource(source: PlaylistSource, delta: Int) = order(JSONObject().put("op", "move").put("id", source.id).put("d", delta), "Verschieben")
    override fun removeSource(source: PlaylistSource) = order(JSONObject().put("op", "remove").put("id", source.id), "Löschen")
    fun refresh() = order(JSONObject().put("op", "refresh"), "Neu laden")

    /** Bearbeitete Senderliste schicken (nur wenn etwas geändert wurde) */
    fun sendList(model: RemoteListModel) {
        if (!model.dirty) return
        order(JSONObject().put("op", "layout").put("v", model.version).put("groups", model.export()), "Senderliste")
    }

    private fun send(o: JSONObject) {
        scope.launch(Dispatchers.IO) { sendNow(o) }
    }

    private suspend fun sendNow(o: JSONObject) {
        val k = keys ?: return
        val r = relay ?: return
        withContext(Dispatchers.IO) {
            sendLock.withLock {
                runCatching { r.publish(k.toBox, RemoteCrypto.seal(k, o.toString())) }
                    .onFailure { e -> withContext(Dispatchers.Main) { status = "Senden fehlgeschlagen: ${e.message}" } }
            }
        }
    }
}

/**
 * Senderliste der anderen Box zum Bearbeiten – mit derselben Bedienung wie die eigene (ListEditor).
 * Schlüssel: Gruppe „G<Index>“, Sender „C<Gruppe>:<Index>“ (Positionen im verschickten Stand, keine Links).
 */
class RemoteListModel(arr: JSONArray, val version: Int, private val onActivity: () -> Unit) : ListEditTarget {
    private val original: List<EditGroup>
    private val originalHidden: Set<String>

    init {
        val hidden = HashSet<String>()
        original = (0 until arr.length()).map { gi ->
            val g = arr.getJSONObject(gi)
            val c = g.optJSONArray("c") ?: JSONArray()
            val name = g.optString("n")
            val channels = (0 until c.length()).map { ci ->
                val pair = c.getJSONArray(ci)
                val key = "C$gi:$ci"
                if (pair.optInt(1) == 1) hidden += key
                Channel(name = pair.optString(0), group = name, logo = null, url = key)
            }
            EditGroup(
                key = "G$gi", name = name, originalName = g.optString("o").ifEmpty { name },
                sourceName = g.optString("s").ifEmpty { null }, channels = channels, hidden = g.optInt("h") == 1,
            )
        }
        originalHidden = hidden
    }

    final override var editGroups by mutableStateOf(original, referentialEqualityPolicy())
        private set
    final override var hiddenChannels by mutableStateOf<Set<String>>(originalHidden)
        private set
    var dirty by mutableStateOf(false)
        private set

    private fun changed() { dirty = true; onActivity() }

    override fun channelNote(channel: Channel): String = ""

    override fun moveGroup(key: String, delta: Int) {
        val l = editGroups.toMutableList()
        val i = l.indexOfFirst { it.key == key }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        editGroups = l; changed()
    }

    override fun toggleGroupHidden(key: String) {
        editGroups = editGroups.map { if (it.key == key) it.copy(hidden = !it.hidden) else it }; changed()
    }

    override fun renameGroup(key: String, name: String) {
        editGroups = editGroups.map { if (it.key == key) it.copy(name = name.trim().ifEmpty { it.originalName }) else it }; changed()
    }

    override fun moveChannel(groupKey: String, channelKey: String, delta: Int) {
        editGroups = editGroups.map { g ->
            if (g.key != groupKey) return@map g
            val l = g.channels.toMutableList()
            val i = l.indexOfFirst { it.key == channelKey }
            val j = i + delta
            if (i < 0 || j !in l.indices) return@map g
            l[i] = l[j].also { l[j] = l[i] }
            g.copy(channels = l)
        }
        changed()
    }

    override fun toggleChannelHidden(channelKey: String) {
        hiddenChannels = if (channelKey in hiddenChannels) hiddenChannels - channelKey else hiddenChannels + channelKey; changed()
    }

    /** Gruppe auf den Stand zurück, der von der anderen Box kam */
    override fun resetGroup(groupKey: String) {
        val orig = original.firstOrNull { it.key == groupKey } ?: return
        val keys = orig.channels.map { it.key }.toSet()
        editGroups = editGroups.map { if (it.key == groupKey) orig else it }
        hiddenChannels = (hiddenChannels - keys) + originalHidden.intersect(keys)
        changed()
    }

    /** Neuer Stand für die andere Box: Positionen statt Links (siehe ListEditCodec.decode) */
    fun export(): JSONArray {
        val arr = JSONArray()
        editGroups.forEach { g ->
            val c = JSONArray()
            g.channels.forEach { ch ->
                val ci = ch.url.substringAfter(':').toInt()
                c.put(JSONArray().put(ci).put(if (ch.url in hiddenChannels) 1 else 0))
            }
            arr.put(JSONObject().put("i", g.key.drop(1).toInt()).put("n", g.name).put("h", if (g.hidden) 1 else 0).put("c", c))
        }
        return arr
    }
}
