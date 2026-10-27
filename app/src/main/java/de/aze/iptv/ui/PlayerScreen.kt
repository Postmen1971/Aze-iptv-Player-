package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.aze.iptv.AppController
import de.aze.iptv.data.Http
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(app: AppController, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
            .apply { playWhenReady = true }
    }

    // Name + Nummer nur zeigen, solange kein Bild da ist.
    var noPicture by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(false) }
    var digitJob by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                noPicture = false
            }

            override fun onPlayerError(error: PlaybackException) {
                noPicture = true
                // Live-Streams brechen manchmal ab: einmal neu verbinden.
                scope.launch {
                    delay(3_000)
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val currentKey = app.current?.channel?.key
    LaunchedEffect(currentKey) {
        val ch = app.current?.channel ?: return@LaunchedEffect
        noPicture = true
        val builder = MediaItem.Builder().setUri(ch.url)
        if (ch.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (ch.url.contains(".mpd", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_MPD)
        player.setMediaItem(builder.build())
        player.prepare()
    }

    fun commitDigits() {
        digitJob?.cancel()
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && !app.playNumber(n)) {
            notFound = "$n ?"
            scope.launch {
                delay(1_500)
                notFound = null
            }
        }
    }

    fun typeDigit(d: Int) {
        digits = (digits + d).takeLast(4)
        digitJob?.cancel()
        digitJob = scope.launch {
            delay(2_000)
            commitDigits()
        }
    }

    LaunchedEffect(overlay) {
        if (!overlay) runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (overlay || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                when (code) {
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        typeDigit(code - AndroidKeyEvent.KEYCODE_0); true
                    }
                    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> {
                        typeDigit(code - AndroidKeyEvent.KEYCODE_NUMPAD_0); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                        app.step(+1); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        app.step(-1); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (digits.isNotEmpty()) commitDigits() else overlay = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_SETTINGS -> {
                        onOpenSettings(); true
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { overlay = true },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(false)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    this.player = player
                }
            },
        )

        if (app.lineup.all.isEmpty()) {
            Text(
                text = if (app.loading) "Senderliste wird geladen …" else "Noch keine Sender. OK drücken für die Senderliste, Menü für Einstellungen.",
                color = Palette.TextMuted,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        // Kleine Anzeige oben rechts: getippte Ziffern, sonst Nummer + Name nur bei schwarzem Bild.
        val cur = app.current
        val corner: (@Composable () -> Unit)? = when {
            digits.isNotEmpty() -> { { Text(digits, color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            notFound != null -> { { Text(notFound!!, color = Palette.Live, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            noPicture && cur != null && !overlay -> {
                {
                    Row {
                        Text("${cur.number}", color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("  ${cur.channel.name}", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> null
        }
        if (corner != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 28.dp)
                    .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) { corner() }
        }

        if (overlay) {
            ChannelOverlay(
                app = app,
                onClose = { overlay = false },
                onOpenSettings = {
                    overlay = false
                    onOpenSettings()
                },
            )
        }
    }
}
