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

    /** Ein durchgehender Stream (.ts o. ä.) – nur dafür wird nahtlos neu verbunden. HLS/DASH regeln das selbst. */
    fun isProgressive(item: MediaItem): Boolean {
        val cfg = item.localConfiguration ?: return false
        return Util.inferContentTypeForUriAndMimeType(cfg.uri, cfg.mimeType) == C.CONTENT_TYPE_OTHER &&
            cfg.uri.scheme?.lowercase() in setOf("http", "https")
    }

    fun create(item: MediaItem): MediaSource =
        if (isProgressive(item)) progressive.createMediaSource(item) else adaptive.createMediaSource(item)

    /**
     * Folge-Verbindung: Viele Anbieter schicken bei jeder neuen Verbindung zuerst die letzten 20–30 s noch einmal
     * (Vorrat) – ein Teil davon wurde schon gezeigt. Der Zeitstempel des zuletzt empfangenen Bildes reist im
     * Schlüssel der Anfrage mit; [EndOnDropDataSource] überspringt alles bis dahin. Ohne Zeitstempel: wie bisher.
     */
    private fun createRejoin(item: MediaItem): MediaSource {
        val last = track.last
        if (!isProgressive(item) || last < 0) return create(item)
        return progressive.createMediaSource(item.buildUpon().setCustomCacheKey(CUT_PREFIX + last).build())
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
        /** So kurz vor dem Ende des Vorrats wird die Folge-Verbindung geöffnet (Zeit für Verbinden + erstes Bild). */
        const val REJOIN_LEAD_MS = 3_000L

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
internal class PtsTrack { @Volatile var last = -1L }

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
    fun videoPts(b: ByteArray, p: Int): Long {
        if (!pusi(b, p)) return -1
        val s = payload(b, p)
        if (s < 0 || s + 14 > p + SIZE) return -1
        if (b[s].toInt() != 0 || b[s + 1].toInt() != 0 || b[s + 2].toInt() != 1) return -1
        val sid = b[s + 3].toInt() and 0xFF
        if (sid !in 0xE0..0xEF) return -1
        if ((b[s + 7].toInt() and 0x80) == 0) return -1
        val t = s + 9
        return ((b[t].toLong() shr 1) and 7 shl 30) or
            ((b[t + 1].toLong() and 0xFF) shl 22) or
            (((b[t + 2].toLong() and 0xFF) shr 1) shl 15) or
            ((b[t + 3].toLong() and 0xFF) shl 7) or
            ((b[t + 4].toLong() and 0xFF) shr 1)
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
    private var acc = ByteArray(256 * 1024)
    private var accLen = 0
    private var dropped = 0L
    private var firstPts = -1L
    private val pmts = HashSet<Int>()
    private val psi = LinkedHashMap<Int, ByteArray>()
    private var out: ByteArray? = null
    private var outPos = 0
    private val tmp = ByteArray(64 * 1024)

    // --- Zeitstempel mitlesen (für die nächste Folge-Verbindung) ---
    private val tb = ByteArray(Ts.SIZE)
    private var tbLen = 0
    private var connMax = -1L

    override fun addTransferListener(transferListener: TransferListener) = up.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        begin(dataSpec.key?.takeIf { it.startsWith(LiveSource.CUT_PREFIX) }?.removePrefix(LiveSource.CUT_PREFIX)?.toLongOrNull() ?: -1L)
        return up.open(dataSpec)
    }

    /** Neue Verbindung: [cutPts] ≥ 0 = alles bis zu diesem Bild-Zeitstempel ist schon gezeigt. */
    internal fun begin(cutPts: Long) {
        got = 0; accLen = 0; dropped = 0; firstPts = -1; out = null; outPos = 0; tbLen = 0; connMax = -1
        if (acc.size < 256 * 1024) acc = ByteArray(256 * 1024)
        pmts.clear(); psi.clear()
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

    /** Liest so lange, bis feststeht, ab wo neu ist. false = Verbindung zu Ende, bevor etwas Neues kam. */
    private fun cutting(): Boolean {
        while (phase != 0) {
            val n = upRead(tmp, 0, tmp.size)
            if (n == C.RESULT_END_OF_INPUT) { phase = 0; return false }
            if (accLen + n > acc.size) acc = acc.copyOf(maxOf(acc.size * 2, accLen + n))
            System.arraycopy(tmp, 0, acc, accLen, n)
            accLen += n
            var p = 0
            while (accLen - p >= Ts.SIZE) {
                if (acc[p].toInt() != 0x47) { p++; continue }
                val pid = Ts.pid(acc, p)
                if (pid == 0) { Ts.patPmts(acc, p, pmts); psi[pid] = acc.copyOfRange(p, p + Ts.SIZE) }
                else if (pid in pmts && Ts.pusi(acc, p)) psi[pid] = acc.copyOfRange(p, p + Ts.SIZE)
                val pts = Ts.videoPts(acc, p)
                if (pts >= 0) {
                    val d = Ts.diff(pts, cut)
                    if (phase == 1) {
                        firstPts = pts
                        // nichts doppelt (schon neuer) oder ganz andere Zeitrechnung: alles wie bisher weitergeben
                        if (d > 0 || d < -60L * 90_000) { finish(0, keepPsi = false, overlap = 0); return true }
                        phase = 2
                    }
                    if (phase == 2 && d > 0 && (Ts.rai(acc, p) || d > 2L * 90_000)) {
                        finish(p, keepPsi = true, overlap = Ts.diff(cut, firstPts))
                        return true
                    }
                }
                p += Ts.SIZE
            }
            // noch kein Bild gefunden: nicht endlos sammeln (z. B. Radio ohne Bild) → wie bisher weitergeben
            if (phase == 1 && accLen > 1024 * 1024) { finish(0, keepPsi = false, overlap = 0); return true }
            if (phase == 2) {
                // alles vor p ist schon gezeigt – wegwerfen (Tabellen PAT/PMT werden gemerkt)
                dropped += p
                System.arraycopy(acc, p, acc, 0, accLen - p)
                accLen -= p
                if (dropped > 24L * 1024 * 1024) { finish(0, keepPsi = true, overlap = -1); return true }
            }
        }
        return true
    }

    /** Ab [from] im Sammelpuffer weitergeben, davor die gemerkten Tabellen. */
    private fun finish(from: Int, keepPsi: Boolean, overlap: Long) {
        val head = if (keepPsi) psi.values.fold(ByteArray(0)) { a, b -> a + b } else ByteArray(0)
        val rest = acc.copyOfRange(from, accLen)
        val all = head + rest
        dropped += from
        phase = 0
        accLen = 0
        acc = ByteArray(0)
        out = if (all.isEmpty()) null else all
        outPos = 0
        if (all.isNotEmpty()) watch(all, 0, all.size)
        if (overlap > 0) {
            val sec = overlap / 9_000 / 10.0
            val mb = dropped / 1024 / 1024.0
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                de.aze.iptv.data.HangLog.add("DOPPELT_UEBERSPRUNGEN %.1f s (%.1f MB) – Anbieter schickte schon Gezeigtes noch einmal".format(java.util.Locale.GERMANY, sec, mb))
            }
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
        if (connMax < 0 || Ts.diff(pts, connMax) > 0) { connMax = pts; track.last = pts }
    }

    override fun getUri(): Uri? = up.uri
    override fun getResponseHeaders(): Map<String, List<String>> = up.responseHeaders
    override fun close() {
        out = null; acc = ByteArray(256 * 1024); accLen = 0
        up.close()
    }
}
