package de.aze.iptv

import de.aze.iptv.data.AdultFilter
import de.aze.iptv.data.Channel
import de.aze.iptv.data.Epg
import de.aze.iptv.data.Football
import de.aze.iptv.data.KeyAction
import de.aze.iptv.data.KeyMap
import de.aze.iptv.data.ListLayout
import de.aze.iptv.data.LiveOnSat
import de.aze.iptv.data.M3uParser
import de.aze.iptv.data.Numbering
import de.aze.iptv.data.Programme
import de.aze.iptv.data.RemoteKey
import de.aze.iptv.data.TodayMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Automatische Prüfung der App-Logik (läuft bei jedem Build in GitHub Actions). */
class LogicTest {

    private val m3u = """
        #EXTM3U url-tvg="http://epg.example/epg.xml"
        #EXTINF:-1 tvg-id="ard.de" tvg-logo="http://l/ard.png" group-title="Deutschland",Das Erste HD
        http://s.example/live/u/p/1.ts
        #EXTINF:-1 tvg-id='zdf.de' group-title='Deutschland',ZDF
        http://s.example/live/u/p/2.ts
        #EXTINF:-1 group-title="TR-SPOR",beIN Sports 1 HD
        http://s.example/live/u/p/3.ts
        #EXTINF:-1 group-title="Filme",Ein Film (2020)
        http://s.example/movie/u/p/100.mkv
        #EXTINF:-1,Ohne Gruppe Sender
        #EXTGRP:Sonstiges
        http://s.example/live/u/p/4.ts
        #EXTINF:-1 group-title="XXX Adult",Nachtprogramm
        http://s.example/live/u/p/5.ts
    """.trimIndent()

    @Test fun m3uParser_readsGroupsAndSkipsMovies() {
        val list = M3uParser.parse(m3u)
        assertEquals(5, list.size) // Film übersprungen
        assertEquals("Das Erste HD", list[0].name)
        assertEquals("Deutschland", list[0].group)
        assertEquals("ard.de", list[0].tvgId)
        assertEquals("http://l/ard.png", list[0].logo)
        assertEquals("zdf.de", list[1].tvgId) // einfache Anführungszeichen
        assertEquals("Sonstiges", list[3].group) // #EXTGRP
    }

    @Test fun numbering_favoritesFirstAndHiddenSkipped() {
        val ch = M3uParser.parse(m3u).map { it.copy(source = "s1") }
        val fav = listOf(ch[2].key) // beIN = Favorit
        val layout = ListLayout(hiddenGroups = setOf(ListLayout.groupKey("s1", "XXX Adult")))
        val lu = Numbering.build(ch, fav, emptyList(), layout)
        assertEquals(1, lu.byKey.getValue(ch[2].key).number) // Favorit bekommt 1
        assertEquals(2, lu.byKey.getValue(ch[0].key).number) // danach weiter
        assertFalse(lu.byKey.containsKey(ch[4].key)) // ausgeblendete Gruppe hat keine Nummer
        // Favorit erscheint nur einmal
        assertEquals(1, lu.all.count { it.channel.key == ch[2].key })
        // Nummern lückenlos
        assertEquals((1..lu.all.size).toList(), lu.all.map { it.number })
    }

    @Test fun numbering_customOrderAndHiddenChannel() {
        val ch = M3uParser.parse(m3u).map { it.copy(source = "s1") }
        val key = ListLayout.groupKey("s1", "Deutschland")
        val layout = ListLayout(channelOrder = mapOf(key to listOf(ch[1].key, ch[0].key)), hiddenChannels = setOf(ch[3].key))
        val lu = Numbering.build(ch, emptyList(), emptyList(), layout)
        assertEquals(1, lu.byKey.getValue(ch[1].key).number) // ZDF vor ARD
        assertFalse(lu.byKey.containsKey(ch[3].key))
    }

    @Test fun adultFilter() {
        assertTrue(AdultFilter.isAdult("XXX Adult"))
        assertTrue(AdultFilter.isAdult("TR | Yetişkin +18"))
        assertTrue(AdultFilter.isAdult("Erotik Kanal"))
        assertFalse(AdultFilter.isAdult("TR-HABER"))
        assertFalse(AdultFilter.isAdult("Essex FC")) // "sex" nur als eigenes Wort
        assertFalse(AdultFilter.isAdult("Sussex News"))
    }

    @Test fun keyMap_roundTrip() {
        val m = KeyMap.parse("{}")
        assertEquals(KeyAction.PREVIOUS, m[RemoteKey.OK_SHORT])
        val changed = m + (RemoteKey.UP to KeyAction.LIST)
        val back = KeyMap.parse(KeyMap.toJson(changed))
        assertEquals(KeyAction.LIST, back[RemoteKey.UP])
        assertEquals(KeyAction.QUICK, back[RemoteKey.OK_LONG])
        // kaputte Daten → Standard
        assertEquals(RemoteKey.LEFT.default, KeyMap.parse("kaputt")[RemoteKey.LEFT])
    }

