package de.aze.iptv.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.aze.iptv.data.YouTube

private const val ORIGIN = "https://de.aze.iptv"

/**
 * Offizieller eingebetteter YouTube-Player im Vollbild.
 * Die WebView nimmt keinen Fokus an, damit die Fernbedienung weiter die App steuert.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeView(url: String, modifier: Modifier, onPlaying: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val web = remember(url) {
        WebView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(object {
                @JavascriptInterface
                fun playing() {
                    main.post { onPlaying() }
                }

                @JavascriptInterface
                fun error(code: Int) {
                    val text = when (code) {
                        101, 150, 152, 153 -> "YouTube: Einbetten nicht erlaubt"
                        100 -> "YouTube: Video nicht gefunden"
                        2 -> "YouTube: Link ungültig"
                        else -> "YouTube: Fehler $code"
                    }
                    main.post { onError(text) }
                }
            }, "Aze")
            val src = YouTube.embedUrl(url, ORIGIN)
            val html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
                iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style></head><body>
                <iframe id="p" src="$src" allow="autoplay; encrypted-media; picture-in-picture"
                  referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                function onYouTubeIframeAPIReady(){
                  new YT.Player('p',{events:{
                    onStateChange:function(e){ if(e.data===1){ Aze.playing(); } },
                    onError:function(e){ Aze.error(e.data); }
                  }});
                }
                </script></body></html>
            """.trimIndent()
            loadDataWithBaseURL("$ORIGIN/", html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(web) {
        onDispose {
            web.loadUrl("about:blank")
            web.destroy()
        }
    }
    AndroidView(factory = { web }, modifier = modifier)
}
