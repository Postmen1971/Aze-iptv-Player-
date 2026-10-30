package de.aze.iptv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dauerhafte Einstellungen: Quellen, Favoriten, Aktualisierungsintervall, letzter Sender. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("aze", Context.MODE_PRIVATE)

    init {
        Secure.init(context)
        // Einmalig: ältere, langsam verschlüsselte Werte auf die schnelle Verschlüsselung umstellen
        if (!prefs.getBoolean("enc2Done", false)) {
            val e = prefs.edit()
            listOf("sources", "favorites", "layout", "epgUrl", "lastChannel", "proxyPass").forEach { k ->
                val v = prefs.getString(k, null)
                if (v != null && v.startsWith("enc1:")) Secure.decrypt(v)?.let { e.putString(k, Secure.encrypt(it)) }
            }
            e.putBoolean("enc2Done", true).apply()
        }
        // Einmalig nach dem Update: Tunneling aus (verträgt sich nicht mit dem Anpassen der Bildwiederholrate)
        if (!prefs.getBoolean("tunnelReset1", false)) {
            prefs.edit().putBoolean("tunneling", false).putBoolean("tunnelReset1", true).apply()
        }
        // Einmalig: neue Standardwerte (Puffer mittel, immer Media3, Bildqualität automatisch, nie umschalten)
        if (!prefs.getBoolean("tuned4", false)) {
            prefs.edit().putString("buffer", "MEDIUM").putString("engine", "MEDIA3").putBoolean("forceHighest", false)
                .putBoolean("afr50", false).putBoolean("afr", false)
                .putBoolean("tuned1", true).putBoolean("tuned2", true).putBoolean("tuned3", true).putBoolean("afrReset1", true)
                .putBoolean("tuned4", true).apply()
        }
        // Einmalig: von Aziz festgelegte Standardwerte (Puffer groß, immer Media3, Bildqualität automatisch, 50 Hz in der App)
        if (!prefs.getBoolean("tuned3", false)) {
            prefs.edit().putString("buffer", "LARGE").putString("engine", "MEDIA3").putBoolean("forceHighest", false)
                .putBoolean("afr50", true).putBoolean("afr", false)
                .putBoolean("tuned1", true).putBoolean("tuned2", true).putBoolean("afrReset1", true).putBoolean("tuned3", true).apply()
        }
        // Farbschemata neu sortiert: Mitternacht 0, Ozean 1, Graphit 2 bleiben – alle anderen werden Ozean
        if (!prefs.getBoolean("schemes2", false)) {
            val old = prefs.getInt("scheme", 0)
            val neu = when (old) { 0 -> 0; 2 -> 1; 4 -> 2; else -> 1 }
            prefs.edit().putInt("scheme", neu).putBoolean("schemes2", true).apply()
        }
        // Einmalig: fest 50 Hz statt Umschalten je Sender (ständiges Umschalten beim Zappen ließ das Bild stehen)
        if (!prefs.getBoolean("tuned2", false)) {
            prefs.edit().putBoolean("afr", false).putBoolean("afr50", true).putInt("afrDelay", 6)
                .putBoolean("tuned1", true).putBoolean("afrReset1", true).putBoolean("tuned2", true).apply()
        }
        // Einmalig: die bei Aziz auf der SHIELD bewährten Werte übernehmen
        // (Bildwiederholrate an Sender anpassen, 6 s Wartezeit, immer beste Stufe, keine Fehlersuche-Anzeige)
        if (!prefs.getBoolean("tuned1", false)) {
            prefs.edit()
                .putBoolean("afr", true).putBoolean("afr50", false).putInt("afrDelay", 6)
                .putBoolean("forceHighest", true).putBoolean("showDiag", false).putBoolean("tunneling", false)
                .putBoolean("afrReset1", true).putBoolean("tuned1", true)
                .apply()
        }
        // Einmalig nach dem Update: Bildwiederholrate anpassen aus (lässt auf manchen Geräten das Bild einfrieren)
        if (!prefs.getBoolean("afrReset1", false)) {
            prefs.edit().putBoolean("afr", false).putBoolean("afrReset1", true).apply()
        }
    }

    fun sources(): List<PlaylistSource> {
        // Gespeichert verschlüsselt (Links enthalten oft Benutzer und Passwort).
        val raw = Secure.decrypt(prefs.getString("sources", "[]")) ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlaylistSource(
                id = o.getString("id"),
                name = o.optString("name"),
                type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.M3U),
                url = o.optString("url"),
                user = o.optString("user"),
                pass = o.optString("pass"),
            )
        }
    }

    fun saveSources(list: List<PlaylistSource>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("type", it.type.name)
                    .put("url", it.url).put("user", it.user).put("pass", it.pass)
            )
        }
        prefs.edit().putString("sources", Secure.encrypt(arr.toString())).apply()
    }

    fun favorites(): List<String> {
        // Sender-Links enthalten oft Benutzer und Passwort, deshalb verschlüsselt.
        val raw = Secure.decrypt(prefs.getString("favorites", "[]")) ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun saveFavorites(keys: List<String>) {
        prefs.edit().putString("favorites", Secure.encrypt(JSONArray(keys).toString())).apply()
    }

    /** 0 = nur manuell, sonst Stunden (24, 48, 72, 336 = zwei Wochen). */
    var refreshHours: Int
        get() = prefs.getInt("refreshHours", 24)
        set(v) = prefs.edit().putInt("refreshHours", v).apply()

    var lastRefresh: Long
        get() = prefs.getLong("lastRefresh", 0L)
        set(v) = prefs.edit().putLong("lastRefresh", v).apply()

    var bufferLevel: String?
        get() = prefs.getString("buffer", null)
        set(v) = prefs.edit().putString("buffer", v).apply()

    /** Eigene Anordnung/Ausblendungen. Verschlüsselt, weil Sender-Links Zugangsdaten enthalten können. */
    var layout: ListLayout
        get() = ListLayout.fromJson(Secure.decrypt(prefs.getString("layout", null)))
        set(v) = prefs.edit().putString("layout", Secure.encrypt(v.toJson())).apply()

    var youtube: List<YtEntry>
        get() {
            val arr = runCatching { JSONArray(prefs.getString("youtube", "[]")) }.getOrDefault(JSONArray())
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                YtEntry(o.getString("id"), o.optString("name"), o.optBoolean("channel"), o.getString("ref"))
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("channel", it.isChannel).put("ref", it.ref)) }
            prefs.edit().putString("youtube", arr.toString()).apply()
        }

    /** Eigene EPG-Adresse (optional). Verschlüsselt, weil sie Zugangsdaten enthalten kann. */
    var epgUrl: String
        get() = Secure.decrypt(prefs.getString("epgUrl", "")) ?: ""
        set(v) = prefs.edit().putString("epgUrl", if (v.isBlank()) "" else Secure.encrypt(v.trim())).apply()

    /** Weiterschauen: gemerkte Stelle je Film/Folge (Millisekunden). */
    fun resumePositions(): MutableMap<String, Long> {
        val o = runCatching { JSONObject(prefs.getString("resume", "{}")) }.getOrDefault(JSONObject())
        val m = HashMap<String, Long>()
        o.keys().forEach { k -> m[k] = o.optLong(k) }
        return m
    }

    fun saveResumePositions(m: Map<String, Long>) {
        val o = JSONObject()
        // höchstens 300 Einträge behalten
        m.entries.toList().takeLast(300).forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("resume", o.toString()).apply()
    }

    var playerEngine: String?
        get() = prefs.getString("engine", null)
        set(v) = prefs.edit().putString("engine", v).apply()

    var forceHighest: Boolean
        get() = prefs.getBoolean("forceHighest", false)
        set(v) = prefs.edit().putBoolean("forceHighest", v).apply()

    var tunneling: Boolean
        get() = prefs.getBoolean("tunneling", false)
        set(v) = prefs.edit().putBoolean("tunneling", v).apply()

    /** Bildwiederholrate des Bildschirms an den Sender anpassen (25/50 Hz). */
    var afr: Boolean
        get() = prefs.getBoolean("afr", false)
        set(v) = prefs.edit().putBoolean("afr", v).apply()

    /** Bildschirm fest auf 50 Hz (einmal beim Start umschalten statt bei jedem Sender). */
    var afr50: Boolean
        get() = prefs.getBoolean("afr50", false)
        set(v) = prefs.edit().putBoolean("afr50", v).apply()

    /** Wartezeit nach dem Umschalten der Frequenz (Sekunden), wie bei TiviMate einstellbar. */
    var afrDelay: Int
        get() = prefs.getInt("afrDelay", 6)
        set(v) = prefs.edit().putInt("afrDelay", v).apply()

    /** Kleine Anzeige zur Fehlersuche im Bild (Frequenz, Bilder, Position). */
    var showDiag: Boolean
        get() = prefs.getBoolean("showDiag", false)
        set(v) = prefs.edit().putBoolean("showDiag", v).apply()

    /** YouTube-Sender in der offiziellen YouTube-App öffnen statt im eingebauten Player. */
    var youtubeExternal: Boolean
        get() = prefs.getBoolean("ytExternal", false)
        set(v) = prefs.edit().putBoolean("ytExternal", v).apply()

    /** PIN-Abfrage vor den Einstellungen (an/aus). */
    var pinEnabled: Boolean
        get() = prefs.getBoolean("pinEnabled", true)
        set(v) = prefs.edit().putBoolean("pinEnabled", v).apply()

    /** Optional: Schlüssel für omdbapi.com, um IMDb-Bewertungen anzuzeigen. */
    var omdbKey: String
        get() = Secure.decrypt(prefs.getString("omdbKey", "")) ?: ""
        set(v) = prefs.edit().putString("omdbKey", if (v.isBlank()) "" else Secure.encrypt(v.trim())).apply()

    /** Erwachsenen-Inhalte ausblenden (Standard: an). */
    var adultFilter: Boolean
        get() = prefs.getBoolean("adultFilter", true)
        set(v) = prefs.edit().putBoolean("adultFilter", v).apply()

    /** Eigene EPG-Zuordnung: Sender-Schlüssel → EPG-Kennung (verschlüsselt, Schlüssel enthalten Login-Daten) */
    fun epgMap(): Map<String, String> = jsonMap(Secure.decrypt(prefs.getString("epgMap", "")) ?: "") { o, k -> o.getString(k) }

    fun saveEpgMap(m: Map<String, String>) {
        prefs.edit().putString("epgMap", Secure.encrypt(JSONObject(m as Map<*, *>).toString())).apply()
    }

    /** Ton-Versatz für alle Sender in Millisekunden (positiv = Ton später) */
    var audioDelay: Int
        get() = prefs.getInt("audioDelay", 0)
        set(v) = prefs.edit().putInt("audioDelay", v).apply()

    /** Zusätzlicher Ton-Versatz je Sender (Sender-Schlüssel → ms) */
    fun audioDelays(): Map<String, Int> = jsonMap(Secure.decrypt(prefs.getString("audioDelays", "")) ?: "") { o, k -> o.getInt(k) }

    fun saveAudioDelays(m: Map<String, Int>) {
        prefs.edit().putString("audioDelays", Secure.encrypt(JSONObject(m as Map<*, *>).toString())).apply()
    }

    private fun <T> jsonMap(json: String, get: (JSONObject, String) -> T): Map<String, T> =
        if (json.isBlank()) emptyMap() else runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { get(o, it) }
        }.getOrDefault(emptyMap())

    /** Belegung der Fernbedienungstasten (JSON) */
    var keyMap: String
        get() = prefs.getString("keyMap", "{}") ?: "{}"
        set(v) = prefs.edit().putString("keyMap", v).apply()

    /** Hoch/Runter nur innerhalb der aktuellen Gruppe */
    var zapInGroup: Boolean
        get() = prefs.getBoolean("zapInGroup", false)
        set(v) = prefs.edit().putBoolean("zapInGroup", v).apply()

    /** Sendername beim Umschalten: 0 = nur bei schwarzem Bild, 1 = 3 Sekunden mit Sendung, 2 = aus */
    var nameDisplay: Int
        get() = prefs.getInt("nameDisplay", 0)
        set(v) = prefs.edit().putInt("nameDisplay", v).apply()

    /** Erweiterte Einstellungen anzeigen */
    var expertMode: Boolean
        get() = prefs.getBoolean("expertMode", false)
        set(v) = prefs.edit().putBoolean("expertMode", v).apply()

    var autostart: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(v) = prefs.edit().putBoolean("autostart", v).apply()

    /** Schriftfarbe (Index in TextColors, 0 = Standard) */
    var textColor: Int
        get() = prefs.getInt("textColor", 0)
        set(v) = prefs.edit().putInt("textColor", v).apply()

    var scheme: Int
        get() = prefs.getInt("scheme", 0)
        set(v) = prefs.edit().putInt("scheme", v).apply()

    /** "MODERN" oder "ENIGMA" */
    var listStyle: String?
        get() = prefs.getString("listStyle", null)
        set(v) = prefs.edit().putString("listStyle", v).apply()

    var aspectMode: String?
        get() = prefs.getString("aspect", null)
        set(v) = prefs.edit().putString("aspect", v).apply()

    /** Bevorzugte Tonspur-Sprache, z. B. "tr" oder "de". */
    var audioLanguage: String?
        get() = prefs.getString("audioLang", null)
        set(v) = prefs.edit().putString("audioLang", v).apply()

    var subtitlesOn: Boolean
        get() = prefs.getBoolean("subtitles", false)
        set(v) = prefs.edit().putBoolean("subtitles", v).apply()

    var proxy: ProxySettings
        get() = ProxySettings(
            type = runCatching { ProxyType.valueOf(prefs.getString("proxyType", "OFF")!!) }.getOrDefault(ProxyType.OFF),
            host = prefs.getString("proxyHost", "")!!,
            port = prefs.getInt("proxyPort", 0),
            user = prefs.getString("proxyUser", "")!!,
            pass = Secure.decrypt(prefs.getString("proxyPass", "")) ?: "",
        )
        set(v) = prefs.edit()
            .putString("proxyType", v.type.name).putString("proxyHost", v.host).putInt("proxyPort", v.port)
            .putString("proxyUser", v.user).putString("proxyPass", if (v.pass.isEmpty()) "" else Secure.encrypt(v.pass))
            .apply()

    /** PIN für die Einstellungen. Standard: 0000. Gespeichert wird nur eine Prüfsumme. */
    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pinSalt", null) ?: return pin == DEFAULT_PIN
        return Secure.hashPin(pin, salt) == prefs.getString("pinHash", null)
    }

    fun setPin(pin: String) {
        val salt = Secure.newSalt()
        prefs.edit().putString("pinSalt", salt).putString("pinHash", Secure.hashPin(pin, salt)).apply()
    }

    val pinIsDefault: Boolean get() = prefs.getString("pinSalt", null) == null || checkPin(DEFAULT_PIN)

    companion object {
        const val DEFAULT_PIN = "0000"
    }

    var lastChannelKey: String?
        get() = Secure.decrypt(prefs.getString("lastChannel", null))
        set(v) = prefs.edit().putString("lastChannel", v?.let { Secure.encrypt(it) }).apply()
}
