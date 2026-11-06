package de.aze.iptv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import de.aze.iptv.ui.Palette
import de.aze.iptv.ui.PinScreen
import de.aze.iptv.ui.VodScreen
import de.aze.iptv.ui.PlayerScreen
import de.aze.iptv.ui.SettingsScreen

private enum class Screen { PLAYER, PIN, SETTINGS, VOD }

class MainActivity : ComponentActivity() {

    private lateinit var app: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        guardMainLoop()
        de.aze.iptv.data.HangLog.init(applicationContext)
        de.aze.iptv.data.Tmdb.init(applicationContext)
        de.aze.iptv.data.CoverStore.init(applicationContext)
        de.aze.iptv.data.VodDisk.init(applicationContext)
        // KEIN Vorwärmen der Web-Anzeige im Hintergrund-Thread: Chromium bindet sich dann an diesen Thread,
        // und SkorText ließ sich danach gar nicht mehr öffnen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // Absturz-Protokoll: Ursache beim nächsten Start anzeigen (hilft beim Finden von Fehlern)
        val crashFile = filesDir.resolve("letzter_absturz.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous !is CrashLogger) Thread.setDefaultUncaughtExceptionHandler(CrashLogger(crashFile, previous))

        // Zwischenspeicher automatisch aufräumen (wie „Cache leeren“, aber ohne Logos/Playlists zu verlieren)
        if (savedInstanceState == null) de.aze.iptv.data.CacheCleaner.onStart(applicationContext)

        app = AppController(applicationContext, lifecycleScope)
        app.start()
        // Nur im Test-Build (Emulator-Test bei GitHub): Test-Playlist per Start-Parameter. Im normalen Build gibt es das nicht.
        if (BuildConfig.TEST_HOOKS) {
            // Zwischenspeicher leeren wie der Knopf in den Einstellungen (während ein Sender läuft)
            if (intent?.getBooleanExtra("aze_test_clear_cache", false) == true) {
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(8_000)
                    de.aze.iptv.data.CacheCleaner.clearAll(applicationContext)
                    de.aze.iptv.ui.qaLog("CACHE_CLEARED")
                }
            }
            if (intent?.getBooleanExtra("aze_test_multiview", false) == true) app.testMultiView = true
            if (intent?.getBooleanExtra("aze_test_listeditor", false) == true) app.testListEditor = true
            // Test-Anbieter für Filme & Serien (Xtream) hinzufügen
            intent?.getStringExtra("aze_test_xtream")?.let { url ->
                if (app.sources.none { it.url == url }) {
                    app.addSource(de.aze.iptv.data.PlaylistSource(java.util.UUID.randomUUID().toString(), "Test-VOD", de.aze.iptv.data.SourceType.XTREAM, url, "qa", "qa"))
                }
            }
            app.testOpenVod = intent?.getBooleanExtra("aze_test_vod", false) == true
            app.testSkorText = intent?.getBooleanExtra("aze_test_skortext", false) == true
            if (intent?.getBooleanExtra("aze_test_football", false) == true) app.pendingFootball = true
            // Herzschlag aus dem Haupt-Thread alle 0,25 s: Lücken > 1,5 s = App hing (prüft der Emulator-Test)
            lifecycleScope.launch { while (true) { kotlinx.coroutines.delay(250); lastBeat = android.os.SystemClock.uptimeMillis(); de.aze.iptv.ui.qaLog("BEAT ${beatNo++}") } }
            // Stockt der Haupt-Thread > 1 s: festhalten, WO er gerade hängt (zeigt der Emulator-Test als STALL)
            if (!stallWatch) {
                stallWatch = true
                val main = android.os.Looper.getMainLooper().thread
                de.aze.iptv.ui.qaLog("STALL_WATCH an")
                Thread {
                    var reported = 0L
                    while (true) {
                        Thread.sleep(200)
                        val gap = android.os.SystemClock.uptimeMillis() - lastBeat
                        if (lastBeat > 0 && gap > 1_000 && reported != lastBeat) {
                            reported = lastBeat
                            val st = main.stackTrace.filter { !it.className.startsWith("java.") && !it.className.startsWith("android.os.MessageQueue") }
                                .take(14).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                            de.aze.iptv.ui.qaLog("STALL ${gap} ms: $st")
                        }
                    }
                }.apply { isDaemon = true; name = "qa-stall" }.start()
            }
            // Lippensynchron-Wert für den Test setzen
            intent?.getIntExtra("aze_test_audio_delay", Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { app.updateAudioDelay(it) }
            // zusätzlicher Versatz für einen Sender (Schlüssel = Adresse)
            intent?.getStringExtra("aze_test_channel_delay_url")?.let { key ->
                app.updateChannelDelay(key, intent.getIntExtra("aze_test_channel_delay", 0))
            }
            intent?.getStringExtra("aze_test_playlist")?.let { url ->
                if (app.sources.none { it.url == url }) {
                    app.addSource(de.aze.iptv.data.PlaylistSource(java.util.UUID.randomUUID().toString(), "Test", de.aze.iptv.data.SourceType.M3U, url))
                }
            }
        }
        if (crashFile.exists()) {
            val first = runCatching { crashFile.readLines().take(2).joinToString(" · ") }.getOrDefault("")
            crashFile.delete()
            if (first.isNotBlank()) app.message = "Beim letzten Mal ist die App abgestürzt: ${first.take(200)}"
        }

        // Bilder (Poster, Logos): großer Speicher-Zwischenspeicher und dauerhafter Zwischenspeicher auf dem Gerät,
        // auch wenn der Server "nicht speichern" meldet – so sind Poster beim zweiten Mal sofort da.
        // „Filme & Serien bevorzugt“: größerer Zwischenspeicher (1 GB statt 250 MB), mehr Bilder im Arbeitsspeicher
        val vodFast = app.vodFast
        de.aze.iptv.ui.CoverUrls.proxy = app.coverProxy
        de.aze.iptv.ui.CoverUrls.tmdbFirst = app.coverTmdbFirst
        // ein gemeinsamer Zwischenspeicher auf dem Gerät für sichtbare und vorgeladene Cover
        val coverDisk = coil.disk.DiskCache.Builder()
            .directory(cacheDir.resolve("bilder"))
            .maxSizeBytes(if (vodFast) 1024L * 1024 * 1024 else 250L * 1024 * 1024)
            .build()
        // Vorladen im Hintergrund über eine eigene, schmale Leitung (2 gleichzeitig):
        // so stehen die sichtbaren Cover nie hinter hunderten vorgeladenen Schlange.
        de.aze.iptv.ui.CoverUrls.bg = coil.ImageLoader.Builder(this)
            .memoryCache(null as coil.memory.MemoryCache?)
            .diskCache(coverDisk)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 })
                    .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            }
            .components { add(CoverFallback()) }
            // Cover-Prüfungen (Dateien, Merkliste) nicht im Haupt-Thread
            .interceptorDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .respectCacheHeaders(false)
            .build()
        // Cover-Vorrat NIE im Haupt-Thread (Listen mit zehntausenden Einträgen + Dateiprüfungen → Hänger/Abstürze)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { de.aze.iptv.ui.runCoverStock(app, applicationContext) } }
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (vodFast) 0.25 else 0.15).build() }
                .diskCache(coverDisk)
                // Standard lädt nur 5 Bilder gleichzeitig vom selben Server – Cover standen Schlange
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 6 })
                        // hängt ein Bild, nicht ewig warten (dann Rückfall bzw. nächstes Bild)
                        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                }
                // Bilderdienst nicht erreichbar oder Bild dort unbekannt → Original direkt vom Anbieter laden
                .components { add(CoverFallback()) }
            // Cover-Prüfungen (Dateien, Merkliste) nicht im Haupt-Thread
            .interceptorDispatcher(kotlinx.coroutines.Dispatchers.IO)
                .respectCacheHeaders(false)
                .crossfade(false)
                .build(),
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.Accent,
                    onPrimary = Palette.OnAccent,
                    background = Palette.Ground,
                    surface = Palette.Panel,
                    onSurface = Palette.Text,
                ),
            ) {
                // Einstellungen sind mit PIN geschützt (Standard 0000).
                // Erster Start ohne Playlist: direkt in die Einstellungen (ohne PIN) – die App schließt sich dabei nie von selbst
                var screen by remember { mutableStateOf(if (!app.hasAnySource || app.testListEditor) Screen.SETTINGS else if (app.testOpenVod) Screen.VOD else Screen.PLAYER) }
                var vodSeries by remember { mutableStateOf(false) }
                val openSettings = { screen = if (app.pinEnabled) Screen.PIN else Screen.SETTINGS }
                // Startbildschirm mit Logo, solange die Senderliste beim Start noch lädt (mind. 1,2 s, höchstens 25 s)
                var splash by remember { mutableStateOf(app.hasAnySource) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (!splash) return@LaunchedEffect
                    val start = System.currentTimeMillis()
                    kotlinx.coroutines.delay(1_200)
                    while (System.currentTimeMillis() - start < 25_000 && (app.loading || app.lineup.all.isEmpty()) && app.hasAnySource) {
                        kotlinx.coroutines.delay(150)
                        if (!app.loading && app.lineup.all.isNotEmpty()) break
                        // keine Kopie und Laden fehlgeschlagen: nicht ewig warten
                        if (!app.loading && System.currentTimeMillis() - start > 3_000) break
                    }
                    splash = false
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    // Live-TV bleibt unter PIN und Einstellungen bestehen: kein Abbau/Neuaufbau des Players,
                    // beim Zurückkommen ist das Bild sofort da. Nur Filme & Serien (eigener Player) ersetzen ihn.
                    if (screen != Screen.VOD) {
                        PlayerScreen(
                            app,
                            onOpenSettings = openSettings,
                            onOpenVod = { series ->
                                vodSeries = series
                                screen = Screen.VOD
                            },
                            active = screen == Screen.PLAYER && !splash,
                        )
                    }
                    when (screen) {
                        Screen.PLAYER -> Unit
                        Screen.VOD -> VodScreen(
                            app, startWithSeries = vodSeries, onExit = { screen = Screen.PLAYER }, onOpenSettings = openSettings,
                            onFootball = { app.pendingFootball = true; screen = Screen.PLAYER },
                            onQuit = { finish() },
                        )
                        Screen.PIN -> CoverLayer {
                            PinScreen(
                                title = "PIN für die Einstellungen",
                                hint = if (app.pinIsDefault) "Standard-PIN: 0000 – bitte in den Einstellungen ändern" else null,
                                onEntered = { pin ->
                                    val ok = app.checkPin(pin)
                                    if (ok) screen = Screen.SETTINGS
                                    ok
                                },
                                onCancel = { screen = Screen.PLAYER },
                            )
                        }
                        Screen.SETTINGS -> CoverLayer { SettingsScreen(app, onClose = { screen = Screen.PLAYER }) }
                    }
                    // Kurzer Hinweis auf ein neues Update: einmal je Start, ca. 3 s unten rechts, ohne Fenster/Tastendruck
                    var updateHint by remember { mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(4_000)
                        while (splash) kotlinx.coroutines.delay(500)
                        val rel = runCatching { de.aze.iptv.update.Updater.check() }.getOrNull() ?: return@LaunchedEffect
                        updateHint = rel.versionName
                        kotlinx.coroutines.delay(3_000)
                        updateHint = null
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = updateHint != null,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(28.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .background(Palette.Ground.copy(alpha = 0.88f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            de.aze.iptv.ui.AzeLogo(20.dp)
                            androidx.compose.material3.Text(
                                "Update ${updateHint.orEmpty()} verfügbar · Einstellungen → App-Update",
                                color = Palette.TextSoft, fontSize = 13.sp,
                            )
                        }
                    }
                    if (splash && screen == Screen.PLAYER) CoverLayer {
                        de.aze.iptv.ui.SplashScreen(
                            status = if (app.loading) "Senderliste wird geladen …" else "Gleich geht's los …",
                            version = BuildConfig.VERSION_NAME,
                        )
                    }
                }
            }
        }
    }

    /** Android meldet knappen Speicher: Bilder im Arbeitsspeicher sofort freigeben. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}


/** Ebene über dem Live-TV: fängt alle Berührungen ab, damit kein Tippen zum Player darunter durchgeht. */
@androidx.compose.runtime.Composable
private fun CoverLayer(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) { content() }
}

