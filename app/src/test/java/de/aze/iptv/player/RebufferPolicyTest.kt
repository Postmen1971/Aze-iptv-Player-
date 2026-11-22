package de.aze.iptv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schneller Wiederanlauf nach einer Bildlücke: nur beim ersten Hänger seit 3 Minuten, je Sender neu. */
class RebufferPolicyTest {

    @Test
    fun firstStall_isQuick() {
        assertTrue(RebufferPolicy().onStall(1_000_000L))
    }

    @Test
    fun secondStallWithinWindow_waitsFull() {
        val p = RebufferPolicy()
        assertTrue(p.onStall(1_000_000L))
        assertFalse(p.onStall(1_000_000L + 60_000L))
        // dritter kurz danach: weiter voll (Leitung wackelig)
        assertFalse(p.onStall(1_000_000L + 150_000L))
    }

    @Test
    fun stallAfterQuietWindow_isQuickAgain() {
        val p = RebufferPolicy()
        assertTrue(p.onStall(1_000_000L))
        assertTrue(p.onStall(1_000_000L + 181_000L))
    }

    @Test
    fun windowCountsFromLastStall_notFromFirst() {
        val p = RebufferPolicy()
        assertTrue(p.onStall(0L))
        assertFalse(p.onStall(170_000L))
        // 3 min nach dem ERSTEN, aber nur 20 s nach dem letzten Hänger: noch wackelig
        assertFalse(p.onStall(190_000L))
    }

    @Test
    fun newChannel_resetsHistory() {
        val p = RebufferPolicy()
        assertTrue(p.onStall(1_000_000L))
        p.reset()
        assertTrue(p.onStall(1_000_000L + 10_000L))
    }

    @Test
    fun clockStartingAtZero_firstStallStillQuick() {
        // elapsedRealtime kann klein sein (frisch gestartetes Gerät) – kein Überlauf beim ersten Vergleich
        assertTrue(RebufferPolicy().onStall(0L))
    }
}
