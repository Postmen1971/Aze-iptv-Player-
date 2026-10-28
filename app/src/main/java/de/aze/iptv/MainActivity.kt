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

        app = AppController(applicationContext, lifecycleScope)
        app.start()

        // Bilder (Poster, Logos): großer Speicher-Zwischenspeicher und dauerhafter Zwischenspeicher auf dem Gerät,
        // auch wenn der Server "nicht speichern" meldet – so sind Poster beim zweiten Mal sofort da.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
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
                var screen by remember { mutableStateOf(if (!app.hasAnySource) Screen.PIN else Screen.PLAYER) }
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
                        onCancel = { if (!app.hasAnySource) finish() else screen = Screen.PLAYER },
                    )
                    Screen.SETTINGS -> SettingsScreen(app, onClose = {
                        if (!app.hasAnySource) finish() else screen = Screen.PLAYER
                    })
                }
            }
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
