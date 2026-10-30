package de.aze.iptv.data

import org.json.JSONObject

/** Was eine Taste beim Fernsehen auslöst. */
enum class KeyAction(val label: String) {
    NONE("Nichts"),
    PREVIOUS("Letzter Sender"),
    LIST("Senderliste"),
    GROUPS("Gruppen"),
    QUICK("Schnellmenü"),
    NEXT_CH("Nächster Sender"),
    PREV_CH("Vorheriger Sender"),
    SEARCH("Suche"),
    FOOTBALL("Fußball heute"),
    SKORTEXT("SkorText"),
    MOVIES("Filme"),
    SERIES("Serien"),
    SETTINGS("Einstellungen"),
}

/** Frei belegbare Tasten der Fernbedienung (Zifferntasten und Zurück bleiben fest). */
enum class RemoteKey(val label: String, val default: KeyAction) {
    OK_SHORT("OK kurz", KeyAction.PREVIOUS),
    OK_LONG("OK lang", KeyAction.QUICK),
    UP("Hoch", KeyAction.NEXT_CH),
    DOWN("Runter", KeyAction.PREV_CH),
    LEFT("Links", KeyAction.LIST),
    RIGHT("Rechts", KeyAction.LIST),
    MENU("Menü-Taste", KeyAction.QUICK),
    RED("Rot", KeyAction.LIST),
    GREEN("Grün", KeyAction.QUICK),
    YELLOW("Gelb", KeyAction.SEARCH),
    BLUE("Blau", KeyAction.SETTINGS),
    REWIND("Zurückspulen", KeyAction.PREVIOUS),
    FORWARD("Vorspulen", KeyAction.LIST),
}

object KeyMap {
    fun parse(json: String?): Map<RemoteKey, KeyAction> {
        val o = runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject())
        return RemoteKey.entries.associateWith { k ->
            runCatching { KeyAction.valueOf(o.optString(k.name)) }.getOrDefault(k.default)
        }
    }

    fun toJson(map: Map<RemoteKey, KeyAction>): String =
        JSONObject().apply { map.forEach { (k, v) -> put(k.name, v.name) } }.toString()
}
