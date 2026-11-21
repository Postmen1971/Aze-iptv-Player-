package de.aze.iptv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Live-Sender ohne Hänger beim Neuverbinden.
 *
 * Problem vorher: Beendet der Anbieter einen .ts-Stream (oder bricht die Leitung ab), merkte der Player das erst,
 * wenn der ganze Vorrat (oft 20–30 s) abgespielt war. Dann: schwarzes Bild, Decoder neu, wieder puffern → Hänger.
 *
 * Jetzt: Sobald die Verbindung endet, wird SOFORT eine neue Verbindung als nächstes Stück in die Wiedergabeliste
 * gehängt. Der Player lädt sie, während der alte Vorrat noch läuft, und wechselt am Ende ohne Neustart weiter
 * (gleicher Decoder, kein Schwarzbild). Die alte Verbindung ist dann schon zu – auch Anbieter mit nur 1 Verbindung
 * machen mit. Ein Verbindungsabbruch mitten im Stream zählt wie ein Ende (statt bis zum Leerlaufen zu warten).
 */
@OptIn(UnstableApi::class)
class LiveSource(context: Context, http: HttpDataSource.Factory) {
    private val plain: DataSource.Factory = DefaultDataSource.Factory(context, http)
    /** Zeitstempel (PTS) des neuesten Bildes der letzten Verbindung – für das Überspringen doppelter Stücke. */
    private val track = PtsTrack()
    private val endOnDrop = DataSource.Factory { EndOnDropDataSource(plain.createDataSource(), track) }
    private val policy = livePolicy()
    private val adaptive = DefaultMediaSourceFactory(plain).setLoadErrorHandlingPolicy(policy)
    private val progressive = ProgressiveMediaSource.Factory(endOnDrop).setLoadErrorHandlingPolicy(policy)
    /**
     * Für Folge-Verbindungen, bei denen Doppeltes übersprungen wird: Der Einstieg liegt am nächsten Einstiegsbild des
     * Anbieters. Manche Sender (z. B. TEVE 2) setzen die Einstiegsmarke auf Vollbilder, die Media3 sonst NICHT als
     * Einstieg akzeptiert („kein IDR“) – dann stand das Bild nach dem Übergang mehrere Sekunden. Mit diesem Schalter
     * zählen auch diese Vollbilder; schlimmstenfalls ist das allererste Bild nach dem Übergang kurz unsauber.
     */
    private val progressiveRejoin = ProgressiveMediaSource.Factory(
        endOnDrop,
        androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES),
    ).setLoadErrorHandlingPolicy(policy)

    /** Ein durchgehender Stream (.ts o. ä.) – nur dafür wird nahtlos neu verbunden. HLS/DASH regeln das selbst. */
    fun isProgressive(item: MediaItem): Boolean {
        val cfg = item.localConfiguration ?: return false
        return Util.inferContentTypeForUriAndMimeType(cfg.uri, cfg.mimeType) == C.CONTENT_TYPE_OTHER &&
            cfg.uri.scheme?.lowercase() in setOf("http", "https")
    }

    /** Neuer Sender (oder Neustart): Zeitstempel des vorigen gilt nicht mehr. */
    fun create(item: MediaItem): MediaSource {
        track.gen++
        track.last = -1
        return if (isProgressive(item)) progressive.createMediaSource(item) else adaptive.createMediaSource(item)
    }

    /**
     * Folge-Verbindung: Viele Anbieter schicken bei jeder neuen Verbindung zuerst die letzten 20–30 s noch einmal
     * (Vorrat) – ein Teil davon wurde schon gezeigt. Der Zeitstempel des zuletzt empfangenen Bildes reist im
     * Schlüssel der Anfrage mit; [EndOnDropDataSource] überspringt alles bis dahin. Ohne Zeitstempel: wie bisher.
     */
    private fun createRejoin(item: MediaItem): MediaSource {
        val last = track.last
        // Folge-Verbindung gehört zum selben Sender: Zeitstempel-Kette NICHT zurücksetzen (anders als create)
        if (!isProgressive(item)) return adaptive.createMediaSource(item)
        if (last < 0) return progressive.createMediaSource(item)
        return progressiveRejoin.createMediaSource(item.buildUpon().setCustomCacheKey(CUT_PREFIX + last).build())
    }

    /**
     * Hängt an [player] die automatische Folge-Verbindung. [onRejoin] meldet „Verbindung zu Ende, neue angehängt“,
     * [onSwitch] den Moment des Übergangs (zum Protokollieren). Rückgabe: zum Abmelden aufrufen.
     */
    fun attach(player: ExoPlayer, onRejoin: () -> Unit = {}, onSwitch: () -> Unit = {}): () -> Unit {
        val handler = android.os.Handler(player.applicationLooper)
        var shortRuns = 0
        /** zählt hoch, sobald eine wartende Folge-Verbindung nicht mehr gilt (neue Ladung fertig, Neustart, Senderwechsel) */
        var generation = 0
        val analytics = object : AnalyticsListener {
            override fun onLoadCompleted(t: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                if (data.dataType != C.DATA_TYPE_MEDIA) return
                val cur = player.currentMediaItem ?: return
                // nur für das letzte Stück der Liste (sonst würde jede Verbindung mehrfach nachgelegt)
                if (t.windowIndex != player.mediaItemCount - 1 || !isProgressive(cur)) return
                handler.removeCallbacksAndMessages(null)
                generation++
                // Bremse: Antwortet der Anbieter nur ganz kurz (fast leere Antwort, sofort wieder zu), nicht im
                // Dauerfeuer neu verbinden – das wirkt wie ein Angriff und endet in Sperren. Wartezeit wächst
                // 0,3 → 0,6 → 1,2 … höchstens 8 s; nach der ersten normalen Verbindung wieder sofort.
                val short = info.loadDurationMs < 3_000 && info.bytesLoaded < 512 * 1024
                if (!short) {
                    shortRuns = 0
                    // Manche Anbieter schicken ein Stück (z. B. 30 s) im Schnelldurchlauf und schließen dann. Wer sich sofort
                    // neu verbindet, bekommt fast dasselbe Stück noch einmal – die Szene läuft doppelt. Deshalb erst neu
                    // verbinden, wenn der Vorrat fast aufgebraucht ist: dann ist der Anbieter entsprechend weiter.
                    // Normale Sender (Echtzeit, Vorrat klein) verbinden wie bisher sofort.
                    val gen = ++generation
                    var told = false
                    fun appendWhenDue() {
                        if (gen != generation || player.currentMediaItem?.localConfiguration?.uri != cur.localConfiguration?.uri) return
                        // vorsichtig gerechnet (kleinerer der beiden Werte); gewartet wird nur, solange das Bild läuft
                        val remaining = minOf(player.totalBufferedDuration, (player.bufferedPosition - player.currentPosition).coerceAtLeast(0))
                        if (remaining > REJOIN_LEAD_MS + 500 && player.isPlaying) {
                            if (!told) { told = true; de.aze.iptv.data.HangLog.add("REJOIN_WARTET Vorrat ${remaining / 1000} s – verbindet kurz vor dem Ende neu") }
                            handler.postDelayed({ appendWhenDue() }, minOf(remaining - REJOIN_LEAD_MS, 2_000L))
                            return
                        }
                        player.addMediaSource(createRejoin(cur))
                        onRejoin()
                    }
                    appendWhenDue()
                    return
                }
                val wait = minOf(300L shl minOf(shortRuns, 5), 8_000L)
                shortRuns++
                val count = player.mediaItemCount
                handler.postDelayed({
                    // nur wenn inzwischen nichts anderes passiert ist (Senderwechsel, Neustart)
                    if (player.mediaItemCount == count && player.currentMediaItem?.localConfiguration?.uri == cur.localConfiguration?.uri) {
                        player.addMediaSource(createRejoin(cur))
                        onRejoin()
                    }
                }, wait)
            }
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Neustart (stop/prepare): die alte Verbindung wird neu geladen – eine wartende Folge-Verbindung
                // würde sonst parallel dazu aufgehen (Anbieter mit nur einer Verbindung lehnen das ab)
                if (playbackState == androidx.media3.common.Player.STATE_IDLE) { generation++; handler.removeCallbacksAndMessages(null) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) { generation++; handler.removeCallbacksAndMessages(null) }
                if (reason != androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                // abgespielte alte Verbindungen aus der Liste nehmen
                val idx = player.currentMediaItemIndex
                if (idx > 0) player.removeMediaItems(0, idx)
                onSwitch()
            }
        }
        player.addAnalyticsListener(analytics)
        player.addListener(listener)
        return {
            handler.removeCallbacksAndMessages(null)
            player.removeAnalyticsListener(analytics)
            player.removeListener(listener)
        }
    }

    companion object {
        /**
         * So viel Vorrat ist noch übrig, wenn die Folge-Verbindung geöffnet wird. 3 s (1.0.269) reichte nicht: liefert
         * der Anbieter die neue Verbindung NICHT im Schnelldurchlauf (Now TV, 17.11.), lief der Vorrat nach dem
         * Überspringen des Doppelten leer → Puffern, Wächter. Seit das Doppelte übersprungen wird (1.0.281), kostet
         * früheres Neuverbinden keine Wiederholung mehr – also mehr Sicherheitsabstand.
         */
        const val REJOIN_LEAD_MS = 10_000L

        /** Schlüssel-Vorsatz einer Folge-Verbindung: dahinter der PTS des zuletzt empfangenen Bildes. */
        internal const val CUT_PREFIX = "aze-cut:"

        /**
         * Schnelle Wiederholung statt Media3-Standard (6 Versuche mit 1–5 s Pause ≈ 15 s): Viele Anbieter geben die
         * alte Verbindung erst nach kurzer Zeit frei und lehnen solange ab (403/429/509). Alle 0,4 s neu versuchen,
         * dann ruhiger; endgültig falsche Adressen (401/404/410) sofort als Fehler melden.
         */
        fun livePolicy(): LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(MAX_TRIES) {
            override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val e = info.exception
                if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in intArrayOf(401, 404, 410)) return C.TIME_UNSET
                if (super.getRetryDelayMsFor(info) == C.TIME_UNSET) return C.TIME_UNSET
                return when {
                    info.errorCount <= 10 -> 300L // neu verbinden nach 0,3 s
                    info.errorCount <= MAX_TRIES -> 1_500L
                    else -> C.TIME_UNSET
                }
            }
        }

        private const val MAX_TRIES = 20
    }
}