/** Schreibt die Absturz-Ursache in eine Datei und gibt dann an Android weiter. */
private class CrashLogger(private val file: java.io.File, private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        // Bekannter Compose-Fehler (BringIntoViewRequester) kommt über einen Coroutine-Fehler hierher:
        // harmlos (es wird nur nicht hingescrollt) – nicht die ganze App beenden.
        if (generateSequence(e) { it.cause }.any { it is IllegalStateException && it.message?.contains("BringIntoViewRequester") == true }) {
            runCatching { de.aze.iptv.ui.qaLog("COMPOSE_BRING_INTO_VIEW abgefangen") }
            return
        }
        runCatching {
            val root = generateSequence(e) { it.cause }.last()
            file.writeText(
                "${root.javaClass.simpleName}: ${root.message.orEmpty()}\n" +
                    (root.stackTrace.firstOrNull { it.className.startsWith("de.aze") }?.toString() ?: "") + "\n\n" +
                    e.stackTraceToString().take(8_000),
            )
        }
        // auch ins Hänger-Protokoll (Einstellungen → Hilfe & Fehlersuche → Anzeigen)
        runCatching {
            val root = generateSequence(e) { it.cause }.last()
            de.aze.iptv.data.HangLog.add("ABSTURZ ${root.javaClass.simpleName}: ${root.message.orEmpty().take(120)} @ " +
                (root.stackTrace.firstOrNull { it.className.startsWith("de.aze") }?.let { "${it.fileName}:${it.lineNumber}" } ?: "?") +
                " · Speicher frei ${Runtime.getRuntime().let { r -> (r.maxMemory() - (r.totalMemory() - r.freeMemory())) / 1048576 }} MB")
        }
        next?.uncaughtException(t, e)
    }
}

