package de.aze.iptv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lädt Filme und Folgen aufs Gerät – einer nach dem anderen, mit Fortsetzen nach Abbruch (HTTP-Range).
 * Lebt so lange wie der App-Prozess (nicht wie der Bildschirm); solange etwas lädt, hält [de.aze.iptv.DownloadService]
 * den Prozess am Leben (Hinweis in der Benachrichtigungsleiste).
 *
 * Fernsehen hat Vorrang (Aziz: Umschalten muss schnell bleiben): läuft Fernsehen oder ein Film vom Anbieter, lädt der
 * Download gebremst – oder wartet, wenn der Anbieter nur eine Verbindung erlaubt (siehe [DownloadRules.mode]).
 */
object Downloader {
    /** Liste für die Anzeige (Hauptthread) */
    var entries by mutableStateOf<List<DlEntry>>(emptyList())
        private set
    /** Zustand des laufenden Downloads in einfachen Worten („lädt · 3,2 MB/s“, „wartet – Fernsehen läuft“) */
    var status by mutableStateOf("")
        private set
    /** Speicherort: 0 = Gerät, 1… = USB-Stick/SD-Karte (falls vorhanden) */
    var storageIdx by mutableStateOf(0)
        private set

    /** Abspiel-Adresse für einen Eintrag (setzt die App, sobald Playlists da sind) */
    @Volatile var resolver: ((DlEntry) -> String?)? = null
    /** Wie geladen werden darf (setzt die App; ohne = volle Geschwindigkeit) */
    @Volatile var policy: ((DlEntry) -> DownloadRules.Mode)? = null
    /** App ist sichtbar (sonst läuft weder Fernsehen noch Film) */
    @Volatile var appVisible = false

    private lateinit var ctx: Context
    private val lock = Any()
    private val list = ArrayList<DlEntry>()
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    /** Eintrag, der gerade lädt (wird abgebrochen, wenn ihn der Nutzer anhält oder löscht) */
    @Volatile private var runningKey: String? = null
    @Volatile private var stopRunning = false

