package de.aze.iptv.player

/**
 * Zäher Hänger: Das Bild steht (Player puffert), es kommen aber noch Daten – nur viel langsamer als Echtzeit.
 *
 * Aziz' Protokoll 22.11. (1.0.317): die meisten Sekunden ohne Bild waren genau das (DAS ERSTE 4/3/5/4/5 s, PROSIEBEN FUN
 * bis 10 s, RTL 6/9 s, TRT 4K 3/7 s – „puffert · Vorrat 0,5–1,5 s · Ton ja“, kein Wächter). Weil noch etwas kam, griff
 * der Wächter („keine neuen Daten“) nie ein; der Player wartete, bis der Wiederanlauf-Vorrat (Mittel: 4 s) tröpfchenweise
 * voll war. Wo der Wächter dagegen neu verbunden hatte, war das Bild fast immer nach 1 s wieder da: dieser Anbieter
 * schickt jeder neuen Verbindung erst einen Schwung im Schnelldurchlauf.
 *
 * Regel: Dauert das Warten bei der gemessenen Liefergeschwindigkeit noch deutlich länger als ein Neuverbinden, neu
 * verbinden. Bei Sendern, bei denen Neuverbinden schon einmal schnell geholfen hat, früher. Hat es nicht geholfen
 * (z. B. langsame Leitung zu Hause – da bringt eine neue Verbindung keinen Schwung), für diesen Sender eine Weile aus.
 * Reine Rechenregel ohne Android, damit sie im Unit-Test geprüft wird.
 */
internal object SlowStall {
    /** Länger als das noch warten → neu verbinden lohnt sich (Neuverbinden + Schwung dauert ~1 s) */
    const val WORTH_MS = 2_500L
    /** frühestens nach so vielen Sekunden Hänger – bei Sendern, wo Neuverbinden bewährt ist, schon nach 3 s */
    const val MIN_SEC_UNKNOWN = 5
    const val MIN_SEC_PROVEN = 3
    /** Neuverbinden hat geholfen, wenn das Bild spätestens nach so vielen Sekunden wieder läuft */
    const val HELPED_SEC = 3
    /** Hat es nicht geholfen: so lange für diesen Sender keine weiteren Versuche */
    const val BLOCK_MS = 10 * 60_000L

    /**
     * Wie lange es bei gleichbleibender Lieferung noch dauert, bis der Player von selbst weiterspielt (ms).
     * [gainedMs] = so viel Vorrat ist in den [stallMs] seit Beginn des Hängers dazugekommen. Long.MAX_VALUE = kommt nie.
     */
    fun remainingMs(aheadMs: Long, resumeAtMs: Long, gainedMs: Long, stallMs: Long): Long {
        if (aheadMs >= resumeAtMs) return 0
        if (gainedMs <= 0 || stallMs <= 0) return Long.MAX_VALUE
        val rate = gainedMs.toDouble() / stallMs
        // unter 5 % Echtzeit: praktisch nichts
        if (rate < 0.05) return Long.MAX_VALUE
        return ((resumeAtMs - aheadMs) / rate).toLong()
    }

    /**
     * Neu verbinden? Nur bei zähem Nachschub ([gainedMs] > 0 in [windowMs]) – kommt gar nichts mehr, ist das der normale
     * Wächter („starved“). [stallSec] = Sekunden-Takte im Hänger. [proven] = bei diesem Sender hat Neuverbinden schon
     * schnell geholfen.
     */
    fun shouldReconnect(stallSec: Int, aheadMs: Long, resumeAtMs: Long, gainedMs: Long, windowMs: Long, proven: Boolean): Boolean {
        if (gainedMs <= 0) return false
        if (stallSec < (if (proven) MIN_SEC_PROVEN else MIN_SEC_UNKNOWN)) return false
        return remainingMs(aheadMs, resumeAtMs, gainedMs, windowMs) > WORTH_MS
    }

    /** Kommt gar nichts mehr: nach so vielen Sekunden greift der Wächter ein (bisher immer 5). */
    fun starvedLimit(firstFrameSeen: Boolean, proven: Boolean): Int = when {
        !firstFrameSeen -> 8
        proven -> MIN_SEC_PROVEN
        else -> 5
    }
}
