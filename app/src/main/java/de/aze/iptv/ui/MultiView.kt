package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import de.aze.iptv.AppController
import de.aze.iptv.data.Http
import de.aze.iptv.data.NumberedChannel
import de.aze.iptv.data.YouTube
import de.aze.iptv.player.PlaybackConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Anordnung der Bilder im Multi-View. */
enum class MvLayout(val label: String, val slots: Int) {
    SIDE("Nebeneinander", 2),
    PIP("Bild im Bild", 2),
    GRID("4 Bilder", 4),
}

/**
 * Multi-View: mehrere Sender gleichzeitig (z. B. zwei Fußballspiele).
 *  ◀ ▶        Bild wählen (blauer Rahmen; nur dieses hat Ton)
 *  ▲ ▼ / CH±  im gewählten Bild umschalten, Ziffern = Direktwahl
 *  OK         Sender für das gewählte Bild aussuchen
 *  OK lang / Menü  Anordnung wechseln (nebeneinander, Bild im Bild, 4 Bilder)
 *  Zurück     Multi-View beenden – der Sender des gewählten Bildes läuft im Vollbild weiter
 * Hinweis: Jedes Bild ist eine eigene Verbindung – der Anbieter muss mehrere gleichzeitige Verbindungen erlauben.
 */
