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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
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
        if (crashFile.exists()) {
            val first = runCatching { crashFile.readLines().take(2).joinToString(" · ") }.getOrDefault("")
            crashFile.delete()
            if (first.isNotBlank()) app.message = "Beim letzten Mal ist die App abgestürzt: ${first.take(200)}"
        }

        // Bilder (Poster, Logos): großer Speicher-Zwischenspeicher und dauerhafter Zwischenspeicher auf dem Gerät,
        // auch wenn der Server "nicht speichern" meldet – so sind Poster beim zweiten Mal sofort da.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build() }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("bilder"))
                        .maxSizeBytes(250L * 1024 * 1024)
                        .build()
                }
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
                when (screen) {
                    Screen.PLAYER -> PlayerScreen(
                        app,
                        onOpenSettings = { screen = if (app.pinEnabled) Screen.PIN else Screen.SETTINGS },
                        onOpenVod = { series ->
                            vodSeries = series
                            screen = Screen.VOD
                        },
                    )
                    Screen.VOD -> VodScreen(app, startWithSeries = vodSeries, onExit = { screen = Screen.PLAYER }, onOpenSettings = { screen = if (app.pinEnabled) Screen.PIN else Screen.SETTINGS })
                    Screen.PIN -> PinScreen(
                        title = "PIN für die Einstellungen",
                        hint = if (app.pinIsDefault) "Standard-PIN: 0000 – bitte in den Einstellungen ändern" else null,
                        onEntered = { pin ->
                            val ok = app.checkPin(pin)
                            if (ok) screen = Screen.SETTINGS
                            ok
                        },
                        onCancel = { screen = Screen.PLAYER },
                    )
                    Screen.SETTINGS -> SettingsScreen(app, onClose = { screen = Screen.PLAYER })
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
