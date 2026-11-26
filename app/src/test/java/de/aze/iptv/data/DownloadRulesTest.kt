package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRulesTest {
    private val film = VodRef("s1", false, "42", "Kahramanmaraş: Die Reise?", ext = "MKV")
    private val serie = VodRef("s1", true, "7", "Hazine Avcıları")

    @Test
    fun fernsehenHatVorrang() {
        assertEquals(DownloadRules.Mode.FULL, DownloadRules.mode(watching = false, maxCons = 1))
        // nur eine Verbindung (oder unbekannt): warten, sonst lehnt der Anbieter das Fernsehen ab
        assertEquals(DownloadRules.Mode.WAIT, DownloadRules.mode(watching = true, maxCons = 1))
        assertEquals(DownloadRules.Mode.WAIT, DownloadRules.mode(watching = true, maxCons = null))
        assertEquals(DownloadRules.Mode.SLOW, DownloadRules.mode(watching = true, maxCons = 2))
    }

    @Test
    fun speicherPlatz() {
        val gb = 1024L * 1024 * 1024
        assertTrue(DownloadRules.fits(total = 2 * gb, done = 0, free = 3 * gb))
        // 500 MB müssen frei bleiben
        assertFalse(DownloadRules.fits(total = 2 * gb, done = 0, free = 2 * gb + 100L * 1024 * 1024))
        // schon zur Hälfte geladen: nur der Rest zählt
        assertTrue(DownloadRules.fits(total = 2 * gb, done = gb, free = gb + 600L * 1024 * 1024))
        assertTrue(DownloadRules.fits(total = -1, done = 0, free = gb))
        assertFalse(DownloadRules.fits(total = -1, done = 0, free = 100L * 1024 * 1024))
    }

    @Test
    fun dateinameOhneSonderzeichen() {
        val n = DownloadRules.fileName(DlEntry("m:s1:42", film))
        assertTrue(n, n.startsWith("Kahramanmaraş Die Reise [") && n.endsWith("].mkv"))
        assertFalse(n.contains(':') || n.contains('?'))
        val f = DownloadRules.fileName(DlEntry("e:s1:9", serie, EpisodeRef("9", "Pilot", 1, 3, "mp4")))
        assertTrue(f, f.startsWith("Hazine Avcıları – S1 E3 · Pilot [") && f.endsWith(".mp4"))
        // gleicher Titel, anderer Eintrag → anderer Name
        assertFalse(n == DownloadRules.fileName(DlEntry("m:s1:43", film)))
    }

    @Test
    fun fortsetzenNachAbbruch() {
        assertEquals(100L to 1000L, DownloadRules.resumeInfo(100, 206, "bytes 100-999/1000", 900))
        // Server kann nicht fortsetzen → von vorn
        assertEquals(0L to 1000L, DownloadRules.resumeInfo(100, 200, null, 1000))
        // falscher Anfang → von vorn (null)
        assertNull(DownloadRules.resumeInfo(100, 206, "bytes 0-999/1000", 1000))
        // Gesamtgröße unbekannt
        assertEquals(100L to 1000L, DownloadRules.resumeInfo(100, 206, "bytes 100-999/*", 900))
        assertEquals(0L to -1L, DownloadRules.resumeInfo(0, 200, null, -1))
    }

    @Test
    fun wartezeiten() {
        assertEquals(5_000L, DownloadRules.retryDelayMs(0, rejected = false))
        assertEquals(120_000L, DownloadRules.retryDelayMs(20, rejected = false))
        assertEquals(60_000L, DownloadRules.retryDelayMs(0, rejected = true))
    }

    @Test
    fun speichernUndLesen_laufendWirdWartend() {
        val l = listOf(
            DlEntry("m:s1:42", film, state = DlState.RUN, done = 5, total = 10, file = "/x/a.mkv", added = 1),
            DlEntry("e:s1:9", serie, EpisodeRef("9", "Pilot", 1, 3, "mp4"), DlState.DONE, 10, 10, "/x/b.mp4", "alt", 2),
        )
        val back = DownloadRules.fromJson(DownloadRules.toJson(l))
        assertEquals(2, back.size)
        assertEquals(DlState.WAIT, back[0].state)
        assertEquals(5L, back[0].done)
        assertEquals(l[1], back[1])
        assertEquals(50, back[0].percent)
        assertEquals("Hazine Avcıları – S1 E3 · Pilot", back[1].title)
    }

    @Test
    fun groessen() {
        assertEquals("1,5 GB", DownloadRules.size(1536L * 1024 * 1024))
        assertEquals("700 MB", DownloadRules.size(700L * 1024 * 1024))
    }
}