    private val losHtml = """
        <html><body><script>var x = "Fiorentina v Atalanta";</script>
        <div class="comp_head">Italian Serie A - Week 10</div>
        <div class="fLeft">Fiorentina v Atalanta</div>
        <div class="fLeft_time_live">ST: 20:45</div>
        <table><tr><td>
          <a href="#" onmouseover="show('<table><tr><td>satellite</td><td>pos</td><td>freq</td></tr><tr><td>Thor 5</td><td>0.8&deg;w</td><td>12000 V 28000 - 7/8</td></tr></table>')">DAZN 1 Bar Deutsch HD</a>
        </td></tr><tr><td><span>S Sport 2 Türkiye HD</span></td></tr>
        <tr><td>Peacock Premium USA (${'$'}/geo/R)</td></tr>
        <tr><td>Conax</td></tr><tr><td>Thor 6</td></tr><tr><td>30000 - 3/4</td></tr><tr><td>0.8&deg;w</td></tr>
        <tr><td><a href="#">Arena Sport 2 Hrvatska HD</a></td></tr></table>
        <div class="fLeft">Frosinone v Lecce</div>
        <div>ST: 18:30</div>
        <table><tr><td><a>Sky Sport Calcio HD</a></td></tr></table>
        </body></html>
    """.trimIndent()

    @Test fun liveOnSat_readsOnlyChannelNames() {
        val fixtures = LiveOnSat.parse(losHtml)
        assertEquals(2, fixtures.size)
        val f = fixtures[0]
        assertEquals("Fiorentina", f.home)
        assertEquals("Atalanta", f.away)
        assertTrue(f.channels.contains("DAZN 1 Bar Deutsch HD"))
        assertTrue(f.channels.contains("S Sport 2 Türkiye HD"))
        assertTrue(f.channels.contains("Arena Sport 2 Hrvatska HD"))
        // Satelliten-Infos sind keine Sender
        listOf("satellite", "pos", "freq", "Thor 5", "Thor 6", "Conax", "30000 - 3/4", "0.8°w").forEach {
            assertFalse("$it darf kein Sender sein", f.channels.contains(it))
        }
        // nur europäische, deutsche/türkische zuerst
        val eu = LiveOnSat.europeanSorted(f.channels)
        assertFalse(eu.any { it.contains("Peacock") })
        assertTrue(eu.indexOf("DAZN 1 Bar Deutsch HD") < eu.indexOf("Arena Sport 2 Hrvatska HD"))
    }

