package de.aze.iptv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Eigene Anordnung der Senderliste (wie das Bouquet-Bearbeiten bei Enigma2).
 * Gruppen werden über "playlist-id|gruppenname" erkannt, Sender über ihren Stream-Link.
 */
data class ListLayout(
    val groupOrder: List<String> = emptyList(),
    val hiddenGroups: Set<String> = emptySet(),
    val groupNames: Map<String, String> = emptyMap(),
    val channelOrder: Map<String, List<String>> = emptyMap(),
    val hiddenChannels: Set<String> = emptySet(),
) {
    fun toJson(): String = JSONObject()
        .put("groupOrder", JSONArray(groupOrder))
        .put("hiddenGroups", JSONArray(hiddenGroups.toList()))
        .put("groupNames", JSONObject(groupNames as Map<*, *>))
        .put("channelOrder", JSONObject().apply { channelOrder.forEach { (k, v) -> put(k, JSONArray(v)) } })
        .put("hiddenChannels", JSONArray(hiddenChannels.toList()))
        .toString()

    companion object {
        fun groupKey(sourceId: String, group: String) = "$sourceId|$group"

        fun fromJson(json: String?): ListLayout {
            if (json.isNullOrBlank()) return ListLayout()
            return runCatching {
                val o = JSONObject(json)
                fun arr(name: String): List<String> {
                    val a = o.optJSONArray(name) ?: return emptyList()
                    return (0 until a.length()).map { a.getString(it) }
                }
                val names = o.optJSONObject("groupNames")
                val order = o.optJSONObject("channelOrder")
                ListLayout(
                    groupOrder = arr("groupOrder"),
                    hiddenGroups = arr("hiddenGroups").toSet(),
                    groupNames = names?.keys()?.asSequence()?.associateWith { names.getString(it) }.orEmpty(),
                    channelOrder = order?.keys()?.asSequence()?.associateWith { k ->
                        val a = order.getJSONArray(k)
                        (0 until a.length()).map { a.getString(it) }
                    }.orEmpty(),
                    hiddenChannels = arr("hiddenChannels").toSet(),
                )
            }.getOrDefault(ListLayout())
        }
    }
}

/** Eine Gruppe in der Bearbeiten-Ansicht – auch ausgeblendete. */
data class EditGroup(
    val key: String,
    val name: String,
    val originalName: String,
    val sourceName: String?,
    val channels: List<Channel>,
    val hidden: Boolean,
)
