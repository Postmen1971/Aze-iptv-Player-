package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
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
import de.aze.iptv.data.KeyAction
import de.aze.iptv.data.NumberedChannel
import de.aze.iptv.data.RemoteKey
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
fun PlayerScreen(
    app: AppController,
    onOpenSettings: () -> Unit,
    onOpenVod: (series: Boolean) -> Unit = {},
    /**
     * false = Einstellungen/PIN liegen darüber. Das Programm läuft weiter (beim Zurückkommen ist das Bild sofort da),
     * aber der Player holt sich dann weder Fokus noch Zurück-Taste.
     */
    active: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val level = app.bufferLevel

    // Bei geänderter Puffer-Stufe wird der Player neu aufgebaut.
    val tunnel = app.tunneling
    // Ton-Bild-Versatz nur einbauen, wenn er auch genutzt wird (sonst exakt der Standard-Player)
    val syncUsed = app.audioDelay != 0 || app.audioDelays.isNotEmpty()
    // „Ton später“ braucht mehr Bild im Vorrat (in 0,5-s-Stufen, damit der Player nicht bei jeder Kleinigkeit neu entsteht)
    // Vorrat nach dem GRÖSSTEN eingestellten Versatz – sonst entstand der Player bei jedem Senderwechsel neu
    // (Sender mit eigenem Versatz), und das kostete jedes Mal Decoder-Neustart und eine kurze Blockade.
    val maxLead = (app.audioDelay + (app.audioDelays.values.maxOrNull() ?: 0).coerceAtLeast(0)).coerceIn(0, 3_000)
    val audioLeadMs = ((maxLead + 499) / 500) * 500
    val http = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            // Live-Sender schicken laufend Daten: 10 s Stille heißt Verbindung tot (vorher 20 s Standbild)
            .setReadTimeoutMs(10_000)
    }
    // Nahtloses Neuverbinden, wenn der Anbieter den Stream beendet (siehe LiveSource)
    val live = remember { de.aze.iptv.player.LiveSource(context, http) }
    /** Zeitpunkt des letzten Senderwechsels (0 = keiner offen) – für die Messung der Umschaltzeit */
    val zapStartedAt = remember { longArrayOf(0L) }
    /** Beim Neuverbinden das letzte Bild stehen lassen statt Schwarz (beim Senderwechsel aus) */
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }
    // muss VOR player.stop() gesetzt sein – darum direkt an der Bildfläche, nicht über den Compose-Zustand
    fun keepFrame(on: Boolean) { playerViewRef[0]?.setKeepContentOnPlayerReset(on) }
    val player = remember(level, tunnel, syncUsed, audioLeadMs) {
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                (if (syncUsed) de.aze.iptv.player.SyncRenderersFactory(context) else DefaultRenderersFactory(context)).setEnableDecoderFallback(true),
            )
            // Audio-Fokus: bei Anrufen/anderen Apps leiser bzw. Pause, wie es Android erwartet
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            // Nur auf Handys/Tablets bei abgezogenem Kopfhörer pausieren. Auf dem Fernseher meldet Android das auch,
            // wenn HDMI beim Umschalten der Bildwiederholrate kurz den Ton trennt – dann blieb das Bild stehen.
            .setHandleAudioBecomingNoisy(!de.aze.iptv.player.FrameRate.isTv(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(PlaybackConfig.loadControl(context, level, audioLeadMs))
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
                // finally: auch bei Abbruch darf „Umschalten“ nie hängen bleiben – sonst startet kein Sender mehr
                try {
                    val before = displayWatch.tick
                    FrameRate.apply(act, mode)
                    displayWatch.awaitChange(before, 8_000)
                    delay(app.afrDelay * 1_000L) // Fernseher/HDMI braucht nach dem Wechsel noch einen Moment
                } finally {
                    switching = false
                    restartTick++
                }
            }
        }.also { it.attach(player) }
    }
    // „Immer 50 Hz“ einschalten bzw. beim Ausschalten zurück zur Standard-Frequenz – immer wie oben:
    // anhalten → umschalten → warten → neu starten (nie mitten in der Wiedergabe, das friert auf SHIELD/Fire TV ein)
    val afr50On = app.afr50
    LaunchedEffect(afrOn, afr50On) {
        val act = activity ?: return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return@LaunchedEffect
        val mode: Int = when {
            afr50On -> FrameRate.target50(act) ?: return@LaunchedEffect
            !afrOn && act.window.attributes.preferredDisplayModeId != 0 -> 0
            else -> return@LaunchedEffect
        }
        switching = true
        app.displaySettleUntil = System.currentTimeMillis() + 15_000
        try {
            player.stop()
            val before = displayWatch.tick
            if (mode == 0) FrameRate.reset(act) else FrameRate.apply(act, mode)
            displayWatch.awaitChange(before, 8_000)
            delay(app.afrDelay * 1_000L)
        } finally {
            app.displaySettleUntil = 0
            switching = false
            restartTick++
        }
    }
    // Wächter gegen stehendes Bild:
    //  1. „läuft“, aber 6 s kein neues Bild – auch wenn der Ton weiterläuft
    //  2. „puffert“ und 8 s kommen GAR KEINE Daten mehr (Verbindung tot) → neu verbinden, statt bis zur
    //     Zeitüberschreitung zu warten. Beim Senderstart erst, wenn nach 10 s noch kein Bild da ist.
    //  3. Puffern, solange noch Daten kommen, wird NIE unterbrochen (das verursachte früher die Neustart-Schleife).
    //  Gibt nie ganz auf, wird aber immer ruhiger: sofort, dann frühestens nach 5 s, 15 s, 30 s, danach jede Minute.
    //  Nach 60 s flüssigem Bild beginnt die Zählung von vorn. Keine Neustarts, solange z. B. Alexa den Ton hat.
    var watchdogRestarts by remember { mutableIntStateOf(0) }
    var diagText by remember { mutableStateOf("") }
    /** Seit dem letzten (Neu-)Start schon ein Bild gezeigt? (vom Player gemeldet, klappt auch mit Tunneling) */
    var firstFrameSeen by remember { mutableStateOf(false) }
    /** Zeitpunkt des letzten Starts (prepare) */
    var preparedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        var lastFrames = -1L
        var lastPos = -1L
        var lastBuffered = -1L
        var frozen = 0
        var starved = 0
        var jump = 0
        var healthy = 0
        var nextAllowed = 0L
        // Bildlücke: Sekunden, in denen Bild laufen sollte, aber keins kam (fürs Hänger-Protokoll)
        var gap = 0
        var gapInfo = ""
        var gapKey: String? = null
        while (true) {
            delay(1_000)
            val now = System.currentTimeMillis()
            val f = probe.frames
            val pos = player.currentPosition
            val buffered = player.bufferedPosition
            // Pausiert, obwohl Live-TV laufen soll (z. B. nach HDMI-Umschaltung)? Sofort weiter.
            if (!switching && player.playbackState != Player.STATE_IDLE && !player.playWhenReady) {
                player.playWhenReady = true
            }
            // Bei Tunneling meldet das Gerät keine einzelnen Bilder – dann gilt die laufende Zeit
            val pictureMoving = if (app.tunneling) lastPos >= 0 && pos != lastPos else lastFrames >= 0 && f != lastFrames
            val hasVideo = player.videoFormat != null
            val unsuppressed = player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            val ready = player.playbackState == Player.STATE_READY && player.playWhenReady && unsuppressed
            val buffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
            val sinceStart = if (preparedAt > 0) now - preparedAt else 0L

            frozen = if (!switching && ready && hasVideo && firstFrameSeen && !pictureMoving) frozen + 1 else 0
            // Zeitsprung im Stream: Uhr/Ton läuft, Vorrat da, aber kein neues Bild (Bilder „noch nicht dran“) → nach 3 s neu
            jump = if (!switching && !app.tunneling && player.isPlaying && hasVideo && firstFrameSeen &&
                lastFrames >= 0 && f == lastFrames && lastPos >= 0 && pos != lastPos && buffered - pos > 1_000
            ) jump + 1 else 0
            starved = if (!switching && buffering && buffered == lastBuffered && (firstFrameSeen || sinceStart >= 10_000)) starved + 1 else 0
            if (firstFrameSeen && pictureMoving && !buffering) healthy++ else if (!pictureMoving) healthy = 0
            if (healthy >= 60) { healthy = 0; watchdogRestarts = 0; nextAllowed = 0 }
            val stateName = when (player.playbackState) {
                Player.STATE_READY -> if (player.isPlaying) "läuft" else "bereit"
                Player.STATE_BUFFERING -> "puffert"
                Player.STATE_IDLE -> "angehalten"
                else -> "Ende"
            }
            val snap = "$stateName · Vorrat %.1f s · Uhr %s · Ton %s".format(
                (buffered - pos).coerceAtLeast(0) / 1000f, if (pos != lastPos) "läuft" else "steht",
                if (player.audioFormat != null) "ja" else "–",
            )
            val key = app.current?.channel?.key
            if (key != gapKey) { gap = 0; gapKey = key }
            if (!switching && firstFrameSeen && player.playWhenReady && !pictureMoving &&
                player.playbackState != Player.STATE_IDLE
            ) {
                if (gap == 0) gapInfo = snap
                gap++
            } else {
                if (gap >= 1 && pictureMoving) qaLog("LUECKE ${gap}s ${app.current?.channel?.name} | Beginn: $gapInfo")
                gap = 0
            }

            if (app.showDiag) {
                @Suppress("DEPRECATION")
                val hz = activity?.windowManager?.defaultDisplay?.refreshRate ?: 0f
                val state = when (player.playbackState) {
                    Player.STATE_READY -> if (player.isPlaying) "läuft" else "bereit"
                    Player.STATE_BUFFERING -> "puffert"
                    Player.STATE_IDLE -> "angehalten"
                    else -> "Ende"
                }
                diagText = "%.2f Hz · Bilder +%d/s · Zeit %s · %s · Vorrat %.1f s · Ton %s%s".format(
                    hz, if (lastFrames < 0) 0 else (f - lastFrames), if (pos != lastPos) "läuft" else "steht", state,
                    (buffered - pos).coerceAtLeast(0) / 1000f,
                    if (player.audioFormat != null) "ja" else "–",
                    if (switching) " · Umschalten …" else if (watchdogRestarts > 0) " · Neustarts $watchdogRestarts" else "",
                )
            }
            // Nur im Test-Build: Zustand jede Sekunde ins Protokoll (zur Auswertung des Emulator-Tests)
            if (de.aze.iptv.BuildConfig.TEST_HOOKS && preparedAt > 0) {
                qaLog(
                    "TICK st=${player.playbackState} pwr=${player.playWhenReady} sup=${player.playbackSuppressionReason} " +
                        "img=${if (lastFrames < 0) 0 else f - lastFrames} pos=$pos buf=$buffered first=$firstFrameSeen " +
                        "frozen=$frozen starved=$starved",
                )
            }
            lastFrames = f
            lastPos = pos
            lastBuffered = buffered
            // Läuft schon (erstes Bild war da) und puffert 5 s ohne neue Daten → neu. Beim Start weiterhin 8 s Geduld.
            val starvedLimit = if (firstFrameSeen) 5 else 8
            if ((jump >= 2 || frozen >= 6 || starved >= starvedLimit) && now >= nextAllowed) {
                qaLog("WATCHDOG ${if (jump >= 2) "jump" else if (frozen >= 6) "frozen" else "starved"} ${app.current?.channel?.name} | $snap")
                // Leicht neu verbinden (gleiche Adresse, letztes Bild bleibt). Nur Portal-Links (laufen ab) holen eine neue Adresse.
                val light = starved < starvedLimit || (key != null && !StalkerClient.isStalker(key))
                frozen = 0
                starved = 0
                jump = 0
                healthy = 0
                val pause = longArrayOf(5_000, 15_000, 30_000, 60_000)
                nextAllowed = now + pause[minOf(watchdogRestarts, pause.size - 1)]
                watchdogRestarts++
                if (light) {
                    // Verbindung ist da, nur das Bild hängt: direkt neu synchronisieren – ohne Senderwechsel-Ablauf,
                    // ohne Namenseinblendung, letztes Bild bleibt stehen
                    keepFrame(true)
                    player.stop()
                    preparedAt = System.currentTimeMillis()
                    player.prepare()
                } else restartTick++
            }
        }
    }

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
    /** Fußball: TV-Plan mit Vorschau offen → Bild klein oben rechts */
    var footballPreview by remember { mutableStateOf(false) }
    // 1 = Ton-Bild-Synchron für den Sender, 2 = EPG zuordnen
    var tool by remember { mutableStateOf(0) }
    // „Fußball heute“: Liste der gefundenen Sender rechts, bleibt beim Durchschalten offen
    var matchZap by remember { mutableStateOf<Pair<String, List<NumberedChannel>>?>(null) }
    // Multi-View: mehrere Sender gleichzeitig (der normale Player pausiert so lange)
    var multiView by remember { mutableStateOf(false) }
    var mvSecond by remember { mutableStateOf<NumberedChannel?>(null) }
    // Nur im Test-Build: Multi-View direkt mit zwei Sendern öffnen
    LaunchedEffect(app.lineup) {
        if (app.testMultiView && app.lineup.all.size >= 2) {
            app.testMultiView = false
            // letzter Sender der Liste (im Test: „Test 4 Abbruch“ – Server trennt nach 15 s)
            mvSecond = app.lineup.all.firstOrNull { it.channel.name.contains("Abbruch") }
                ?: app.lineup.all.lastOrNull { it.channel.key != app.current?.channel?.key }
            multiView = true
        }
    }
    // Senderliste direkt in der Gruppenübersicht öffnen (Taste mit Aktion „Gruppen“)
    var overlayGroups by remember { mutableStateOf(false) }
    var overlayFocused by remember { mutableStateOf(false) }
    var overlayKick by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlay) { if (!overlay) overlayFocused = false }
    // Tablet/Handy: Leiste mit großen Knöpfen (Tippen ins Bild zeigt/versteckt sie)
    val touchDevice = remember(context) { !de.aze.iptv.player.FrameRate.isTv(context) }
    var touchBar by remember { mutableStateOf(false) }
    var touchTick by remember { mutableIntStateOf(0) }
    var teletextWeb by remember { mutableStateOf<android.webkit.WebView?>(null) }
    // SkorText lädt noch → Hinweis in der Bildmitte („SkorText startet …“)
    var skorLoading by remember { mutableStateOf(false) }
    // aus „Fußball heute“ im Menü der Filme: hier öffnen
    LaunchedEffect(app.pendingFootball) { if (app.pendingFootball) { app.pendingFootball = false; football = true } }
    LaunchedEffect(Unit) { if (app.testSkorText) { app.testSkorText = false; delay(3_000); teletext = true; skorLoading = true } }
    // Versteckter Videotext rechnet nicht weiter im Hintergrund – bleibt aber 10 Minuten bereit (zweites Öffnen sofort)
    LaunchedEffect(teletext) {
        if (!teletext && teletextWeb != null) qaLog("SKOR_CLOSE")
        teletextWeb?.let { if (teletext) it.onResume() else it.onPause() }
        if (!teletext && teletextWeb != null) {
            delay(10 * 60_000L)
            val w = teletextWeb
            teletextWeb = null
            runCatching { w?.loadUrl("about:blank"); w?.destroy() }
        }
    }
    // SkorText-WebView erst beim Verlassen des Players freigeben
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                teletextWeb?.loadUrl("about:blank")
                teletextWeb?.destroy()
            }
        }
    }
    // Zurück beim Fernsehen: erst die Senderliste zeigen (weiter: Gruppen, dann App beenden)
    androidx.activity.compose.BackHandler(enabled = active && !multiView && !overlay && !quickMenu && !search && !teletext && !football && matchZap == null && tool == 0) { overlay = true }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var digitJob by remember { mutableStateOf<Job?>(null) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var okLong by remember { mutableStateOf(false) }
    var arrowLong by remember { mutableStateOf(false) }
    // welche Pfeiltaste hier gedrückt wurde – das Loslassen zählt nur dann (sonst öffnete z. B. das Loslassen von ▶,
    // mit dem die Senderliste gerade geschlossen wurde, die Liste gleich wieder)
    var arrowDown by remember { mutableIntStateOf(0) }
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
    // Ton-Bild-Versatz: allgemein + dieser Sender (wirkt sofort, auch während der Wiedergabe)
    val syncMs = app.effectiveDelay(curCh?.key)
    androidx.compose.runtime.SideEffect { de.aze.iptv.player.AudioSync.setMs(syncMs) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameSeen = true
                qaLog("FIRST_FRAME ${app.current?.channel?.name}")
                // Umschaltzeit: vom Senderwechsel bis zum ersten Bild (für Emulator-Messung und Hänger-Protokoll)
                if (zapStartedAt[0] > 0) {
                    qaLog("ZAPTIME ${android.os.SystemClock.elapsedRealtime() - zapStartedAt[0]} ${app.current?.channel?.name}")
                    zapStartedAt[0] = 0
                }
                noPicture = false
                if (ytError?.startsWith("Anbieter antwortet") == true) ytError = null
                retries = 0
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Manche Live-Streams "enden" einfach (Verbindung vom Server getrennt): neu verbinden.
                if (state == Player.STATE_ENDED && player.currentMediaItem != null) {
                    qaLog("ENDED ${app.current?.channel?.name}")
                    retryJob?.cancel()
                    // Anbieter hat den Stream beendet: nach 0,3 s neu verbinden (stop + prepare, wie im Multi-View)
                    retryJob = scope.launch {
                        delay(300)
                        keepFrame(true)
                        player.stop()
                        player.prepare()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                qaLog("ERROR ${error.errorCodeName} ${app.current?.channel?.name}")
                noPicture = true
                retryJob?.cancel()
                // Automatisch: Format-/Decoder-Fehler oder wiederholte Abbrüche → diesen Sender mit VLC abspielen.
                val key = app.current?.channel?.key
                val group = error.errorCode / 1000
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                if (app.playerEngine == PlayerEngine.AUTO && key != null && (group == 3 || group == 4 || (retries >= 2 && http == null && group != 2))) {
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
                // Viele Anbieter erlauben nur 1 Verbindung gleichzeitig und geben die alte erst nach ein paar
                // Sekunden frei. Beim schnellen Vorwärts-Zappen lehnt der Server den neuen Sender dann ab (403/429/509 …),
                // obwohl er eigentlich geht. Darum gleich nach dem Umschalten schnell und mehrfach neu versuchen.
                if (http != null && retries < 12) ytError = "Anbieter antwortet mit Fehler ${http.responseCode} – neuer Versuch …"
                retryJob = scope.launch {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        // Zu weit hinter "live" gefallen: direkt an den Live-Punkt springen.
                        player.seekToDefaultPosition()
                    } else {
                        // Neu verbinden: erst schnell (0,7 · 1 · 1,5 · 2 · 3 s), dann seltener bis 15 s.
                        // Abgelehnte Verbindung: etwas schneller; sonst ruhiger, damit der Anbieter nicht überrannt wird
                        val steps = if (http != null) longArrayOf(1_500, 2_500, 4_000, 8_000, 15_000)
                        else longArrayOf(2_000, 4_000, 8_000, 15_000)
                        delay(steps[minOf(retries, steps.size - 1)])
                        retries++
                    }
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        val detachLive = live.attach(
            player,
            onRejoin = { qaLog("REJOIN ${app.current?.channel?.name}") },
            onSwitch = { qaLog("TRANSITION ${app.current?.channel?.name}") },
        )
        onDispose {
            retryJob?.cancel()
            detachLive()
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
    LaunchedEffect(currentKey) { watchdogRestarts = 0; CoverUrls.lastZap = System.currentTimeMillis() }
    // Sendername beim Umschalten (Einstellung „3 Sekunden mit Sendung“)
    var zapInfo by remember { mutableStateOf(false) }
    // Infoleiste per Taste (Einstellungen → Bedienung → Tastenbelegung): zeigen bzw. wieder ausblenden
    var infoForced by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(infoTick) {
        if (infoTick == 0 || !infoForced) return@LaunchedEffect
        delay(app.infoSeconds * 1_000L + 2_000L)
        infoForced = false
    }
    LaunchedEffect(currentKey) {
        if ((app.nameDisplay == 1 || app.nameDisplay == 3) && currentKey != null) {
            zapInfo = true
            delay(app.infoSeconds * 1_000L)
            zapInfo = false
        }
    }
    LaunchedEffect(currentKey, player, resumeTick, useVlc, relinkTick, restartTick, multiView) {
        val ch = app.current?.channel ?: return@LaunchedEffect
        // Multi-View offen: eigener Player je Bild – der normale Player gibt Decoder und Verbindung frei
        if (multiView) {
            retryJob?.cancel()
            player.stop()
            return@LaunchedEffect
        }
        retryJob?.cancel()
        if (startedKey != ch.key) {
            retries = 0
            zapStartedAt[0] = android.os.SystemClock.elapsedRealtime()
        }
        // neuer Sender: altes Bild nicht stehen lassen; gleicher Sender (Neustart): letztes Bild halten
        keepFrame(startedKey == ch.key)
        startedKey = ch.key
        noPicture = true
        ytError = null
        resolvedUrl = null
        // Altes Programm sofort anhalten, damit beim schnellen Durchschalten nichts nachläuft.
        player.stop()
        // erst NACH dem Anhalten zurücksetzen – sonst zählen Bilder des alten Senders für den neuen
        firstFrameSeen = false
        probe.restart()
        // App im Hintergrund (Home-Taste): nichts starten, sonst läuft der Ton hinter anderen Apps
        if (inBackground) return@LaunchedEffect
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
        // Bildschirm schaltet gerade die Frequenz um: erst danach starten
        while (switching || System.currentTimeMillis() < app.displaySettleUntil) delay(200)
        val url = try {
            app.resolveStreamUrl(ch.url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ytError = "Portal: Sender nicht verfügbar"
            return@LaunchedEffect
        }
        if (inBackground) return@LaunchedEffect
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
        // Abspielgeschwindigkeit fest 1,0: Media3 regelt bei Live-HLS sonst zwischen 0,97× und 1,03× nach,
        // um den Abstand zu „live“ zu halten – auf dem Fernseher sind das doppelte/ausgelassene Bilder (Ruckeln)
        val liveCfg = MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f)
        level.liveOffsetMs?.let { liveCfg.setTargetOffsetMs(it) }
        builder.setLiveConfiguration(liveCfg.build())
        player.setMediaSource(live.create(builder.build()))
        // Live-TV läuft immer – auch falls der Player zuvor (z. B. vom System) pausiert wurde
        player.playWhenReady = true
        preparedAt = System.currentTimeMillis()
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

    LaunchedEffect(active, overlay, quickMenu, search, teletext, football, matchZap, tool, multiView) {
        if (active && !multiView && !overlay && !quickMenu && !search && !football && matchZap == null && tool == 0) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
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

    /** Aktion einer (frei belegbaren) Taste ausführen. */
    fun perform(a: KeyAction) {
        when (a) {
            KeyAction.NONE -> Unit
            KeyAction.PREVIOUS -> app.playPrevious()
            KeyAction.LIST -> { overlayGroups = false; overlay = true }
            KeyAction.GROUPS -> { overlayGroups = true; overlay = true }
            KeyAction.QUICK -> quickMenu = true
            KeyAction.NEXT_CH -> app.step(+1)
            KeyAction.PREV_CH -> app.step(-1)
            KeyAction.SEARCH -> search = true
            KeyAction.FOOTBALL -> football = true
            KeyAction.SKORTEXT -> { teletext = true; skorLoading = teletextWeb == null }
            KeyAction.MOVIES -> onOpenVod(false)
            KeyAction.SERIES -> onOpenVod(true)
            KeyAction.SETTINGS -> onOpenSettings()
            KeyAction.INFO -> { infoForced = !infoForced; infoTick++; qaLog("INFO_BAR ${if (infoForced) "an" else "aus"}") }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (ytActive) Color.Transparent else Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                // Senderliste offen, aber ohne Fokus (z. B. nach Touch-Bedienung): ▲/▼ holt den Fokus in die Liste
                if (overlay && !overlayFocused && e.type == KeyEventType.KeyDown &&
                    (e.key == androidx.compose.ui.input.key.Key.DirectionUp || e.key == androidx.compose.ui.input.key.Key.DirectionDown ||
                        e.key == androidx.compose.ui.input.key.Key.DirectionLeft || e.key == androidx.compose.ui.input.key.Key.DirectionRight ||
                        e.key == androidx.compose.ui.input.key.Key.DirectionCenter || e.key == androidx.compose.ui.input.key.Key.Enter)
                ) { overlayKick++; qaLog("ROOT_KEY kick=$overlayKick"); return@onPreviewKeyEvent true }
                false
            }
            .onKeyEvent { e ->
                // Einstellungen/PIN liegen darüber: keine Tasten an den Player (sonst unsichtbares Umschalten)
                if (!active || multiView) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                // Videotext offen: alle Tasten gehen an den Videotext.
                if (teletext) {
                    // Während SkorText noch startet: nur Zurück zählt (sonst landeten ungeduldige OK-Drücke in der Seite)
                    if (skorLoading) {
                        if (e.type == KeyEventType.KeyDown && code == AndroidKeyEvent.KEYCODE_BACK) { teletext = false; skorLoading = false }
                        return@onKeyEvent true
                    }
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
                // Offene Fenster (Senderliste, Schnellmenü, Suche, Fußball heute) bekommen die Tasten selbst
                if (overlay || quickMenu || search || football || matchZap != null || tool != 0) return@onKeyEvent false
                val isOk = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER ||
                    code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

                if (isOk) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                okLong = false
                            } else if (!okLong && digits.isEmpty()) {
                                okLong = true
                                perform(app.keyAction(RemoteKey.OK_LONG))
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
                                    else -> perform(app.keyAction(RemoteKey.OK_SHORT))
                                }
                            }
                            okLong = false
                        }
                    }
                    return@onKeyEvent true
                }
                // Pfeiltasten mit eigener Belegung für „lang gedrückt“: kurz = beim Loslassen, lang = nach ~0,5 s
                val arrow = when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> RemoteKey.UP to RemoteKey.UP_LONG
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> RemoteKey.DOWN to RemoteKey.DOWN_LONG
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> RemoteKey.LEFT to RemoteKey.LEFT_LONG
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> RemoteKey.RIGHT to RemoteKey.RIGHT_LONG
                    else -> null
                }
                if (arrow != null && app.keyAction(arrow.second) != KeyAction.NONE) {
                    when (e.type) {
                        KeyEventType.KeyDown -> if (e.nativeKeyEvent.repeatCount == 0) { arrowLong = false; arrowDown = code; qaLog("ARROW down $code") }
                            else if (!arrowLong && arrowDown == code) { arrowLong = true; perform(app.keyAction(arrow.second)) }
                        KeyEventType.KeyUp -> { if (!arrowLong && arrowDown == code) perform(app.keyAction(arrow.first)); arrowLong = false; arrowDown = 0 }
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
                    // Frei belegbare Tasten (Einstellungen → Bedienung)
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> { perform(app.keyAction(RemoteKey.UP)); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { perform(app.keyAction(RemoteKey.DOWN)); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { perform(app.keyAction(RemoteKey.LEFT)); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { perform(app.keyAction(RemoteKey.RIGHT)); true }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> { perform(app.keyAction(RemoteKey.MENU)); true }
                    AndroidKeyEvent.KEYCODE_PROG_RED -> { perform(app.keyAction(RemoteKey.RED)); true }
                    AndroidKeyEvent.KEYCODE_PROG_GREEN -> { perform(app.keyAction(RemoteKey.GREEN)); true }
                    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> { perform(app.keyAction(RemoteKey.YELLOW)); true }
                    AndroidKeyEvent.KEYCODE_PROG_BLUE -> { perform(app.keyAction(RemoteKey.BLUE)); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { perform(app.keyAction(RemoteKey.REWIND)); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { perform(app.keyAction(RemoteKey.FORWARD)); true }
                    // Feste Tasten
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { app.step(+1); true }
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { app.step(-1); true }
                    AndroidKeyEvent.KEYCODE_GUIDE, AndroidKeyEvent.KEYCODE_TV_INPUT -> { overlayGroups = false; overlay = true; true }
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> { app.playPrevious(); true }
                    AndroidKeyEvent.KEYCODE_CAPTIONS -> { quickMenu = true; true }
                    AndroidKeyEvent.KEYCODE_TV_TELETEXT -> {
                        teletext = true; skorLoading = teletextWeb == null; true
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
            .focusProperties { canFocus = active }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    // Nur wenn kein Fenster offen ist – sonst würde ein Tipp daneben ein zweites Fenster öffnen
                    onTap = {
                        if (!overlay && !quickMenu && !search && !football && !teletext && matchZap == null && tool == 0) {
                            if (touchDevice) { touchBar = !touchBar; touchTick++ } else overlay = true
                        }
                    },
                    onDoubleTap = { if (!overlay && !quickMenu && !search && !football && !teletext && matchZap == null && tool == 0) app.playPrevious() },
                    onLongPress = { if (!overlay && !search && !football && !teletext && matchZap == null && tool == 0) quickMenu = true },
                )
            }
            .pointerInput(Unit) {
                // Wischen: hoch = nächster Sender, runter = vorheriger, nach rechts = Senderliste
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { change, amount -> dx += amount.x; dy += amount.y; change.consume() },
                    onDragEnd = {
                        if (!overlay && !quickMenu && !search && !football && !teletext && matchZap == null && tool == 0) {
                            val limit = 60.dp.toPx()
                            when {
                                kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy < -limit -> app.step(+1)
                                kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy > limit -> app.step(-1)
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > limit -> overlay = true
                            }
                        }
                    },
                )
            },
    ) {
        // Hochformat (Tablet/Handy in der Hand): immer seitengerecht einpassen, nie strecken oder abschneiden
        val portrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val aspect = app.aspectMode.let { if (portrait && (it == AspectMode.STRETCH || it == AspectMode.ZOOM)) AspectMode.FIT else it }
        val preview = football && footballPreview
        val previewModifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp).width(360.dp).aspectRatio(16f / 9f)
        val videoModifier = if (preview) previewModifier else if (aspect.ratio != null) {
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
        // Vorschau: Rest des Bildschirms ruhig dunkel
        if (preview) Box(Modifier.fillMaxSize().background(Palette.Ground))
        if (multiView) {
            // Multi-View zeigt eigene Bilder – hier kein zweiter Player (YouTube/VLC)
        } else if (ytUrl != null) {
            // Im Hintergrund wird der YouTube-Player entfernt, damit kein Ton weiterläuft.
            // Gleiche WebView für alle YouTube-Sender (kein key) – das Umschalten geht so deutlich schneller
            if (!inBackground && !app.youtubeExternal) {
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
                        audioDelayMs = syncMs,
                        onFirstFrame = { noPicture = false },
                        modifier = if (preview) previewModifier else Modifier.fillMaxSize(),
                        onError = {
                            noPicture = true
                            // Portal-Link abgelaufen? Neuen anfordern.
                            if (currentKey != null && StalkerClient.isStalker(currentKey) && retries < 5) {
                                retries++
                                scope.launch { delay(3_000); relinkTick++ }
                            }
                        },
                    )
                }
            }
        } else key(if (afrOn || afr50On) displayWatch.surfaceTick else 0) {
            // Nach einem Frequenzwechsel die Bildfläche neu anlegen: manche Geräte lassen die alte stehen (Standbild)
            AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { view ->
                view.player = player
                playerViewRef[0] = view
                view.resizeMode = resize
            },
            onRelease = { view -> view.player = null; if (playerViewRef[0] === view) playerViewRef[0] = null },
        )
        }

        if (app.lineup.all.isEmpty()) {
            if (app.loading) {
                Text("Senderliste wird geladen …", color = Palette.TextMuted, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            } else if (active) {
                // Noch keine Playlist: klare Knöpfe statt Sackgasse
                val btn = remember { FocusRequester() }
                LaunchedEffect(active) { if (active) { withFrameNanos { }; runCatching { btn.requestFocus() } } }
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Noch keine Sender. Bitte in den Einstellungen eine Playlist hinzufügen.", color = Palette.TextSoft, fontSize = 18.sp)
                    TvButton("Einstellungen öffnen", modifier = Modifier.focusRequester(btn), onClick = onOpenSettings)
                    TvButton("App beenden", onClick = { activity?.finish() })
                }
            }
        }

        // Kleine Anzeige oben rechts: getippte Ziffern, sonst Nummer + Name nur bei schwarzem Bild.
        val cur = app.current
        val corner: (@Composable () -> Unit)? = when {
            digits.isNotEmpty() -> { { Text(digits, color = Palette.Highlight, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            notFound != null -> { { Text(notFound ?: "", color = Palette.Live, fontSize = 30.sp, fontWeight = FontWeight.Bold) } }
            ytError != null && !overlay && !search -> { { Text(ytError ?: "", color = Palette.Live, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
            cur != null && !overlay && !search && !teletext && !football && matchZap == null && tool == 0 &&
                when (app.nameDisplay) { 0 -> noPicture; 1 -> zapInfo || noPicture; 3 -> noPicture && !zapInfo; else -> false } -> {
                {
                    Column {
                        Row {
                            Text("${cur.number}", color = Palette.Highlight, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("  ${cur.channel.name}", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (app.nameDisplay == 1) {
                            app.epg.now(cur.channel)?.let { p ->
                                Text(p.title, color = Palette.EpgText, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            else -> null
        }
        if (app.showDiag && !overlay) {
            // eigener kleiner Baustein: die sekündlich neue Messzeile zeichnet nur sich selbst neu, nicht den ganzen Player
            DiagLine({ diagText }, Modifier.align(Alignment.TopStart))
        }
        // Infoleiste unten beim Umschalten (Einstellung „Infoleiste unten“)
        if (((app.nameDisplay == 3 && zapInfo) || infoForced) && cur != null && !overlay && !quickMenu && !search && !teletext && !football && matchZap == null && tool == 0) {
            val vf = player.videoFormat
            ZapBar(app, cur, vf?.let { f -> if (f.height > 0) "${f.height}p" else null }, Modifier.align(Alignment.BottomCenter))
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
                startInGroups = overlayGroups,
                focusKick = overlayKick,
                onFocusInside = { overlayFocused = it },
            )
        }

        if (search) {
            SearchOverlay(app = app, onClose = { search = false })
        }

        // Tablet/Handy: Knopfleiste (oben) und große Zapp-Knöpfe (rechts); verschwindet nach 6 s
        if (touchDevice && touchBar && !overlay && !quickMenu && !search && !football && !teletext && matchZap == null && tool == 0) {
            LaunchedEffect(touchTick) {
                delay(6_000)
                touchBar = false
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton("☰ Sender", onClick = { touchBar = false; overlay = true })
                    TvButton("⚽ Heute", onClick = { touchBar = false; football = true })
                    TvButton("⋯ Menü", onClick = { touchBar = false; quickMenu = true })
                    TvButton("⚙ Einstellungen", onClick = { touchBar = false; onOpenSettings() })
                    TvButton("✕ Beenden", danger = true, onClick = { activity?.finish() })
                }
                Text("Wischen ▲▼ = Sender wechseln · nach rechts = Senderliste · doppelt tippen = letzter Sender", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TvButton("  ▲  ", onClick = { touchTick++; app.step(+1) })
                TvButton("  ▼  ", onClick = { touchTick++; app.step(-1) })
            }
        }

        if (football) {
            FootballGuide(
                app = app, onClose = { football = false },
                onWatch = { title, list -> matchZap = title to list },
                onPreview = { footballPreview = it },
            )
        }

        // Fußball heute: gefundene Sender rechts – bleibt beim Durchschalten offen, Zurück schließt
        matchZap?.let { (title, list) ->
            MatchZapPanel(
                app = app, title = title, channels = list,
                modifier = Modifier.align(Alignment.CenterEnd),
                onClose = { matchZap = null },
            )
        }

        if (teletext) {
            // Erst den Hinweis zeigen, DANN die Web-Anzeige anlegen (das erste Anlegen dauert auf dem Gerät einige Sekunden –
            // vorher war in dieser Zeit nichts zu sehen und das Schnellmenü wirkte eingefroren)
            LaunchedEffect(Unit) {
                if (teletextWeb != null) return@LaunchedEffect
                skorLoading = true
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                val created = Teletext.create(context, onReady = { skorLoading = false }, onCrash = {
                    // Absturz der Anzeige: nur den Videotext schließen und beim nächsten Mal neu anlegen.
                    val dead = teletextWeb
                    teletext = false
                    teletextWeb = null
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { dead?.destroy() } }, 500)
                })
                if (created == null) {
                    qaLog("SKOR_FAIL Anzeige ließ sich nicht anlegen")
                    app.message = "SkorText konnte nicht starten – bitte gleich nochmal versuchen"
                    skorLoading = false
                    teletext = false
                } else teletextWeb = created
            }
            teletextWeb?.let { TeletextView(web = it, modifier = Modifier.fillMaxSize()) }
            if (skorLoading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.background(Palette.PanelDark.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(horizontal = 34.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("SkorText startet …", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("einen Moment bitte – Zurück bricht ab", color = Palette.TextMuted, fontSize = 14.sp)
                    }
                }
            }
        }

        if (tool != 0 && curCh != null) {
            if (tool == 1) AudioSyncPanel(app, curCh, onClose = { tool = 0 })
            else EpgMapDialog(app, curCh, onClose = { tool = 0 })
        }

        if (quickMenu) {
            // tracksVersion lesen, damit die Werte nach einem Spurwechsel neu angezeigt werden.
            @Suppress("UNUSED_VARIABLE") val v = tracksVersion
            // Reihenfolge von links: Fußball (SkorText), Bild, Ton, Player, Puffer, dann Wechsel zu Filme/Serien/Einstellungen.
            val items = listOfNotNull(
                QuickItem("Fußball", "SkorText") {
                    quickMenu = false
                    teletext = true
                    skorLoading = teletextWeb == null
                },
                QuickItem("Spiele", "heute") {
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
                if (app.multiViewOn) QuickItem("Multi-View", "Beta · 2 Sender") {
                    quickMenu = false
                    mvSecond = null
                    multiView = true
                } else null,
                QuickItem("Infoleiste", if (app.nameDisplay == 3) "an" else "aus") {
                    app.updateNameDisplay(if (app.nameDisplay == 3) 0 else 3)
                },
                QuickItem("Lippensynchron", msLabel(app.channelDelay(curCh?.key))) {
                    quickMenu = false
                    if (curCh != null) tool = 1
                },
                QuickItem(
                    "Programm (EPG)",
                    when {
                        curCh == null -> "–"
                        app.epg.isManual(curCh) -> "eigene Zuordnung"
                        app.epg.now(curCh) != null -> "vorhanden"
                        else -> "zuordnen"
                    },
                ) {
                    quickMenu = false
                    if (curCh != null) tool = 2
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

        if (multiView) {
            MultiViewScreen(
                app = app,
                start = app.current,
                startSecond = mvSecond,
                onExit = { ch ->
                    multiView = false
                    // Sender des gewählten Bildes läuft im Vollbild weiter
                    if (ch != null && ch.channel.key != app.current?.channel?.key) app.play(ch) else restartTick++
                },
            )
        }
    }
}


/** Kurze Zeilen fürs Protokoll (logcat, Kennung AzeQA) – der automatische Emulator-Test liest sie mit. */
internal fun qaLog(msg: String) {
    android.util.Log.i("AzeQA", msg)
    // auch ins Hänger-Protokoll auf dem Gerät (ohne die sekündlichen Messzeilen)
    if (!msg.startsWith("TICK") && !msg.startsWith("MV_TICK") && !msg.startsWith("BEAT")) de.aze.iptv.data.HangLog.add(msg)
}

@Composable
private fun DiagLine(text: () -> String, modifier: Modifier) {
    val t = text()
    if (t.isEmpty()) return
    Text(
        t, color = Color.White, fontSize = 13.sp,
        modifier = modifier.padding(12.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Infoleiste unten beim Umschalten (Einstellung „Infoleiste unten“, Aurora-Design) */
@Composable
internal fun ZapBar(app: AppController, cur: de.aze.iptv.data.NumberedChannel, quality: String?, modifier: Modifier) {
    val now = System.currentTimeMillis()
    val prog = app.epg.now(cur.channel)
    val next = app.epg.next(cur.channel)
    val hm = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY) }
    Row(
        modifier
            .fillMaxWidth()
            // näher an den unteren Bildrand
            .padding(start = 32.dp, end = 32.dp, bottom = 10.dp)
            .background(Palette.PanelDark.copy(alpha = 0.92f), RoundedCornerShape(22.dp))
            .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${cur.number}", color = Palette.Highlight, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(cur.channel.name, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    "● LIVE", color = Palette.Live, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.background(Palette.Live.copy(alpha = 0.16f), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                quality?.let {
                    Text(
                        it, color = Palette.TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Palette.Raised, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (prog != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(prog.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Text(hm.format(java.util.Date(prog.start)) + " – " + hm.format(java.util.Date(prog.stop)), color = Palette.TextMuted, fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(5.dp).background(Palette.Raised, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(prog.progress(now)).height(5.dp).background(Palette.Highlight, RoundedCornerShape(3.dp)))
                }
                next?.let { Text("Danach " + hm.format(java.util.Date(it.start)) + " " + it.title, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1) }
            } else {
                Text("Keine Programminfo", color = Palette.TextMuted, fontSize = 13.sp)
            }
        }
        Text(hm.format(java.util.Date(now)), color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}
