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
 * Programmdaten (EPG) für die Sender. Zuordnung über tvg-id, sonst über den Sendernamen.
 */
class Epg(
    private val byId: Map<String, List<Programme>>,
    private val idByName: Map<String, String>,
) {
    val channelCount: Int get() = byId.size

    private fun idFor(ch: Channel): String? =
        ch.tvgId?.takeIf { byId.containsKey(it) } ?: idByName[normName(ch.name)]

    fun now(ch: Channel, at: Long = System.currentTimeMillis()): Programme? =
        idFor(ch)?.let { id -> byId[id]?.firstOrNull { it.start <= at && it.stop > at } }

    fun next(ch: Channel, at: Long = System.currentTimeMillis()): Programme? =
        idFor(ch)?.let { id -> byId[id]?.firstOrNull { it.start > at } }

    companion object {
        val EMPTY = Epg(emptyMap(), emptyMap())

        /** Namen vergleichbar machen: "Das Erste HD" ≈ "das erste". */
        // einmal vorbereitet (wird beim Durchblättern der Senderliste sehr oft gebraucht)
        private val qualityRx = Regex("""\b(hd|fhd|uhd|4k|sd|\+1)\b""")
        private val otherRx = Regex("[^a-z0-9äöüßçğışİ]")

        fun normName(s: String): String = s.lowercase(Locale.ROOT)
            .replace(qualityRx, "")
            .replace(otherRx, "")
    }
}

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

    /**
     * Lädt alle EPG-Adressen und behält nur Sendungen der vorhandenen Sender
     * im Zeitfenster von 2 Stunden zurück bis 24 Stunden voraus.
     */
    suspend fun refresh(urls: List<String>, channels: List<Channel>): Result<Epg> = withContext(Dispatchers.IO) {
        runCatching {
            val wantedIds = channels.mapNotNull { it.tvgId }.toHashSet()
            val wantedNames = channels.filter { it.tvgId.isNullOrBlank() }.map { Epg.normName(it.name) }.toHashSet()
            val from = System.currentTimeMillis() - 2 * 3_600_000L
            val to = System.currentTimeMillis() + 24 * 3_600_000L
            val byId = HashMap<String, MutableList<Programme>>()
            val idByName = HashMap<String, String>()
            var anyOk = false
            urls.distinct().forEach { url ->
                val ok = runCatching {
                    val conn = Http.open(url)
                    try {
                        parse(conn.inputStream, wantedIds, wantedNames, from, to, byId, idByName)
                    } finally {
                        conn.disconnect()
                    }
                }.isSuccess
                anyOk = anyOk || ok
            }
            if (!anyOk) error("EPG konnte nicht geladen werden")
            byId.values.forEach { list -> list.sortBy { it.start } }
            save(byId, idByName)
            Epg(byId, idByName)
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
                        val keep = ch != null && (ch in wantedIds || ch in nameIds)
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
                        val norm = Epg.normName(p.text ?: "")
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