    @Test fun football_matchesFixturesAndOwnChannels() {
        val match = TodayMatch("1", "Serie A", "ACF Fiorentina", "Atalanta", 1_000_000_000L, "", null, false)
        val ch = LiveOnSat.forMatch(match, LiveOnSat.parse(losHtml))
        assertTrue(ch.isNotEmpty())
        // Zuordnung zu eigenen Sendern über den Namen
        val own = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="TR-SPOR",TR: S SPORT 2 FHD
            http://x/1.ts
            #EXTINF:-1 group-title="AR",AR: Syria Satellite
            http://x/2.ts
            """.trimIndent(),
        )
        val lu = Numbering.build(own, emptyList())
        val idx = Football.nameIndex(lu)
        assertEquals("TR: S SPORT 2 FHD", Football.matchByName("S Sport 2 Türkiye HD", idx).single().channel.name)
    }

    @Test fun football_listedCountryMustMatch() {
        val own = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="TR | ULUSAL",(TR) 7-24 Disney+ 1
            http://x/d1.ts
            #EXTINF:-1 group-title="DE | KIDS",(DE) Disney Channel 4K+
            http://x/d2.ts
            #EXTINF:-1 group-title="TR | ULUSAL",(TR) 7-24 Konusanlar Disney
            http://x/d3.ts
            #EXTINF:-1 group-title="TR | SPOR",(TR) Bein Sports 1 4K ultra
            http://x/b1.ts
            #EXTINF:-1 group-title="DE | SPORT",DE: Sky Sport Bundesliga
            http://x/s1.ts
            """.trimIndent(),
        )
        val idx = Football.nameIndex(Numbering.build(own, emptyList()))
        // Norwegischer Disney+ ist keiner der eigenen (türkischen/deutschen) Disney-Sender
        assertTrue(Football.matchByName("Disney+ Norge (${'$'}/geo/R)", idx).isEmpty())
        // passendes Land wird weiter gefunden
        assertEquals("(TR) Bein Sports 1 4K ultra", Football.matchByName("beIN Sports Türkiye 1 HD", idx).single().channel.name)
        assertEquals("DE: Sky Sport Bundesliga", Football.matchByName("Sky Sport Bundesliga Deutschland", idx).single().channel.name)
        assertTrue(Football.matchByName("Sky Sport Bundesliga Österreich", idx).isEmpty())
        // (TR)-Vorsatz wird als Land erkannt
        assertEquals("tr", Epg.countryOf(own[0]))
    }

    @Test fun football_otherLeagueIsNotThisMatch() {
        val kick = 1_700_000_000_000L
        fun ch(n: String, id: String) = Channel(n, "Sport", null, "http://x/$id.ts", tvgId = id)
        val chans = listOf(ch("DE: Sky Sport Mix", "mix"), ch("DE: Sky Sport Top Event", "top"), ch("TR: beIN Sports 1", "bein"), ch("DE: Sport Live", "live"))
        val p = { t: String -> listOf(Programme(kick - 60 * 60_000, kick + 120 * 60_000, t)) }
        val epg = Epg(
            mapOf("mix" to p("Fußball: Premier League"), "top" to p("Fußball: Bundesliga"), "bein" to p("Trendyol Süper Lig"), "live" to p("Live-Fußball")),
            emptyMap(),
        )
        val lu = Numbering.build(chans, emptyList())
        val m = TodayMatch("3", "Süper Lig", "Fenerbahçe", "Göztepe", kick, "", null, false)
        val found = Football.channelsFor(m, Football.index(epg, lu, kick - 3_600_000, kick + 3_600_000)).map { it.channel.channel.name }
        assertFalse(found.contains("DE: Sky Sport Mix"))
        assertFalse(found.contains("DE: Sky Sport Top Event"))
        assertTrue(found.contains("TR: beIN Sports 1"))
        assertTrue(found.contains("DE: Sport Live")) // allgemeiner Fußball ohne anderen Wettbewerb bleibt „vermutlich“
        // 2. Bundesliga ist nicht Bundesliga (und umgekehrt)
        assertTrue(Football.namesOtherLeague(Football.norm("Fußball: 2. Bundesliga"), "Bundesliga"))
        assertFalse(Football.namesOtherLeague(Football.norm("Fußball: 2. Bundesliga"), "2. Bundesliga"))
        assertTrue(Football.namesOtherLeague(Football.norm("Fußball: Bundesliga"), "2. Bundesliga"))
    }

    @Test fun football_findsChannelInEpg() {
        val kick = 1_700_000_000_000L
        val c = Channel("beIN Sports 1", "Sport", null, "http://x/b1.ts", tvgId = "bein1")
        val epg = Epg(mapOf("bein1" to listOf(Programme(kick - 15 * 60_000, kick + 110 * 60_000, "Süper Lig: Galatasaray - Fenerbahçe"))), emptyMap())
        val lu = Numbering.build(listOf(c), emptyList())
        val m = TodayMatch("2", "Süper Lig", "Galatasaray", "Fenerbahce", kick, "", null, false)
        val idx = Football.index(epg, lu, kick - 3_600_000, kick + 3_600_000)
        val found = Football.channelsFor(m, idx)
        assertEquals(1, found.size)
        assertTrue(found[0].sure)
    }

    @Test fun epg_matchingOrder() {
        val prog = listOf(Programme(0, Long.MAX_VALUE / 2, "Tagesschau"))
        val epg = Epg(
            byId = mapOf("DasErste.de" to prog, "showtv.tr" to prog, "kanal7.tr" to prog),
            idByName = mapOf(
                Epg.nameKey("Show TV", "tr") to "showtv.tr", Epg.nameKey("Kanal 7", "tr") to "kanal7.tr",
                Epg.nameKey("ATV", "at") to "atv.at", "atv|*" to "atv.at",
            ),
        )
        // tvg-id mit anderer Groß-/Kleinschreibung
        assertEquals("DasErste.de", epg.idFor(Channel("ARD", "DE", null, "u1", tvgId = "daserste.de")))
        // Ländervorsatz und HD im Namen
        assertEquals("showtv.tr", epg.idFor(Channel("TR: SHOW TV HD", "TR", null, "u2")))
        assertEquals("showtv.tr", epg.idFor(Channel("|TR| Show TV FHD", "TR", null, "u3")))
        // tvg-name, wenn Anzeigename anders
        assertEquals("kanal7.tr", epg.idFor(Channel("K7 Ulusal", "TR", null, "u4", tvgName = "Kanal 7")))
        // eigene Zuordnung geht vor
        val manual = epg.withManual(mapOf("u2" to "kanal7.tr"))
        assertEquals("kanal7.tr", manual.idFor(Channel("TR: SHOW TV HD", "TR", null, "u2")))
        assertTrue(manual.isManual(Channel("x", "", null, "u2")))
        // türkischer Sender bekommt NICHT das österreichische/deutsche Programm mit gleichem Namen
        assertEquals(null, epg.idFor(Channel("TR: ATV HD", "TR ULUSAL", null, "u6")))
        assertEquals("atv.at", epg.idFor(Channel("ATV", "Österreich", null, "u7")))
        assertEquals("tr", Epg.countryOf(Channel("Haber", "TÜRKIYE | Haber", null, "u8")))
        assertEquals("tr", Epg.urlCountry("https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz"))
        // unbekannt
        assertEquals(null, epg.idFor(Channel("Irgendwas", "", null, "u5")))
    }

    @Test fun m3uParser_readsTvgName() {
        val l = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="Kanal 7" group-title="TR",K7 Ulusal
            http://x/7.ts
            """.trimIndent(),
        )
        assertEquals("Kanal 7", l.single().tvgName)
    }
}
