package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSectionsTest {
    private fun ch(n: Int) = NumberedChannel(n, Channel(name = "S$n", group = "g", logo = null, url = "http://x/$n"), false)
    private fun g(name: String, src: String?, fav: Boolean = false) =
        ChannelGroup(name, fav, listOf(ch(1)), sourceId = src, sourceName = src?.let { "Liste $it" })

    private val groups = listOf(
        g("Favoriten", null, fav = true),
        g("DE", "a"), g("TR", "a"),
        g("Sport", "b"),
        g("Kinder", "a"),
    )

    @Test
    fun einePlaylistEinAbschnitt() {
        val s = GroupSections.build(groups, multi = false, singleId = "a", singleTitle = "Mein Anbieter")
        assertEquals(1, s.size)
        assertEquals("Mein Anbieter", s[0].title)
        assertEquals(listOf(0, 1, 2, 3, 4), s[0].groups)
    }

    @Test
    fun mehrerePlaylistsJeEinAbschnitt() {
        val s = GroupSections.build(groups, multi = true, singleId = null, singleTitle = "")
        assertEquals(listOf("*", "a", "b"), s.map { it.id })
        assertEquals(listOf("Alle Playlists", "Liste a", "Liste b"), s.map { it.title })
        // Reihenfolge innerhalb der Playlist bleibt, auch wenn Gruppen dazwischen umsortiert wurden
        assertEquals(listOf(1, 2, 4), s[1].groups)
    }

    @Test
    fun zugeklapptZeigtNurDenNamen() {
        val s = GroupSections.build(groups, multi = true, singleId = null, singleTitle = "")
        val rows = GroupSections.rows(s, setOf("a"))
        assertEquals(5, rows.size) // 3 Köpfe + Favoriten + Sport
        assertEquals(true, (rows[2] as GroupSections.Row.Header).collapsed)
        assertEquals(3, (rows[4] as GroupSections.Row.Item).index)
    }

    @Test
    fun weiterschaltenUeberspringtZugeklappte() {
        val s = GroupSections.build(groups, multi = true, singleId = null, singleTitle = "")
        // von Favoriten (0) weiter: a ist zu → Sport (3)
        assertEquals(3, GroupSections.next(s, setOf("a"), groups.size, 0, +1) { true })
        // alles zu → wie bisher alle Gruppen
        assertEquals(1, GroupSections.next(s, setOf("*", "a", "b"), groups.size, 0, +1) { true })
        // nichts zu → nächste
        assertEquals(1, GroupSections.next(s, emptySet(), groups.size, 0, +1) { true })
    }
}
