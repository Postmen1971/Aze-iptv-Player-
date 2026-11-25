package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class VodLibraryTest {
    private fun movie(id: String, name: String = "Film $id") = VodRef("s1", false, id, name, "http://p/$id.jpg", "7.1", "2024", "mkv", "c1")
    private fun show(id: String) = VodRef("s1", true, id, "Serie $id")
    private fun ep(id: String, s: Int, n: Int) = EpisodeRef(id, "Folge $n", s, n, "mp4")

    @Test
    fun weiterschauenNeuOben() {
        var l = emptyList<ContinueEntry>()
        l = VodLibrary.afterPlay(l, ContinueEntry(movie("1"), "m:s1:1", at = 1), stillOpen = true)
        l = VodLibrary.afterPlay(l, ContinueEntry(movie("2"), "m:s1:2", at = 2), stillOpen = true)
        assertEquals(listOf("2", "1"), l.map { it.ref.id })
        // nochmal Film 1: nach oben, nicht doppelt
        l = VodLibrary.afterPlay(l, ContinueEntry(movie("1"), "m:s1:1", at = 3), stillOpen = true)
        assertEquals(listOf("1", "2"), l.map { it.ref.id })
    }

    @Test
    fun fertigGeschautVerschwindet() {
        var l = listOf(ContinueEntry(movie("1"), "m:s1:1", at = 1), ContinueEntry(movie("2"), "m:s1:2", at = 2))
        l = VodLibrary.afterPlay(l, ContinueEntry(movie("1"), "m:s1:1", at = 3), stillOpen = false)
        assertEquals(listOf("2"), l.map { it.ref.id })
    }

    @Test
    fun serieNurEinmalMitLetzterFolge() {
        var l = emptyList<ContinueEntry>()
        l = VodLibrary.afterPlay(l, ContinueEntry(show("9"), "e:s1:901", ep("901", 1, 1), 1), stillOpen = true)
        l = VodLibrary.afterPlay(l, ContinueEntry(show("9"), "e:s1:902", ep("902", 1, 2), 2), stillOpen = true)
        assertEquals(1, l.size)
        assertEquals("902", l[0].episode?.id)
    }

    @Test
    fun nurMitGemerkterStelleUndPassenderPlaylist() {
        val l = listOf(
            ContinueEntry(movie("1"), "m:s1:1", at = 5),
            ContinueEntry(movie("2"), "m:s1:2", at = 9),
            ContinueEntry(show("3"), "e:s1:30", ep("30", 1, 1), 7),
            ContinueEntry(VodRef("s2", false, "4", "Andere"), "m:s2:4", at = 8),
        )
        val resume = mapOf("m:s1:1" to 120_000L, "m:s1:2" to 0L, "e:s1:30" to 90_000L, "m:s2:4" to 70_000L)
        val movies = VodLibrary.continueFor(l, "s1", false) { resume[it] ?: 0L }
        assertEquals(listOf("1"), movies.map { it.ref.id })
        assertEquals(listOf("3"), VodLibrary.continueFor(l, "s1", true) { resume[it] ?: 0L }.map { it.ref.id })
    }

    @Test
    fun merklisteAnAus() {
        var w = emptyList<VodRef>()
        w = VodLibrary.toggleWatch(w, movie("1"))
        w = VodLibrary.toggleWatch(w, movie("2"))
        assertEquals(listOf("2", "1"), w.map { it.id })
        w = VodLibrary.toggleWatch(w, movie("1", "anderer Name"))
        assertEquals(listOf("2"), w.map { it.id })
        // gleiche id als Serie ist ein anderer Eintrag
        w = VodLibrary.toggleWatch(w, VodRef("s1", true, "2", "Serie 2"))
        assertEquals(2, w.size)
    }

    @Test
    fun neuHinzugefuegtNeuesteZuerst() {
        val items = listOf(
            VodItem("1", "A", null, null, null, "mp4", cat = "c1", added = 100),
            VodItem("2", "B", null, null, null, "mp4", cat = "c1", added = 300),
            VodItem("3", "C", null, null, null, "mp4", cat = "versteckt", added = 900),
            VodItem("4", "D", null, null, null, "mp4", cat = "c1", added = 0),
            VodItem("5", "E", null, null, null, "mp4", cat = "c2", added = 200),
        )
        val n = VodLibrary.newest(items, { it.cat != "versteckt" })
        assertEquals(listOf("2", "5", "1"), n.map { it.id })
        assertEquals(listOf("2"), VodLibrary.newest(items, { true }, 2).drop(1).map { it.id })
    }

    @Test
    fun trailerAusIdOderLink() {
        assertEquals("dQw4w9WgXcQ", VodLibrary.trailerId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", VodLibrary.trailerId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3"))
        assertEquals("dQw4w9WgXcQ", VodLibrary.trailerId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", VodLibrary.trailerId(" https://www.youtube.com/embed/dQw4w9WgXcQ "))
        assertNull(VodLibrary.trailerId(""))
        assertNull(VodLibrary.trailerId("null"))
        assertNull(VodLibrary.trailerId(null))
        assertNull(VodLibrary.trailerId("kein trailer"))
    }

    @Test
    fun speichernUndLaden() {
        val l = listOf(
            ContinueEntry(movie("1"), "m:s1:1", at = 11),
            ContinueEntry(show("9"), "e:s1:902", ep("902", 2, 5), 12),
        )
        assertEquals(l, VodLibrary.continueFromJson(VodLibrary.continueToJson(l)))
        val w = listOf(movie("1"), show("9"))
        assertEquals(w, VodLibrary.watchFromJson(VodLibrary.watchToJson(w)))
        assertTrue(VodLibrary.continueFromJson("kaputt").isEmpty())
        assertTrue(VodLibrary.watchFromJson(null).isEmpty())
    }

    @Test
    fun ampelZeilen() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val now = 1_795_608_000_000L
        val day = 86_400_000L
        val ok = AccountInfo(now + 109 * day + 3_600_000L, "Active", 2, 1)
        assertEquals(AccountInfo.Level.OK, ok.level(now))
        assertEquals("noch 109 Tage", ok.daysLine(now))
        assertEquals("Gültig bis 14.03.2027", ok.dateLine(now, berlin))
        assertEquals("Verbindungen 1 von 2", ok.extraLine())
        val warn = AccountInfo(now + 12 * day + 3_600_000L, "Active")
        assertEquals(AccountInfo.Level.WARN, warn.level(now))
        assertEquals("noch 12 Tage", warn.daysLine(now))
        val bad = AccountInfo(now + 3 * day + 3_600_000L, "Active")
        assertEquals(AccountInfo.Level.BAD, bad.level(now))
        val gone = AccountInfo(now - 3 * day - 3_600_000L, "Expired")
        assertEquals(AccountInfo.Level.BAD, gone.level(now))
        assertEquals("seit 3 Tagen abgelaufen", gone.daysLine(now))
        assertTrue(gone.dateLine(now, berlin).startsWith("Abgelaufen am "))
        val endless = AccountInfo(null, "Active")
        assertNull(endless.daysLine(now))
        assertEquals("Unbegrenzt gültig", endless.dateLine(now, berlin))
        assertNull(endless.extraLine())
    }
}
