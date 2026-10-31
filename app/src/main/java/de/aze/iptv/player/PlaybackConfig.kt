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
    // Start-Puffer klein halten: Live-Sender (TS) kommen vom Server nur in Echtzeit – 2 s Start-Puffer hieß
    // 2 s stehendes erstes Bild bei jedem Umschalten. Den großen Vorrat baut der Player danach im Laufen auf.
    SMALL("Klein", "Schnelles Umschalten, für gute Verbindungen", 10_000, 30_000, 500, 2_000, null),
    MEDIUM("Mittel", "Ausgewogen (empfohlen)", 20_000, 50_000, 1_500, 4_000, null),
    LARGE("Groß", "Sehr stabil, dafür etwas später als live", 40_000, 90_000, 2_500, 6_000, 30_000L);

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

    /**
     * [audioLeadMs]: Lippensynchron „Ton später“ (positiver Wert). Dann wird das Bild um diesen Wert FRÜHER gezeigt –
     * dafür muss so viel Bild schon im Vorrat sein. Live-Sender kommen nur in Echtzeit; mit dem normalen kleinen
     * Start-Vorrat fehlten die Bilder → Bild blieb stehen, Ton lief weiter. Darum wächst der Start-Vorrat mit.
     */
    @OptIn(UnstableApi::class)
    fun loadControl(context: Context, level: BufferLevel, audioLeadMs: Int = 0): LoadControl {
        val lead = audioLeadMs.coerceIn(0, 5_000)
        val start = if (lead > 0) maxOf(level.startMs, lead + 2_000) else level.startMs
        val rebuffer = if (lead > 0) maxOf(level.rebufferMs, lead + 3_000) else level.rebufferMs
        val min = maxOf(level.minMs, rebuffer)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, maxOf(level.maxMs, min), start, rebuffer)
            .setTargetBufferBytes(bufferBytes(context))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }
}
