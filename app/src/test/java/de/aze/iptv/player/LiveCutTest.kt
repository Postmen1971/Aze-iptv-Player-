package de.aze.iptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Doppelte Stücke einer Folge-Verbindung werden anhand der Bild-Zeitstempel übersprungen. */
class LiveCutTest {
    private fun pkt(pid: Int, pusi: Boolean, rai: Boolean = false, payload: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteArray(188) { 0xFF.toByte() }
        b[0] = 0x47
        b[1] = (((if (pusi) 0x40 else 0) or (pid shr 8)) and 0xFF).toByte()
        b[2] = (pid and 0xFF).toByte()
        var p: Int
        if (rai) {
            b[3] = 0x30; b[4] = 1; b[5] = 0x40; p = 6
        } else {
            b[3] = 0x10; p = 4
        }
        System.arraycopy(payload, 0, b, p, payload.size)
        return b
    }

    private fun pat(pmt: Int) = pkt(0, true, payload = byteArrayOf(
        0, 0x00, 0xB0.toByte(), 13, 0, 1, 0xC1.toByte(), 0, 0,
        0, 1, (0xE0 or (pmt shr 8)).toByte(), (pmt and 0xFF).toByte(), 0, 0, 0, 0,
    ))

    private fun ptsBytes(pts: Long) = byteArrayOf(
            (0x21L or (((pts shr 30) and 7L) shl 1)).toInt().toByte(),
            ((pts shr 22) and 0xFFL).toInt().toByte(),
            ((((pts shr 15) and 0x7FL) shl 1) or 1L).toInt().toByte(),
            ((pts shr 7) and 0xFFL).toInt().toByte(),
            (((pts and 0x7FL) shl 1) or 1L).toInt().toByte(),
        )

    private fun audioPes(pts: Long) =
        pkt(0x101, true, payload = byteArrayOf(0, 0, 1, 0xC0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5) + ptsBytes(pts))

    private fun video(pts: Long, key: Boolean): ByteArray {
        val t = byteArrayOf(
            (0x21L or (((pts shr 30) and 7L) shl 1)).toInt().toByte(),
            ((pts shr 22) and 0xFFL).toInt().toByte(),
            ((((pts shr 15) and 0x7FL) shl 1) or 1L).toInt().toByte(),
            ((pts shr 7) and 0xFFL).toInt().toByte(),
            (((pts and 0x7FL) shl 1) or 1L).toInt().toByte(),
        )
        return pkt(0x100, true, key, byteArrayOf(0, 0, 1, 0xE0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5) + t)
    }

    private fun audio() = pkt(0x101, false)

