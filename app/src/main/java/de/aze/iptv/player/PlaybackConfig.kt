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
 *  quickMs      = beim ERSTEN Hänger (seit 3 Minuten) reicht schon so viel – siehe [LiveLoadControl]; null = immer rebufferMs
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
    val quickMs: Int?,
) {
    // Start-Puffer klein halten: Live-Sender (TS) kommen vom Server nur in Echtzeit – 2 s Start-Puffer hieß
    // 2 s stehendes erstes Bild bei jedem Umschalten. Den großen Vorrat baut der Player danach im Laufen auf.
    SMALL("Klein", "Schnelles Umschalten, für gute Verbindungen", 10_000, 30_000, 500, 2_000, null, 1_000),
    MEDIUM("Mittel", "Ausgewogen (empfohlen)", 20_000, 50_000, 1_500, 4_000, null, 2_000),
    // Groß: wer das wählt, will lieber einmal länger warten und danach mehr Vorrat – bleibt wie bisher
    LARGE("Groß", "Sehr stabil, dafür etwas später als live", 40_000, 90_000, 2_500, 6_000, 30_000L, null);

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
    fun loadControl(context: Context, level: BufferLevel, audioLeadMs: Int = 0, maxBytes: Int? = null): LoadControl {
        val lead = audioLeadMs.coerceIn(0, 5_000)
        val start = if (lead > 0) maxOf(level.startMs, lead + 2_000) else level.startMs
        val rebuffer = if (lead > 0) maxOf(level.rebufferMs, lead + 3_000) else level.rebufferMs
        val min = maxOf(level.minMs, rebuffer)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, maxOf(level.maxMs, min), start, rebuffer)
            .setTargetBufferBytes(maxBytes?.let { minOf(it, bufferBytes(context)) } ?: bufferBytes(context))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }

    /**
     * Wie [loadControl], aber mit schnellem Wiederanlauf nach einer Bildlücke (siehe [LiveLoadControl]).
     * Für den Haupt-Player (Live-TV). Je Player eine eigene Instanz – sie merkt sich die Hänger dieses Players.
     */
    @OptIn(UnstableApi::class)
    fun liveLoadControl(context: Context, level: BufferLevel, audioLeadMs: Int = 0): LiveLoadControl {
        val lead = audioLeadMs.coerceIn(0, 5_000)
        val rebuffer = if (lead > 0) maxOf(level.rebufferMs, lead + 3_000) else level.rebufferMs
        // „Ton später“: so viel Bild muss mindestens vorausliegen (gleicher Abstand wie beim Start-Vorrat), sonst bliebe
        // das Bild gleich wieder stehen
        val quick = level.quickMs?.let { q -> minOf(rebuffer, if (lead > 0) maxOf(q, lead + 2_000) else q) }
        return LiveLoadControl(loadControl(context, level, audioLeadMs), quick, rebuffer)
    }
}

/**
 * Wann darf es nach einem Hänger weitergehen: beim ersten Hänger seit [windowMs] schon mit dem kleinen Vorrat,
 * kommt innerhalb dieser Zeit ein weiterer, wieder erst mit dem vollen. Reine Rechenregel (ohne Android), damit sie
 * im Unit-Test geprüft werden kann.
 */
internal class RebufferPolicy(private val windowMs: Long = 180_000L) {
    private var lastStallAt = Long.MIN_VALUE

    /** Ein neuer Hänger beginnt um [nowMs]. true = schneller Wiederanlauf erlaubt. */
    @Synchronized
    fun onStall(nowMs: Long): Boolean {
        val quick = lastStallAt == Long.MIN_VALUE || nowMs - lastStallAt > windowMs
        lastStallAt = nowMs
        return quick
    }

    /** Neuer Sender: frühere Hänger gehören zum alten. */
    @Synchronized
    fun reset() { lastStallAt = Long.MIN_VALUE }
}

