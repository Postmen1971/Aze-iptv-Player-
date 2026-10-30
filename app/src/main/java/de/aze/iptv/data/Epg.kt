package de.aze.iptv.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Eine Sendung. Zeiten in Millisekunden. */
class Programme(val start: Long, val stop: Long, val title: String) {
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/**
 * Programmdaten (EPG) für die Sender. Zuordnung in dieser Reihenfolge:
 *  1. eigene Zuordnung (im Schnellmenü gewählt), 2. tvg-id (auch bei anderer Groß-/Kleinschreibung),
 *  3. tvg-name, 4. Sendername (ohne „HD“, Länderkürzel wie „DE:“ usw.).
 */
class Epg(
    private val byId: Map<String, List<Programme>>,
    private val idByName: Map<String, String>,
    /** Sender-Schlüssel → EPG-Kennung (eigene Zuordnung) */
    private val manual: Map<String, String> = emptyMap(),
) {
    val channelCount: Int get() = byId.size

    private val byLower: Map<String, String> by lazy { byId.keys.associateBy { it.lowercase(Locale.ROOT) } }

    private fun known(id: String?): String? =
        if (id.isNullOrBlank()) null else if (byId.containsKey(id)) id else byLower[id.lowercase(Locale.ROOT)]

    /** Welche EPG-Kennung ein Sender bekommt (null = kein Programm gefunden). */
    fun idFor(ch: Channel): String? =
        known(manual[ch.key])
            ?: known(ch.tvgId)
            ?: ch.tvgName?.let { idByName[normName(it)] }
            ?: idByName[normName(ch.name)]

    fun isManual(ch: Channel): Boolean = manual.containsKey(ch.key)

    fun withManual(m: Map<String, String>): Epg = Epg(byId, idByName, m)

    fun now(ch: Channel, at: Long = System.currentTimeMillis()): Programme? =
        idFor(ch)?.let { id -> byId[id]?.firstOrNull { it.start <= at && it.stop > at } }

    /** Alle geladenen Sendungen eines Senders (zeitlich sortiert) oder null. */
    fun programmes(ch: Channel): List<Programme>? = idFor(ch)?.let { byId[it] }

    fun next(ch: Channel, at: Long = System.currentTimeMillis()): Programme? =
        idFor(ch)?.let { id -> byId[id]?.firstOrNull { it.start > at } }

    companion object {
        val EMPTY = Epg(emptyMap(), emptyMap())

        /** Namen vergleichbar machen: "DE: Das Erste HD" ≈ "Das Erste" ≈ "das erste". */
        // einmal vorbereitet (wird beim Durchblättern der Senderliste sehr oft gebraucht)
        private val prefixRx = Regex("""^\s*(\|?\s*[a-z]{2,4}\s*[:|]\s*)+""")
        private val qualityRx = Regex("""\b(hd|fhd|uhd|4k|sd|hevc|h265|50fps|\+1)\b""")
        private val otherRx = Regex("[^a-z0-9äöüßçğışİ]")

        fun normName(s: String): String = s.lowercase(Locale.ROOT)
            .replace(prefixRx, "")
            .replace(qualityRx, "")
            .replace(otherRx, "")
    }
}

/** Ein Sender aus den EPG-Quellen (für die eigene Zuordnung). */
class EpgChannel(val id: String, val name: String)

/** Lädt XMLTV-Daten speicherschonend (Zeile für Zeile) und hält eine kompakte Kopie auf dem Gerät. */
class EpgRepository(context: Context) {

    private val file = File(context.filesDir, "epg.bin")

    /** Gespeichertes EPG ist älter als [hours] Stunden (oder fehlt). */
    fun isStale(hours: Int): Boolean =
        !file.exists() || System.currentTimeMillis() - file.lastModified() > hours * 3_600_000L

