package de.aze.iptv.data

import org.json.JSONObject

/**
 * Gemeinschaftsliste für „Fußball heute“: welche eigenen Sender (Name) zu einem Sender laut liveonsat passen.
 * Liegt öffentlich lesbar im GitHub-Projekt; jede App holt sie sich (höchstens alle 12 h). Neue Einträge kommen dazu,
 * wenn jemand in den Einstellungen „Gelernte Sender teilen“ nutzt und die Zuordnungen geprüft übernommen werden.
 *
 * Format: { "map": { "bein sports 1 france": ["BEIN SPORTS 1 FR HD", …], … } } – Schlüssel klein geschrieben.
 */
object Community {
    const val URL = "https://raw.githubusercontent.com/Postmen1971/Aze-iptv-Player-/main/community/liveonsat.json"

    fun parse(json: String): Map<String, List<String>> = runCatching {
        val m = JSONObject(json).optJSONObject("map") ?: return emptyMap()
        m.keys().asSequence().associate { k ->
            val a = m.optJSONArray(k)
            k.lowercase() to (if (a == null) listOfNotNull(m.optString(k).takeIf { it.isNotBlank() })
            else (0 until a.length()).map { a.getString(it) })
        }
    }.getOrDefault(emptyMap())

    /** Blockierend – nur im Hintergrund. Gibt den rohen Text zurück (zum Zwischenspeichern). */
    fun fetch(): String {
        // kurze Zeiten: ohne Internet soll „Fußball heute“ nicht lange warten (dann gilt die zuletzt geladene Liste)
        val c = java.net.URL(URL).openConnection() as java.net.HttpURLConnection
        try {
            c.connectTimeout = 5_000
            c.readTimeout = 8_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            if (c.responseCode !in 200..299) error("Gemeinschaftsliste: ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }
}
