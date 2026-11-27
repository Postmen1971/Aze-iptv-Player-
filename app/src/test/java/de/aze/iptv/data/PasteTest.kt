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