    /**
     * Strom: Bild alle 0,04 s, Schlüsselbild alle [keyEvery] Bilder, PTS ab [from] (Sekunden) für [secs] Sekunden.
     * [audioPes]: Ton mit eigenen Zeitstempeln (sonst nur Fülldaten).
     */
    private fun stream(from: Int, secs: Int, keyEvery: Int = 25, audioPes: Boolean = false): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(pat(0x1000)); o.write(pkt(0x1000, true, payload = byteArrayOf(0, 2)))
        for (f in 0 until secs * 25) {
            val pts = (from * 25L + f) * 3600
            o.write(video(pts, f % keyEvery == 0)); o.write(if (audioPes) audioPes(pts) else audio())
        }
        return o.toByteArray()
    }

    private fun firstAudioPts(b: ByteArray): Long {
        var p = 0
        while (p + 188 <= b.size) {
            if (Ts.pid(b, p) == 0x101) { val t = Ts.pesPts(b, p); if (t >= 0) return t }
            p += 188
        }
        return -1
    }

    private class Fake(private val data: ByteArray) : DataSource {
        private var pos = 0
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun open(dataSpec: DataSpec): Long = C.LENGTH_UNSET.toLong()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= data.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, data.size - pos, 5000) // absichtlich krumme Stücke
            System.arraycopy(data, pos, buffer, offset, n); pos += n; return n
        }
        override fun getUri(): Uri? = null
        override fun close() {}
    }

    private fun readAll(src: EndOnDropDataSource): ByteArray {
        val o = ByteArrayOutputStream()
        val buf = ByteArray(7000)
        while (true) {
            val n = src.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            o.write(buf, 0, n)
        }
        return o.toByteArray()
    }

    private fun firstVideoPts(b: ByteArray): Long {
        var p = 0
        while (p + 188 <= b.size) { val t = Ts.videoPts(b, p); if (t >= 0) return t; p += 188 }
        return -1
    }

    @Test fun firstConnection_passesEverything_andRemembersNewestPicture() {
        val track = PtsTrack()
        val data = stream(100, 20)
        val src = EndOnDropDataSource(Fake(data), track); src.begin(-1)
        assertEquals(data.size, readAll(src).size)
        assertEquals((100 * 25L + 20 * 25 - 1) * 3600, track.last)
    }

    @Test fun rejoin_skipsWhatWasAlreadyShown_startsAtKeyframe() {
        val track = PtsTrack()
        // alte Verbindung: Sekunde 100–125; neue liefert wieder ab Sekunde 103 (22 s doppelt)
        EndOnDropDataSource(Fake(stream(100, 25)), track).also { it.begin(-1); readAll(it) }
        val src = EndOnDropDataSource(Fake(stream(103, 40)), track); src.begin(track.last)
        val out = readAll(src)
        assertEquals(0x47, out[0].toInt()) // beginnt mit Tabelle (PAT)
        assertEquals(125 * 25L * 3600, firstVideoPts(out)) // erstes Schlüsselbild nach dem Gezeigten
        assertTrue(out.size < stream(103, 40).size)
    }

    @Test fun rejoin_withoutOverlap_changesNothing() {
        val track = PtsTrack()
        EndOnDropDataSource(Fake(stream(100, 10)), track).also { it.begin(-1); readAll(it) }
        val data = stream(115, 10)
        val src = EndOnDropDataSource(Fake(data), track); src.begin(track.last)
        assertEquals(data.size, readAll(src).size)
    }

    @Test fun rejoin_unrelatedTimeline_changesNothing() {
        val track = PtsTrack()
        EndOnDropDataSource(Fake(stream(5000, 10)), track).also { it.begin(-1); readAll(it) }
        val data = stream(10, 10) // Zeitrechnung neu gestartet
        val src = EndOnDropDataSource(Fake(data), track); src.begin(track.last)
        assertEquals(data.size, readAll(src).size)
    }

    @Test fun rejoin_onlyOldData_endsWithoutOutput() {
        val track = PtsTrack()
        EndOnDropDataSource(Fake(stream(100, 25)), track).also { it.begin(-1); readAll(it) }
        val src = EndOnDropDataSource(Fake(stream(105, 15)), track); src.begin(track.last)
        assertEquals(0, readAll(src).size)
    }

    /** Liefert bei jedem Öffnen das nächste Stück (wie ein Anbieter, der beim zweiten Versuch weiter ist). */
    private class Seq(private val parts: List<ByteArray>) : DataSource {
        private var i = 0
        private var pos = 0
        fun next() { i++; pos = 0 }
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun open(dataSpec: DataSpec): Long = C.LENGTH_UNSET.toLong()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val d = parts.getOrNull(i) ?: return C.RESULT_END_OF_INPUT
            if (pos >= d.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, d.size - pos, 5000)
            System.arraycopy(d, pos, buffer, offset, n); pos += n; return n
        }
        override fun getUri(): Uri? = null
        override fun close() {}
    }

    @Test fun rejoin_onlyOldData_reconnectsItself_insteadOfEmptyPiece() {
        val track = PtsTrack()
        EndOnDropDataSource(Fake(stream(100, 25)), track).also { it.begin(-1); readAll(it) }
        // 1. Versuch: nur schon Gezeigtes (105–120 s); 2. Versuch: Anbieter ist weiter (120–130 s)
        val up = Seq(listOf(stream(105, 15), stream(120, 10)))
        val src = EndOnDropDataSource(up, track)
        src.begin(track.last)
        src.reopen = { up.next() }
        val out = readAll(src)
        assertTrue(out.isNotEmpty())
        assertTrue(firstVideoPts(out) >= 125 * 25L * 3600)
    }

    @Test fun newChannel_oldConnectionCannotOverwriteTimestamp() {
        val track = PtsTrack()
        val old = EndOnDropDataSource(Fake(stream(100, 25)), track)
        old.begin(-1)
        // neuer Sender wird gestartet, während die alte Verbindung noch Daten liefert
        track.gen++; track.last = -1
        readAll(old)
        assertEquals(-1L, track.last)
    }

    @Test fun rejoin_soundContinuesAtCut_pictureAtNextKeyframe() {
        val track = PtsTrack()
        // alt: 100,00–112,96 s; neu ab 104 s, Schlüsselbild alle 2 s (104, 106 … 112, 114)
        EndOnDropDataSource(Fake(stream(100, 13, audioPes = true)), track).also { it.begin(-1); readAll(it) }
        val src = EndOnDropDataSource(Fake(stream(104, 20, keyEvery = 50, audioPes = true)), track); src.begin(track.last)
        val out = readAll(src)
        assertEquals(113 * 25L * 3600, firstAudioPts(out)) // Ton nahtlos ab dem ersten neuen Stück
        assertEquals(114 * 25L * 3600, firstVideoPts(out)) // Bild ab dem nächsten Schlüsselbild
    }
}