/** Wenn ein Cover über images.weserv.nl nicht kommt: dasselbe Bild direkt von der Original-Adresse laden. */
/** Messwerte der Cover seit dem App-Start – stehen in Einstellungen → Filme & Serien (für ein Foto an Claude) */
object CoverStats {
    val loaded = java.util.concurrent.atomic.AtomicInteger(0)
    val totalMs = java.util.concurrent.atomic.AtomicLong(0)
    val slowest = java.util.concurrent.atomic.AtomicLong(0)
    val over3s = java.util.concurrent.atomic.AtomicInteger(0)
    val failed = java.util.concurrent.atomic.AtomicInteger(0)
    val tmdb = java.util.concurrent.atomic.AtomicInteger(0)
    val store = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var host: String = "–"
    fun text(): String {
        val n = loaded.get()
        if (n == 0 && failed.get() == 0 && store.get() == 0) return "Noch keine Cover geladen – einmal Filme oder Serien öffnen."
        return "Cover aus dem Netz: $n · Ø ${if (n > 0) totalMs.get() / n else 0} ms · langsamstes ${slowest.get()} ms · " +
            "über 3 s: ${over3s.get()} · nicht gekommen: ${failed.get()} · von TMDB: ${tmdb.get()} · aus dem Vorrat: ${store.get()} · " +
            "im Vorrat: ${de.aze.iptv.data.CoverStore.count} · Server: $host"
    }
}

