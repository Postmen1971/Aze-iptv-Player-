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

data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

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
        val apks = ArrayList<Pair<String, String>>()
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name")
                if (n.endsWith(".apk")) apks.add(n to a.optString("browser_download_url"))
            }
        }
        val abis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.toList() else emptyList()
        val apk = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.first.endsWith("-$abi.apk") }?.second }
            ?: apks.firstOrNull { it.first.contains("universal") }?.second
            ?: apks.firstOrNull()?.second
        if (apk == null || code <= BuildConfig.VERSION_CODE) null
        else Release(code, tag.removePrefix("v"), apk, json.optString("body"))
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
            Http.download(release.apkUrl, f, onProgress)
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