/**
 * Schneller Wiederanlauf nach einer Bildlücke mitten im Programm.
 *
 * Bisher wartete Media3 nach jedem Hänger, bis wieder der volle Nachlade-Vorrat da war (Mittel: 4 s). Live-Sender
 * kommen aber nur in Echtzeit nach: 4 s sammeln hieß 4 s stehendes Bild – auch wenn schon 1–2 s im Vorrat lagen
 * (Aziz' Protokoll 22.11.: DAS ERSTE 3 s Lücke bei 1,8 s Vorrat, 4 s bei leerem Vorrat; SIXX 5 s bei 1,0 s).
 *
 * Jetzt: Beim ersten Hänger seit 3 Minuten geht es schon mit [quickMs] weiter (Mittel: 2 s). Kommt innerhalb von
 * 3 Minuten noch einer, ist die Leitung wirklich wackelig – dann gilt wieder der volle Wert, damit der größere Vorrat
 * vor dem nächsten schützt. Kürzer als bisher wird also nur gewartet, nie länger.
 *
 * Alles andere (wann geladen wird, Speichergrenze, Start-Vorrat beim Umschalten) macht unverändert [DefaultLoadControl].
 * Bewusst eine Hülle statt einer Unterklasse: der Konstruktor von DefaultLoadControl ist in jeder Media3-Version anders
 * (normale App 1.11, Vergleichs-App 1.5.1) – die hier weitergereichten Methoden gibt es in beiden gleich; nur
 * getAllocator unterscheidet sich und steht je Version in [AllocatorForwarding] (src/media3new bzw. src/media3old).
 */
@OptIn(UnstableApi::class)
class LiveLoadControl(base: LoadControl, private val quickMs: Int?, private val rebufferMs: Int) : AllocatorForwarding(base) {
    private val quickUs = quickMs?.let { it * 1_000L }
    private val policy = RebufferPolicy()
    /** gerade in einem Hänger (vom Abspiel-Thread gesetzt, vom App-Thread bei „läuft wieder“ beendet) */
    @Volatile private var inStall = false
    @Volatile private var quick = false

    /** Neuer Sender gewählt (App-Thread). */
    fun newChannel() { policy.reset(); inStall = false }

    /** Player meldet „bereit“ (App-Thread): ein Hänger ist auf jeden Fall vorbei – auch wenn Media3 ihn ohne
     * Rückfrage beendet hat (z. B. Verbindung zu Ende, alles Geladene abspielbar). */
    fun stallOver() { inStall = false }

    /** Ab wie viel Vorrat es im laufenden Hänger weitergeht (für den Wächter: lohnt sich Neuverbinden?) */
    fun resumeAtMs(): Int = quickMs?.takeIf { inStall && quick } ?: rebufferMs

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        // Start nach Umschalten/Neustart: unverändert (Start-Vorrat der Puffer-Stufe)
        if (!parameters.rebuffering) { inStall = false; return base.shouldStartPlayback(parameters) }
        if (!inStall) {
            inStall = true
            quick = quickUs != null && policy.onStall(android.os.SystemClock.elapsedRealtime())
        }
        if (base.shouldStartPlayback(parameters)) { inStall = false; return true }
        val q = quickUs
        if (quick && q != null && parameters.bufferedDurationUs >= q) {
            inStall = false
            val msg = "PUFFER_SCHNELL weiter nach Hänger mit %.1f s Vorrat (erster Hänger seit 3 min)"
                .format(java.util.Locale.GERMANY, parameters.bufferedDurationUs / 1_000_000.0)
            // wie die übrigen Einträge aus dem Abspiel-Thread über den App-Thread ins Protokoll
            android.os.Handler(android.os.Looper.getMainLooper()).post { de.aze.iptv.data.HangLog.add(msg) }
            return true
        }
        return false
    }

    // --- alles andere unverändert an den normalen LoadControl ---
    override fun onPrepared(playerId: androidx.media3.exoplayer.analytics.PlayerId) = base.onPrepared(playerId)
    override fun onStopped(playerId: androidx.media3.exoplayer.analytics.PlayerId) = base.onStopped(playerId)
    override fun onReleased(playerId: androidx.media3.exoplayer.analytics.PlayerId) = base.onReleased(playerId)
    override fun getBackBufferDurationUs(playerId: androidx.media3.exoplayer.analytics.PlayerId): Long = base.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: androidx.media3.exoplayer.analytics.PlayerId): Boolean =
        base.retainBackBufferFromKeyframe(playerId)
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean = base.shouldContinueLoading(parameters)
    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: androidx.media3.exoplayer.source.TrackGroupArray,
        trackSelections: Array<out androidx.media3.exoplayer.trackselection.ExoTrackSelection?>,
    ) = base.onTracksSelected(parameters, trackGroups, trackSelections)
}
