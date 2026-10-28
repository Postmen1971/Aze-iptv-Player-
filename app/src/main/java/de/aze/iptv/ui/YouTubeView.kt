package de.aze.iptv.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.aze.iptv.BuildConfig
import de.aze.iptv.data.YouTube
import kotlinx.coroutines.delay

/**
 * YouTube verlangt, dass sich eingebettete Player in Apps über den Referer mit der App-Kennung ausweisen
 * (https://<App-ID>). Fehlt er, meldet YouTube Fehler 153.
 */
private val ORIGIN = "https://${BuildConfig.APPLICATION_ID}"

/**
 * Offizieller eingebetteter YouTube-Player im Vollbild.
 * Die WebView nimmt keinen Fokus an, damit die Fernbedienung weiter die App steuert.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeView(url: String, modifier: Modifier, onPlaying: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val playing by rememberUpdatedState(onPlaying)
    val failed by rememberUpdatedState(onError)
    val web = remember(url) {
        WebView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            // durchsichtig, damit ein "durchgestanztes" Video sichtbar wird; die Seite selbst ist schwarz
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            // Eingebetteten Player direkt laden – mit Referer, damit YouTube die App erkennt.
            loadUrl(YouTube.embedUrl(url, ORIGIN), mapOf("Referer" to "$ORIGIN/"))
        }
    }

    // Prüfen, ob das Video läuft oder YouTube einen Fehler zeigt.
    LaunchedEffect(web) {
        var reported = false
        while (true) {
            delay(1_000)
            web.evaluateJavascript(
                """(function(){
                    var v=document.querySelector('video');
                    if(v && v.currentTime>0 && !v.paused) return 'play';
                    var e=document.querySelector('.ytp-error, .ytp-error-content-wrap-reason');
                    if(e && e.offsetParent!==null) return 'err:'+(e.innerText||'').trim().slice(0,80);
                    return '';
                })()""",
            ) { r ->
                val v = r?.trim('"').orEmpty()
                when {
                    v == "play" -> playing()
                    v.startsWith("err:") && !reported -> {
                        reported = true
                        val txt = v.removePrefix("err:")
                        failed(if (txt.isBlank()) "YouTube: kann nicht abgespielt werden" else "YouTube: $txt")
                    }
                }
            }
        }
    }

    // Die WebView wird NICHT in die Compose-Oberfläche eingebettet, sondern direkt ins App-Fenster
    // hinter die Oberfläche gelegt. Eingebettet zeichnen manche Android-TV-Geräte (z. B. SHIELD) das Video nicht –
    // Ton ja, Bild schwarz. Die Oberfläche darüber ist an dieser Stelle durchsichtig.
    val root = remember(context) {
        de.aze.iptv.player.FrameRate.findActivity(context)?.findViewById<android.view.ViewGroup>(android.R.id.content)
    }
    DisposableEffect(web, root) {
        root?.addView(
            web, 0,
            android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT),
        )
        onDispose {
            root?.removeView(web)
            web.loadUrl("about:blank")
            web.destroy()
        }
    }
    if (root == null) AndroidView(factory = { web }, modifier = modifier)
}
