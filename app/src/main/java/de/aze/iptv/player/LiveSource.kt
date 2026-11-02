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
    private val endOnDrop = DataSource.Factory { EndOnDropDataSource(plain.createDataSource()) }
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
        val analytics = object : AnalyticsListener {
            override fun onLoadCompleted(t: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                if (data.dataType != C.DATA_TYPE_MEDIA) return
                val cur = player.currentMediaItem ?: return
                // nur für das letzte Stück der Liste (sonst würde jede Verbindung mehrfach nachgelegt)
                if (t.windowIndex != player.mediaItemCount - 1 || !isProgressive(cur)) return
                player.addMediaSource(create(cur))
                onRejoin()
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
                    info.errorCount <= 10 -> 400L
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
 */
@OptIn(UnstableApi::class)
private class EndOnDropDataSource(private val up: DataSource) : DataSource {
    private var got = 0L
    override fun addTransferListener(transferListener: TransferListener) = up.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        got = 0
        return up.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        up.read(buffer, offset, length).also { if (it > 0) got += it }
    } catch (e: java.net.SocketTimeoutException) {
        // Lesezeit überschritten = Leitung tot
        if (got > 0) C.RESULT_END_OF_INPUT else throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: IOException) {
        if (got > 0) C.RESULT_END_OF_INPUT else throw e
    }

    override fun getUri(): Uri? = up.uri
    override fun getResponseHeaders(): Map<String, List<String>> = up.responseHeaders
    override fun close() = up.close()
}