private class CoverFallback : coil.intercept.Interceptor {
    private val logged = java.util.concurrent.atomic.AtomicInteger(0)
    private val slow = java.util.concurrent.atomic.AtomicInteger(0)

    private fun originalOf(data: String): String =
        if (data.startsWith(de.aze.iptv.ui.CoverUrls.PROXY))
            runCatching { android.net.Uri.parse(data).getQueryParameter("url") }.getOrNull() ?: data
        else data

    /** geladenes Cover (liegt jetzt im Bild-Zwischenspeicher) klein in den eigenen Vorrat übernehmen */
    private fun keep(ctx: android.content.Context, used: String, key: String) {
        if (de.aze.iptv.data.CoverStore.full) return
        coverKeepScope.launch {
            runCatching {
                val dc = coil.Coil.imageLoader(ctx).diskCache ?: return@runCatching
                val saved = dc.openSnapshot(used)?.use { de.aze.iptv.data.CoverStore.saveFrom(it.data.toFile(), key) } == true
                // großes Original wird nicht mehr gebraucht → Platz im Zwischenspeicher frei
                if (saved) dc.remove(used)
            }
        }
    }

    private fun ok(r: coil.request.ImageResult?) = r != null && r !is coil.request.ErrorResult

    /** Ersatz von TMDB holen und laden; null = keiner gefunden */
    private suspend fun fromTmdb(chain: coil.intercept.Interceptor.Chain, title: String, series: Boolean, provider: String?, key: String): coil.request.ImageResult? {
        val url = de.aze.iptv.data.Tmdb.poster(title, series) ?: return null
        if (provider != null) de.aze.iptv.data.Tmdb.rememberReplacement(provider, url)
        val r = chain.proceed(chain.request.newBuilder().data(url).build())
        if (ok(r)) { CoverStats.tmdb.incrementAndGet(); keep(chain.request.context, url, key) }
        if (logged.getAndIncrement() < 12) de.aze.iptv.ui.qaLog("COVER TMDB-Ersatz ${if (ok(r)) "ok" else "FEHLER"}")
        return r
    }

    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val req = chain.request
        val data = req.data as? String ?: return chain.proceed(req)
        val title = req.parameters.value<String>("title")
        val series = req.parameters.value<Boolean>("series") ?: false
        val tmdb = title != null && de.aze.iptv.data.Tmdb.enabled
        val provider = if (data == "tmdb:") null else originalOf(data)
        val key = provider ?: ("tmdb:" + (title ?: ""))
        // 1. eigener Vorrat auf dem Gerät: sofort da
        de.aze.iptv.data.CoverStore.file(key)?.takeIf { it.exists() }?.let {
            CoverStats.store.incrementAndGet()
            return chain.proceed(req.newBuilder().data(it).build())
        }
        // Anbieter hat gar kein Cover → gleich TMDB
        if (provider == null) {
            return (if (tmdb) fromTmdb(chain, title!!, series, null, key) else null)
                ?: coil.request.ErrorResult(null, req, IllegalStateException("kein Cover"))
        }
        // Schalter „TMDB zuerst“: erst TMDB, Anbieter nur, wenn TMDB nichts hat
        if (tmdb && de.aze.iptv.ui.CoverUrls.tmdbFirst) {
            fromTmdb(chain, title!!, series, null, key)?.let { if (ok(it)) return it }
            return chain.proceed(req.newBuilder().data(provider).build()).also { if (ok(it)) keep(req.context, provider, key) }
        }
        // dieses Anbieter-Cover kam schon einmal nicht → gleich den gemerkten Ersatz
        if (tmdb) de.aze.iptv.data.Tmdb.replacementFor(provider)?.let { u ->
            return chain.proceed(req.newBuilder().data(u).build()).also { if (ok(it)) keep(req.context, u, key) }
        }

