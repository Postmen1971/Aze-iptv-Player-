package de.aze.iptv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dauerhafte Einstellungen: Quellen, Favoriten, Aktualisierungsintervall, letzter Sender. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("aze", Context.MODE_PRIVATE)

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
        get() = prefs.getBoolean("afr", true)
        set(v) = prefs.edit().putBoolean("afr", v).apply()

    var autostart: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(v) = prefs.edit().putBoolean("autostart", v).apply()

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
