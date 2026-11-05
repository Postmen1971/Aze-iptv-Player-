package de.aze.iptv

import de.aze.iptv.data.Tmdb
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbTest {
    @Test fun titelAufraeumen() {
        assertEquals("Der Pate" to "1972", Tmdb.clean("DE - Der Pate (1972)"))
        assertEquals("Kurtlar Vadisi" to null, Tmdb.clean("|TR| Kurtlar Vadisi [4K]"))
        assertEquals("Oppenheimer" to "2023", Tmdb.clean("EN: Oppenheimer 2023 FHD"))
        assertEquals("Up - Oben" to null, Tmdb.clean("Up - Oben"))
    }
}