        val t0 = android.os.SystemClock.elapsedRealtime()
        // mit TMDB als Ersatz: höchstens 5 s auf den Anbieter warten
        val first = if (tmdb) kotlinx.coroutines.withTimeoutOrNull(5_000) { chain.proceed(req) } else chain.proceed(req)
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        if (ms > 80) { // echter Download (nicht aus dem Zwischenspeicher)
            if (!ok(first)) CoverStats.failed.incrementAndGet()
            else { CoverStats.loaded.incrementAndGet(); CoverStats.totalMs.addAndGet(ms) }
            if (ms > CoverStats.slowest.get()) CoverStats.slowest.set(ms)
            if (ms > 3_000) CoverStats.over3s.incrementAndGet()
            runCatching { android.net.Uri.parse(provider).host }.getOrNull()?.let { CoverStats.host = it }
        }
        val viaProxy = data.startsWith(de.aze.iptv.ui.CoverUrls.PROXY)
        // die ersten Cover je Start mit Ladezeit ins Hänger-Protokoll (nur echte Downloads, nicht aus dem Zwischenspeicher)
        if (ms > 150 && logged.getAndIncrement() < 6) {
            de.aze.iptv.ui.qaLog("COVER ${if (viaProxy) "Bilderdienst" else "direkt"} $ms ms ${if (first == null) "ZU LANGSAM" else if (!ok(first)) "FEHLER" else "ok"} " +
                runCatching { android.net.Uri.parse(data).host }.getOrNull())
        }
        if (viaProxy && (!ok(first) || ms > 3_000)) {
            // Bilderdienst bei diesem Anbieter langsam oder erfolglos → für diesen Programmstart direkt laden
            if (slow.incrementAndGet() >= 3 && de.aze.iptv.ui.CoverUrls.proxy) {
                de.aze.iptv.ui.CoverUrls.proxy = false
                de.aze.iptv.ui.qaLog("COVER Bilderdienst zu langsam – lade direkt")
            }
        }
        if (ok(first)) { keep(req.context, data, key); return first!! }
        // Bilderdienst erfolglos → Original direkt vom Anbieter
        var result = first
        if (viaProxy && provider != data) {
            result = if (tmdb) kotlinx.coroutines.withTimeoutOrNull(5_000) { chain.proceed(req.newBuilder().data(provider).build()) }
                     else chain.proceed(req.newBuilder().data(provider).build())
            if (ok(result)) { keep(req.context, provider, key); return result!! }
        }
        // Anbieter kaputt oder zu langsam → TMDB
        if (tmdb) fromTmdb(chain, title!!, series, provider, key)?.let { if (ok(it)) return it }
        return result ?: coil.request.ErrorResult(null, req, java.io.IOException("Cover zu langsam"))
    }
}

