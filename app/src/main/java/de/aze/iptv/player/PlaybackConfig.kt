package de.aze.iptv.player

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Puffer-Stufen.
 *  minMs/maxMs  = so viel Vorrat baut der Player im Hintergrund auf
 *  startMs      = so viel muss da sein, bevor das Bild startet (kleiner = schnelleres Umschalten)
 *  rebufferMs   = so viel wird nach einem Hänger gesammelt, bevor es weitergeht
 *  liveOffsetMs = wie weit hinter "live" abgespielt wird (größer = mehr Verzögerung, aber stabiler)
 */
enum class BufferLevel(
    val label: String,
    val hint: String,
    val minMs: Int,
    val maxMs: Int,
    val startMs: Int,
    val rebufferMs: Int,
    val liveOffsetMs: Long?,
) {
    SMALL("Klein", "Schnelles Umschalten, für gute Verbindungen", 10_000, 30_000, 1_000, 2_500, null),
    MEDIUM("Mittel", "Ausgewogen (empfohlen)", 20_000, 50_000, 2_000, 4_000, null),
    LARGE("Groß", "Sehr stabil, dafür etwas später als live", 40_000, 90_000, 4_000, 8_000, 30_000L);

    companion object {
        fun of(name: String?): BufferLevel = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Bildformat. ratio != null erzwingt ein Seitenverhältnis. */
enum class AspectMode(val label: String, val ratio: Float?) {
    FIT("Original", null),
    ZOOM("Füllen (Zoom)", null),
    STRETCH("Strecken", null),
    WIDE("16:9", 16f / 9f),
    CLASSIC("4:3", 4f / 3f),
    /** Kinoformat – für Filme, die sonst gestreckt aussehen */
    CINEMA("2,35:1", 2.35f);

    companion object {
        fun of(name: String?): AspectMode = entries.firstOrNull { it.name == name } ?: FIT
    }
}

/** Welcher Player abspielt. AUTO: Media3, bei Problemen automatisch VLC für diesen Sender. */
enum class PlayerEngine(val label: String) {
    AUTO("Automatisch"),
    MEDIA3("Immer Media3"),
    VLC("Immer VLC");

    companion object {
        fun of(name: String?): PlayerEngine = entries.firstOrNull { it.name == name } ?: MEDIA3
    }
}

object PlaybackConfig {

    /** Schwache Geräte (z. B. ältere Fire Sticks) bekommen eine Speichergrenze, damit nichts abstürzt. */
    private fun bufferBytes(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowRam = am.isLowRamDevice || am.memoryClass <= 128
        return if (lowRam) 24 * 1024 * 1024 else 64 * 1024 * 1024
    }

    @OptIn(UnstableApi::class)
    fun loadControl(context: Context, level: BufferLevel): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(level.minMs, level.maxMs, level.startMs, level.rebufferMs)
            .setTargetBufferBytes(bufferBytes(context))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
}
