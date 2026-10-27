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
) {
    val context = LocalContext.current
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val error by rememberUpdatedState(onError)
    var restart by remember { mutableIntStateOf(0) }
    var failures by remember { mutableIntStateOf(0) }

    val libVlc = remember {
        LibVLC(context, arrayListOf("--http-reconnect", "--no-stats", "--audio-time-stretch"))
    }
    val mp = remember { MediaPlayer(libVlc) }
    val layout = remember { VLCVideoLayout(context) }

    DisposableEffect(Unit) {
        handle.player = mp
        mp.attachViews(layout, null, false, false)
        mp.setEventListener { ev ->
            when (ev.type) {
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
                mp.stop()
                mp.detachViews()
                mp.release()
                libVlc.release()
            }
        }
    }

    LaunchedEffect(url, cachingMs, restart) {
        if (restart > 0) {
            // Neu verbinden: 2, 4, 8, 15 … Sekunden
            delay(minOf(2_000L shl minOf(failures, 3), 15_000L))
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

    LaunchedEffect(aspect) {
        mp.videoScale = when (aspect) {
            AspectMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectMode.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectMode.WIDE -> MediaPlayer.ScaleType.SURFACE_16_9
            AspectMode.CLASSIC -> MediaPlayer.ScaleType.SURFACE_4_3
        }
    }

    AndroidView(factory = { layout }, modifier = modifier)
}