/** verkleinert geladene Cover nacheinander in den Vorrat (eine Aufgabe zur Zeit, blockiert nichts) */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val coverKeepScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1),
)


@Volatile private var lastBeat = 0L
// fortlaufende Nummer: sonst fasst Android gleiche Protokollzeilen zusammen („chatty“) und es sähe nach Hängern aus
private var beatNo = 0L
@Volatile private var stallWatch = false
@Volatile private var loopGuarded = false

/**
 * Schutz vor einem bekannten Fehler in Jetpack Compose: „Expected BringIntoViewRequester to not be used before
 * parents are placed“ – tritt auf, wenn ein Eintrag den Fokus bekommt, während seine Liste gerade neu aufgebaut wird.
 * Er ist harmlos (es wird nur nicht zum Eintrag gescrollt), beendete aber die ganze App. Nur DIESER Fehler wird
 * abgefangen und protokolliert; alle anderen Fehler laufen wie gewohnt weiter.
 */
private fun guardMainLoop() {
    if (loopGuarded) return
    loopGuarded = true
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        while (true) {
            try {
                android.os.Looper.loop()
            } catch (e: IllegalStateException) {
                if (e.message?.contains("BringIntoViewRequester") != true) throw e
                de.aze.iptv.ui.qaLog("COMPOSE_BRING_INTO_VIEW abgefangen")
            }
        }
    }
}
