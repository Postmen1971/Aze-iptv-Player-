package de.aze.iptv.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.aze.iptv.BuildConfig
import de.aze.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class Release(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    /** erwartete Größe in Bytes (0 = unbekannt) */
    val size: Long = 0,
    /** SHA-256 laut GitHub (leer = unbekannt) */
    val sha256: String = "",
)

/**
 * Manuelles App-Update: fragt das neueste Release im GitHub-Repository ab,
 * lädt die APK herunter und übergibt sie an den Android-Installer.
 */
object Updater {

    private val api get() = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    /** Gibt ein neueres Release zurück oder null, wenn die App aktuell ist. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val json = JSONObject(Http.text(api, accept = "application/vnd.github+json"))
        val tag = json.optString("tag_name")
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
        val assets = json.optJSONArray("assets")
        // Passende APK für dieses Gerät wählen: erst nach Prozessor-Typ, sonst "universal", sonst irgendeine.
        val apks = ArrayList<JSONObject>()
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val url = a.optString("browser_download_url")
                // Nur APKs aus diesem GitHub-Repository
                if (a.optString("name").endsWith(".apk") && url.startsWith("https://github.com/${BuildConfig.UPDATE_REPO}/")) apks.add(a)
            }
        }
        val abis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.toList() else emptyList()
        // Passende APK: nach Prozessor-Typ, sonst „universal“ – nie eine beliebige andere
        val apk = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").endsWith("-$abi.apk") } }
            ?: apks.firstOrNull { it.optString("name").contains("universal") }
        if (apk == null || code <= BuildConfig.VERSION_CODE) null
        else Release(
            code, tag.removePrefix("v"), apk.optString("browser_download_url"), json.optString("body"),
            size = apk.optLong("size"),
            sha256 = apk.optString("digest").removePrefix("sha256:").lowercase(),
        )
    }

    private fun apkFile(context: Context): File =
        if (Build.VERSION.SDK_INT >= 24) {
            File(File(context.cacheDir, "updates"), "update.apk")
        } else {
            // Ältere Android-Versionen: der Installer braucht eine für ihn lesbare Datei.
            File(context.getExternalFilesDir(null) ?: context.cacheDir, "update.apk")
        }

    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val f = apkFile(context)
            f.parentFile?.mkdirs()
            // Erst in eine Teil-Datei laden, prüfen, dann umbenennen – nie eine halbe oder fremde APK installieren
            val part = File(f.parentFile, f.name + ".part")
            part.delete()
            try {
                Http.download(release.apkUrl, part, onProgress)
                if (release.size > 0 && part.length() != release.size) {
                    throw java.io.IOException("Download unvollständig (${part.length()} von ${release.size} Bytes)")
                }
                if (release.sha256.length == 64) {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    part.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            md.update(buf, 0, n)
                        }
                    }
                    val got = md.digest().joinToString("") { "%02x".format(it) }
                    if (got != release.sha256) throw java.io.IOException("Prüfsumme stimmt nicht – Update verworfen")
                }
                f.delete()
                if (!part.renameTo(f)) throw java.io.IOException("Update konnte nicht gespeichert werden")
            } catch (e: Exception) {
                part.delete()
                throw e
            }
            f
        }

    /** true = Installer geöffnet; false = zuerst muss "Unbekannte Apps installieren" erlaubt werden. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        } else {
            Uri.fromFile(apk)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