/**
 * Bricht die Leitung ab, NACHDEM schon Daten kamen, gilt das als „Stream zu Ende“ statt als Fehler.
 * So hängt [LiveSource] sofort die neue Verbindung an (Media3 würde sonst erst nach dem ganzen Vorrat neu starten).
 *
 * WICHTIG – nicht wieder einbauen: eine KURZE Lesezeit, um „tote Leitungen schneller zu erkennen“ (Versuch in
 * 1.0.255 mit 4 s). Viele Anbieter schicken erst einen großen Vorrat und danach nur noch stoßweise mit Pausen von
 * mehreren Sekunden. Eine kurze Lesezeit hält so eine Pause für eine tote Leitung; nach dem Neuverbinden schickt der
 * Anbieter denselben Vorrat noch einmal und Szenen laufen doppelt. „Nach Zeitablauf einfach weiterlesen“ geht auf
 * Android auch nicht (die Verbindung wird beim Zeitablauf geschlossen). Die Lesezeit kommt deshalb vom Aufrufer
 * (PlayerScreen: 20 s); eine wirklich tote Leitung bemerkt der Wächter am leeren Vorrat.
 */
/** Neuester Bild-Zeitstempel (PTS, 90 kHz) der zuletzt gelesenen Verbindung; −1 = unbekannt. */
internal class PtsTrack {
    @Volatile var last = -1L
    /**
     * Zählt je neuem Sender hoch. Eine Verbindung schreibt ihren Zeitstempel nur, solange ihr Sender noch läuft –
     * sonst landete der Wert eines Fernsehsenders beim nächsten (z. B. Radio ohne Bild: Neuverbinden hing dort).
     */
    @Volatile var gen = 0
}

