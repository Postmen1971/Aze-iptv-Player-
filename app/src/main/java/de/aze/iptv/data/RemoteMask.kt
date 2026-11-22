package de.aze.iptv.data

/**
 * Fernhilfe: Der Helfer soll Playlists ändern können, aber die Geheimnisse des Nutzers nicht zu sehen bekommen
 * (Prüfung 22.11.: bisher gingen Passwort, Passwort im M3U-Link und die volle MAC verschlüsselt zum Helfer).
 * Statt der Werte schickt die App [HIDDEN]. Kommt der Platzhalter unverändert zurück, bleibt der bisherige Wert;
 * tippt der Helfer etwas Neues ein, gilt das Neue. Reine Rechenregel ohne Android (Unit-Test RemoteMaskTest).
 */
object RemoteMask {
    const val HIDDEN = "••••••"

    /** Passwort im Link, z. B. get.php?username=…&password=… (Gruppe 1 = ? oder &, 2 = Name, 3 = Wert) */
    private val URL_SECRET = Regex("([?&])(password|pass|pwd)=([^&#]*)", RegexOption.IGNORE_CASE)

    /** Was der Helfer zu sehen bekommt */
    fun forHelper(s: PlaylistSource): PlaylistSource = s.copy(
        pass = if (s.pass.isEmpty()) "" else HIDDEN,
        url = s.url.replace(URL_SECRET) { m -> if (m.groupValues[3].isEmpty()) m.value else m.groupValues[1] + m.groupValues[2] + "=" + HIDDEN },
        // Portal: die MAC ist der Zugang – Herstellerteil (00:1A:79) bleibt zur Kontrolle sichtbar
        user = if (s.type == SourceType.STALKER && s.user.length > 9) s.user.take(9) + HIDDEN else s.user,
    )

    /**
     * Änderung vom Helfer zurück auf die echten Werte. [old] = die Playlist, wie sie auf dem Gerät gespeichert ist
     * (null bei neuer Playlist). null = ein Platzhalter ließ sich nicht zurückführen (dann nichts übernehmen).
     */
    fun fromHelper(s: PlaylistSource, old: PlaylistSource?): PlaylistSource? {
        var ok = true
        val oldUrl = old?.let { o -> URL_SECRET.findAll(o.url).associate { it.groupValues[2].lowercase() to it.groupValues[3] } } ?: emptyMap()
        val url = s.url.replace(URL_SECRET) { m ->
            if (m.groupValues[3] != HIDDEN) m.value
            else oldUrl[m.groupValues[2].lowercase()]?.let { m.groupValues[1] + m.groupValues[2] + "=" + it } ?: run { ok = false; m.value }
        }
        val pass = if (s.pass == HIDDEN) old?.pass ?: run { ok = false; "" } else s.pass
        val user = if (s.user.endsWith(HIDDEN)) {
            val prefix = s.user.removeSuffix(HIDDEN)
            old?.user?.takeIf { it.startsWith(prefix, ignoreCase = true) } ?: run { ok = false; s.user }
        } else s.user
        if (!ok || HIDDEN in url || HIDDEN in user || HIDDEN in pass) return null
        return s.copy(url = url, pass = pass, user = user)
    }
}
