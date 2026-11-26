package de.aze.iptv.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbTrailerTest {
    @Test
    fun deutscheTrailerZuerst_TeaserNachTrailer_nurYouTube() {
        val a = JSONArray(
            """[
              {"site":"YouTube","key":"enTrailer01","type":"Trailer","iso_639_1":"en","official":true},
              {"site":"YouTube","key":"deTeaser001","type":"Teaser","iso_639_1":"de","official":true},
              {"site":"Vimeo","key":"vimeo123456","type":"Trailer","iso_639_1":"de"},
              {"site":"YouTube","key":"deTrailer01","type":"Trailer","iso_639_1":"de","official":false},
              {"site":"YouTube","key":"deTrailer02","type":"Trailer","iso_639_1":"de","official":true},
              {"site":"YouTube","key":"trTrailer01","type":"Trailer","iso_639_1":"tr","official":true},
              {"site":"YouTube","key":"behindScene","type":"Behind the Scenes","iso_639_1":"de"},
              {"site":"YouTube","key":"kaputt","type":"Trailer","iso_639_1":"de"}
            ]""",
        )
        assertEquals(
            listOf("deTrailer02", "deTrailer01", "deTeaser001", "trTrailer01", "enTrailer01"),
            Tmdb.rankTrailers(a),
        )
        assertEquals(emptyList<String>(), Tmdb.rankTrailers(null))
    }
}
