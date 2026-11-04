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
        de.aze.iptv.data.HangLog.init(applicationContext)
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
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (vodFast) 0.25 else 0.15).build() }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("bilder"))
                        .maxSizeBytes(if (vodFast) 1024L * 1024 * 1024 else 250L * 1024 * 1024)
                        .build()
                }
                // Standard lädt nur 5 Bilder gleichzeitig vom selben Server – Cover standen Schlange
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
                        .build()
                }
                // Bilderdienst nicht erreichbar oder Bild dort unbekannt → Original direkt vom Anbieter laden
                .components { add(CoverFallback()) }
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
                var screen by remember { mutableStateOf(if (!app.hasAnySource) Screen.SETTINGS else Screen.PLAYER) }
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
                        Screen.VOD -> VodScreen(app, startWithSeries = vodSeries, onExit = { screen = Screen.PLAYER }, onOpenSettings = openSettings)
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
        runCatching {
            val root = generateSequence(e) { it.cause }.last()
            file.writeText(
                "${root.javaClass.simpleName}: ${root.message.orEmpty()}\n" +
                    (root.stackTrace.firstOrNull { it.className.startsWith("de.aze") }?.toString() ?: "") + "\n\n" +
                    e.stackTraceToString().take(8_000),
            )
        }
        next?.uncaughtException(t, e)
    }
}

/** Wenn ein Cover über images.weserv.nl nicht kommt: dasselbe Bild direkt von der Original-Adresse laden. */
private class CoverFallback : coil.intercept.Interceptor {
    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val result = chain.proceed(chain.request)
        val data = chain.request.data as? String ?: return result
        if (result !is coil.request.ErrorResult || !data.startsWith(de.aze.iptv.ui.CoverUrls.PROXY)) return result
        val original = runCatching { android.net.Uri.parse(data).getQueryParameter("url") }.getOrNull() ?: return result
        return chain.proceed(chain.request.newBuilder().data(original).build())
    }
}
