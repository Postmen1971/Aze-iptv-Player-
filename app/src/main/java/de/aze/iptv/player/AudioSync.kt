package de.aze.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.util.concurrent.atomic.AtomicLong

/**
 * Ton-Bild-Synchronisation für Media3.
 * Das Bild richtet sich nach der Ton-Uhr. Meldet die Ton-Uhr eine um [offsetUs] spätere Zeit,
 * kommt das Bild entsprechend früher – der Ton wirkt also um [offsetUs] verzögert (positiv = Ton später).
 */
object AudioSync {
    /** Aktueller Versatz in Mikrosekunden – kann während der Wiedergabe geändert werden. */
    val offsetUs = AtomicLong(0)

    fun setMs(ms: Int) = offsetUs.set(ms * 1000L)
}

@OptIn(UnstableApi::class)
private class OffsetAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        if (p == AudioSink.CURRENT_POSITION_NOT_SET) return p
        return maxOf(0L, p + AudioSync.offsetUs.get())
    }
}

/** Wie der Standard, nur mit verschiebbarer Ton-Uhr. */
@OptIn(UnstableApi::class)
class SyncRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let { OffsetAudioSink(it) }
}
