package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.aze.iptv.AppController
import de.aze.iptv.data.Http
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.BufferLevel
import de.aze.iptv.player.PlaybackConfig
import de.aze.iptv.player.TrackHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vollbild-Wiedergabe.
 *  Hoch/Runter      Sender wechseln (ohne Einblendung)
 *  0–9              Direktwahl (Nummer klein oben rechts)
 *  OK kurz          zum zuletzt geschauten Sender springen (bei getippter Nummer: sofort umschalten)
 *  OK lang / Menü   Schnellmenü: Bildformat, Tonspur, Untertitel, Puffer
 *  Links/Rechts     Senderliste
 * Touch: Tippen = Senderliste, doppelt Tippen = letzter Sender, lange Tippen = Schnellmenü.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(app: AppController, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val level = app.bufferLevel

    // Bei geänderter Puffer-Stufe wird der Player neu aufgebaut.
    val player = remember(level) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(PlaybackConfig.loadControl(context, level))
            .build()
            .apply { playWhenReady = true }
    }

    var noPicture by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(false) }
    var quickMenu by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var digitJob by remember { mutableStateOf<Job?>(null) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var okLong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                noPicture = false
                retries = 0
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onPlayerError(error: PlaybackException) {
                noPicture = true
                retryJob?.cancel()
                retryJob = scope.launch {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        // Zu weit hinter "live" gefallen: direkt an den Live-Punkt springen.
                        player.seekToDefaultPosition()
                    } else {
                        // Neu verbinden mit steigender Wartezeit: 2, 4, 8, 15, 15 … Sekunden.
                        delay(minOf(2_000L shl minOf(retries, 3), 15_000L))
                        retries++
                    }
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            retryJob?.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    val currentKey = app.current?.channel?.key
    LaunchedEffect(currentKey, player) {
        val ch = app.current?.channel ?: return@LaunchedEffect
        retryJob?.cancel()
        retries = 0
        noPicture = true
        // Bevorzugte Tonsprache und Untertitel-Wahl gelten für jeden neuen Sender.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguage(app.audioLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !app.subtitlesOn)
            .build()
        val builder = MediaItem.Builder().setUri(ch.url)
        if (ch.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (ch.url.contains(".mpd", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_MPD)
        level.liveOffsetMs?.let {
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(it).build())
        }
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

    LaunchedEffect(overlay, quickMenu) {
        if (!overlay && !quickMenu) runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (overlay || quickMenu) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val isOk = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER ||
                    code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

                if (isOk) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                okLong = false
                            } else if (!okLong && digits.isEmpty()) {
                                okLong = true
                                quickMenu = true
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (!okLong) {
                                if (digits.isNotEmpty()) commitDigits() else app.playPrevious()
                            }
                            okLong = false
                        }
                    }
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false

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
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_GUIDE -> {
                        overlay = true; true
                    }
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> {
                        app.playPrevious(); true
                    }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO,
                    AndroidKeyEvent.KEYCODE_CAPTIONS -> {
                        quickMenu = true; true
                    }
                    AndroidKeyEvent.KEYCODE_SETTINGS -> {
                        onOpenSettings(); true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { overlay = true },
                    onDoubleTap = { app.playPrevious() },
                    onLongPress = { quickMenu = true },
                )
            },
    ) {
        val aspect = app.aspectMode
        val videoModifier = if (aspect.ratio != null) {
            Modifier.align(Alignment.Center).aspectRatio(aspect.ratio)
        } else {
            Modifier.fillMaxSize()
        }
        val resize = when (aspect) {
            AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(false)
                    isFocusable = false
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resize
            },
        )

        if (app.lineup.all.isEmpty()) {
            Text(
                text = if (app.loading) "Senderliste wird geladen …" else "Noch keine Sender. Links/Rechts öffnet die Senderliste mit dem Knopf „Einstellungen“.",
                color = Palette.TextMuted,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        // Kleine Anzeige oben rechts: getippte Ziffern, sonst Nummer + Name nur bei schwarzem Bild.
        val cur = app.current
        val corner: (@Composable () -> Unit)? = when {
            digits.isNotEmpty() -> { { Text(digits, color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            notFound != null -> { { Text(notFound ?: "", color = Palette.Live, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
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

        if (quickMenu) {
            // tracksVersion lesen, damit die Werte nach einem Spurwechsel neu angezeigt werden.
            @Suppress("UNUSED_VARIABLE") val v = tracksVersion
            val items = listOf(
                QuickItem("Bild", aspect.label) {
                    val all = AspectMode.entries
                    app.updateAspect(all[(all.indexOf(aspect) + 1) % all.size])
                },
                QuickItem("Ton", TrackHelper.currentLabel(player, C.TRACK_TYPE_AUDIO, "Standard")) {
                    TrackHelper.nextAudio(player)?.language?.let { app.updateAudioLanguage(it) }
                    tracksVersion++
                },
                QuickItem("Untertitel", TrackHelper.currentLabel(player, C.TRACK_TYPE_TEXT, "Keine")) {
                    val chosen = TrackHelper.nextSubtitle(player)
                    app.updateSubtitles(chosen != null)
                    tracksVersion++
                },
                QuickItem("Puffer", level.label) {
                    val all = BufferLevel.entries
                    app.updateBuffer(all[(all.indexOf(level) + 1) % all.size])
                },
            )
            QuickMenu(items = items, onClose = { quickMenu = false })
        }
    }
}
