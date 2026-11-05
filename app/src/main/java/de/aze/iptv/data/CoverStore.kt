package de.aze.iptv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Eigener Cover-Vorrat auf dem Gerät: jedes Cover einmal klein (200×300, WebP, ~10 KB) gespeichert.
 * Von dort lädt das Raster sofort – ohne Anbieter-Server, ohne Netz.
 * (Der normale Bild-Zwischenspeicher hält die großen Originale und wirft sie bei 250 MB wieder raus.)
 */
object CoverStore {
    private var dir: File? = null
    const val MAX_FILES = 40_000
    @Volatile var count = 0
        private set

    fun init(context: Context) {
        val d = File(context.filesDir, "cover").apply { mkdirs() }
        dir = d
        count = d.list()?.size ?: 0
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun file(key: String): File? = dir?.let { File(it, sha1(key) + ".webp") }
    fun has(key: String): Boolean = file(key)?.exists() == true
    val full: Boolean get() = count >= MAX_FILES

    /** großes Original verkleinern und als kleines WebP ablegen */
    fun saveFrom(src: File, key: String): Boolean = save(key) { o -> BitmapFactory.decodeFile(src.path, o) }
    fun saveFrom(bytes: ByteArray, key: String): Boolean = save(key) { o -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) }

    @Synchronized
    private fun save(key: String, decode: (BitmapFactory.Options) -> Bitmap?): Boolean = runCatching {
        val out = file(key) ?: return false
        if (out.exists()) return true
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return false
        var sample = 1
        while (o.outWidth / (sample * 2) >= 200 && o.outHeight / (sample * 2) >= 300) sample *= 2
        // RGB_565: halber Speicher beim Verkleinern (Cover brauchen keine Transparenz)
        val raw = decode(BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }) ?: return false
        val scale = maxOf(200f / raw.width, 300f / raw.height).coerceAtMost(1f)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use {
            @Suppress("DEPRECATION")
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bmp.compress(fmt, 72, it)
        }
        if (bmp !== raw) bmp.recycle()
        raw.recycle()
        tmp.renameTo(out).also { if (it) count++ }
    }.getOrElse { false } // auch OutOfMemoryError: Cover auslassen, nie abstürzen

    fun sizeMb(): Long = (dir?.listFiles()?.sumOf { it.length() } ?: 0L) / (1024 * 1024)
    fun clear() { dir?.listFiles()?.forEach { it.delete() }; count = 0 }
}
