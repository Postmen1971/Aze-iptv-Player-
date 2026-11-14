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
class LiveSource(context: Context, http: HttpDataSource.Factory, fastHttp: HttpDataSource.Factory? = null) {
    private val plain: DataSource.Factory = DefaultDataSource.Factory(context, http)
    /** Gleiche Verbindung mit kurzer Lesezeit: tote Leitung wird früher erkannt (siehe [EndOnDropDataSource]). */
    private val plainFast: DataSource.Factory? = fastHttp?.let { DefaultDataSource.Factory(context, it) }
    private val endOnDrop = DataSource.Factory { EndOnDropDataSource(plain.createDataSource(), plainFast?.createDataSource()) }
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
     * Hängt an [player] die automatische Folge-Verbindung. [onRejoin] meldet „Verbindung zu Ende, neue angehängt“,
     * [onSwitch] den Moment des Übergangs (zum Protokollieren). Rückgabe: zum Abmelden aufrufen.
     */
    fun attach(player: ExoPlayer, onRejoin: () -> Unit = {}, onSwitch: () -> Unit = {}): () -> Unit {
        val handler = android.os.Handler(player.applicationLooper)
        var shortRuns = 0
        val analytics = object : AnalyticsListener {
            override fun onLoadCompleted(t: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                if (data.dataType != C.DATA_TYPE_MEDIA) return
                val cur = player.currentMediaItem ?: return
                // nur für das letzte Stück der Liste (sonst würde jede Verbindung mehrfach nachgelegt)
                if (t.windowIndex != player.mediaItemCount - 1 || !isProgressive(cur)) return
                handler.removeCallbacksAndMessages(null)
                // Bremse: Antwortet der Anbieter nur ganz kurz (fast leere Antwort, sofort wieder zu), nicht im
                // Dauerfeuer neu verbinden – das wirkt wie ein Angriff und endet in Sperren. Wartezeit wächst
                // 0,3 → 0,6 → 1,2 … höchstens 8 s; nach der ersten normalen Verbindung wieder sofort.
                val short = info.loadDurationMs < 3_000 && info.bytesLoaded < 512 * 1024
                if (!short) {
                    shortRuns = 0
                    player.addMediaSource(create(cur))
                    onRejoin()
                    return
                }
                val wait = minOf(300L shl minOf(shortRuns, 5), 8_000L)
                shortRuns++
                val count = player.mediaItemCount
                handler.postDelayed({
                    // nur wenn inzwischen nichts anderes passiert ist (Senderwechsel, Neustart)
                    if (player.mediaItemCount == count && player.currentMediaItem?.localConfiguration?.uri == cur.localConfiguration?.uri) {
                        player.addMediaSource(create(cur))
                        onRejoin()
                    }
                }, wait)
            }
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
 * Mit [fast] (kurze Lesezeit) wird eine tote Leitung – Verbindung offen, aber es kommt nichts mehr – nach wenigen
 * Sekunden statt nach 10 s erkannt. Sicherung gegen Fehlalarme: Antwortet ein Server beim Verbinden langsamer als
 * die kurze Lesezeit oder macht er öfter längere Sendepausen, gilt für ihn ab dann wieder die geduldige Verbindung.
 */
@OptIn(UnstableApi::class)
private class EndOnDropDataSource(private val slow: DataSource, private val fast: DataSource?) : DataSource {
    private var up: DataSource = slow
    private var got = 0L
    private var host: String? = null

    override fun addTransferListener(transferListener: TransferListener) {
        slow.addTransferListener(transferListener)
        fast?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        got = 0
        host = dataSpec.uri.host
        val f = fast
        val h = host
        if (f != null && h != null && !slowHosts.containsKey(h)) {
            up = f
            val t0 = android.os.SystemClock.elapsedRealtime()
            try {
                return f.open(dataSpec)
            } catch (e: IOException) {
                // nur „Server antwortet zu langsam“ (nicht: Verbindungsaufbau scheitert, Fehlercode, Abbruch)
                val quick = android.os.SystemClock.elapsedRealtime() - t0 < 10_000
                if (!quick || !isTimeout(e) || Thread.currentThread().isInterrupted) throw e
                slowHosts[h] = true
                try { f.close() } catch (_: Exception) {}
            }
        }
        up = slow
        return slow.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        up.read(buffer, offset, length).also { if (it > 0) got += it }
    } catch (e: InterruptedIOException) {
        // Lesezeit überschritten = Leitung tot (SocketTimeoutException ist eine InterruptedIOException)
        if (isTimeout(e) && got > 0) { noteTimeout(); C.RESULT_END_OF_INPUT } else throw e
    } catch (e: IOException) {
        if (got > 0) {
            if (isTimeout(e)) noteTimeout()
            C.RESULT_END_OF_INPUT
        } else throw e
    }

    /** Dritte Lesezeit-Überschreitung der kurzen Verbindung in 2 Minuten → dieser Server bekommt wieder 10 s Geduld. */
    private fun noteTimeout() {
        val h = host ?: return
        if (up !== fast) return
        val now = android.os.SystemClock.elapsedRealtime()
        val c = timeouts.getOrPut(h) { longArrayOf(0L, now) }
        synchronized(c) {
            if (now - c[1] > 120_000) { c[0] = 0; c[1] = now }
            if (++c[0] >= 3) slowHosts[h] = true
        }
    }

    private fun isTimeout(e: Throwable): Boolean {
        var c: Throwable? = e
        var n = 0
        while (c != null && n++ < 6) {
            if (c is java.net.SocketTimeoutException) return true
            c = c.cause
        }
        return false
    }

    override fun getUri(): Uri? = up.uri
    override fun getResponseHeaders(): Map<String, List<String>> = up.responseHeaders
    override fun close() = up.close()

    private companion object {
        val slowHosts = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val timeouts = java.util.concurrent.ConcurrentHashMap<String, LongArray>()
    }
}
