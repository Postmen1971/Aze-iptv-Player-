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
    BufferLevel.MEDIUM -> 2_500
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

    DisposableEffect(Unit) {
        handle.player = mp
        mp.attachViews(layout, null, false, false)
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> runCatching { mp.setAudioDelay(delayMs * 1000L) }
                MediaPlayer.Event.Vout -> if (ev.voutCount > 0) {
                    failures = 0
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
            // Anhalten/Freigeben kann bei Netz-Streams Sekunden dauern → nicht auf dem Haupt-Thread (sonst hängt die App)
            Thread({
                runCatching {
                    mp.stop()
                    mp.release()
                }
            }, "vlc-release").start()
        }
    }

    LaunchedEffect(url, cachingMs, restart) {
        if (restart > 0) {
            // Neu verbinden: erst schnell (Anbieter mit nur 1 Verbindung geben die alte verzögert frei), dann seltener
            val steps = longArrayOf(700, 1_000, 1_500, 2_000, 3_000, 5_000, 8_000, 15_000)
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
        mp.play()
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

    fun get(context: android.content.Context): LibVLC = instance ?: synchronized(this) {
        instance ?: LibVLC(context.applicationContext, arrayListOf("--http-reconnect", "--no-stats", "--audio-time-stretch"))
            .also { instance = it }
    }
}
