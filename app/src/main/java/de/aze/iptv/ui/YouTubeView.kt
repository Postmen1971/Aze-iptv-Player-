package de.aze.iptv.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
    // Absturz der Web-Anzeige (kommt bei wenig Speicher vor): neue WebView anlegen statt die App zu beenden
    var generation by remember { mutableIntStateOf(0) }
    // EINE WebView für alle YouTube-Sender: beim Umschalten wird nur die Adresse gewechselt
    // (eine neue WebView je Sender kostet spürbar Zeit und Speicher)
    val web = remember(generation) {
        WebView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            // durchsichtig, damit ein "durchgestanztes" Video sichtbar wird; die Seite selbst ist schwarz
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Seite in voller Bildschirmbreite darstellen (statt halber Breite bei Fernseher-Dichte):
            // YouTube wählt die Qualität nach der Playergröße – so bekommt es Platz für 1080p.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                @SuppressLint("NewApi")
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    // true = die App läuft weiter; die kaputte WebView wird ersetzt
                    runCatching { (view?.parent as? android.view.ViewGroup)?.removeView(view) }
                    runCatching { view?.destroy() }
                    generation++
                    return true
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    // Adresse laden – kurz warten, damit beim schnellen Durchschalten nur der letzte Sender lädt.
    // Danach beobachten: läuft das Video, zeigt YouTube einen Fehler, oder hängt das Bild?
    LaunchedEffect(web, url) {
        // alten Sender sofort stumm schalten
        runCatching { web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){v.pause();v.muted=true;}})()", null) }
        delay(250)
        web.loadUrl(YouTube.embedUrl(url, ORIGIN), mapOf("Referer" to "$ORIGIN/"))
        var reported = false
        var qualityTries = 0
        var lastTime = -1.0
        var stuckSince = 0L
        var reloads = 0
        while (true) {
            delay(if (qualityTries < 6) 1_000 else 3_000)
            // Qualität nur in den ersten Sekunden hochsetzen – ständiges Setzen lässt YouTube neu puffern (Hänger)
            val setQuality = qualityTries < 6
            val result = kotlinx.coroutines.CompletableDeferred<String>()
            runCatching {
                web.evaluateJavascript(
                    """(function(){
                        var v=document.querySelector('video');
                        try{
                          var p=document.getElementById('movie_player');
                          if($setQuality && p && p.getAvailableQualityLevels){
                            var q=p.getAvailableQualityLevels().filter(function(x){return x!=='auto';});
                            if(q.length && p.getPlaybackQuality && p.getPlaybackQuality()!==q[0]){
                              if(p.setPlaybackQualityRange) p.setPlaybackQualityRange(q[0], q[0]);
                              if(p.setPlaybackQuality) p.setPlaybackQuality(q[0]);
                            }
                          }
                        }catch(e){}
                        var e=document.querySelector('.ytp-error, .ytp-error-content-wrap-reason');
                        if(e && e.offsetParent!==null) return 'err:'+(e.innerText||'').trim().slice(0,80);
                        if(v && v.currentTime>0) return (v.paused?'pause:':'play:')+v.currentTime;
                        return '';
                    })()""",
                ) { r -> result.complete(r?.trim('"').orEmpty()) }
            }.onFailure { result.complete("") }
            val v = kotlinx.coroutines.withTimeoutOrNull(2_500) { result.await() } ?: ""
            val now = System.currentTimeMillis()
            when {
                v.startsWith("err:") -> if (!reported) {
                    reported = true
                    val txt = v.removePrefix("err:")
                    failed(if (txt.isBlank()) "YouTube: kann nicht abgespielt werden" else "YouTube: $txt")
                }
                v.startsWith("play:") -> {
                    if (qualityTries < 6) qualityTries++
                    val t = v.removePrefix("play:").toDoubleOrNull() ?: 0.0
                    if (t != lastTime) {
                        lastTime = t
                        stuckSince = 0L
                        reloads = 0
                        playing()
                    } else if (stuckSince == 0L) stuckSince = now
                }
                else -> if (stuckSince == 0L) stuckSince = now
            }
            // Bild steht seit 15 Sekunden (Live-Stream hängt): Seite neu laden, höchstens 3-mal hintereinander
            if (!reported && stuckSince > 0L && now - stuckSince > 15_000 && reloads < 3) {
                reloads++
                stuckSince = 0L
                lastTime = -1.0
                qualityTries = 0
                web.loadUrl(YouTube.embedUrl(url, ORIGIN), mapOf("Referer" to "$ORIGIN/"))
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
            runCatching { root?.removeView(web) }
            runCatching {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            }
        }
    }
    if (root == null) AndroidView(factory = { web }, modifier = modifier)
}
