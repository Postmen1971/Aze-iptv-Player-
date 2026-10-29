package de.aze.iptv.data

/** Erkennt Erwachsenen-Inhalte (Gruppen, Sender, Film-Kategorien) an typischen Namen. */
object AdultFilter {
    private val rx = Regex(
        "(?i)(^|[^a-z0-9])(xxx+|adult|adults|18\\+|\\+18|21\\+|\\+21|porn|porno|pornhub|sex|sexy|erotik|erotic|erotica|erotique|" +
            "yeti[sş]kin|playboy|hustler|brazzers|dorcel|redlight|red light|vivid|penthouse|babes|blue ?hustler|venus tv|private tv|" +
            "nsfw|for adults|nur f[uü]r erwachsene|yeti[sş]kinlere|\\+18 only|onlyfans|xvideos|xhamster)([^a-z0-9]|$)",
    )

    fun isAdult(text: String?): Boolean = text != null && rx.containsMatchIn(text)
}
