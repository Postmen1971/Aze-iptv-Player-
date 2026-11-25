package de.aze.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class AccountInfoTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    // 25.11.2026 12:00 UTC
    private val now = 1_795_608_000_000L
    private val day = 86_400_000L

    @Test
    fun xtreamMitAblauf() {
        val exp = (now + 109 * day + 3_600_000L) / 1000
        val a = AccountInfo.parseXtream(
            """{"user_info":{"username":"x","status":"Active","exp_date":"$exp","is_trial":"0","active_cons":"1","max_connections":"2"}}""", now,
        )!!
        assertEquals(109L, a.daysLeft(now))
        assertEquals(AccountInfo.Level.OK, a.level(now))
        assertEquals("Gültig bis 14.03.2027 · noch 109 Tage · Verbindungen 1 von 2", a.describe(now, berlin))
    }

    @Test
    fun unbegrenztUndZahlAlsZahl() {
        val a = AccountInfo.parseXtream("""{"user_info":{"status":"Active","exp_date":null,"max_connections":1,"active_cons":0}}""", now)!!
        assertNull(a.expiresAt)
        assertEquals("Unbegrenzt gültig · Verbindungen 0 von 1", a.describe(now, berlin))
        val b = AccountInfo.parseXtream("""{"user_info":{"exp_date":"null"}}""", now)!!
        assertNull(b.expiresAt)
    }

    @Test
    fun baldUndAbgelaufen() {
        assertEquals(AccountInfo.Level.WARN, AccountInfo(now + 20 * day).level(now))
        assertEquals(AccountInfo.Level.BAD, AccountInfo(now + 3 * day).level(now))
        val gone = AccountInfo(now - 2 * day, status = "Expired")
        assertEquals(AccountInfo.Level.BAD, gone.level(now))
        assertEquals("Abgelaufen am 23.11.2026 · Status: abgelaufen", gone.describe(now, berlin))
        assertEquals("Gültig bis 25.11.2026 · läuft heute ab", AccountInfo(now + 3_600_000L).describe(now, berlin))
    }

    @Test
    fun ohneUserInfoNichts() {
        assertNull(AccountInfo.parseXtream("""{"error":"x"}""", now))
        assertNull(AccountInfo.parseXtream("kein json", now))
    }

    @Test
    fun speichernUndLaden() {
        val a = AccountInfo(now + 10 * day, "Active", 2, 1, trial = true, checkedAt = now)
        assertEquals(a, AccountInfo.fromJson(a.toJson()))
        val u = AccountInfo(null, checkedAt = now)
        assertEquals(u, AccountInfo.fromJson(u.toJson()))
    }

    @Test
    fun portalDaten() {
        val d = AccountInfo.parsePortalDate("2027-03-14 00:00:00", berlin)!!
        assertEquals("14.03.2027", java.text.SimpleDateFormat("dd.MM.yyyy").apply { timeZone = berlin }.format(d))
        assertEquals(d, AccountInfo.parsePortalDate("2027-03-14", berlin))
        assertEquals(d, AccountInfo.parsePortalDate("14.03.2027", berlin))
        assertEquals(d, AccountInfo.parsePortalDate("March 14, 2027", berlin))
        assertNull(AccountInfo.parsePortalDate("0000-00-00 00:00:00", berlin))
        assertNull(AccountInfo.parsePortalDate("Unlimited", berlin))
    }
}
