package de.aze.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Lädt Playlists aus dem Netz und hält eine Kopie auf dem Gerät, damit die App sofort startet. */
class Repository(context: Context) {

    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }

    private fun cacheFile(source: PlaylistSource) = File(dir, "${source.id}.m3u")

    suspend fun loadCached(sources: List<PlaylistSource>): List<Channel> = withContext(Dispatchers.IO) {
        sources.flatMap { src ->
            val f = cacheFile(src)
            if (f.exists()) {
                runCatching { M3uParser.parse(f.readText()).map { it.copy(source = src.id) } }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }

    fun hasCache(sources: List<PlaylistSource>): Boolean = sources.any { cacheFile(it).exists() }

    /** Lädt alle Quellen neu. Gibt Fehlermeldungen pro Quelle zurück (leer = alles gut). */
    suspend fun refresh(sources: List<PlaylistSource>): List<String> = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        sources.forEach { src ->
            try {
                val text = Http.text(src.m3uUrl())
                if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true) && !text.contains("#EXTINF")) {
                    errors.add("${src.name}: Antwort ist keine M3U-Liste")
                } else {
                    val tmp = File(dir, "${src.id}.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(cacheFile(src))
                }
            } catch (e: Exception) {
                errors.add("${src.name}: ${e.message ?: "Fehler beim Laden"}")
            }
        }
        errors
    }

    fun delete(source: PlaylistSource) {
        cacheFile(source).delete()
    }
}
