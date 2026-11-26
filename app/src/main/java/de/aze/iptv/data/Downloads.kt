package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Filme und Folgen aufs Gerät laden (Aziz 26.11.: „aufnehmen, aber nur Filme und Serien“).
 * Hier nur Daten und Regeln ohne Android (Unit-Test DownloadRulesTest); das Laden selbst macht [Downloader].
 *
 * Gespeichert wird KEINE Adresse (darin stünden Benutzername und Passwort des Anbieters), sondern nur, welcher Film
 * bzw. welche Folge es ist – die Adresse baut die App beim Laden aus der Playlist neu.
 */
data class DlEntry(
    /** gleich dem Schlüssel der gemerkten Stelle: „m:<playlist>:<film>“ bzw. „e:<playlist>:<folge>“ */
    val key: String,
    /** Film bzw. Serie (für Cover, Titel und die Liste „Heruntergeladen“) */
    val ref: VodRef,
    /** nur bei Folgen */
    val episode: EpisodeRef? = null,
    val state: DlState = DlState.WAIT,
    val done: Long = 0,
    /** Gesamtgröße in Byte, −1 = unbekannt */
    val total: Long = -1,
    /** voller Pfad der fertigen Datei (beim Laden mit „.part“ dahinter) */
    val file: String = "",
    val error: String? = null,
    val added: Long = 0,
) {
    val title: String
        get() = episode?.let { e -> "${ref.name} – S${e.season} E${e.number}" + e.title.takeIf { it.isNotBlank() && !it.equals(ref.name, true) }?.let { " · $it" }.orEmpty() }
            ?: ref.name

    /** Anteil 0..100, −1 = unbekannt */
    val percent: Int get() = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key); put("ref", ref.toJson()); episode?.let { put("ep", it.toJson()) }
        put("st", state.name); put("done", done); put("total", total); put("file", file)
        error?.let { put("err", it) }; put("added", added)
    }

    companion object {
        fun fromJson(o: JSONObject): DlEntry? {
            val key = o.optString("key").ifBlank { return null }
            val ref = o.optJSONObject("ref")?.let { VodRef.fromJson(it) } ?: return null
            val st = runCatching { DlState.valueOf(o.optString("st")) }.getOrDefault(DlState.WAIT)
            return DlEntry(
                key, ref, EpisodeRef.fromJson(o.optJSONObject("ep")),
                // lief beim Beenden der App gerade: wartet wieder (geht beim nächsten Start weiter)
                if (st == DlState.RUN) DlState.WAIT else st,
                o.optLong("done"), o.optLong("total", -1), o.optString("file"), o.optString("err").ifBlank { null }, o.optLong("added"),
            )
        }
    }
}

enum class DlState {
    /** in der Warteschlange (lädt, sobald es dran ist) */
    WAIT,
    RUN,
    /** vom Nutzer angehalten */
    PAUSE,
    DONE,
    FAIL,
}

object DownloadRules {
    /** Während Fernsehen/Film vom Anbieter läuft und mehrere Verbindungen erlaubt sind: höchstens so schnell laden */
    const val SLOW_BYTES_PER_SEC = 2L * 1024 * 1024
    /** so viel muss nach dem Download noch frei bleiben (das Gerät braucht selbst Platz) */
    const val KEEP_FREE = 500L * 1024 * 1024
    /** darunter hält ein laufender Download an */
    const val STOP_FREE = 300L * 1024 * 1024

    enum class Mode {
        /** volle Geschwindigkeit */
        FULL,
        /** gebremst, damit Fernsehen/Film flüssig bleibt */
        SLOW,
        /** warten: Anbieter erlaubt nur eine Verbindung (oder unbekannt) und gerade läuft etwas */
        WAIT,
    }

    /**
     * Wie geladen wird. [watching] = gerade läuft Fernsehen oder ein Film vom Anbieter (nicht vom Gerät),
     * [maxCons] = erlaubte Verbindungen laut Anbieter (null = unbekannt).
     * Schnelles Umschalten hat Vorrang: der Download darf das Fernsehen nie stören.
     */
    fun mode(watching: Boolean, maxCons: Int?): Mode = when {
        !watching -> Mode.FULL
        maxCons != null && maxCons >= 2 -> Mode.SLOW
        else -> Mode.WAIT
    }

    /** Passt der Download noch auf den Speicher? [total] −1 = unbekannt (dann nur die Mindestreserve prüfen) */
    fun fits(total: Long, done: Long, free: Long): Boolean =
        if (total > 0) total - done <= free - KEEP_FREE else free > KEEP_FREE

    /** Dateiname ohne Zeichen, die Speicher (FAT32 auf USB-Sticks) nicht mögen */
    fun fileName(e: DlEntry): String {
        val ext = (e.episode?.ext ?: e.ref.ext).lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp4" }.take(5)
        val base = e.title.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("\\s+"), " ").trim().take(90).ifBlank { "Film" }
        // eindeutig je Eintrag (zwei Filme mit gleichem Namen überschreiben sich nicht)
        val tag = Integer.toHexString(e.key.hashCode()).takeLast(6)
        return "$base [$tag].$ext"
    }

    /**
     * Antwort auf eine Anfrage ab Byte [from]: Wo geht es weiter, wie groß ist alles?
     * 206 mit „Content-Range: bytes 100-999/1000“ → (100, 1000); 200 = Server kann nicht fortsetzen → von vorn.
     * Gibt null zurück, wenn die Antwort nicht passt (anderer Anfang) – dann von vorn laden.
     */
    fun resumeInfo(from: Long, code: Int, contentRange: String?, contentLength: Long): Pair<Long, Long>? {
        if (code == 200) return 0L to (if (contentLength > 0) contentLength else -1L)
        if (code != 206) return null
        val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").find(contentRange.orEmpty())
            ?: return if (from == 0L) 0L to (if (contentLength > 0) contentLength else -1L) else null
        val start = m.groupValues[1].toLong()
        if (start != from) return null
        val total = m.groupValues[3].toLongOrNull() ?: if (contentLength > 0) from + contentLength else -1L
        return start to total
    }

    /** Wartezeit vor dem nächsten Versuch nach Abbruch (Leitung weg, Anbieter lehnt ab) */
    fun retryDelayMs(attempt: Int, rejected: Boolean): Long =
        if (rejected) 60_000L else longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000).let { it[attempt.coerceIn(0, it.lastIndex)] }

    /** Nach so vielen Fehlschlägen hintereinander aufgeben (der Nutzer kann „Weiter“ drücken) */
    const val MAX_ATTEMPTS = 8

    /** 1,4 GB / 700 MB / 12 KB */
    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(java.util.Locale.GERMANY, bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${(bytes / 1024).coerceAtLeast(0)} KB"
    }

    fun toJson(l: List<DlEntry>) = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
    fun fromJson(s: String?): List<DlEntry> = runCatching {
        val a = JSONArray(s ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> DlEntry.fromJson(o) } }
    }.getOrDefault(emptyList())
}
