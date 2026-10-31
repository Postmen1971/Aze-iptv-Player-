package de.aze.iptv.data

import android.content.Context
import java.io.File

/**
 * Zwischenspeicher automatisch aufräumen – wie „Cache leeren“ in den Android-Einstellungen,
 * nur ohne dass man daran denken muss. Playlists, Favoriten, Einstellungen und EPG bleiben erhalten.
 */
object CacheCleaner {

    /** Beim App-Start im Hintergrund: alles Vorübergehende löschen, Senderlogos/Plakate behalten (haben eigene Obergrenze). */
    fun onStart(context: Context) {
        Thread({
            runCatching {
                val cache = context.cacheDir
                // alles im Zwischenspeicher außer den Bildern: Reste der YouTube-/Videotext-Anzeige, alte Update-Dateien, liveonsat-Seite …
                cache.listFiles()?.forEach { f -> if (f.name != "bilder") f.deleteRecursively() }
                // heruntergeladene Update-APK (nach der Installation nicht mehr nötig)
                context.getExternalFilesDir(null)?.listFiles()?.forEach { f ->
                    if (f.name.startsWith("update.apk")) f.delete()
                }
                // Zwischenspeicher der Web-Anzeige (YouTube, Videotext) – Anmeldungen/Einstellungen darin bleiben
                File(context.applicationInfo.dataDir, "app_webview").walkTopDown().maxDepth(3)
                    .filter { it.isDirectory && (it.name == "Cache" || it.name == "Code Cache" || it.name == "GPUCache") }
                    .toList().forEach { it.deleteRecursively() }
                // abgebrochene Downloads der Senderliste / des EPG
                val old = System.currentTimeMillis() - 3_600_000L
                File(context.filesDir, "playlists").listFiles()?.forEach { f ->
                    if (f.name.endsWith(".tmp") && f.lastModified() < old) f.delete()
                }
                context.filesDir.listFiles()?.forEach { f -> if (f.name.endsWith(".tmp") && f.lastModified() < old) f.delete() }
            }
        }, "aufraeumen").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Größe des Zwischenspeichers in MB (für die Anzeige in den Einstellungen). */
    fun sizeMb(context: Context): Long =
        runCatching {
            (context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) / (1024 * 1024)
        }.getOrDefault(0L)

    /**
     * Von Hand: zusätzlich alle gespeicherten Logos und Plakate löschen.
     * Läuft im Hintergrund – vorher auf dem Bedien-Thread: hunderte MB löschen dauerte so lange, dass Android
     * die App als „reagiert nicht“ beendete. Die Bilder-Ablage leert der Bild-Lader selbst (der hat sie offen);
     * Ordner, die die Web-Anzeige gerade benutzt, bleiben stehen.
     */
    suspend fun clearAll(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val loader = runCatching { coil.Coil.imageLoader(context) }.getOrNull()
        runCatching { loader?.memoryCache?.clear() }
        runCatching { loader?.diskCache?.clear() }
        context.cacheDir.listFiles()?.forEach { f ->
            val n = f.name.lowercase()
            if (n == "bilder" || n.contains("webview") || n.contains("chromium")) return@forEach
            runCatching { f.deleteRecursively() }
        }
    }
}