/** Kleine Helfer zum Lesen von MPEG-TS-Paketen (188 Bytes). Nur Kopfzeilen, kein Umbau der Daten. */
internal object Ts {
    const val SIZE = 188
    private const val MASK = (1L shl 33) - 1
    fun pid(b: ByteArray, p: Int) = ((b[p + 1].toInt() and 0x1F) shl 8) or (b[p + 2].toInt() and 0xFF)
    fun pusi(b: ByteArray, p: Int) = (b[p + 1].toInt() and 0x40) != 0
    private fun afc(b: ByteArray, p: Int) = (b[p + 3].toInt() shr 4) and 3

    /** Beginn der Nutzdaten im Paket oder −1. */
    fun payload(b: ByteArray, p: Int): Int {
        val a = afc(b, p)
        if (a == 0 || a == 2) return -1
        val s = if (a == 3) p + 5 + (b[p + 4].toInt() and 0xFF) else p + 4
        return if (s < p + SIZE) s else -1
    }

    /** „Hier darf man einsteigen“ (meist ein Schlüsselbild). */
    fun rai(b: ByteArray, p: Int): Boolean {
        val a = afc(b, p)
        return a >= 2 && (b[p + 4].toInt() and 0xFF) > 0 && (b[p + 5].toInt() and 0x40) != 0
    }