@Composable
fun MultiViewScreen(
    app: AppController,
    start: NumberedChannel?,
    startSecond: NumberedChannel? = null,
    onExit: (NumberedChannel?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf(MvLayout.SIDE) }
    val slots = remember { mutableStateListOf(start, startSecond, null, null) }
    var active by remember { mutableIntStateOf(if (start == null) 0 else if (startSecond == null) 1 else 0) }
    var picker by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var digitJob by remember { mutableStateOf<Job?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var okLong by remember { mutableStateOf(false) }
    var pickerFocusTick by remember { mutableIntStateOf(0) }
    var pickerHasFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun show(msg: String) {
        toast = msg
        scope.launch { delay(2_500); if (toast == msg) toast = null }
    }

    fun nextLayout() {
        val all = MvLayout.entries
        layout = all[(all.indexOf(layout) + 1) % all.size]
        if (active >= layout.slots) active = 0
        show(layout.label + if (layout == MvLayout.GRID) " – braucht ein starkes Gerät und 4 Verbindungen beim Anbieter" else "")
    }

    fun zap(delta: Int) {
        val all = app.lineup.all
        if (all.isEmpty()) return
        val cur = slots[active]
        val i = cur?.let { c -> all.indexOfFirst { it.channel.key == c.channel.key } } ?: -1
        slots[active] = all[if (i < 0) 0 else Math.floorMod(i + delta, all.size)]
    }

    fun commitDigits() {
        digitJob?.cancel()
        val n = digits.toIntOrNull()
        digits = ""
        val target = n?.let { app.lineup.byNumber[it] }
        if (target != null) slots[active] = target else if (n != null) show("$n ?")
    }

    BackHandler(enabled = !picker) { onExit(slots[active] ?: slots.firstOrNull { it != null }) }

    LaunchedEffect(picker) {
        if (!picker) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) { show("Multi-View · ◀ ▶ Bild wählen · OK Sender · Menü Anordnung") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                // Liste offen: Tasten gehören der Liste (▲ ▼ bewegen dort den Fokus – NICHT abfangen).
                // Nur wenn die Liste gar keinen Fokus hat, ihn zurückholen.
                if (picker) { if (!pickerHasFocus) pickerFocusTick++; return@onKeyEvent false }
                val code = e.nativeKeyEvent.keyCode
                val isOk = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER ||
                    code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                if (isOk) {
                    when (e.type) {
                        KeyEventType.KeyDown -> if (e.nativeKeyEvent.repeatCount == 0) okLong = false
                        else if (!okLong) { okLong = true; nextLayout() }
                        KeyEventType.KeyUp -> {
                            if (!okLong) { if (digits.isNotEmpty()) commitDigits() else picker = true }
                            okLong = false
                        }
                    }
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { active = Math.floorMod(active - 1, layout.slots); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { active = Math.floorMod(active + 1, layout.slots); true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> { nextLayout(); true }
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        digits = (digits + (code - AndroidKeyEvent.KEYCODE_0)).takeLast(4)
                        digitJob?.cancel()
                        digitJob = scope.launch { delay(2_000); commitDigits() }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        @Composable
        fun slot(i: Int, modifier: Modifier, texture: Boolean = false) {
            MvSlot(app, i, slots[i], active = i == active, texture = texture, modifier = modifier)
        }
        when (layout) {
            MvLayout.SIDE -> Row(
                Modifier.fillMaxSize().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slot(0, Modifier.weight(1f).aspectRatio(16f / 9f))
                slot(1, Modifier.weight(1f).aspectRatio(16f / 9f))
            }
            MvLayout.PIP -> Box(Modifier.fillMaxSize()) {
                slot(0, Modifier.fillMaxSize())
                // kleines Bild als TextureView, damit es über dem großen Bild gezeichnet wird
                slot(1, Modifier.align(Alignment.BottomEnd).padding(28.dp).fillMaxWidth(0.32f).aspectRatio(16f / 9f), texture = true)
            }
            MvLayout.GRID -> Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0..1) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slot(r * 2, Modifier.weight(1f).fillMaxHeight())
                        slot(r * 2 + 1, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }

        // Hinweise oben in der Mitte (Anordnung, Bedienung) und getippte Ziffern
        val msg = if (digits.isNotEmpty()) digits else toast
        if (msg != null) {
            Text(
                msg, color = Palette.Text, fontSize = if (digits.isNotEmpty()) 26.sp else 14.sp,
                fontWeight = if (digits.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                    .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (picker) {
            MvPicker(
                app = app,
                focusTick = pickerFocusTick,
                onFocus = { pickerHasFocus = it },
                title = "Sender für Bild ${active + 1}",
                onPick = { nc -> slots[active] = nc; picker = false },
                onClose = { picker = false },
            )
        }
    }
}

/** Ein Bild im Multi-View: eigener Player, Ton nur im gewählten Bild. */
@OptIn(UnstableApi::class)
@Composable
private fun MvSlot(app: AppController, index: Int, channel: NumberedChannel?, active: Boolean, texture: Boolean, modifier: Modifier) {
    Box(
        modifier
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(if (active) 2.5.dp else 1.dp, if (active) Palette.Highlight else Palette.Line.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(2.dp),
    ) {
        if (channel == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", color = if (active) Palette.Highlight else Palette.TextMuted, fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text(if (active) "OK = Sender wählen" else "Sender wählen", color = Palette.TextMuted, fontSize = 14.sp)
            }
        } else if (YouTube.isYouTube(channel.channel.url)) {
            Text("YouTube geht im Multi-View nicht", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            key(channel.channel.key) { MvVideo(app, index, channel, active, texture) }
        }
        if (channel != null) Text(
            "${channel.number}  ${channel.channel.name}",
            color = Palette.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Palette.Ground.copy(alpha = if (active) 0.8f else 0.55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MvVideo(app: AppController, index: Int, channel: NumberedChannel, active: Boolean, texture: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>("Lädt …") }
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(10_000)
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(PlaybackConfig.loadControl(context, app.bufferLevel))
            .build()
            .apply { playWhenReady = true }
    }
    // Ton nur im gewählten Bild
    SideEffect { player.volume = if (active) 1f else 0f }

    DisposableEffect(player) {
        var retries = 0
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                status = null
                retries = 0
                qaLog("MV_FIRST_FRAME $index ${channel.channel.name}")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) scope.launch { delay(1_000); player.prepare() }
            }

            override fun onPlayerError(error: PlaybackException) {
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                status = if (http != null) "Anbieter lehnt ab (${http.responseCode}) – erlaubt er mehrere Verbindungen?"
                else "Kein Bild – neuer Versuch …"
                qaLog("MV_ERROR $index ${error.errorCodeName}")
                scope.launch {
                    delay(minOf(3_000L * (retries + 1), 15_000L))
                    retries++
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

    // Wächter je Bild (wie beim normalen Fernsehen): Bild steht 6 s oder 8 s keine neuen Daten → neu verbinden
    val frames = remember { java.util.concurrent.atomic.AtomicLong(0) }
    DisposableEffect(player) {
        player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() }
        onDispose { }
    }
    LaunchedEffect(player) {
        var lastF = -1L; var lastBuf = -1L; var frozen = 0; var starved = 0; var started = false; var pause = 0
        while (true) {
            delay(1_000)
            val f = frames.get()
            val buf = player.bufferedPosition
            if (f > 0) started = true
            val ready = player.playbackState == Player.STATE_READY && player.videoFormat != null
            val buffering = player.playbackState == Player.STATE_BUFFERING
            frozen = if (started && ready && f == lastF) frozen + 1 else 0
            starved = if (started && buffering && buf == lastBuf) starved + 1 else 0
            lastF = f; lastBuf = buf
            if (pause > 0) { pause--; continue }
            if (frozen >= 6 || starved >= 8) {
                qaLog("MV_WATCHDOG $index ${if (frozen >= 6) "frozen" else "starved"}")
                status = "Bild hing – wird neu verbunden …"
                frozen = 0; starved = 0; pause = 10
                player.stop()
                player.prepare()
            }
        }
    }

    LaunchedEffect(player) {
        val url = try {
            app.resolveStreamUrl(channel.channel.url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Sender nicht verfügbar"
            return@LaunchedEffect
        }
        val b = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (url.contains(".mpd", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_MPD)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(app.audioLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.setMediaItem(b.build())
        player.prepare()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                de.aze.iptv.player.createPlayerView(ctx, texture).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    // Bildfläche nimmt nie den Fokus (sonst gehen Tasten der Fernbedienung ins Leere)
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
        )
        status?.let {
            Text(it, color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp))
        }
    }
}

/** Senderauswahl für ein Bild: links Gruppen, OK öffnet die Sender der Gruppe; Zurück = Gruppen bzw. schließen. */
@Composable
private fun MvPicker(
    app: AppController,
    focusTick: Int,
    title: String,
    onPick: (NumberedChannel) -> Unit,
    onClose: () -> Unit,
    onFocus: (Boolean) -> Unit = {},
) {
    val groups = app.lineup.groups.filter { it.channels.isNotEmpty() }
    var group by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (group != null) group = null else onClose() }
    val first = remember(group) { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // Fokus sicher in die Liste holen (sonst landen die Tasten nirgends und die Liste wirkt eingefroren)
    LaunchedEffect(group, focusTick) {
        repeat(30) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
            withFrameNanos { }
            if (hasFocus) return@LaunchedEffect
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            Modifier
                .onFocusChanged { hasFocus = it.hasFocus; onFocus(it.hasFocus) }
                .fillMaxHeight()
                .width(340.dp)
                .background(Palette.Ground.copy(alpha = 0.96f))
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            SectionLabel(if (group == null) "Gruppen" else groups.getOrNull(group!!)?.name ?: "", modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            val g = group
            if (g == null) {
                LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    itemsIndexed(groups) { i, gr ->
                        PickRow(
                            text = (if (gr.isFavorites) "★ " else "") + gr.name, right = gr.range,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { group = i },
                        )
                    }
                }
            } else {
                val list = groups.getOrNull(g)?.channels.orEmpty()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    itemsIndexed(list, key = { _, nc -> nc.channel.key }) { i, nc ->
                        PickRow(
                            text = nc.channel.name, left = "${nc.number}",
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { onPick(nc) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(text: String, modifier: Modifier, onClick: () -> Unit, left: String? = null, right: String? = null) {
    Focusable(modifier = modifier.fillMaxWidth(), onClick = onClick) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(if (focused) Palette.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (left != null) Text(left, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.width(44.dp))
            Text(
                text, color = if (focused) Palette.OnAccent else Palette.TextSoft, fontSize = 15.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (right != null) Text(right, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 13.sp)
        }
    }
}
