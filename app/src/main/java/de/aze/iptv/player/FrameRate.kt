package de.aze.iptv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bildwiederholrate anpassen ("AFR"):
 * Deutsche/europäische Sender senden mit 25 bzw. 50 Bildern pro Sekunde. Läuft der Fernseher/Stick mit 60 Hz,
 * passt das nicht auf – das Bild ruckelt leicht ("unruhig"), besonders bei Lauftexten und Kameraschwenks.
 * Hier wird die echte Bildrate des Senders gemessen und der Bildschirm auf eine passende Frequenz umgestellt
 * (25/50 → 50 Hz, 30/60 → 60 Hz, 24 → 24 Hz). Das kurze Schwarzbild beim Umschalten macht der Fernseher.
 */
object FrameRate {

    fun findActivity(context: Context): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Stellt den Bildschirm auf eine zur Bildrate passende Frequenz um.
     * Gibt true zurück, wenn wirklich umgeschaltet wird – dann muss die Wiedergabe kurz neu gestartet werden,
     * weil manche Geräte (z. B. Fire TV) das Bild nach dem Umschalten sonst einfrieren lassen.
     */
    fun match(activity: Activity, fps: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || fps < 10f || fps > 125f) return false
        @Suppress("DEPRECATION")
        val display: Display = activity.windowManager.defaultDisplay
        val cur = display.mode
        val sameSize = display.supportedModes.filter {
            it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight
        }
        // Frequenz muss ein ganzzahliges Vielfaches der Bildrate sein (Toleranz für 23,976/29,97/59,94)
        fun multiple(rate: Float): Int? {
            val ratio = rate / fps
            val n = ratio.roundToInt()
            if (n < 1 || abs(ratio - n) > 0.012f * n) return null
            return n
        }
        val candidates = sameSize.mapNotNull { m -> multiple(m.refreshRate)?.let { m to it } }
        if (candidates.isEmpty()) return false
        // Bevorzugt 48 Hz und mehr (24/25-Hz-Modi flackern bei manchen Fernsehern im Menü), dann das kleinste Vielfache
        val best = candidates.minWith(
            compareBy<Pair<Display.Mode, Int>>({ if (it.first.refreshRate >= 47f) 0 else 1 }, { it.second }, { abs(it.first.refreshRate / it.second - fps) }),
        ).first
        if (best.modeId == cur.modeId) return false
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId == best.modeId) return false
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs
        return true
    }

    /** Zurück zur Standard-Frequenz des Geräts. */
    fun reset(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId != 0) {
            attrs.preferredDisplayModeId = 0
            activity.window.attributes = attrs
        }
    }
}

/**
 * Misst die tatsächliche Bildrate aus den Zeitstempeln der ersten Bilder.
 * (Viele Live-Streams geben die Bildrate nicht an – gemessen ist sie immer richtig.)
 */
@OptIn(UnstableApi::class)
class FpsProbe(private val onFps: (Float) -> Unit) : VideoFrameMetadataListener {
    private val main = Handler(Looper.getMainLooper())
    private val stamps = LongArray(48)
    @Volatile private var count = 0
    @Volatile private var done = false

    /** Neue Messung (bei jedem Senderwechsel aufrufen). */
    fun restart() {
        count = 0
        done = false
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: androidx.media3.common.Format,
        mediaFormat: android.media.MediaFormat?,
    ) {
        if (done) return
        // Angegebene Bildrate nutzen, wenn vorhanden
        if (format.frameRate > 0f && count == 0) {
            done = true
            val f = format.frameRate
            main.post { onFps(f) }
            return
        }
        if (count > 0 && presentationTimeUs <= stamps[count - 1]) return
        stamps[count++] = presentationTimeUs
        if (count < stamps.size) return
        done = true
        val deltas = (1 until stamps.size).map { stamps[it] - stamps[it - 1] }.filter { it > 0 }.sorted()
        if (deltas.isEmpty()) return
        val median = deltas[deltas.size / 2]
        val fps = 1_000_000f / median
        main.post { onFps(fps) }
    }

    fun attach(player: ExoPlayer) = player.setVideoFrameMetadataListener(this)
}

/**
 * Merkt, wenn der Bildschirm die Frequenz gewechselt hat. Danach muss die Bildfläche des Players neu angelegt werden –
 * sonst bleibt auf manchen Geräten (z. B. Fire TV) das Bild stehen, bis man ein Menü öffnet.
 */
class DisplayWatch {
    /** Ändert sich nach jedem Frequenzwechsel → Bildfläche neu anlegen. */
    var tick by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val main = Handler(Looper.getMainLooper())
    private val bump = Runnable { tick++ }

    /** Vom System gemeldete Änderung: kurz warten, bis der Wechsel fertig ist. */
    fun changed() {
        main.removeCallbacks(bump)
        main.postDelayed(bump, 500)
    }

    /** Nach eigenem Umschalten: falls das System nichts meldet, nach 3 s trotzdem neu anlegen. */
    fun expectSwitch() {
        val start = tick
        main.postDelayed({ if (tick == start) tick++ }, 3_000)
    }
}

@androidx.compose.runtime.Composable
fun rememberDisplayWatch(): DisplayWatch {
    val context = androidx.compose.ui.platform.LocalContext.current
    val watch = androidx.compose.runtime.remember { DisplayWatch() }
    androidx.compose.runtime.DisposableEffect(context) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) watch.changed()
            }
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }
    return watch
}