    /** PTS, wenn hier ein Bild-Paket (Video-PES) beginnt, sonst −1. */
    fun videoPts(b: ByteArray, p: Int): Long = pesPts(b, p, videoOnly = true)

    /** Stream-Kennung (stream_id), wenn hier ein PES beginnt, sonst −1. */
    fun pesSid(b: ByteArray, p: Int): Int {
        if (!pusi(b, p)) return -1
        val s = payload(b, p)
        if (s < 0 || s + 4 > p + SIZE) return -1
        if (b[s].toInt() != 0 || b[s + 1].toInt() != 0 || b[s + 2].toInt() != 1) return -1
        return b[s + 3].toInt() and 0xFF
    }

    fun isVideo(sid: Int) = sid in 0xE0..0xEF

    /** PTS, wenn hier ein PES beginnt (Bild oder Ton), sonst −1. */
    fun pesPts(b: ByteArray, p: Int, videoOnly: Boolean = false): Long {
        val sid = pesSid(b, p)
        if (sid < 0 || (videoOnly && !isVideo(sid))) return -1
        val s = payload(b, p)
        if (s + 14 > p + SIZE) return -1
        if ((b[s + 7].toInt() and 0x80) == 0) return -1
        val t = s + 9
        return (((b[t].toLong() shr 1) and 7L) shl 30) or
            ((b[t + 1].toLong() and 0xFFL) shl 22) or
            (((b[t + 2].toLong() and 0xFFL) shr 1) shl 15) or
            ((b[t + 3].toLong() and 0xFFL) shl 7) or
            ((b[t + 4].toLong() and 0xFFL) shr 1)
    }

    /** a − b mit Überlauf (33 Bit), Ergebnis mit Vorzeichen. */
    fun diff(a: Long, b: Long): Long {
        var d = (a - b) and MASK
        if (d >= (1L shl 32)) d -= (1L shl 33)
        return d
    }

    /** PMT-PIDs aus einer PAT. */
    fun patPmts(b: ByteArray, p: Int, into: MutableSet<Int>) {
        if (!pusi(b, p)) return
        val s = payload(b, p)
        if (s < 0) return
        val t = s + 1 + (b[s].toInt() and 0xFF)
        if (t + 8 > p + SIZE || b[t].toInt() != 0) return
        val len = ((b[t + 1].toInt() and 0x0F) shl 8) or (b[t + 2].toInt() and 0xFF)
        val end = minOf(t + 3 + len - 4, p + SIZE - 4)
        var i = t + 8
        while (i + 4 <= end) {
            val prog = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            if (prog != 0) into.add(((b[i + 2].toInt() and 0x1F) shl 8) or (b[i + 3].toInt() and 0xFF))
            i += 4
        }
    }
}

@OptIn(UnstableApi::class)
internal class EndOnDropDataSource(private val up: DataSource, private val track: PtsTrack) : DataSource {
    private var got = 0L

    // --- doppelte Stücke überspringen (nur bei Folge-Verbindungen mit Zeitstempel) ---
    private var cut = -1L
    /** 0 = durchreichen, 1 = warten auf das erste Bild, 2 = Überspringen bis hinter [cut] */
    private var phase = 0
    // Sammelpuffer nur, wenn wirklich übersprungen wird (sonst ~600 KB Müll je Senderwechsel)
    private var acc = EMPTY
    private var accLen = 0
    private var dropped = 0L
    private var firstPts = -1L
    private var latestPts = -1L
    private var t0 = 0L
    /** Kein Rückblick-Vorrat, sondern laufendes Programm mit neuer Zeitrechnung: am nächsten Schlüsselbild einsteigen. */
    private var relax = false
    private var relaxAt = 0L
    private val pmts = HashSet<Int>()
    private val seenPes = HashSet<Int>()
    private val started = HashSet<Int>()
    private var videoPid = -1
    private var scanPos = 0
    private var filterPos = 0
    private var logged = false
    private var keptMedia = false
    private val kept = java.io.ByteArrayOutputStream()
    private var out: ByteArray? = null
    private var outPos = 0
    private val tmp by lazy { ByteArray(64 * 1024) }
    /** zu welchem Sender diese Verbindung gehört (siehe PtsTrack.gen) */
    private var myGen = track.gen
    /** dieselbe Verbindung neu öffnen (null = geht nicht, z. B. Test ohne Adresse) */
    internal var reopen: (() -> Unit)? = null
    /** Folge-Verbindung brachte nur schon Gezeigtes: so oft wurde selbst neu verbunden */
    private var retries = 0