    private val prefs by lazy { ctx.getSharedPreferences("aze_downloads", Context.MODE_PRIVATE) }

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        storageIdx = prefs.getInt("storage", 0)
        synchronized(lock) {
            list.clear()
            list.addAll(DownloadRules.fromJson(prefs.getString("list", "[]")))
            // fertige Dateien, die es nicht mehr gibt (z. B. USB-Stick abgezogen oder von Hand gelöscht), bleiben als Fehler sichtbar
            for (i in list.indices) {
                val e = list[i]
                if (e.state == DlState.DONE && !File(e.file).exists()) list[i] = e.copy(state = DlState.FAIL, error = "Datei fehlt (Speicher entfernt?)")
            }
        }
        publish(save = false)
    }

    // ---------- Speicherort ----------

    /** mögliche Speicherorte: interner Speicher, dazu eingesteckte USB-Sticks/SD-Karten */
    fun roots(): List<File> = runCatching { ctx.getExternalFilesDirs(null).filterNotNull() }.getOrDefault(emptyList())
        .ifEmpty { listOf(ctx.filesDir) }

    fun rootName(i: Int): String = if (i == 0) "Gerät" else if (roots().size > 2) "USB/SD $i" else "USB-Stick/SD-Karte"

    fun dir(): File {
        val r = roots()
        return File(r[storageIdx.coerceIn(0, r.lastIndex)], "downloads").apply { mkdirs() }
    }

    fun freeBytes(): Long = runCatching { dir().usableSpace }.getOrDefault(0L)

    fun setStorage(i: Int) {
        storageIdx = i.coerceIn(0, roots().lastIndex)
        prefs.edit().putInt("storage", storageIdx).apply()
    }

    /** belegt von allen Downloads (fertig und angefangen) */
    fun usedBytes(): Long = synchronized(lock) { list.sumOf { e -> runCatching { File(e.file).length() + File(e.file + ".part").length() }.getOrDefault(0L) } }

    // ---------- Abfragen ----------

    fun get(key: String): DlEntry? = entries.firstOrNull { it.key == key }

    /** fertig geladene Datei (zum Abspielen ohne Internet) */
    fun localUri(key: String): String? =
        get(key)?.takeIf { it.state == DlState.DONE }?.file?.let { File(it) }?.takeIf { it.exists() }?.let { android.net.Uri.fromFile(it).toString() }

    // ---------- Aktionen (vom Hauptthread) ----------

    /** In die Warteschlange (schon vorhanden: weitermachen) */
    fun enqueue(ref: VodRef, key: String, episode: EpisodeRef? = null) {
        synchronized(lock) {
            val at = list.indexOfFirst { it.key == key }
            if (at >= 0) {
                val e = list[at]
                if (e.state == DlState.DONE && File(e.file).exists()) return
                list[at] = e.copy(state = DlState.WAIT, error = null)
            } else {
                val e0 = DlEntry(key, ref, episode, added = System.currentTimeMillis())
                list.add(e0.copy(file = File(dir(), DownloadRules.fileName(e0)).absolutePath))
            }
        }
        de.aze.iptv.ui.qaLog("DL_NEU $key")
        publish()
        kick()
    }

    fun pause(key: String) {
        update(key) { if (it.state == DlState.WAIT || it.state == DlState.RUN) it.copy(state = DlState.PAUSE) else it }
        if (runningKey == key) stopRunning = true
        de.aze.iptv.ui.qaLog("DL_PAUSE $key")
    }

    fun resume(key: String) {
        update(key) { if (it.state == DlState.PAUSE || it.state == DlState.FAIL) it.copy(state = DlState.WAIT, error = null) else it }
        kick()
    }

    fun delete(key: String) {
        val e = synchronized(lock) { list.firstOrNull { it.key == key }?.also { list.remove(it) } } ?: return
        if (runningKey == key) stopRunning = true
        scope.launch {
            // laufender Download schließt die Datei gleich – kurz warten, dann löschen
            repeat(20) { if (runningKey != key) return@repeat; delay(100) }
            File(e.file).delete(); File(e.file + ".part").delete()
        }
        de.aze.iptv.ui.qaLog("DL_GELOESCHT $key")
        publish()
    }

    fun deleteAll() { entries.map { it.key }.forEach { delete(it) } }

    /** Beim App-Start (Playlists da): angefangene Downloads fortsetzen */
    fun start() { if (synchronized(lock) { list.any { it.state == DlState.WAIT } }) kick() }

    // ---------- Laden ----------

    private fun kick() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            startService()
            try {
                while (isActive) {
                    val next = synchronized(lock) { list.firstOrNull { it.state == DlState.WAIT } } ?: break
                    runOne(next)
                }
            } finally {
                setStatus("")
                stopService()
                // kam gerade noch etwas dazu (während die Schleife endete)? Dann gleich weiter
                main.postDelayed({ if (synchronized(lock) { list.any { it.state == DlState.WAIT } }) kick() }, 500)
            }
        }
    }

    private suspend fun runOne(first: DlEntry) {
        val key = first.key
        runningKey = key
        stopRunning = false
        var attempt = 0
        try {
            while (true) {
                val e = synchronized(lock) { list.firstOrNull { it.key == key } } ?: return
                if (e.state != DlState.WAIT && e.state != DlState.RUN) return
                if (stopRunning) return
                // Fernsehen hat Vorrang
                val mode = currentMode(e)
                if (mode == DownloadRules.Mode.WAIT) {
                    setStatus("wartet – Fernsehen läuft (Anbieter erlaubt nur eine Verbindung)")
                    update(key) { it.copy(state = DlState.WAIT) }
                    delay(3_000); continue
                }
                val url = resolver?.invoke(e)
                if (url.isNullOrBlank()) {
                    // App noch nicht bereit (Playlists werden gelesen) – oder Playlist gelöscht
                    if (resolver == null) { setStatus("wartet auf die Playlist"); delay(3_000); continue }
                    fail(key, "Playlist nicht gefunden"); return
                }
                val result = try {
                    transfer(e, url)
                } catch (x: IOException) {
                    Result.Retry(x.message ?: "Verbindung abgebrochen", rejected = false)
                }
                when (result) {
                    Result.Done -> {
                        val part = File(e.file + ".part")
                        val dst = File(e.file)
                        if (!part.renameTo(dst)) { part.copyTo(dst, overwrite = true); part.delete() }
                        update(key) { it.copy(state = DlState.DONE, done = dst.length(), total = dst.length(), error = null) }
                        de.aze.iptv.ui.qaLog("DL_FERTIG $key ${dst.length()}")
                        HangLog.add("DOWNLOAD_FERTIG ${e.title} ${DownloadRules.size(dst.length())}")
                        return
                    }
                    Result.Stopped -> return
                    Result.ModeChanged -> continue
                    is Result.Fail -> { fail(key, result.why); return }
                    is Result.Retry -> {
                        attempt++
                        de.aze.iptv.ui.qaLog("DL_NEUVERSUCH $key $attempt ${result.why}")
                        if (attempt >= DownloadRules.MAX_ATTEMPTS) { fail(key, result.why); return }
                        val wait = DownloadRules.retryDelayMs(attempt - 1, result.rejected)
                        setStatus("${result.why} – neuer Versuch in ${wait / 1000} s")
                        update(key) { it.copy(state = DlState.WAIT, error = result.why) }
                        // in kleinen Schritten warten (Anhalten/Löschen wirkt sofort)
                        var waited = 0L
                        while (waited < wait && !stopRunning) { delay(500); waited += 500 }
                    }
                }
            }
        } finally {
            runningKey = null
        }
    }

    private sealed class Result {
        object Done : Result()
        object Stopped : Result()
        object ModeChanged : Result()
        class Fail(val why: String) : Result()
        class Retry(val why: String, val rejected: Boolean) : Result()
    }

    private fun currentMode(e: DlEntry): DownloadRules.Mode =
        if (!appVisible) DownloadRules.Mode.FULL else runCatching { policy?.invoke(e) }.getOrNull() ?: DownloadRules.Mode.FULL

    private fun transfer(e: DlEntry, url: String): Result {
        val part = File(e.file + ".part")
        part.parentFile?.mkdirs()
        val from = if (part.exists()) part.length() else 0L
        val free = runCatching { part.parentFile!!.usableSpace }.getOrDefault(0L)
        if (!DownloadRules.fits(e.total, from, free)) {
            return Result.Fail("Zu wenig Speicher frei (${DownloadRules.size(free)})")
        }
        val conn = open(url, from)
        try {
            val code = conn.responseCode
            when (code) {
                401, 403, 429, 503, 509 -> return Result.Retry("Anbieter lehnt gerade ab (Fehler $code)", rejected = true)
                404, 410 -> return Result.Fail("Gibt es beim Anbieter nicht mehr (Fehler $code)")
                416 -> {
                    // „Bereich passt nicht“: Datei ist schon vollständig – oder Server hat eine andere Größe
                    if (e.total > 0 && from >= e.total) return Result.Done
                    part.delete(); return Result.Retry("Neu anfangen", rejected = false)
                }
            }
            if (code !in 200..299) return Result.Retry("Server antwortet mit Fehler $code", rejected = false)
            val info = DownloadRules.resumeInfo(from, code, conn.getHeaderField("Content-Range"), conn.getHeaderField("Content-Length")?.trim()?.toLongOrNull() ?: -1L)
                ?: run { part.delete(); return Result.Retry("Neu anfangen", rejected = false) }
            val (start, total) = info
            if (!DownloadRules.fits(total, start, free)) {
                return Result.Fail("Zu wenig Speicher: braucht ${DownloadRules.size(total - start)}, frei ${DownloadRules.size(free)}")
            }
            update(e.key) { it.copy(state = DlState.RUN, done = start, total = total, error = null) }
            de.aze.iptv.ui.qaLog("DL_START ${e.key} ab $start von $total")
            return RandomAccessFile(part, "rw").use { out ->
                out.setLength(start)
                out.seek(start)
                conn.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var done = start
                    var mark = System.currentTimeMillis()
                    var markBytes = done
                    var secStart = mark
                    var secBytes = 0L
                    var mode = currentMode(e)
                    var savedAt = mark
                    while (true) {
                        if (stopRunning) return Result.Stopped
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        secBytes += n
                        val now = System.currentTimeMillis()
                        // gebremst: höchstens SLOW_BYTES_PER_SEC je Sekunde
                        if (mode == DownloadRules.Mode.SLOW && secBytes >= DownloadRules.SLOW_BYTES_PER_SEC) {
                            val rest = 1000 - (now - secStart)
                            if (rest > 0) Thread.sleep(rest)
                        }
                        if (now - secStart >= 1000) { secStart = System.currentTimeMillis(); secBytes = 0 }
                        if (now - mark >= 1000) {
                            val speed = (done - markBytes) * 1000 / (now - mark).coerceAtLeast(1)
                            mark = now; markBytes = done
                            update(e.key, save = false) { it.copy(done = done, total = total) }
                            val pct = if (total > 0) " · ${(done * 100 / total)} %" else ""
                            setStatus("lädt · ${DownloadRules.size(speed)}/s$pct" + if (mode == DownloadRules.Mode.SLOW) " (gebremst, Fernsehen läuft)" else "")
                            de.aze.iptv.DownloadService.progress(ctx, e.title, if (total > 0) (done * 100 / total).toInt() else -1)
                            val m = currentMode(e)
                            if (m != mode) {
                                // Fernsehen gestartet und nur eine Verbindung erlaubt: sofort Verbindung freigeben
                                if (m == DownloadRules.Mode.WAIT) { save(); return Result.ModeChanged }
                                mode = m
                            }
                            // Stand ab und zu sichern (nach Absturz/Neustart geht es fast an derselben Stelle weiter)
                            if (now - savedAt >= 10_000) { savedAt = now; save() }
                            val freeNow = runCatching { part.parentFile!!.usableSpace }.getOrDefault(Long.MAX_VALUE)
                            if (freeNow < DownloadRules.STOP_FREE) return Result.Fail("Speicher ist voll")
                        }
                    }
                    update(e.key) { it.copy(done = done) }
                    if (total > 0 && done < total) return Result.Retry("Verbindung abgebrochen", rejected = false)
                    if (done == 0L) return Result.Retry("Keine Daten", rejected = false)
                    Result.Done
                }
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** Verbindung mit „ab Byte …“, Weiterleitungen selbst (der Range-Kopf muss bei jeder Station mit) */
    private fun open(url: String, from: Long): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel")
                val next = URL(URL(current), loc).toString()
                c.disconnect()
                if (current.startsWith("https://", true) && next.startsWith("http://", true)) throw IOException("Unsichere Weiterleitung blockiert")
                current = next
            } else return c
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    private fun fail(key: String, why: String) {
        update(key) { it.copy(state = DlState.FAIL, error = why) }
        de.aze.iptv.ui.qaLog("DL_FEHLER $key $why")
        HangLog.add("DOWNLOAD_FEHLER $why")
    }

    // ---------- Zustand ----------

    private fun update(key: String, save: Boolean = true, f: (DlEntry) -> DlEntry) {
        synchronized(lock) {
            val at = list.indexOfFirst { it.key == key }
            if (at < 0) return
            list[at] = f(list[at])
        }
        publish(save)
    }

    private fun save() = publish(save = true)

    private fun publish(save: Boolean = true) {
        val copy = synchronized(lock) { list.toList() }
        if (save) runCatching { prefs.edit().putString("list", DownloadRules.toJson(copy)).apply() }
        main.post { entries = copy }
    }

    private fun setStatus(s: String) { main.post { status = s } }

    private fun startService() = runCatching { de.aze.iptv.DownloadService.start(ctx) }
    private fun stopService() = runCatching { de.aze.iptv.DownloadService.stop(ctx) }
}

