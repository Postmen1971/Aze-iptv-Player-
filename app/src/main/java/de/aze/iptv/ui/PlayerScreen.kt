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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import de.aze.iptv.data.StalkerClient
import de.aze.iptv.data.YouTube
import androidx.compose.runtime.key
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.BufferLevel
import de.aze.iptv.player.FpsProbe
import de.aze.iptv.player.FrameRate
import de.aze.iptv.player.rememberDisplayWatch
import de.aze.iptv.player.awaitChange
import de.aze.iptv.player.PlaybackConfig
import de.aze.iptv.player.TrackHelper
import de.aze.iptv.player.PlayerEngine
import de.aze.iptv.player.VlcHandle
import de.aze.iptv.player.VlcVideo
import de.aze.iptv.player.vlcCachingMs
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
fun PlayerScreen(app: AppController, onOpenSettings: () -> Unit, onOpenVod: (series: Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val level = app.bufferLevel

    // Bei geänderter Puffer-Stufe wird der Player neu aufgebaut.
    val tunnel = app.tunneling
    val player = remember(level, tunnel) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            // Audio-Fokus: bei Anrufen/anderen Apps leiser bzw. Pause, wie es Android erwartet
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(PlaybackConfig.loadControl(context, level))
            // Leitung von Anfang an gut einschätzen: schneller Start, aber gleich mit hoher Qualitätsstufe
            .setBandwidthMeter(
                androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
                    .setInitialBitrateEstimate(8_000_000L)
                    .build(),
            )
            .build()
            .apply {
                playWhenReady = true
                // Frequenz wählt die App selbst (siehe FrameRate) – nicht zusätzlich der Player
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                // Tunneling: Bild direkt an den Video-Chip des Geräts
                (trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)?.let { ts ->
                    ts.setParameters(ts.buildUponParameters().setTunnelingEnabled(tunnel))
                }
            }
    }

    // Bildwiederholrate an den Sender anpassen
    val activity = remember(context) { FrameRate.findActivity(context) }
    val afrOn = app.afr
    // Umschalten wie bei TiviMate: Sender anhalten → Frequenz umstellen → warten, bis der Fernseher fertig ist →
    // Sender neu starten. (Umschalten mitten in der Wiedergabe lässt auf SHIELD/Fire TV das Bild einfrieren.)
    val displayWatch = rememberDisplayWatch()
    var restartTick by remember { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    val probe = remember(player) {
        FpsProbe { fps ->
            val act = activity ?: return@FpsProbe
            if (!app.afr || switching) return@FpsProbe
            val mode = FrameRate.target(act, fps) ?: return@FpsProbe
            switching = true
            player.stop()
            scope.launch {
                val before = displayWatch.tick
                FrameRate.apply(act, mode)
                displayWatch.awaitChange(before, 6_000)
                delay(1_500) // HDMI braucht nach dem Wechsel noch einen Moment
                switching = false
                restartTick++
            }
        }.also { it.attach(player) }
    }
    // Wächter: läuft der Sender, aber es kommen 5 s lang keine neuen Bilder, wird er neu gestartet (höchstens 3×).
    var watchdogRestarts by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) {
        var last = -1L
        var still = 0
        while (true) {
            delay(1_000)
            val f = probe.frames
            val active = !switching && player.playbackState == Player.STATE_READY && player.playWhenReady &&
                player.videoFormat != null
            if (active && f == last) still++ else still = 0
            last = f
            if (still >= 5 && watchdogRestarts < 3) {
                still = 0
                watchdogRestarts++
                restartTick++
            }
        }
    }
    LaunchedEffect(afrOn) { if (!afrOn) activity?.let { FrameRate.reset(it) } }

    var noPicture by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(false) }
    var quickMenu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var ytError by remember { mutableStateOf<String?>(null) }
    // Fußball-Videotext (SkorText) als Ebene über dem Programm
    var teletext by remember { mutableStateOf(false) }
    var football by remember { mutableStateOf(false) }
    var teletextWeb by remember { mutableStateOf<android.webkit.WebView?>(null) }
    // Zurück beim Fernsehen: erst die Senderliste zeigen (weiter: Gruppen, dann App beenden)
    androidx.activity.compose.BackHandler(enabled = !overlay && !quickMenu && !search && !teletext && !football) { overlay = true }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var digitJob by remember { mutableStateOf<Job?>(null) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var okLong by remember { mutableStateOf(false) }
    // Abspiel-Adresse (bei Portal + MAC erst beim Umschalten angefordert)
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var relinkTick by remember { mutableIntStateOf(0) }
    var startedKey by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val vlcHandle = remember { VlcHandle() }
    val curCh = app.current?.channel
    // VLC statt Media3: wenn so eingestellt, oder automatisch für Sender, die Media3 nicht schafft.
    val useVlc = curCh != null && !YouTube.isYouTube(curCh.url) &&
        (app.playerEngine == PlayerEngine.VLC || (app.playerEngine == PlayerEngine.AUTO && curCh.key in app.vlcFallback))

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                noPicture = false
                retries = 0
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Manche Live-Streams "enden" einfach (Verbindung vom Server getrennt): neu verbinden.
                if (state == Player.STATE_ENDED && player.currentMediaItem != null) {
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        delay(1_000)
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                noPicture = true
                retryJob?.cancel()
                // Automatisch: Format-/Decoder-Fehler oder wiederholte Abbrüche → diesen Sender mit VLC abspielen.
                val key = app.current?.channel?.key
                val group = error.errorCode / 1000
                if (app.playerEngine == PlayerEngine.AUTO && key != null && (group == 3 || group == 4 || retries >= 2)) {
                    player.stop()
                    app.markVlc(key)
                    return
                }
                // Portal-Links laufen ab: nach kurzer Pause einen neuen Link anfordern.
                if (key != null && StalkerClient.isStalker(key) &&
                    error.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                ) {
                    retryJob = scope.launch {
                        delay(minOf(2_000L shl minOf(retries, 3), 15_000L))
                        retries++
                        relinkTick++
                    }
                    return
                }
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

    // App im Hintergrund (Home-Taste, Bildschirmschoner): Wiedergabe stoppen, beim Zurückkommen neu laden.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    var inBackground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        var stopped = false
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stopped = true
                    inBackground = true
                    retryJob?.cancel()
                    player.stop()
                }
                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    inBackground = false
                    resumeTick++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val currentKey = app.current?.channel?.key
    // YouTube-App: nur beim Wählen des Senders öffnen, nicht erneut beim Zurückkommen in die App
    var startedYt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentKey) { watchdogRestarts = 0 }
    LaunchedEffect(currentKey, player, resumeTick, useVlc, relinkTick, restartTick) {
        val ch = app.current?.channel ?: return@LaunchedEffect
        retryJob?.cancel()
        if (startedKey != ch.key) retries = 0
        probe.restart()
        startedKey = ch.key
        noPicture = true
        ytError = null
        resolvedUrl = null
        // Altes Programm sofort anhalten, damit beim schnellen Durchschalten nichts nachläuft.
        player.stop()
        if (YouTube.isYouTube(ch.url)) {
            // YouTube läuft in einem eigenen Player; Media3 leeren.
            player.clearMediaItems()
            if (app.youtubeExternal && startedYt != ch.key) {
                // Nach kurzem Warten (schnelles Durchschalten) in der offiziellen YouTube-App öffnen
                delay(600)
                startedYt = ch.key
                val ok = runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(YouTube.watchUrl(ch.url)))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                ytError = if (ok) "In der YouTube-App geöffnet" else "YouTube-App ist nicht installiert"
            }
            return@LaunchedEffect
        }
        startedYt = null
        if (useVlc) player.clearMediaItems()
        // Kurz warten: wer mehrfach schnell ▲/▼ drückt, lädt nur den Sender, bei dem er stehen bleibt.
        // (Ein neuer Tastendruck bricht diese Wartezeit ab.)
        delay(220)
        val url = try {
            app.resolveStreamUrl(ch.url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ytError = "Portal: Sender nicht verfügbar"
            return@LaunchedEffect
        }
        resolvedUrl = url
        if (useVlc) return@LaunchedEffect
        // Bevorzugte Tonsprache und Untertitel-Wahl gelten für jeden neuen Sender.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguage(app.audioLanguage)
            // Sofort die beste Qualitätsstufe statt vorsichtig hochzuschalten
            .setForceHighestSupportedBitrate(app.forceHighest)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !app.subtitlesOn)
            .build()
        val builder = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (url.contains(".mpd", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_MPD)
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

    LaunchedEffect(overlay, quickMenu, search, teletext) {
        if (!overlay && !quickMenu && !search) runCatching { focus.requestFocus() }
    }

    // YouTube: Manche Geräte (z. B. Fire TV) zeigen das Video der WebView auf einer Ebene HINTER der App
    // ("durchgestanzt"). Dann muss alles darüber durchsichtig sein – sonst Ton ohne Bild.
    val ytActive = curCh != null && YouTube.isYouTube(curCh.url) && !inBackground
    DisposableEffect(ytActive) {
        val w = activity?.window
        if (ytActive && w != null) {
            w.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        onDispose {
            if (ytActive && w != null) {
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                w.setFormat(android.graphics.PixelFormat.OPAQUE)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (ytActive) Color.Transparent else Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                // Videotext offen: alle Tasten gehen an den Videotext.
                if (teletext) {
                    if (e.type == KeyEventType.KeyDown) {
                        val k = Teletext.mapKey(code)
                        val w = teletextWeb
                        if (k != null && w != null) Teletext.sendKey(w, k) { teletext = false }
                        else if (code == AndroidKeyEvent.KEYCODE_BACK) teletext = false
                    }
                    return@onKeyEvent true
                }
                // Ziffern in der Senderliste: Liste schließen und direkt wählen (wie bei Enigma2).
                if (overlay && e.type == KeyEventType.KeyDown &&
                    (code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 || code in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9)
                ) {
                    overlay = false
                    typeDigit(if (code <= AndroidKeyEvent.KEYCODE_9) code - AndroidKeyEvent.KEYCODE_0 else code - AndroidKeyEvent.KEYCODE_NUMPAD_0)
                    return@onKeyEvent true
                }
                if (overlay || quickMenu || search) return@onKeyEvent false
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
                                val yt = app.current?.channel?.url?.takeIf { YouTube.isYouTube(it) }
                                when {
                                    digits.isNotEmpty() -> commitDigits()
                                    // YouTube-Sender, der nicht eingebettet laufen darf: OK öffnet ihn in der YouTube-App
                                    yt != null && ytError != null -> {
                                        val ok = runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(YouTube.watchUrl(yt)))
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }.isSuccess
                                        if (!ok) ytError = "YouTube-App ist nicht installiert"
                                    }
                                    else -> app.playPrevious()
                                }
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
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        app.playPrevious(); true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AndroidKeyEvent.KEYCODE_TV_INPUT -> {
                        overlay = true; true
                    }
                    // Farbtasten (falls vorhanden): Rot Senderliste, Grün Schnellmenü, Gelb Suche, Blau Einstellungen
                    AndroidKeyEvent.KEYCODE_PROG_RED -> {
                        overlay = true; true
                    }
                    AndroidKeyEvent.KEYCODE_PROG_GREEN -> {
                        quickMenu = true; true
                    }
                    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> {
                        search = true; true
                    }
                    AndroidKeyEvent.KEYCODE_PROG_BLUE -> {
                        onOpenSettings(); true
                    }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO,
                    AndroidKeyEvent.KEYCODE_CAPTIONS -> {
                        quickMenu = true; true
                    }
                    AndroidKeyEvent.KEYCODE_TV_TELETEXT -> {
                        teletext = true; true
                    }
                    AndroidKeyEvent.KEYCODE_SEARCH -> {
                        search = true; true
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
        val ytUrl = app.current?.channel?.url?.takeIf { YouTube.isYouTube(it) }
        if (ytUrl != null) {
            // Im Hintergrund wird der YouTube-Player entfernt, damit kein Ton weiterläuft.
            if (!inBackground && !app.youtubeExternal)
            key(ytUrl) {
                YouTubeView(
                    url = ytUrl,
                    modifier = Modifier.fillMaxSize(),
                    onPlaying = { noPicture = false },
                    onError = { ytError = "$it · OK = in YouTube-App öffnen" },
                )
            }
        } else if (useVlc) {
            val vlcUrl = resolvedUrl
            if (!inBackground && vlcUrl != null) {
                key(currentKey, vlcUrl) {
                    VlcVideo(
                        url = vlcUrl,
                        cachingMs = level.vlcCachingMs(),
                        aspect = aspect,
                        handle = vlcHandle,
                        onFirstFrame = { noPicture = false },
                        onError = {
                            noPicture = true
                            // Portal-Link abgelaufen? Neuen anfordern.
                            if (currentKey != null && StalkerClient.isStalker(currentKey) && retries < 5) {
                                retries++
                                scope.launch { delay(3_000); relinkTick++ }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else AndroidView(
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
            digits.isNotEmpty() -> { { Text(digits, color = Palette.Highlight, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            notFound != null -> { { Text(notFound ?: "", color = Palette.Live, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            ytError != null && !overlay && !search -> { { Text(ytError ?: "", color = Palette.Live, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
            noPicture && cur != null && !overlay && !search && !teletext -> {
                {
                    Row {
                        Text("${cur.number}", color = Palette.Highlight, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
            val openSettings = {
                overlay = false
                onOpenSettings()
            }
            val openSearch = {
                overlay = false
                search = true
            }
            ChannelOverlay(
                app = app, onClose = { overlay = false }, onOpenSettings = openSettings, onSearch = openSearch,
                onVod = { overlay = false; onOpenVod(it) },
                onExitApp = { activity?.finish() },
                onFootball = { overlay = false; football = true },
            )
        }

        if (search) {
            SearchOverlay(app = app, onClose = { search = false })
        }

        if (football) {
            FootballGuide(app = app, onClose = { football = false })
        }

        if (teletext) {
            TeletextView(
                modifier = Modifier.fillMaxSize(),
                onReady = { teletextWeb = it },
                onClose = { teletext = false },
            )
        }

        if (quickMenu) {
            // tracksVersion lesen, damit die Werte nach einem Spurwechsel neu angezeigt werden.
            @Suppress("UNUSED_VARIABLE") val v = tracksVersion
            // Reihenfolge von links: Fußball (SkorText), Bild, Ton, Player, Puffer, dann Wechsel zu Filme/Serien/Einstellungen.
            val items = listOf(
                QuickItem("Fußball", "SkorText") {
                    quickMenu = false
                    teletext = true
                },
                QuickItem("Spiele", "im TV") {
                    quickMenu = false
                    football = true
                },
                QuickItem("Bild", aspect.label) {
                    val all = AspectMode.entries
                    app.updateAspect(all[(all.indexOf(aspect) + 1) % all.size])
                },
                QuickItem("Ton", if (useVlc) vlcHandle.audioLabel() else TrackHelper.currentLabel(player, C.TRACK_TYPE_AUDIO, "Standard")) {
                    if (useVlc) vlcHandle.nextAudio()
                    else TrackHelper.nextAudio(player)?.language?.let { app.updateAudioLanguage(it) }
                    tracksVersion++
                },
                QuickItem(
                    "Player",
                    when {
                        app.playerEngine != PlayerEngine.AUTO -> app.playerEngine.label
                        useVlc -> "VLC"
                        else -> "Media3"
                    },
                ) {
                    // Automatisch-Modus: diesen Sender zwischen Media3 und VLC umschalten.
                    val k = app.current?.channel?.key
                    if (app.playerEngine == PlayerEngine.AUTO && k != null) app.toggleVlc(k)
                },
                QuickItem("Puffer", level.label) {
                    val all = BufferLevel.entries
                    app.updateBuffer(all[(all.indexOf(level) + 1) % all.size])
                },
                QuickItem("Wechseln", "Filme") {
                    quickMenu = false
                    onOpenVod(false)
                },
                QuickItem("Wechseln", "Serien") {
                    quickMenu = false
                    onOpenVod(true)
                },
                QuickItem("Wechseln", "Einstellungen") {
                    quickMenu = false
                    onOpenSettings()
                },
            )
            QuickMenu(items = items, onClose = { quickMenu = false })
        }
    }
}
