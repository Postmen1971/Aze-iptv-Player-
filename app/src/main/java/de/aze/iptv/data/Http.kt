package de.aze.iptv.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Kleine HTTP-Hilfe, die auch Weiterleitungen zwischen http und https folgt. */
object Http {

    const val USER_AGENT = "AzeIPTV/1.0 (Android)"

    fun open(url: String, accept: String? = null): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (accept != null) conn.setRequestProperty("Accept", accept)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel")
                current = URL(URL(current), location).toString()
                conn.disconnect()
            } else if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Server antwortet mit Fehler $code")
            } else {
                return conn
            }
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    fun text(url: String, accept: String? = null): String {
        val conn = open(url, accept)
        try {
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, target: File, onProgress: (Int) -> Unit = {}) {
        val conn = open(url)
        try {
            val total = conn.contentLength
            target.parentFile?.mkdirs()
            conn.inputStream.use { input: InputStream ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
