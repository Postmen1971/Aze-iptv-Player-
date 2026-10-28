package de.aze.iptv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** Eine wählbare Ton- oder Untertitelspur. */
data class TrackOption(
    val label: String,
    val language: String?,
    val group: Tracks.Group,
    val index: Int,
    val selected: Boolean,
)

/** Liest Ton- und Untertitelspuren aus dem Player und schaltet sie um. */
object TrackHelper {

    fun options(player: Player, type: Int): List<TrackOption> {
        val result = ArrayList<TrackOption>()
        player.currentTracks.groups.filter { it.type == type }.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                val langName = lang?.let { Locale(it).getDisplayLanguage(Locale.GERMAN).replaceFirstChar { c -> c.uppercase() } }
                val base = f.label?.takeIf { it.isNotBlank() } ?: langName ?: "Spur ${result.size + 1}"
                val extra = if (type == C.TRACK_TYPE_AUDIO) when (f.channelCount) {
                    1 -> " · Mono"
                    2 -> " · Stereo"
                    6 -> " · 5.1"
                    8 -> " · 7.1"
                    else -> ""
                } else ""
                result.add(TrackOption(base + extra, lang, group, i, group.isTrackSelected(i)))
            }
        }
        return result
    }

    /** Wählt die nächste Tonspur. Gibt die neue Spur zurück. */
    @OptIn(UnstableApi::class)
    fun nextAudio(player: Player): TrackOption? {
        val opts = options(player, C.TRACK_TYPE_AUDIO)
        if (opts.size < 2) return opts.firstOrNull()
        val cur = opts.indexOfFirst { it.selected }
        val next = opts[(cur + 1) % opts.size]
        select(player, next)
        return next
    }

    /** Untertitel: Aus → Spur 1 → Spur 2 → … → Aus. Gibt null zurück, wenn jetzt aus. */
    fun nextSubtitle(player: Player): TrackOption? {
        val opts = options(player, C.TRACK_TYPE_TEXT)
        val off = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val cur = if (off) -1 else opts.indexOfFirst { it.selected }
        val nextIndex = cur + 1
        return if (nextIndex >= opts.size) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            null
        } else {
            select(player, opts[nextIndex])
            opts[nextIndex]
        }
    }

    fun select(player: Player, option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.group.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
    }

    fun currentLabel(player: Player, type: Int, emptyLabel: String): String {
        if (type == C.TRACK_TYPE_TEXT && player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            return if (options(player, type).isEmpty()) "Keine" else "Aus"
        }
        val opts = options(player, type)
        if (opts.isEmpty()) return emptyLabel
        val idx = opts.indexOfFirst { it.selected }
        val label = opts.getOrNull(idx)?.label ?: opts.first().label
        return if (opts.size > 1) "$label (${idx + 1}/${opts.size})" else label
    }
}