    // --- Zeitstempel mitlesen (für die nächste Folge-Verbindung) ---
    private val tb = ByteArray(Ts.SIZE)
    private var tbLen = 0
    private var connMax = -1L

    override fun addTransferListener(transferListener: TransferListener) = up.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        myGen = track.gen
        reopen = { up.open(dataSpec) }
        retries = 0
        begin(dataSpec.key?.takeIf { it.startsWith(LiveSource.CUT_PREFIX) }?.removePrefix(LiveSource.CUT_PREFIX)?.toLongOrNull() ?: -1L)
        return up.open(dataSpec)
    }

    /** Neue Verbindung: [cutPts] ≥ 0 = alles bis zu diesem Bild-Zeitstempel ist schon gezeigt. */
    internal fun begin(cutPts: Long) {
        got = 0; accLen = 0; dropped = 0; firstPts = -1; latestPts = -1; t0 = 0; relax = false
        out = null; outPos = 0; tbLen = 0; connMax = -1
        if (cutPts >= 0 && acc.size < 256 * 1024) acc = ByteArray(256 * 1024)
        pmts.clear(); seenPes.clear(); started.clear(); videoPid = -1; scanPos = 0; filterPos = 0; logged = false; keptMedia = false
        kept.reset()
        cut = cutPts
        phase = if (cut >= 0) 1 else 0
    }

    /** Lesen wie bisher: Abbruch nach erhaltenen Daten = Ende. */
    private fun upRead(buffer: ByteArray, offset: Int, length: Int): Int = try {
        up.read(buffer, offset, length).also { if (it > 0) got += it }
    } catch (e: java.net.SocketTimeoutException) {
        // Lesezeit überschritten = Leitung tot (SocketTimeoutException ist eine InterruptedIOException)
        if (got > 0) C.RESULT_END_OF_INPUT else throw e
    } catch (e: InterruptedIOException) {
        throw e // abgebrochener Ladevorgang
    } catch (e: IOException) {
        if (got > 0) C.RESULT_END_OF_INPUT else throw e
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        out?.let { o ->
            val n = minOf(length, o.size - outPos)
            System.arraycopy(o, outPos, buffer, offset, n)
            outPos += n
            if (outPos >= o.size) { out = null; outPos = 0 }
            return n
        }
        if (phase != 0) {
            if (!cutting()) return C.RESULT_END_OF_INPUT
            return read(buffer, offset, length)
        }
        val n = upRead(buffer, offset, length)
        if (n > 0) watch(buffer, offset, n)
        return n
    }

    /**
     * Liest so lange, bis feststeht, ab wo neu ist, und gibt nur Neues weiter. false = Verbindung zu Ende, bevor etwas
     * Neues kam.
     *
     * Jede Spur einzeln: Der TON läuft ab dem ersten Ton-Paket hinter [cut] nahtlos weiter; das BILD steigt am nächsten
     * vollständigen Bild (Schlüsselbild) hinter [cut] ein. Am Übergang bleibt so höchstens kurz das Bild stehen,
     * der Ton springt nicht (vorher sprangen Bild und Ton gemeinsam bis zum Schlüsselbild nach vorn – kleiner Ruck).
     */
    private fun cutting(): Boolean {
        while (phase != 0) {
            // Senderwechsel/Abbruch: nicht weiter im Hintergrund Altes verwerfen (blockiert sonst die Leitung)
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val n = upRead(tmp, 0, tmp.size)
            if (n == C.RESULT_END_OF_INPUT) {
                // Phase 1 ohne Bild: alles weitergeben; Phase 2: nur, was schon als neu erkannt ist
                val rest = if (phase == 1) acc.copyOf(accLen) else ByteArray(0)
                val last = if (phase == 2 && (videoPid < 0 || videoPid !in started)) ByteArray(0) else kept.toByteArray()
                val all = last + rest
                if (all.isEmpty() && reconnect()) continue
                phase = 0; accLen = 0; kept.reset()
                if (all.isEmpty()) return false
                return emit(all)
            }
            if (t0 == 0L) t0 = android.os.SystemClock.elapsedRealtime()
            if (accLen + n > acc.size) acc = acc.copyOf(maxOf(acc.size * 2, accLen + n))
            System.arraycopy(tmp, 0, acc, accLen, n)
            accLen += n

            if (phase == 1) {
                // erst entscheiden: Ist überhaupt etwas doppelt?
                var p = scanPos
                while (accLen - p >= Ts.SIZE) {
                    if (acc[p].toInt() != 0x47) { p++; continue }
                    val pts = Ts.videoPts(acc, p)
                    if (pts >= 0) {
                        firstPts = pts; latestPts = pts
                        val d = Ts.diff(pts, cut)
                        // nichts doppelt (schon neuer) oder ganz andere Zeitrechnung: alles wie bisher weitergeben
                        if (d > 0 || d < -60L * 90_000) { phase = 0; return passRest() }
                        phase = 2; filterPos = 0
                        break
                    }
                    p += Ts.SIZE
                }
                scanPos = p
                // noch kein Bild gefunden: nicht endlos sammeln (z. B. Radio ohne Bild) → wie bisher weitergeben
                if (phase == 1) {
                    if (accLen > 1024 * 1024 || android.os.SystemClock.elapsedRealtime() - t0 > 2_000) { phase = 0; return passRest() }
                    continue
                }
            }

            // Phase 2: Paket für Paket
            var p = filterPos
            while (accLen - p >= Ts.SIZE) {
                if (acc[p].toInt() != 0x47) { p++; continue }
                val pid = Ts.pid(acc, p)
                var keep: Boolean
                if (pid == 0) { Ts.patPmts(acc, p, pmts); keep = true }
                else if (pid in pmts) keep = true
                else {
                    val sid = Ts.pesSid(acc, p)
                    if (sid >= 0) {
                        seenPes.add(pid)
                        if (Ts.isVideo(sid)) videoPid = pid
                        if (pid !in started) {
                            val pts = Ts.pesPts(acc, p)
                            if (Ts.isVideo(sid) && pts >= 0) latestPts = pts
                            val d = if (pts >= 0) Ts.diff(pts, cut) else Long.MIN_VALUE
                            val ok = if (Ts.isVideo(sid)) {
                                if (relax) Ts.rai(acc, p) || android.os.SystemClock.elapsedRealtime() - relaxAt > 1_500
                                else d > 0 && (Ts.rai(acc, p) || d > 2L * 90_000)
                            } else if (relax) {
                                // neue Zeitrechnung: hier gibt es keinen „nahtlosen“ Ton – Ton erst zusammen mit dem Bild
                                videoPid >= 0 && videoPid in started
                            } else d > 0
                            if (ok) started.add(pid)
                        }
                    }
                    keep = pid in started
                }
                if (keep) { kept.write(acc, p, Ts.SIZE); if (pid != 0 && pid !in pmts) keptMedia = true } else dropped += Ts.SIZE
                p += Ts.SIZE
            }
            // verarbeitete Bytes aus dem Sammelpuffer nehmen
            System.arraycopy(acc, p, acc, 0, accLen - p)
            accLen -= p
            filterPos = 0

            // Kommt der Rückblick-Vorrat nicht im Schnelldurchlauf (Anbieter beginnt jede Verbindung mit neuer
            // Zeitrechnung und liefert in Echtzeit), würde das Warten bis hinter [cut] lange Schwarzbild kosten.
            // Dauert es voraussichtlich länger als 4 s: nicht weiter überspringen, am nächsten Schlüsselbild rein.
            if (!relax && videoPid !in started) {
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                if (ms >= 1_000 && firstPts >= 0 && latestPts >= 0) {
                    val span = Ts.diff(latestPts, firstPts) / 90_000.0
                    val rate = maxOf(span / (ms / 1000.0), 0.1)
                    val left = Ts.diff(cut, latestPts) / 90_000.0
                    if (left / rate > 4.0) { relax = true; relaxAt = android.os.SystemClock.elapsedRealtime() }
                }
            }
            val videoIn = videoPid >= 0 && videoPid in started
            if (videoIn && !logged) {
                logged = true
                if (!relax) logSkip(Ts.diff(cut, firstPts))
            }
            // fertig: Bild läuft wieder und alle Spuren sind dabei (oder Ton kommt nicht nach) → ab jetzt durchreichen
            if (videoIn && (started.containsAll(seenPes) || kept.size() > 2 * 1024 * 1024)) {
                phase = 0
                val rest = acc.copyOf(accLen); accLen = 0
                return emit(kept.toByteArray().also { kept.reset() } + rest)
            }
            if (dropped > 24L * 1024 * 1024) { phase = 0; return emit(kept.toByteArray().also { kept.reset() } + passBytes()) }
            // schon Neues da (z. B. Ton): sofort weitergeben, damit der Player nicht leerläuft
            // (nur Tabellen allein noch nicht – sonst bekäme der Player eine „leere“ Verbindung)
            if (keptMedia && kept.size() > 0) return emit(kept.toByteArray().also { kept.reset() })
        }
        return true
    }

    private fun passBytes(): ByteArray { val r = acc.copyOf(accLen); accLen = 0; return r }

    /**
     * Folge-Verbindung brachte NUR schon Gezeigtes (alles übersprungen, nichts Neues). Ein leeres Stück erkennt der
     * Player nicht („Format nicht erkannt“ → Fehler und Neustart des Senders, z. B. PHOENIX, beIN 19./21.11.).
     * Stattdessen hier gleich noch einmal verbinden – das Bild läuft solange aus dem Vorrat weiter.
     */
    private fun reconnect(): Boolean {
        val again = reopen ?: return false
        if (retries >= 4) return false
        retries++
        runCatching { up.close() }
        Thread.sleep(400L * retries)
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
        val c = cut
        begin(c)
        got = 0
        again()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            de.aze.iptv.data.HangLog.add("NEUVERBINDUNG_LEER nur schon Gezeigtes – verbinde erneut (Versuch $retries)")
        }
        return true
    }

    private fun passRest(): Boolean = emit(passBytes())

    /** Bytes als nächste Ausgabe bereitstellen (leer: einfach weiterlesen). */
    private fun emit(all: ByteArray): Boolean {
        if (all.isEmpty()) return true
        out = all
        outPos = 0
        watch(all, 0, all.size)
        return true
    }

    private fun logSkip(overlap: Long) {
        if (overlap <= 0) return
        val sec = overlap / 9_000 / 10.0
        val mb = dropped / 1024 / 1024.0
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            de.aze.iptv.data.HangLog.add("DOPPELT_UEBERSPRUNGEN %.1f s (%.1f MB) – Anbieter schickte schon Gezeigtes noch einmal".format(java.util.Locale.GERMANY, sec, mb))
        }
    }

    /** Zeitstempel der weitergegebenen Daten mitlesen (neuestes Bild dieser Verbindung). */
    private fun watch(b: ByteArray, off: Int, len: Int) {
        var i = off
        val end = off + len
        while (i < end) {
            if (tbLen == 0) {
                // Paketanfang suchen; ganze Pakete direkt im Puffer prüfen
                if (b[i].toInt() != 0x47) { i++; continue }
                if (end - i >= Ts.SIZE) { note(b, i); i += Ts.SIZE; continue }
            }
            val n = minOf(Ts.SIZE - tbLen, end - i)
            System.arraycopy(b, i, tb, tbLen, n)
            tbLen += n; i += n
            if (tbLen == Ts.SIZE) { if (tb[0].toInt() == 0x47) note(tb, 0); tbLen = 0 }
        }
    }

    private fun note(b: ByteArray, p: Int) {
        val pts = Ts.videoPts(b, p)
        if (pts < 0) return
        if (connMax < 0 || Ts.diff(pts, connMax) > 0) { connMax = pts; if (myGen == track.gen) track.last = pts }
    }

    override fun getUri(): Uri? = up.uri
    override fun getResponseHeaders(): Map<String, List<String>> = up.responseHeaders
    override fun close() {
        out = null; acc = EMPTY; accLen = 0
        up.close()
    }

    private companion object { val EMPTY = ByteArray(0) }
}
