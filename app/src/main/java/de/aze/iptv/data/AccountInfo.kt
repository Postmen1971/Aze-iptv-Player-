package de.aze.iptv.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Zugang laut Anbieter (wie bei TiviMate / IBO Player in der Playlist): bis wann er gilt, Status, Verbindungen.
 * Server + Login: player_api.php → user_info (exp_date, status, max_connections, active_cons, is_trial).
 * Portal + MAC: get_main_info → end_date (bzw. bei manchen Portalen im Feld „phone“).
 * Reine Rechen- und Textregeln (Unit-Test AccountInfoTest).
 */
data class AccountInfo(
    /** Ablauf in ms; null = unbegrenzt bzw. vom Anbieter nicht angegeben */
    val expiresAt: Long?,
    val status: String? = null,
    val maxCons: Int? = null,
    val activeCons: Int? = null,
    val trial: Boolean = false,
    val checkedAt: Long = 0,
) {
    enum class Level { OK, WARN, BAD }

    /** volle Tage bis zum Ablauf (0 = heute, negativ = abgelaufen) */
    fun daysLeft(now: Long): Long? = expiresAt?.let { (it - now).floorDiv(86_400_000L) }

    private val inactive get() = status != null && !status.equals("Active", ignoreCase = true)

    fun level(now: Long): Level {
        val d = daysLeft(now)
        return when {
            inactive || (expiresAt != null && expiresAt <= now) -> Level.BAD
            d != null && d < 7 -> Level.BAD
            d != null && d < 30 -> Level.WARN
            else -> Level.OK
        }
    }

    /** z. B. „Gültig bis 14.03.2027 · noch 109 Tage · Verbindungen 1 von 2“ */
    fun describe(now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { timeZone = zone }
        val parts = ArrayList<String>()
        val exp = expiresAt
        val d = daysLeft(now)
        when {
            exp == null -> parts.add("Unbegrenzt gültig")
            exp <= now -> parts.add("Abgelaufen am ${fmt.format(exp)}")
            else -> {
                parts.add("Gültig bis ${fmt.format(exp)}")
                parts.add(
                    when (d) {
                        0L -> "läuft heute ab"
                        1L -> "noch 1 Tag"
                        else -> "noch $d Tage"
                    },
                )
            }
        }
        if (inactive) parts.add("Status: ${statusText(status!!)}")
        if (trial) parts.add("Testzugang")
        if (maxCons != null && maxCons > 0) parts.add("Verbindungen ${activeCons ?: 0} von $maxCons")
        return parts.joinToString(" · ")
    }

    /** Ampel (Einstellungen → Sender & Playlists): „Gültig bis 14.03.2027“, „Abgelaufen am …“, „Unbegrenzt gültig“ */
    fun dateLine(now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { timeZone = zone }
        val exp = expiresAt
        return when {
            exp == null -> "Unbegrenzt gültig"
            exp <= now -> "Abgelaufen am ${fmt.format(exp)}"
            else -> "Gültig bis ${fmt.format(exp)}"
        }
    }

    /** Ampel, große Zeile: „noch 109 Tage“, „noch 1 Tag“, „läuft heute ab“, „abgelaufen“ – null ohne Ablaufdatum */
    fun daysLine(now: Long): String? {
        val exp = expiresAt ?: return if (inactive) "Status: ${statusText(status!!)}" else null
        if (exp <= now) {
            val ago = (now - exp).floorDiv(86_400_000L)
            return when (ago) {
                0L -> "abgelaufen"
                1L -> "seit 1 Tag abgelaufen"
                else -> "seit $ago Tagen abgelaufen"
            }
        }
        return when (val d = daysLeft(now)) {
            0L -> "läuft heute ab"
            1L -> "noch 1 Tag"
            else -> "noch $d Tage"
        }
    }

    /** Ampel, kleine Zeile: Status, Testzugang, Verbindungen */
    fun extraLine(): String? {
        val parts = ArrayList<String>()
        if (inactive && expiresAt != null) parts.add("Status: ${statusText(status!!)}")
        if (trial) parts.add("Testzugang")
        if (maxCons != null && maxCons > 0) parts.add("Verbindungen ${activeCons ?: 0} von $maxCons")
        return parts.joinToString(" · ").ifEmpty { null }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("exp", expiresAt ?: -1L)
        status?.let { put("status", it) }
        maxCons?.let { put("max", it) }
        activeCons?.let { put("act", it) }
        put("trial", trial)
        put("at", checkedAt)
    }

    companion object {
        private fun statusText(s: String) = when (s.lowercase()) {
            "expired" -> "abgelaufen"
            "banned" -> "gesperrt"
            "disabled" -> "abgeschaltet"
            else -> s
        }

        fun fromJson(o: JSONObject): AccountInfo = AccountInfo(
            expiresAt = o.optLong("exp", -1L).takeIf { it > 0 },
            status = o.optString("status").ifBlank { null },
            maxCons = if (o.has("max")) o.optInt("max") else null,
            activeCons = if (o.has("act")) o.optInt("act") else null,
            trial = o.optBoolean("trial"),
            checkedAt = o.optLong("at"),
        )

        /** Antwort von player_api.php (ohne action) → Zugang; null, wenn keine user_info drin ist */
        fun parseXtream(json: String, now: Long): AccountInfo? {
            val info = runCatching { JSONObject(json).optJSONObject("user_info") }.getOrNull() ?: return null
            // exp_date: Sekunden als Zahl oder Text; null/""/"null"/0 = unbegrenzt
            val exp = info.opt("exp_date")?.toString()?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1000 }
            return AccountInfo(
                expiresAt = exp,
                status = info.optString("status").ifBlank { null },
                maxCons = info.opt("max_connections")?.toString()?.toIntOrNull(),
                activeCons = info.opt("active_cons")?.toString()?.toIntOrNull(),
                trial = info.opt("is_trial")?.toString() == "1",
                checkedAt = now,
            )
        }

        /** Datum aus einem Portal: „2027-03-14 00:00:00“, „2027-03-14“, „14.03.2027“, „March 14, 2027“ */
        fun parsePortalDate(text: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty() || t.startsWith("0000")) return null
            val patterns = listOf(
                "yyyy-MM-dd HH:mm:ss" to Locale.ROOT, "yyyy-MM-dd" to Locale.ROOT, "dd.MM.yyyy" to Locale.ROOT,
                "MMMM d, yyyy" to Locale.ENGLISH, "MMM d, yyyy" to Locale.ENGLISH,
            )
            for ((p, l) in patterns) {
                val f = SimpleDateFormat(p, l).apply { timeZone = zone; isLenient = false }
                val pos = java.text.ParsePosition(0)
                val d = f.parse(t, pos) ?: continue
                if (pos.index == t.length) return d.time
            }
            return null
        }
    }
}
