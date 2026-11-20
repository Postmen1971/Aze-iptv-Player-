package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Senderliste zum Bearbeiten außerhalb der App (Handy/PC im Heimnetz, Fernhilfe über das Internet).
 * Es verlassen nur Namen das Gerät – keine Sender-Links (die enthalten oft Zugangsdaten).
 * Zurück kommen nur Positionen (Index der Gruppe bzw. des Senders im verschickten Stand) + ausgeblendet ja/nein.
 */
object ListEditCodec {
    /** Gruppen als JSON: n = Name, o = Originalname, s = Playlist, h = ausgeblendet, c = [[Sendername, ausgeblendet]] */
    fun encode(list: List<EditGroup>, hidden: Set<String>): JSONArray {
        val arr = JSONArray()
        list.forEach { g ->
            val ch = JSONArray()
            g.channels.forEach { c -> ch.put(JSONArray().put(c.name).put(if (c.key in hidden) 1 else 0)) }
            arr.put(
                JSONObject()
                    .put("n", g.name).put("o", g.originalName).put("s", g.sourceName ?: "")
                    .put("h", if (g.hidden) 1 else 0).put("c", ch),
            )
        }
        return arr
    }

    /**
     * Bearbeiteten Stand lesen: groups = [{i: Index der Gruppe, n: Name, h: ausgeblendet, c: [[Index des Senders, ausgeblendet]]}]
     * in der neuen Reihenfolge. Alles muss vollständig sein (jede Gruppe und jeder Sender genau einmal).
     */
    fun decode(arr: JSONArray, snap: List<EditGroup>): List<WebGroupEdit> {
        require(arr.length() == snap.size) { "Gruppen unvollständig" }
        val seenGroups = HashSet<Int>()
        return (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            val gi = g.getInt("i")
            require(gi in snap.indices && seenGroups.add(gi)) { "Ungültige Gruppe" }
            val orig = snap[gi]
            val c = g.getJSONArray("c")
            require(c.length() == orig.channels.size) { "Sender unvollständig in „${orig.name}“" }
            val seen = HashSet<Int>()
            val channels = (0 until c.length()).map { k ->
                val pair = c.getJSONArray(k)
                val ci = pair.getInt(0)
                require(ci in orig.channels.indices && seen.add(ci)) { "Ungültiger Sender" }
                orig.channels[ci].key to (pair.optInt(1) == 1)
            }
            WebGroupEdit(orig.key, g.optString("n").take(80), g.optInt("h") == 1, channels)
        }
    }
}
