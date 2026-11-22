package de.aze.iptv.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.Allocator

/**
 * Media3 1.5.1 (Vergleichs-App „Aze-IPTV-46“): getAllocator ohne Player-Kennung.
 * Gegenstück für die normale App (Media3 1.11): src/media3new/…/LoadControlCompat.kt
 */
@OptIn(UnstableApi::class)
abstract class AllocatorForwarding(protected val base: LoadControl) : LoadControl {
    override fun getAllocator(): Allocator = base.allocator
}
