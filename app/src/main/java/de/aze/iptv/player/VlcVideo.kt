package de.aze.iptv.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.aze.iptv.data.Http
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Zugriff auf den VLC-Player von außen (Tonspur im Schnellmenü). */
class VlcHandle {
    internal var player: MediaPlayer? = null

    fun audioLabel(): String {
        val p = player ?: return "Standard"
        val tracks = runCatching { p.audioTracks }.getOrNull()?.filter { it.id >= 0 } ?: return "Standard"
        if (tracks.isEmpty()) return "Standard"
        val cur = runCatching { p.audioTrack }.getOrDefault(-1)
        val i = tracks.indexOfFirst { it.id == cur }
        val name = tracks.getOrNull(i)?.name ?: tracks.first().name
        return if (tracks.size > 1) "$name (${i + 1}/${tracks.size})" else name
    }

    fun nextAudio() {
        val p = player ?: return
        val tracks = runCatching { p.audioTracks }.getOrNull()?.filter { it.id >= 0 } ?: return
        if (tracks.size < 2) return
        val cur = runCatching { p.audioTrack }.getOrDefault(-1)
        val i = tracks.indexOfFirst { it.id == cur }
        runCatching { p.setAudioTrack(tracks[(i + 1) % tracks.size].id) }
    }
}

/** Netzwerk-Puffer für VLC je Puffer-Stufe (Millisekunden). */
fun BufferLevel.vlcCachingMs(): Int = when (this) {
    BufferLevel.SMALL -> 1_000
    BufferLevel.MEDIUM -> 1_500
    BufferLevel.LARGE -> 6_000
}

/**
 * VLC-Wiedergabe – für Sender, die Media3 nicht abspielen kann (z. B. MPEG-2, seltene Tonformate).
 * Bei Abbruch wird mit steigender Wartezeit neu verbunden.
 */
@Composable
fun VlcVideo(
    url: String,
    cachingMs: Int,
    aspect: AspectMode,
    handle: VlcHandle,
    onFirstFrame: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ton-Versatz in ms (positiv = Ton später) */
    audioDelayMs: Int = 0,
) {
    val context = LocalContext.current
    val delayMs by rememberUpdatedState(audioDelayMs)
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val error by rememberUpdatedState(onError)
    var restart by remember { mutableIntStateOf(0) }
    var failures by remember { mutableIntStateOf(0) }

    // Eine LibVLC für die ganze App: neu anlegen kostet bei jedem Senderwechsel spürbar Zeit
    val libVlc = remember { VlcEngine.get(context) }
    val mp = remember { MediaPlayer(libVlc) }
    val layout = remember { VLCVideoLayout(context) }
    /** Zeitpunkt der letzten Bewegung der Wiedergabe-Zeit (für den VLC-Wächter); 0 = noch nicht gestartet */
    val lastProgress = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    DisposableEffect(Unit) {
        handle.player = mp
        mp.attachViews(layout, null, false, false)
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> runCatching { mp.setAudioDelay(delayMs * 1000L) }
                MediaPlayer.Event.TimeChanged -> lastProgress.set(System.currentTimeMillis())
                MediaPlayer.Event.Vout -> if (ev.voutCount > 0) {
                    failures = 0
                    lastProgress.set(System.currentTimeMillis())
                    de.aze.iptv.ui.qaLog("FIRST_FRAME_VLC")
                    firstFrame()
                }
                MediaPlayer.Event.EncounteredError, MediaPlayer.Event.EndReached -> {
                    error()
                    restart++
                }
                else -> Unit
            }
        }
        onDispose {
            handle.player = null
            runCatching {
                mp.setEventListener(null)
                mp.detachViews()
            }
            // Anhalten/Freigeben kann bei Netz-Streams Sekunden dauern → nicht auf dem Haupt-Thread (sonst hängt die App).
            // Eine Warteschlange statt eines neuen Threads je Sender: beim schnellen Zappen laufen sonst mehrere
            // Freigaben gleichzeitig in VLC – das kann die App im nativen Teil abstürzen lassen.
            VlcEngine.release(mp)
        }
    }

    LaunchedEffect(url, cachingMs, restart) {
        if (restart > 0) {
            // Neu verbinden: erst schnell (Anbieter mit nur 1 Verbindung geben die alte verzögert frei), dann seltener
            val steps = longArrayOf(2_000, 4_000, 8_000, 15_000)
            delay(steps[minOf(failures, steps.size - 1)])
            failures++
        }
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=$cachingMs")
        media.addOption(":live-caching=$cachingMs")
        media.addOption(":http-user-agent=${Http.USER_AGENT}")
        mp.media = media
        media.release()
        lastProgress.set(0L)
        mp.play()
    }

    // VLC-Wächter: Bild war schon da, aber 8 s bewegt sich die Wiedergabe-Zeit nicht mehr (hängt/puffert ohne Daten)
    // → neu verbinden. VLC meldet so einen Hänger sonst nie als Fehler.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val t = lastProgress.get()
            if (t > 0 && System.currentTimeMillis() - t > 8_000) {
                lastProgress.set(0L)
                de.aze.iptv.ui.qaLog("WATCHDOG vlc")
                error()
                restart++
            }
        }
    }

    LaunchedEffect(audioDelayMs) { runCatching { mp.setAudioDelay(audioDelayMs * 1000L) } }

    // Drehen (Hoch-/Querformat): VLC muss die neue Bildfläche kennen, sonst bleibt das Bild verzerrt
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    LaunchedEffect(aspect, orientation) {
        runCatching { mp.updateVideoSurfaces() }
        mp.aspectRatio = if (aspect == AspectMode.CINEMA) "47:20" else null
        mp.videoScale = when (aspect) {
            AspectMode.FIT, AspectMode.CINEMA -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectMode.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectMode.WIDE -> MediaPlayer.ScaleType.SURFACE_16_9
            AspectMode.CLASSIC -> MediaPlayer.ScaleType.SURFACE_4_3
        }
    }

    AndroidView(factory = { layout }, modifier = modifier)
}


/** Gemeinsame LibVLC-Instanz (wird beim ersten VLC-Sender angelegt und bleibt bestehen). */
object VlcEngine {
    @Volatile private var instance: LibVLC? = null

    /** Ein einziger Hintergrund-Thread, der alte Player nacheinander anhält und freigibt. */
    private val releaser = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "vlc-release").apply { isDaemon = true }
    }

    fun release(mp: MediaPlayer) {
        releaser.execute {
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
    }

    fun get(context: android.content.Context): LibVLC = instance ?: synchronized(this) {
        instance ?: LibVLC(context.applicationContext, arrayListOf("--no-stats", "--audio-time-stretch"))
            .also { instance = it }
    }
}
