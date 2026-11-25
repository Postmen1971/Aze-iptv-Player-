package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSearchTest {
    private data class M(val name: String, val cat: String = "1")

    private fun find(items: List<M>, q: String, hidden: Set<String> = emptySet()) =
        VodSearch.search(items, q, { it.name }) { it.cat !in hidden }.map { it.name }

    @Test
    fun sonderbuchstabenEgal() {
        val l = listOf(M("Der Schatz von Kahramanmaraş (2023)"), M("İstanbul Hatırası"), M("Das Mädchen"), M("Straße der Liebe"))
        assertEquals(listOf("Der Schatz von Kahramanmaraş (2023)"), find(l, "kahramanmaras"))
        assertEquals(listOf("İstanbul Hatırası"), find(l, "istanbul hatirasi"))
        assertEquals(listOf("Das Mädchen"), find(l, "madchen"))
        assertEquals(listOf("Das Mädchen"), find(l, "Maedchen"))
        assertEquals(listOf("Straße der Liebe"), find(l, "strasse"))
    }

    @Test
    fun mehrereWoerterBeliebigeReihenfolge() {
        val l = listOf(M("The Matrix (1999)"), M("Matrix Reloaded (2003)"), M("1999"))
        assertEquals(listOf("The Matrix (1999)"), find(l, "1999 matrix"))
    }

    @Test
    fun ausgeblendeteKategorienZaehlenNicht() {
        val l = listOf(M("Schatzinsel", "1"), M("Schatzjäger", "2"), M("Der Schatz", "3"))
        assertEquals(listOf("Schatzinsel", "Der Schatz"), find(l, "schatz", hidden = setOf("2")))
    }

    @Test
    fun reihenfolgeGenauDannAnfangDannWortanfang() {
        val l = listOf(
            M("Rückkehr nach Avatar"),       // Wortanfang
            M("DE - Avatar: The Way of Water"), // Anfang (Kürzel zählt nicht)
            M("Kavatar"),                    // nur enthalten
            M("TR | Avatar"),                // genau
        )
        assertEquals(listOf("TR | Avatar", "DE - Avatar: The Way of Water", "Rückkehr nach Avatar", "Kavatar"), find(l, "avatar"))
    }

    @Test
    fun leereSucheFindetNichts() {
        assertTrue(find(listOf(M("A")), "   ").isEmpty())
        assertTrue(find(listOf(M("A")), "–").isEmpty())
    }

    @Test
    fun kuerzelWerdenEntfernt() {
        assertEquals("Avatar", VodSearch.clean("DE - Avatar"))
        assertEquals("Avatar", VodSearch.clean("[4K] TR | Avatar"))
        assertEquals("Up", VodSearch.clean("EN: Up"))
        // kein Kürzel: bleibt
        assertEquals("Matrix - Reloaded", VodSearch.clean("Matrix - Reloaded"))
    }
}
