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
        val now = System.currentTimeMillis()
        val text = msg.take(400)
        io.execute {
            runCatching {
                // Zeit erst hier formatieren: SimpleDateFormat verträgt keine gleichzeitigen Aufrufe aus mehreren Threads
                f.appendText("${fmt.format(Date(now))}  $text\n")
                // gelegentlich kürzen: nur die letzten Einträge behalten
                if (f.length() > 120_000) {
                    val keep = f.readLines().takeLast(MAX_LINES)
                    f.writeText(keep.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    /** Sofort schreiben (nur für den Absturz-Eintrag: der Hintergrund-Eintrag ginge mit dem Prozess verloren). */
    fun addNow(msg: String) {
        val f = file ?: return
        runCatching { f.appendText("${synchronized(fmt) { fmt.format(Date()) }}  ${msg.take(400)}\n") }
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
        // Sperren beim Anbieter (Sender lehnt ab) – liegen nicht an der App, darum eigens gezählt
        val blocked = lines.count { "  ABGELEHNT HTTP" in it }
        return "$gaps Bildlücken · $dog Wächter-Eingriffe · $err Fehler · $rejoin nahtlose Neuverbindungen" +
            if (blocked > 0) " · $blocked× Sender vom Anbieter gesperrt" else ""
    }

    /**
     * Lädt Kopfzeilen + Protokoll als Text auf paste.rs hoch und gibt den Link zurück.
     * Läuft blockierend – nur aus dem Hintergrund aufrufen.
     */
    fun upload(header: String): String = uploadShare(header + "\n\n" + read())

    /**
     * Zum Teilen (Hänger-Protokoll, gelernte Sender): erst dpaste.org (30 Tage), sonst paste.rs.
     * Aziz 27.11.: paste.rs nimmt seit dem 27.11. gar nichts mehr an (jede Anfrage „400 Bad Request“, auch
     * kleinster Text – im CI geprüft), dpaste.org schon. Rückgabe = Link. Blockierend – nur im Hintergrund.
     */
    fun uploadShare(text: String): String {
        val errors = mutableListOf<String>()
        runCatching { return dpasteOrg(text, "2592000") }.onFailure { errors += "dpaste.org: ${it.message}" }
        runCatching { return uploadText(text) }.onFailure { errors += "paste.rs: ${it.message}" }
        error(errors.joinToString(" · "))
    }

    /** dpaste.org: Text ablegen, Rückgabe „https://dpaste.org/ABCDE“ (Rohtext unter …/raw). [expires] in Sekunden oder „never“. */
    fun dpasteOrg(text: String, expires: String): String {
        val body = "content=" + java.net.URLEncoder.encode(text, "UTF-8") + "&expires=" + expires + "&format=url&lexer=_text"
        val c = URL("https://dpaste.org/api/").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            c.setRequestProperty("User-Agent", "Aze-IPTV")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) error("Hochladen fehlgeschlagen ($code)")
            val link = c.inputStream.bufferedReader().readText().trim().trim('"').trim()
            require(link.startsWith("https://dpaste.org/") && dpasteOrgId(link) != null) { "unerwartete Antwort" }
            return link
        } finally {
            c.disconnect()
        }
    }

    /** Kennung aus einem dpaste.org-Link („https://dpaste.org/AbC12“ → „AbC12“) */
    fun dpasteOrgId(link: String): String? =
        link.trim().trimEnd('/').removeSuffix("/raw").substringAfterLast('/').takeIf { id -> id.length in 3..12 && id.all { it.isLetterOrDigit() } }

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
