package de.aze.iptv.data

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fernhilfe: Box A (Freund) zeigt einen Code, Box B (Helfer) gibt ihn ein. Beide Boxen sind von außen nicht erreichbar,
 * deshalb laufen die Nachrichten über einen öffentlichen Briefkasten (ntfy.sh). Dort liegt nur Datensalat:
 *  - aus dem Code werden ein Schlüssel (AES-GCM, PBKDF2) und zwei zufällig aussehende Briefkasten-Namen abgeleitet,
 *  - jede Nachricht wird vor dem Senden verschlüsselt (und gepackt), ohne Code kann niemand sie lesen oder fälschen.
 * Box A hört nur, solange die Fernhilfe offen ist (erst 10 min, danach 1 min nach der letzten Aktion des Helfers).
 */
object RemoteCrypto {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // ohne 0/O, 1/I – gut vorzulesen
    const val CODE_LEN = 8

    class Keys(val key: SecretKeySpec, /** Helfer → Box */ val toBox: String, /** Box → Helfer */ val toHelper: String)

    fun newCode(): String {
        val r = SecureRandom()
        return (1..CODE_LEN).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** „k7m4 q2xp“ → „K7M4Q2XP“; null, wenn es kein gültiger Code ist */
    fun normalize(input: String): String? {
        val c = input.uppercase().filter { it.isLetterOrDigit() }
            .replace('O', '0').replace('I', '1') // häufige Verwechslungen – beide kommen im Code nicht vor
        return c.takeIf { it.length == CODE_LEN && it.all { ch -> ch in ALPHABET } }
    }

    /** „K7M4Q2XP“ → „K7M4-Q2XP“ */
    fun pretty(code: String) = code.take(4) + "-" + code.drop(4)

    /**
     * Schlüssel UND Briefkasten-Namen aus EINER langsamen Ableitung (PBKDF2, 60 000 Runden, 512 Bit): Wer nur den
     * Briefkasten-Namen sieht (Betreiber, Protokolle), kann den Code daraus nicht in Minuten zurückrechnen.
     * Muss genau zur Webseite (web/fernhilfe.html) passen.
     */
    fun keys(code: String): Keys {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val bits = f.generateSecret(PBEKeySpec(code.toCharArray(), "aze-fernhilfe-2".toByteArray(), 60_000, 512)).encoded
        val h = bits.copyOfRange(32, 52).joinToString("") { "%02x".format(it) }
        return Keys(SecretKeySpec(bits.copyOfRange(0, 32), "AES"), "azefh$h" + "a", "azefh$h" + "b")
    }

    fun seal(keys: Keys, json: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keys.key, GCMParameterSpec(128, iv))
        val packed = java.io.ByteArrayOutputStream().also { bos ->
            java.util.zip.GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return "AZF1:" + Base64.encodeToString(iv + c.doFinal(packed), Base64.NO_WRAP)
    }

    /** null = nicht von uns / falscher Code / verändert */
    fun open(keys: Keys, text: String): JSONObject? = runCatching {
        val t = text.trim()
        require(t.startsWith("AZF1:"))
        val data = Base64.decode(t.substring(5), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keys.key, GCMParameterSpec(128, data, 0, 12))
        val raw = c.doFinal(data, 12, data.size - 12)
        JSONObject(java.util.zip.GZIPInputStream(raw.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) })
    }.getOrNull()
}

/**
 * Briefkasten (ntfy-Schnittstelle): senden = POST auf /<name>, große Nachrichten als Anhang (PUT mit „Filename“);
 * empfangen = GET /<name>/json – eine offene Verbindung, die jede neue Nachricht als JSON-Zeile liefert.
 */
class Relay(private val base: String = DEFAULT) {
    companion object {
        const val DEFAULT = "https://ntfy.sh"
        /** ntfy.sh nimmt Text bis 4 KB direkt, größere Nachrichten als Anhang */
        private const val INLINE_MAX = 3_500
    }

    fun publish(topic: String, payload: String) {
        val big = payload.length > INLINE_MAX
        val c = URL("$base/$topic").openConnection() as HttpURLConnection
        try {
            c.requestMethod = if (big) "PUT" else "POST"
            c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Aze-IPTV")
            c.setRequestProperty("Content-Type", "text/plain")
            if (big) c.setRequestProperty("Filename", "aze.txt")
            c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val rc = c.responseCode
            if (rc == 429) error("Briefkasten überlastet – bitte kurz warten")
            if (rc !in 200..299) error("Senden fehlgeschlagen ($rc)")
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /**
     * Hört auf [topic], bis [active] false wird. Verbindet bei Abbruch neu (ab der zuletzt gesehenen Nachricht).
     * Jede Nachricht genau einmal an [onMessage] (Text bzw. Inhalt des Anhangs). Blockiert – im Hintergrund aufrufen.
     */
    fun listen(topic: String, active: () -> Boolean, onState: (Boolean) -> Unit, onMessage: (String) -> Unit) {
        var since = "all" // der Code ist neu und zufällig – alles im Briefkasten gehört zu dieser Fernhilfe
        val seen = HashSet<String>()
        var pause = 1_000L
        while (active()) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("$base/$topic/json?since=" + URLEncoder.encode(since, "UTF-8")).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 70_000 // ntfy schickt alle 45 s ein Lebenszeichen
                conn.setRequestProperty("User-Agent", "Aze-IPTV")
                if (conn.responseCode !in 200..299) error("Briefkasten antwortet ${conn.responseCode}")
                onState(true)
                pause = 1_000L
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                    while (active()) {
                        val line = r.readLine() ?: break
                        val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        if (o.optString("event") != "message") continue
                        val id = o.optString("id")
                        if (id.isEmpty() || !seen.add(id)) continue
                        since = id
                        val att = o.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotEmpty() }
                        val text = if (att != null) runCatching { fetch(att) }.getOrNull() ?: continue else o.optString("message")
                        onMessage(text)
                    }
                }
            } catch (_: Exception) {
                onState(false)
            } finally {
                conn?.disconnect()
            }
            if (!active()) break
            Thread.sleep(pause)
            pause = (pause * 2).coerceAtMost(10_000L)
        }
    }

    private fun fetch(url: String): String {
        // Anhänge nur vom selben Briefkasten holen
        require(url.startsWith(base) || (base == DEFAULT && url.startsWith("https://ntfy.sh/"))) { "fremder Anhang" }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Aze-IPTV")
            if (c.responseCode !in 200..299) error("Anhang ${c.responseCode}")
            return c.inputStream.use { inp ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    require(out.size() < 16 * 1024 * 1024) { "Anhang zu groß" }
                }
                out.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }
}
