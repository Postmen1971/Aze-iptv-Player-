package de.aze.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowStallTest {
    @Test
    fun genugVorratHeisstSofortWeiter() {
        assertEquals(0L, SlowStall.remainingMs(aheadMs = 4_000, resumeAtMs = 4_000, gainedMs = 0, stallMs = 3_000))
    }

    @Test
    fun nichtsKommtHeisstNie() {
        assertEquals(Long.MAX_VALUE, SlowStall.remainingMs(500, 4_000, 0, 5_000))
        // unter 5 % Echtzeit zählt als nichts
        assertEquals(Long.MAX_VALUE, SlowStall.remainingMs(500, 4_000, 100, 5_000))
    }

    @Test
    fun restzeitAusLiefergeschwindigkeit() {
        // 5 s Hänger, 1,25 s dazugekommen = 0,25-fach; bis 4 s fehlen 2,75 s → 11 s
        assertEquals(11_000L, SlowStall.remainingMs(1_250, 4_000, 1_250, 5_000))
    }

    @Test
    fun zaeherNachschubLohntNeuverbinden() {
        // Protokoll-Fall: 0,25-fach, Mittel (4 s): nach 5 s noch 11 s warten → neu verbinden
        assertTrue(SlowStall.shouldReconnect(stallSec = 5, aheadMs = 1_250, resumeAtMs = 4_000, gainedMs = 1_250, windowMs = 5_000, proven = false))
    }

    @Test
    fun fastGeschafftNichtUnterbrechen() {
        // 0,8-fach: nach 5 s 4 s Vorrat – Ziel erreicht
        assertFalse(SlowStall.shouldReconnect(5, 4_000, 4_000, 4_000, 5_000, false))
        // erster Hänger (schneller Wiederanlauf ab 2 s), 0,35-fach: nach 5 s 1,75 s → nur noch 0,7 s
        assertFalse(SlowStall.shouldReconnect(5, 1_750, 2_000, 1_750, 5_000, false))
    }

    @Test
    fun unbekannterSenderWartetFuenfSekunden() {
        assertFalse(SlowStall.shouldReconnect(4, 400, 4_000, 400, 4_000, false))
        assertTrue(SlowStall.shouldReconnect(3, 300, 4_000, 300, 3_000, true))
        assertFalse(SlowStall.shouldReconnect(2, 200, 4_000, 200, 2_000, true))
    }

    @Test
    fun garNichtsIstSacheDesWaechters() {
        assertFalse(SlowStall.shouldReconnect(10, 0, 4_000, 0, 10_000, true))
        assertEquals(5, SlowStall.starvedLimit(firstFrameSeen = true, proven = false))
        assertEquals(3, SlowStall.starvedLimit(firstFrameSeen = true, proven = true))
        assertEquals(8, SlowStall.starvedLimit(firstFrameSeen = false, proven = true))
    }
}