    suspend fun load(): Epg = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext Epg.EMPTY
        runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                val nNames = input.readInt()
                val names = HashMap<String, String>(nNames * 2)
                repeat(nNames) { names[input.readUTF()] = input.readUTF() }
                val nIds = input.readInt()
                val byId = HashMap<String, List<Programme>>(nIds * 2)
                repeat(nIds) {
                    val id = input.readUTF()
                    val n = input.readInt()
                    byId[id] = List(n) { Programme(input.readLong(), input.readLong(), input.readUTF()) }
                }
                Epg(byId, names)
            }
        }.getOrDefault(Epg.EMPTY)
    }

    private val catalogFile = File(context.filesDir, "epg_channels.txt")

    /** Ergebnis der letzten Aktualisierung, z. B. „2 von 3 Quellen geladen“. */
    var lastReport: String = ""
        private set

    /** Alle Sender, die in den EPG-Quellen stehen (für „EPG zuordnen“). */
    suspend fun catalog(): List<EpgChannel> = withContext(Dispatchers.IO) {
        runCatching {
            catalogFile.readLines().mapNotNull { l ->
                val i = l.indexOf('\t')
                if (i <= 0) null else EpgChannel(l.substring(0, i), l.substring(i + 1))
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Lädt alle EPG-Adressen und behält nur Sendungen der vorhandenen Sender
     * im Zeitfenster von 2 Stunden zurück bis 24 Stunden voraus.
     * [manual] = eigene Zuordnungen (Sender-Schlüssel → EPG-Kennung).
     */
    suspend fun refresh(urls: List<String>, channels: List<Channel>, manual: Map<String, String> = emptyMap()): Result<Epg> = withContext(Dispatchers.IO) {
        runCatching {
            val wantedIds = (channels.mapNotNull { it.tvgId } + manual.values).map { it.lowercase(Locale.ROOT) }.toHashSet()
            // Namen aller Sender – auch mit tvg-id, denn fremde EPG-Quellen nutzen oft andere Kennungen
            val wantedNames = HashSet<String>()
            channels.forEach { c ->
                Epg.normName(c.name).takeIf { it.isNotEmpty() }?.let { wantedNames.add(it) }
                c.tvgName?.let { Epg.normName(it) }?.takeIf { it.isNotEmpty() }?.let { wantedNames.add(it) }
            }
            val from = System.currentTimeMillis() - 2 * 3_600_000L
            val to = System.currentTimeMillis() + 24 * 3_600_000L
            val byId = HashMap<String, MutableList<Programme>>()
            val idByName = HashMap<String, String>()
            val catalog = LinkedHashMap<String, String>()
            val list = urls.distinct()
            var ok = 0
            list.forEach { url ->
                val good = runCatching {
                    val conn = Http.open(url)
                    try {
                        parse(conn.inputStream, wantedIds, wantedNames, from, to, byId, idByName, catalog)
                    } finally {
                        conn.disconnect()
                    }
                }.isSuccess
                if (good) ok++
            }
            lastReport = "$ok von ${list.size} EPG-Quellen geladen"
            if (ok == 0) error("EPG konnte nicht geladen werden")
            byId.values.forEach { l -> l.sortBy { it.start } }
            save(byId, idByName)
            runCatching {
                catalogFile.bufferedWriter().use { w ->
                    catalog.forEach { (id, name) -> w.write(id.replace('\t', ' ').replace('\n', ' ')); w.write("\t"); w.write(name.replace('\n', ' ')); w.write("\n") }
                }
            }
            Epg(byId, idByName, manual)
        }
    }

    private fun parse(
        raw: InputStream,
        wantedIds: Set<String>,
        wantedNames: Set<String>,
        from: Long,
        to: Long,
        byId: HashMap<String, MutableList<Programme>>,
        idByName: HashMap<String, String>,
        catalog: HashMap<String, String>,
    ) {
        val buffered = BufferedInputStream(raw, 64 * 1024)
        // gzip erkennen (viele EPG-Dateien sind .xml.gz)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val input: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered

        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT)
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        val nameIds = HashSet<String>(idByName.values)
        var channelId: String? = null
        var progChannel: String? = null
        var progStart = 0L
        var progStop = 0L
        var inTitle = false
        var inDisplayName = false
        var title: String? = null

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "channel" -> channelId = p.getAttributeValue(null, "id")
                    "display-name" -> inDisplayName = channelId != null
                    "programme" -> {
                        val ch = p.getAttributeValue(null, "channel")
                        val keep = ch != null && (ch in nameIds || ch.lowercase(Locale.ROOT) in wantedIds)
                        if (keep) {
                            progStart = parseTime(fmt, p.getAttributeValue(null, "start"))
                            progStop = parseTime(fmt, p.getAttributeValue(null, "stop"))
                            progChannel = if (progStop > from && progStart < to) ch else null
                        } else {
                            progChannel = null
                        }
                        title = null
                    }
                    "title" -> inTitle = progChannel != null && title == null
                }
                XmlPullParser.TEXT -> {
                    if (inDisplayName) {
                        val id = channelId
                        val text = p.text?.trim().orEmpty()
                        if (id != null && text.isNotEmpty() && catalog.size < 40_000 && !catalog.containsKey(id)) catalog[id] = text
                        val norm = Epg.normName(text)
                        if (id != null && norm.isNotEmpty() && norm in wantedNames && !idByName.containsKey(norm)) {
                            idByName[norm] = id
                            nameIds.add(id)
                        }
                    }
                    if (inTitle) title = p.text?.trim()
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "channel" -> channelId = null
                    "display-name" -> inDisplayName = false
                    "title" -> inTitle = false
                    "programme" -> {
                        val ch = progChannel
                        val t = title
                        if (ch != null && !t.isNullOrEmpty() && progStop > progStart) {
                            byId.getOrPut(ch) { ArrayList() }.add(Programme(progStart, progStop, t))
                        }
                        progChannel = null
                    }
                }
            }
            ev = p.next()
        }
    }

    private fun parseTime(fmt: SimpleDateFormat, s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val v = s.trim()
        return runCatching {
            if (v.length >= 14 && v.contains(' ')) fmt.parse(v)?.time ?: 0L
            else SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(v.take(14))?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun save(byId: Map<String, List<Programme>>, idByName: Map<String, String>) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(idByName.size)
            idByName.forEach { (k, v) -> out.writeUTF(k); out.writeUTF(v) }
            out.writeInt(byId.size)
            byId.forEach { (id, list) ->
                out.writeUTF(id)
                out.writeInt(list.size)
                list.forEach { pr ->
                    out.writeLong(pr.start)
                    out.writeLong(pr.stop)
                    out.writeUTF(pr.title.take(200))
                }
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
