package de.aze.iptv.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.upstream.Allocator

/**
 * Media3 1.11 (normale App): getAllocator bekommt die Player-Kennung.
 * Gegenstück für die Vergleichs-App (Media3 1.5.1): src/media3old/…/LoadControlCompat.kt
 */
@OptIn(UnstableApi::class)
abstract class AllocatorForwarding(protected val base: LoadControl) : LoadControl {
    override fun getAllocator(playerId: PlayerId): Allocator = base.getAllocator(playerId)
}
