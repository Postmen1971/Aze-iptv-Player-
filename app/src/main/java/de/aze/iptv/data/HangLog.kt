package de.aze.iptv.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Hänger-Protokoll: merkt sich auf dem Gerät jede Bildlücke, jeden Wächter-Eingriff, Fehler und Neuverbindungen
 * (Sendername, Uhrzeit, Messwerte). Keine Adressen, keine Zugangsdaten – nur Sendernamen.
 * Über die Einstellungen kann Aziz es hochladen und den Link weitergeben.
 */
object HangLog {
    private const val MAX_LINES = 600
    private var file: File? = null
    private val io = Executors.newSingleThreadExecutor()
    private val fmt = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY)

    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "haenger.log")
    }

    /** Einen Eintrag anhängen (läuft im Hintergrund, blockiert nie die Wiedergabe). */
    fun add(msg: String) {
        val f = file ?: return
        val line = "${fmt.format(Date())}  ${msg.take(400)}"
        io.execute {
            runCatching {
                f.appendText(line + "\n")
                // gelegentlich kürzen: nur die letzten Einträge behalten
                if (f.length() > 120_000) {
                    val keep = f.readLines().takeLast(MAX_LINES)
                    f.writeText(keep.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")

    fun count(): Int = read().lineSequence().count { it.isNotBlank() }

    fun clear() {
        io.execute { runCatching { file?.delete() } }
    }

    /** Wie viele Bildlücken / Wächter-Eingriffe / Fehler stehen drin (für die Übersicht in den Einstellungen) */
    fun summary(): String {
        val lines = read().lineSequence().toList()
        val gaps = lines.count { "LUECKE" in it }
        val dog = lines.count { "WATCHDOG" in it }
        val err = lines.count { "ERROR" in it }
        val rejoin = lines.count { "TRANSITION" in it }
        return "$gaps Bildlücken · $dog Wächter-Eingriffe · $err Fehler · $rejoin nahtlose Neuverbindungen"
    }

    /**
     * Lädt Kopfzeilen + Protokoll als Text auf paste.rs hoch und gibt den Link zurück.
     * Läuft blockierend – nur aus dem Hintergrund aufrufen.
     */
    fun upload(header: String): String = uploadText(header + "\n\n" + read())

    /** Beliebigen Text auf paste.rs ablegen, Rückgabe = Link. Blockierend – nur im Hintergrund. */
    fun uploadText(text: String): String {
        val body = text.toByteArray(Charsets.UTF_8)
        val c = URL("https://paste.rs/").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code !in 200..299) error("Hochladen fehlgeschlagen ($code)")
            return c.inputStream.bufferedReader().readText().trim()
        } finally {
            c.disconnect()
        }
    }
}
