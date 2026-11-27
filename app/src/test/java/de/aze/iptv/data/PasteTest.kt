package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasteTest {
    @Test
    fun dpasteOrgKennung() {
        assertEquals("djGJu", HangLog.dpasteOrgId("https://dpaste.org/djGJu"))
        assertEquals("djGJu", HangLog.dpasteOrgId("https://dpaste.org/djGJu/raw"))
        assertEquals("djGJu", HangLog.dpasteOrgId("https://dpaste.org/djGJu/\n"))
        assertNull(HangLog.dpasteOrgId("https://dpaste.org/<html>"))
    }
}

class NeuOhneErwachseneTest {
    @Test
    fun kategorienMitXSindFuerErwachsene() {
        org.junit.Assert.assertTrue(VodLibrary.adultCategory("XXX"))
        org.junit.Assert.assertTrue(VodLibrary.adultCategory("XXX | Adult 18+"))
        org.junit.Assert.assertTrue(VodLibrary.adultCategory("DE | XXX Filme"))
        org.junit.Assert.assertTrue(VodLibrary.adultCategory("X-Rated"))
        org.junit.Assert.assertTrue(VodLibrary.adultCategory("TR: Yetişkin"))
        org.junit.Assert.assertFalse(VodLibrary.adultCategory("DE | Action"))
        org.junit.Assert.assertFalse(VodLibrary.adultCategory("TR SINEMA"))
    }

    @Test
    fun neuNurNormaleFilme() {
        val cats = mapOf("1" to "DE | Action", "2" to "XXX")
        val all = listOf(
            VodItem("a", "Film A", null, null, null, "mp4", "1", added = 300),
            VodItem("b", "Heißer Film", null, null, null, "mp4", "2", added = 500),
            VodItem("c", "Film C", null, null, null, "mp4", "1", added = 400),
        )
        val shown = cats.filterValues { !VodLibrary.adultCategory(it) }.keys
        val neu = VodLibrary.newest(all, { (it.cat == null || it.cat in shown) && !AdultFilter.isAdult(it.name) })
        assertEquals(listOf("c", "a"), neu.map { it.id })
    }
}
