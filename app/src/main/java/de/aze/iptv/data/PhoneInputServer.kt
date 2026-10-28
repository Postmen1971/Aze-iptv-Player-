package de.aze.iptv.data

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID

/**
 * Kleiner Webserver für die Eingabe einer Playlist vom Handy aus.
 * Schutz:
 *  - läuft nur, solange man ihn in den Einstellungen startet, höchstens 5 Minuten
 *  - nimmt nur Verbindungen aus dem Heimnetz an
 *  - verlangt den 4-stelligen Code, der am Fernseher steht; nach 5 falschen Codes ist Schluss
 *  - beendet sich nach der ersten erfolgreichen Eingabe
 */
class PhoneInputServer(
    private val onSource: (PlaylistSource) -> Unit,
    private val onYoutube: (link: String, name: String) -> Unit,
    private val onStopped: () -> Unit,
) {
    val code: String = (SecureRandom().nextInt(9000) + 1000).toString()
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private var wrongCodes = 0
    @Volatile private var running = false

    fun start(): Boolean {
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8080)) } }
            .recoverCatching { ServerSocket(0) }
            .getOrNull() ?: return false
        server = s
        port = s.localPort
        running = true
        Thread({ acceptLoop(s) }, "phone-input").apply { isDaemon = true }.start()
        main.postDelayed(timeout, 5 * 60 * 1000L)
        return true
    }

    private val timeout = Runnable { stop() }

    fun stop() {
        if (!running) return
        running = false
        runCatching { server?.close() }
        main.removeCallbacks(timeout)
        main.post { onStopped() }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val client = runCatching { s.accept() }.getOrNull() ?: break
            runCatching { handle(client) }
            runCatching { client.close() }
        }
    }

    private fun isLocal(addr: InetAddress): Boolean =
        addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val out = socket.getOutputStream()
        if (!isLocal(socket.inetAddress)) {
            respond(out, 403, page("Kein Zugriff", "Nur aus dem eigenen Heimnetz möglich."))
            return
        }
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        var length = 0
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            if (h.lowercase().startsWith("content-length:")) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val path = parts.getOrNull(1) ?: "/"

        if (method == "POST" && path.startsWith("/add")) {
            if (length <= 0 || length > 16_384) {
                respond(out, 400, page("Fehler", "Ungültige Eingabe."))
                return
            }
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val form = parseForm(String(body, 0, read, Charsets.UTF_8))
            if (form["code"]?.trim() != code) {
                wrongCodes++
                respond(out, 403, page("Falscher Code", "Bitte den Code vom Fernseher eingeben.", back = true))
                if (wrongCodes >= 5) stop()
                return
            }
            if (form["type"] == "youtube") {
                val link = form["url"].orEmpty().trim()
                if (link.isEmpty()) {
                    respond(out, 400, page("Angaben fehlen", "Bitte den YouTube-Link einfügen.", back = true))
                    return
                }
                respond(out, 200, page("Übernommen", "Der YouTube-Sender wird jetzt am Fernseher hinzugefügt. Du kannst diese Seite schließen."))
                main.post { onYoutube(link, form["name"].orEmpty().trim()) }
                stop()
                return
            }
            val type = when (form["type"]) {
                "xtream" -> SourceType.XTREAM
                "portal" -> SourceType.STALKER
                else -> SourceType.M3U
            }
            val url = form["url"].orEmpty().trim()
            val user = (if (type == SourceType.STALKER) form["mac"] else form["user"]).orEmpty().trim().let {
                if (type == SourceType.STALKER) it.uppercase() else it
            }
            val pass = form["pass"].orEmpty()
            if (url.isEmpty() || (type == SourceType.XTREAM && (user.isEmpty() || pass.isEmpty())) ||
                (type == SourceType.STALKER && !Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(user))
            ) {
                respond(out, 400, page("Angaben fehlen", "Bitte Link bzw. Server, Benutzer und Passwort ausfüllen.", back = true))
                return
            }
            val source = PlaylistSource(
                id = UUID.randomUUID().toString(),
                name = form["name"].orEmpty().trim().ifEmpty { if (type == SourceType.M3U) "Playlist" else if (type == SourceType.STALKER) "Portal" else "Anbieter" },
                type = type,
                url = url,
                user = user,
                pass = pass,
            )
            respond(out, 200, page("Übernommen", "Die Playlist wird jetzt am Fernseher geladen. Du kannst diese Seite schließen."))
            main.post { onSource(source) }
            stop()
            return
        }
        respond(out, 200, formPage())
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8_192) return null
            sb.append(c.toChar())
        }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split("&").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null
            else URLDecoder.decode(pair.substring(0, i), "UTF-8") to URLDecoder.decode(pair.substring(i + 1), "UTF-8")
        }.toMap()

    private fun respond(out: OutputStream, status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; else -> "Forbidden" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private val style = """
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body{margin:0;background:#0E1116;color:#EDEFF3;font-family:system-ui,sans-serif;padding:20px}
        h1{font-size:22px;margin:0 0 16px}
        label{display:block;font-size:14px;color:#C7CDD6;margin:14px 0 6px}
        input{width:100%;box-sizing:border-box;height:46px;border-radius:10px;border:1px solid #333B48;background:#151A21;color:#EDEFF3;padding:0 12px;font-size:16px}
        .tabs{display:flex;gap:8px;margin-top:8px}
        .tabs label{flex:1;margin:0;text-align:center;padding:12px;border-radius:10px;background:#1F2630;color:#EDEFF3;font-weight:600}
        .tabs input{display:none}
        .tabs label:has(input:checked){background:#F2B544;color:#15130E}
        button{margin-top:22px;width:100%;height:50px;border:0;border-radius:10px;background:#F2B544;color:#15130E;font-size:17px;font-weight:700}
        p{color:#C7CDD6;line-height:1.5}
        a{color:#F2B544}
        </style>
    """.trimIndent()

    private fun page(title: String, text: String, back: Boolean = false): String =
        "<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>Aze-IPTV-Player</title>$style</head><body>" +
            "<h1>${esc(title)}</h1><p>${esc(text)}</p>" +
            (if (back) "<p><a href=\"/\">Zurück</a></p>" else "") +
            "</body></html>"

    private fun formPage(): String = """
        <!doctype html><html lang="de"><head><meta charset="utf-8"><title>Aze-IPTV-Player</title>$style</head><body>
        <h1>Playlist hinzufügen</h1>
        <form method="post" action="/add" autocomplete="off">
          <div class="tabs">
            <label><input type="radio" name="type" value="m3u" checked onchange="sw()">M3U-Link</label>
            <label><input type="radio" name="type" value="xtream" onchange="sw()">Server + Login</label>
            <label><input type="radio" name="type" value="portal" onchange="sw()">Portal</label>
            <label><input type="radio" name="type" value="youtube" onchange="sw()">YouTube</label>
          </div>
          <label for="name">Name</label>
          <input id="name" name="name" placeholder="z. B. Mein Anbieter">
          <label for="url" id="urlLabel">M3U-Link (komplett)</label>
          <input id="url" name="url" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://…">
          <div id="xt" style="display:none">
            <label for="user">Benutzer</label>
            <input id="user" name="user" autocapitalize="off" autocorrect="off">
            <label for="pass">Passwort</label>
            <input id="pass" name="pass" type="password">
          </div>
          <div id="pt" style="display:none">
            <label for="mac">MAC-Adresse</label>
            <input id="mac" name="mac" value="00:1A:79:" autocapitalize="characters" autocorrect="off">
          </div>
          <label for="code">Code vom Fernseher</label>
          <input id="code" name="code" inputmode="numeric" maxlength="4" required>
          <button type="submit">An den Fernseher senden</button>
        </form>
        <script>
        function sw(){var t=document.querySelector('input[name=type]:checked').value,x=t==='xtream',y=t==='youtube',p=t==='portal';
          document.getElementById('xt').style.display=x?'block':'none';
          document.getElementById('pt').style.display=p?'block':'none';
          document.getElementById('urlLabel').textContent=x?'Server-Adresse':(y?'YouTube-Link (Kanal oder Livestream)':(p?'Portal-Adresse':'M3U-Link (komplett)'));
          document.getElementById('url').placeholder=x?'http://server:port':(y?'In YouTube: Teilen → Link kopieren, hier einfügen':(p?'http://server:port/c/':'http://…'));
          document.getElementById('name').placeholder=y?'z. B. TRT Haber (leer = Kanalname)':'z. B. Mein Anbieter';}
        </script>
        </body></html>
    """.trimIndent()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        /** Die Adresse des Geräts im Heimnetz, z. B. 192.168.178.34. */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
