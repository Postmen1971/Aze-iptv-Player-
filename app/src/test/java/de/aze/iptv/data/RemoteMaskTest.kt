package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMaskTest {
    private val xtream = PlaylistSource("a", "Mein Anbieter", SourceType.XTREAM, "http://srv.example:8080", "aziz", "geheim123")
    private val m3u = PlaylistSource("b", "Liste", SourceType.M3U, "http://srv.example/get.php?username=aziz&password=geheim123&type=m3u_plus")
    private val portal = PlaylistSource("c", "Portal", SourceType.STALKER, "http://portal.example/c/", "00:1A:79:12:34:56")

    @Test
    fun helferSiehtKeineGeheimnisse() {
        val x = RemoteMask.forHelper(xtream)
        assertEquals(RemoteMask.HIDDEN, x.pass)
        assertEquals("aziz", x.user)
        val m = RemoteMask.forHelper(m3u)
        assertFalse("geheim123" in m.url)
        assertEquals("http://srv.example/get.php?username=aziz&password=${RemoteMask.HIDDEN}&type=m3u_plus", m.url)
        val p = RemoteMask.forHelper(portal)
        assertEquals("00:1A:79:${RemoteMask.HIDDEN}", p.user)
    }

    @Test
    fun unveraendertZurueckBehaeltAlteWerte() {
        assertEquals(xtream.copy(name = "Neu"), RemoteMask.fromHelper(RemoteMask.forHelper(xtream).copy(name = "Neu"), xtream))
        assertEquals(m3u, RemoteMask.fromHelper(RemoteMask.forHelper(m3u), m3u))
        assertEquals(portal, RemoteMask.fromHelper(RemoteMask.forHelper(portal), portal))
    }

    @Test
    fun neuEingetipptGilt() {
        val r = RemoteMask.fromHelper(RemoteMask.forHelper(xtream).copy(pass = "neuesPw"), xtream)
        assertEquals("neuesPw", r?.pass)
        val u = RemoteMask.fromHelper(m3u.copy(url = "http://neu.example/get.php?username=x&password=y"), m3u)
        assertEquals("http://neu.example/get.php?username=x&password=y", u?.url)
    }

    @Test
    fun platzhalterOhneAltenWertWirdAbgelehnt() {
        // neue Playlist mit Platzhalter (kann es eigentlich nicht geben) → nichts übernehmen
        assertNull(RemoteMask.fromHelper(RemoteMask.forHelper(xtream), null))
        // andere MAC als gespeichert → nicht raten
        assertNull(RemoteMask.fromHelper(portal.copy(user = "00:AA:BB:${RemoteMask.HIDDEN}"), portal))
    }

    @Test
    fun leeresPasswortBleibtLeer() {
        val s = xtream.copy(pass = "")
        assertEquals("", RemoteMask.forHelper(s).pass)
        assertEquals(s, RemoteMask.fromHelper(RemoteMask.forHelper(s), s))
    }
}
