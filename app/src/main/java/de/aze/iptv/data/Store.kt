package de.aze.iptv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dauerhafte Einstellungen: Quellen, Favoriten, Aktualisierungsintervall, letzter Sender. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("aze", Context.MODE_PRIVATE)

    fun sources(): List<PlaylistSource> {
        val arr = JSONArray(prefs.getString("sources", "[]"))
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
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    fun favorites(): List<String> {
        val arr = JSONArray(prefs.getString("favorites", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun saveFavorites(keys: List<String>) {
        prefs.edit().putString("favorites", JSONArray(keys).toString()).apply()
    }

    /** 0 = nur manuell, sonst Stunden (24, 48, 72, 336 = zwei Wochen). */
    var refreshHours: Int
        get() = prefs.getInt("refreshHours", 24)
        set(v) = prefs.edit().putInt("refreshHours", v).apply()

    var lastRefresh: Long
        get() = prefs.getLong("lastRefresh", 0L)
        set(v) = prefs.edit().putLong("lastRefresh", v).apply()

    var lastChannelKey: String?
        get() = prefs.getString("lastChannel", null)
        set(v) = prefs.edit().putString("lastChannel", v).apply()
}
