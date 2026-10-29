package de.aze.iptv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * SkorText – Fußball-Videotext – als Ebene über dem laufenden Programm.
 * Die Seiten stammen aus dem SkorText-Projekt (assets/skortext) und werden beim Build aktuell übernommen.
 * Die Fernbedienung bleibt bei der App; Tasten werden an die Videotext-Seite weitergereicht.
 */
object Teletext {

    /** Fernbedienungstaste → SkorText-Taste (wie in der SkorText-App). */
    fun mapKey(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "ok"
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "back"
        KeyEvent.KEYCODE_PROG_RED -> "red"
        KeyEvent.KEYCODE_PROG_GREEN -> "green"
        KeyEvent.KEYCODE_PROG_YELLOW -> "yellow"
        KeyEvent.KEYCODE_PROG_BLUE -> "blue"
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "chup"
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> "chdn"
        KeyEvent.KEYCODE_MENU -> "menu"
        KeyEvent.KEYCODE_INFO -> "info"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (code - KeyEvent.KEYCODE_0).toString()
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (code - KeyEvent.KEYCODE_NUMPAD_0).toString()
        else -> null
    }

    /** Taste an die Seite geben. Meldet die Seite "exit" (Zurück auf der Startseite), wird onExit aufgerufen. */
    fun sendKey(web: WebView, key: String, onExit: () -> Unit) {
        web.evaluateJavascript("window.skorKey ? window.skorKey('$key') : ''") { v ->
            if (v == "\"exit\"") onExit()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun create(context: Context, onCrash: () -> Unit): WebView {
        val web = WebView(context)
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        web.setBackgroundColor(android.graphics.Color.BLACK)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        web.addJavascriptInterface(Bridge(context, web), "SkorBridge")
        web.webViewClient = object : WebViewClient() {
            // Stürzt die Anzeige ab, nur den Videotext schließen – nicht die ganze App.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Handler(Looper.getMainLooper()).post { onCrash() }
                return true
            }
        }
        web.loadUrl("file:///android_asset/skortext/index.html")
        return web
    }

    /** Dieselbe Brücke wie in der SkorText-App: Daten holen und Einstellungen merken. */
    private class Bridge(context: Context, private val web: WebView) {
        private val prefs = context.getSharedPreferences("skortext", Context.MODE_PRIVATE)
        private val main = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun get(id: Int, url: String) {
            Thread {
                var c: HttpURLConnection? = null
                try {
                    c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 12_000
                    c.readTimeout = 15_000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    c.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
                    c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,tr;q=0.8,en;q=0.7")
                    c.setRequestProperty("Accept-Encoding", "gzip")
                    val code = c.responseCode
                    var input: InputStream? = if (code >= 400) c.errorStream else c.inputStream
                    var body = ""
                    if (input != null) {
                        if ("gzip".equals(c.contentEncoding, ignoreCase = true)) input = GZIPInputStream(input)
                        val out = ByteArrayOutputStream()
                        input.use { it.copyTo(out, 16_384) }
                        // TFF-Seiten sind windows-1254 (Türkisch)
                        var cs = if (url.contains("tff.org")) "windows-1254" else "UTF-8"
                        val ct = c.contentType
                        if (ct != null && ct.lowercase().contains("charset=")) {
                            cs = ct.substring(ct.lowercase().indexOf("charset=") + 8).replace("\"", "").split(";")[0].trim()
                        }
                        body = runCatching { out.toString(cs) }.getOrElse { out.toString("UTF-8") }
                    }
                    deliver(id, true, code, body)
                } catch (e: Exception) {
                    deliver(id, false, 0, "${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    c?.disconnect()
                }
            }.start()
        }

        /** Updates kommen hier über den Aze-IPTV-Player selbst; der SkorText-Update-Knopf macht nichts. */
        @JavascriptInterface
        fun installApk(url: String) {
            main.post { web.evaluateJavascript("window.__skorUpd && window.__skorUpd('')", null) }
        }

        @JavascriptInterface
        fun pref(key: String, def: String): String = prefs.getString(key, def) ?: def

        @JavascriptInterface
        fun setPref(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        private fun deliver(id: Int, ok: Boolean, status: Int, body: String) {
            val js = "window.__skorRes($id,$ok,$status,${JSONObject.quote(body)})"
            main.post { runCatching { web.evaluateJavascript(js, null) } }
        }
    }
}

/**
 * Videotext-Ebene. Die WebView wird einmal angelegt und beim Schließen nur versteckt (nicht zerstört):
 * Beim nächsten Öffnen ist SkorText sofort da, und Tastendrücke gehen nicht verloren, während die Seite lädt.
 */
@Composable
fun TeletextView(web: WebView, modifier: Modifier) {
    AndroidView(
        factory = {
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web
        },
        modifier = modifier,
    )
}
